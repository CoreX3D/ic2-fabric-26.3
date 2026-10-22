#!/usr/bin/env python3
"""Audit natural vanilla recipe-book discovery, optionally add missing advancements.

Only ordinary visible crafting and furnace recipes belong to this audit. Machine,
gradual, jetpack-attachment and explicitly hidden recipes keep their existing
behavior. --write-missing creates missing JSON files; it never rewrites a recipe
or an existing advancement. Registry/tag checks use the exact target jars when
provided. This static audit complements Minecraft's runtime codec/book tests.
"""
from __future__ import annotations

import argparse
import io
import json
import re
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path


BOOK_TYPES = {
    "ic2:shaped", "ic2:shapeless", "minecraft:crafting_shaped",
    "minecraft:crafting_shapeless", "minecraft:smelting",
}
FLUID_CONTAINERS = {
    # These are also the fixed item identities used by RecipeInputFluidContainer
    # for safe native recipe-book displays and placement. Filled universal cells
    # remain accepted by crafting; discovery does not constrain recipe matching.
    "minecraft:water": "minecraft:water_bucket",
    "minecraft:lava": "minecraft:lava_bucket",
}
IDENTIFIER = re.compile(r"[a-z0-9_.-]+:[a-z0-9_./-]+\Z")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


def decode(raw):
    return json.loads(raw, object_pairs_hook=unique_object)


def load_resources(root):
    resources = {}
    for source in (root / "src/main/resources", root / "src/generated/resources"):
        for path in sorted((source / "data").rglob("*.json")):
            key = path.relative_to(source).as_posix()
            if key in resources:
                raise ValueError(f"duplicate main/generated resource: {key}")
            resources[key] = decode(path.read_text(encoding="utf-8"))
    return resources


def recipe_id(path):
    parts = path.split("/")
    return parts[1] + ":" + "/".join(parts[3:])[:-5]


def recipe_inputs(recipe):
    if "key" in recipe:
        return list(recipe["key"].values())
    if "ingredients" in recipe:
        return recipe["ingredients"]
    return [recipe["ingredient"]]


def selectors(value):
    """Return item IDs / #tag IDs representing any ingredient alternative."""
    if isinstance(value, str):
        return {value}
    if isinstance(value, list):
        return set().union(*(selectors(child) for child in value))
    if isinstance(value, dict):
        if "item" in value:
            return {value["item"]}
        if "tag" in value:
            return {"#" + value["tag"]}
        if "fluid" in value and value.get("amount", 1000) <= 1000:
            if value["fluid"] in FLUID_CONTAINERS:
                return {FLUID_CONTAINERS[value["fluid"]]}
    raise ValueError(f"ingredient needs a reviewed discovery predicate: {value!r}")


def make_advancement(rid, recipe):
    inputs = sorted(set().union(*(selectors(value) for value in recipe_inputs(recipe))))
    if not inputs:
        raise ValueError(f"{rid}: no discoverable ingredients")
    criteria = {}
    for index, value in enumerate(inputs, 1):
        criteria[f"has_ingredient_{index}"] = {
            "trigger": "minecraft:inventory_changed",
            "conditions": {"items": [{"items": value if value.startswith("#") else [value]}]},
        }
    criteria["has_the_recipe"] = {
        "trigger": "minecraft:recipe_unlocked",
        "conditions": {"recipes": rid},
    }
    return {
        "parent": "minecraft:recipes/root",
        "criteria": criteria,
        # One inner list is OR, matching vanilla recipe discovery. Ingredient
        # amounts, durability and fluid/NBT checks still belong to the recipe.
        "requirements": [list(criteria)],
        "rewards": {"recipes": [rid]},
    }


def audit(root, minecraft_jar, fabric_jar, write_missing):
    resources = load_resources(root)
    recipes = {recipe_id(path): data for path, data in resources.items()
               if path.startswith("data/ic2/recipe/")}
    advancements = {path: data for path, data in resources.items()
                    if path.startswith("data/ic2/advancement/")}

    def rewards_by_recipe():
        result = defaultdict(list)
        for path, data in advancements.items():
            for rid in data.get("rewards", {}).get("recipes", []):
                result[rid].append(path)
        return result

    rewards = rewards_by_recipe()
    visible = {rid: data for rid, data in recipes.items()
               if data.get("type") in BOOK_TYPES and not data.get("hidden", False)}
    created = []
    if write_missing:
        for rid, recipe in sorted(visible.items()):
            if rid in rewards:
                continue
            namespace, name = rid.split(":", 1)
            path = f"data/{namespace}/advancement/recipes/{name}.json"
            target = root / "src/main/resources" / path
            if path in resources or target.exists():
                raise ValueError(f"refusing to overwrite advancement: {path}")
            data = make_advancement(rid, recipe)
            target.parent.mkdir(parents=True, exist_ok=True)
            # Exclusive creation also protects against a concurrent addition.
            with target.open("x", encoding="utf-8", newline="\n") as output:
                json.dump(data, output, ensure_ascii=False, indent=2)
                output.write("\n")
            advancements[path] = data
            created.append(rid)
        rewards = rewards_by_recipe()

    errors = []
    item_source = (root / "src/main/java/ic2/core/ref/Ic2Items.java").read_text(encoding="utf-8")
    items = {"ic2:" + name for name in re.findall(r'\bregister\(\s*"([a-z0-9_/]+)"', item_source)}
    fluid_source = (root / "src/main/java/ic2/core/ref/Ic2Fluids.java").read_text(encoding="utf-8")
    items.update("ic2:" + name + "_bucket"
                 for name in re.findall(r'\bcreate\(\s*"([a-z0-9_]+)"', fluid_source))
    tags = defaultdict(list)
    external = set()

    def add_tag(path, data):
        parts = path.split("/")
        if len(parts) >= 5 and parts[0] == "data" and parts[2:4] == ["tags", "item"]:
            tid = parts[1] + ":" + "/".join(parts[4:])[:-5]
            if data.get("replace", False):
                tags[tid] = []
            tags[tid].extend(data.get("values", []))

    def load_archive(archive):
        for path in archive.namelist():
            if path.startswith("assets/minecraft/items/") and path.endswith(".json"):
                items.add("minecraft:" + path[len("assets/minecraft/items/"):-5])
            if path.startswith("data/") and path.endswith(".json"):
                external.add(path)
                if "/tags/item/" in path:
                    add_tag(path, decode(archive.read(path)))

    if minecraft_jar:
        with zipfile.ZipFile(minecraft_jar) as archive:
            if decode(archive.read("version.json")).get("id") != "26.3":
                errors.append("target Minecraft jar is not version 26.3")
            load_archive(archive)
    if fabric_jar:
        with zipfile.ZipFile(fabric_jar) as archive:
            load_archive(archive)
            for path in archive.namelist():
                if path.startswith("META-INF/jars/") and path.endswith(".jar"):
                    with zipfile.ZipFile(io.BytesIO(archive.read(path))) as nested:
                        load_archive(nested)
    for path, data in resources.items():
        add_tag(path, data)

    def check_item(value, origin):
        if not isinstance(value, str) or not IDENTIFIER.fullmatch(value):
            errors.append(f"{origin}: invalid item ID {value!r}")
        elif value.startswith("ic2:") or minecraft_jar:
            if value not in items:
                errors.append(f"{origin}: unknown item {value}")

    def expand_tag(tid, origin, active=()):
        if tid in active:
            errors.append(f"{origin}: recursive item tag {tid}")
            return set()
        if tid not in tags:
            if tid.startswith("ic2:") or minecraft_jar and fabric_jar:
                errors.append(f"{origin}: missing item tag #{tid}")
            return set()
        result = set()
        for entry in tags[tid]:
            value = entry.get("id") if isinstance(entry, dict) else entry
            required = not isinstance(entry, dict) or entry.get("required", True)
            if not isinstance(value, str):
                errors.append(f"{origin}: malformed entry in #{tid}")
            elif value.startswith("#"):
                if required or value[1:] in tags:
                    result.update(expand_tag(value[1:], origin, (*active, tid)))
            elif required or value in items:
                check_item(value, origin)
                result.add(value)
        return result

    counts = Counter()
    for path, advancement in advancements.items():
        awarded = advancement.get("rewards", {}).get("recipes", [])
        if not awarded:
            continue
        counts["recipe_reward_advancements"] += 1
        for rid in awarded:
            if rid not in recipes:
                errors.append(f"{path}: missing rewarded recipe {rid}")
        criteria = advancement.get("criteria", {})
        requirements = advancement.get("requirements", [])
        if not criteria or not requirements or any(not group for group in requirements):
            errors.append(f"{path}: missing criteria/requirements")
        for group in requirements:
            for name in group:
                if name not in criteria:
                    errors.append(f"{path}: unknown required criterion {name}")
        inventory_names = set()
        self_names = set()
        for name, criterion in criteria.items():
            trigger = criterion.get("trigger")
            conditions = criterion.get("conditions", {})
            if trigger == "minecraft:recipe_unlocked":
                if conditions.get("recipes") not in awarded:
                    errors.append(f"{path}: recipe_unlocked must refer to its awarded recipe")
                self_names.add(name)
            elif trigger == "minecraft:inventory_changed":
                inventory_names.add(name)
                predicates = conditions.get("items", [])
                if not predicates:
                    errors.append(f"{path}: inventory trigger lacks item predicates")
                for predicate in predicates:
                    values = predicate.get("items")
                    if isinstance(values, str) and values.startswith("#"):
                        tid = values[1:]
                        if not IDENTIFIER.fullmatch(tid):
                            errors.append(f"{path}: invalid item tag {values!r}")
                        resolved = expand_tag(tid, path)
                        if minecraft_jar and fabric_jar and not resolved:
                            errors.append(f"{path}: empty discovery item tag {values}")
                    elif isinstance(values, list) and values:
                        for value in values:
                            check_item(value, path)
                    else:
                        errors.append(f"{path}: unsupported native item predicate {predicate!r}")
            else:
                errors.append(f"{path}: recipe discovery has unexpected trigger {trigger!r}")
        if not inventory_names or not self_names:
            errors.append(f"{path}: discovery needs an inventory and recipe-unlocked criterion")
        if len(requirements) != 1 or set(requirements[0]) != set(criteria):
            errors.append(f"{path}: discovery should OR its ingredient/self-unlock criteria")
        if advancement.get("parent") != "minecraft:recipes/root":
            errors.append(f"{path}: unexpected recipe discovery parent")

    missing = sorted(set(visible) - rewards.keys())
    errors.extend(f"missing natural recipe discovery: {rid}" for rid in missing)
    duplicates = {rid: paths for rid, paths in sorted(rewards.items()) if len(paths) > 1}
    # Legacy smelting data has equivalent misc/material-category advancements.
    # They award the same recipe idempotently; preserve those existing files.
    for rid in created:
        if len(rewards[rid]) != 1:
            errors.append(f"new advancement duplicates an existing reward: {rid}")
    return {
        "status": "failed" if errors else "passed",
        "visible_book_recipes": len(visible),
        "visible_crafting_recipes": sum(v["type"] != "minecraft:smelting" for v in visible.values()),
        "visible_smelting_recipes": sum(v["type"] == "minecraft:smelting" for v in visible.values()),
        "recipe_reward_advancements": counts["recipe_reward_advancements"],
        "unique_rewarded_recipes": len(rewards),
        "legacy_duplicate_reward_ids": len(duplicates),
        "created_advancements": len(created),
        "created_by_recipe_type": dict(Counter(recipes[rid]["type"] for rid in created)),
        "missing_discovery": missing,
        "vanilla_registry_checked": bool(minecraft_jar),
        "fabric_tags_checked": bool(fabric_jar),
        "errors": sorted(set(errors)),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--minecraft-jar", type=Path)
    parser.add_argument("--fabric-api-jar", type=Path)
    parser.add_argument("--write-missing", action="store_true")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    try:
        report = audit(args.root, args.minecraft_jar, args.fabric_api_jar, args.write_missing)
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    result = json.dumps(report, ensure_ascii=False, indent=2)
    print(result)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(result + "\n", encoding="utf-8")
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())

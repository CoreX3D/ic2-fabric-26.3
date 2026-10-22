#!/usr/bin/env python3
"""Check material identity, 26.3 data shapes and deterministic metal recipe cycles.

This is a static resource check, not a substitute for loading Minecraft's codecs.
Supply the target Minecraft and Fabric API jars to validate vanilla IDs/common tags.
No jars are executed and no files are changed.
"""
from __future__ import annotations

import argparse
import io
import json
import math
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path


FORBIDDEN = {
    "ic2:copper_ore", "ic2:deepslate_copper_ore", "ic2:copper_ingot",
    "ic2:copper_block", "ic2:iron_ingot", "ic2:gold_ingot",
    "ic2:basalt", "ic2:industrial_diamond",
}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


def decode(raw):
    return json.loads(raw, object_pairs_hook=unique_object)


def walk(value):
    yield value
    if isinstance(value, dict):
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def check_resources(root: Path, minecraft_jar: Path | None, fabric_jar: Path | None):
    errors = []
    resources = {}
    source_paths = {}
    tags = defaultdict(list)
    vanilla_items = set()
    external_resources = set()
    counts = Counter()

    def require(ok, message):
        if not ok:
            errors.append(message)

    def add_tags(path, data):
        parts = path.split("/")
        if len(parts) >= 5 and parts[0] == "data" and parts[2:4] == ["tags", "item"]:
            tag = parts[1] + ":" + "/".join(parts[4:])[:-5]
            if data.get("replace", False):
                tags[tag] = []
            tags[tag].extend(data.get("values", []))

    def load_archive(archive):
        for name in archive.namelist():
            if name.startswith("assets/minecraft/items/") and name.endswith(".json"):
                vanilla_items.add("minecraft:" + name[len("assets/minecraft/items/"):-5])
            if name.startswith("data/") and name.endswith(".json"):
                external_resources.add(name)
                if "/tags/item/" in name:
                    add_tags(name, decode(archive.read(name)))

    if minecraft_jar:
        with zipfile.ZipFile(minecraft_jar) as archive:
            version = decode(archive.read("version.json"))
            require(version.get("id") == "26.3", f"Expected vanilla 26.3, got {version.get('id')}")
            load_archive(archive)
    if fabric_jar:
        with zipfile.ZipFile(fabric_jar) as archive:
            load_archive(archive)
            for name in archive.namelist():
                if name.startswith("META-INF/jars/") and name.endswith(".jar"):
                    with zipfile.ZipFile(io.BytesIO(archive.read(name))) as nested:
                        load_archive(nested)

    for source in (root / "src/main/resources", root / "src/generated/resources"):
        for path in sorted((source / "data").rglob("*.json")):
            key = path.relative_to(source).as_posix()
            try:
                data = decode(path.read_text(encoding="utf-8"))
            except (ValueError, UnicodeError) as exc:
                errors.append(f"{key}: {exc}")
                continue
            require(key not in resources, f"Duplicate main/generated resource: {key}")
            resources[key] = data
            source_paths[key] = path
            add_tags(key, data)
            counts["json"] += 1
            for node in walk(data):
                if isinstance(node, str):
                    require(node not in FORBIDDEN, f"{key}: duplicate vanilla material {node}")
                elif isinstance(node, dict):
                    for item in (node.get("item"), node.get("id") if key.find("/recipe/") >= 0 else None):
                        if vanilla_items and isinstance(item, str) and item.startswith("minecraft:"):
                            require(item in vanilla_items, f"{key}: unknown vanilla item {item}")

    def expand_tag(tag, active=()):
        if tag in active:
            errors.append(f"Recursive item tag: {' -> '.join((*active, tag))}")
            return set()
        result = set()
        for value in tags.get(tag, []):
            required = not isinstance(value, dict) or value.get("required", True)
            item = value.get("id") if isinstance(value, dict) else value
            if not isinstance(item, str):
                continue
            if item.startswith("#"):
                if required and minecraft_jar and fabric_jar:
                    require(item[1:] in tags, f"{tag}: missing required tag {item}")
                result.update(expand_tag(item[1:], (*active, tag)))
            else:
                if vanilla_items and item.startswith("minecraft:"):
                    require(item in vanilla_items, f"{tag}: unknown vanilla item {item}")
                result.add(item)
        return result

    expanded_tags = {tag: expand_tag(tag) for tag in list(tags)}

    def ingredients(value):
        if isinstance(value, str):
            if value.startswith("#"):
                return [(item, 1) for item in expanded_tags.get(value[1:], set())]
            return [(value, 1)]
        if isinstance(value, list):
            return [item for option in value for item in ingredients(option)]
        if isinstance(value, dict):
            count = value.get("count", 1)
            if "item" in value:
                return [(value["item"], count)]
            if "tag" in value:
                return [(item, count) for item in expanded_tags.get(value["tag"], set())]
        return []

    def result_stack(value):
        if isinstance(value, str):
            return value, 1
        if isinstance(value, dict):
            return value.get("id", value.get("item")), value.get("count", 1)
        return None, 0

    recipes = {key: data for key, data in resources.items() if "/recipe/" in key}

    def require_recipe_reference(reference, source):
        if not isinstance(reference, str) or reference.startswith("#") or ":" not in reference:
            return
        namespace, path = reference.split(":", 1)
        if namespace == "ic2" or minecraft_jar:
            target = f"data/{namespace}/recipe/{path}.json"
            require(target in recipes or target in external_resources,
                    f"{source}: missing referenced recipe {reference}")

    for key, data in recipes.items():
        counts["recipes"] += 1
        kind = data.get("type", "")
        if kind.startswith("minecraft:"):
            for field in ("ingredient", "ingredients"):
                if field in data:
                    require(not any(isinstance(n, dict) and ("item" in n or "tag" in n)
                                    for n in walk(data[field])), f"{key}: legacy vanilla ingredient")
            require(isinstance(data.get("result"), dict) and "id" in data["result"],
                    f"{key}: native result must use id")
        for node in walk(data):
            if isinstance(node, dict) and isinstance(node.get("tag"), str) and minecraft_jar and fabric_jar:
                require(node["tag"] in tags, f"{key}: missing ingredient item tag {node['tag']}")
        if vanilla_items:
            for item, _ in ingredients(data.get("ingredient")):
                if item.startswith("minecraft:"):
                    require(item in vanilla_items, f"{key}: invalid vanilla ingredient {item}")

    for material in ("copper", "iron", "gold"):
        expectations = {
            f"raw_{material}_to_crushed_{material}": (f"ic2:crushed_{material}", 2),
            f"raw_{material}_block_to_raw_{material}": (f"ic2:crushed_{material}", 18),
            f"{material}_ores_to_raw_{material}": (f"ic2:crushed_{material}", 7 if material == "copper" else 2),
            f"{material}_ingot_to_{material}_dust": (f"ic2:{material}_dust", 1),
        }
        for name, expected in expectations.items():
            key = f"data/ic2/recipe/macerator/{name}.json"
            recipe = recipes.get(key, {})
            require(result_stack(recipe.get("result")) == expected, f"{key}: expected {expected}")
            require(not recipe.get("weighted", False), f"{key}: balance must be deterministic")
        for form in (f"{material}_dust", f"crushed_{material}", f"purified_{material}"):
            key = f"data/ic2/recipe/smelting/{material}_ingot_from_{form}.json"
            require(result_stack(recipes.get(key, {}).get("result")) == (f"minecraft:{material}_ingot", 1),
                    f"{key}: smelting must return one vanilla ingot")
        compression = recipes.get(f"data/ic2/recipe/compressor/raw_{material}_to_raw_{material}_block.json", {})
        require(ingredients(compression.get("ingredient")) == [(f"minecraft:raw_{material}", 9)],
                f"{material}: raw block compression must consume nine raw items")

    require(expanded_tags.get("ic2:overworld_gold_ores") == {
        "minecraft:gold_ore", "minecraft:deepslate_gold_ore"}, "Overworld gold tag must exclude Nether gold")
    require(result_stack(recipes.get("data/ic2/recipe/macerator/nether_gold_ore_to_gold_nugget.json", {}).get("result"))
            == ("minecraft:gold_nugget", 8), "Nether gold ore must yield eight nuggets")
    require(result_stack(recipes.get("data/ic2/recipe/compressor/coal_chunk_to_industrial_diamond.json", {}).get("result"))
            == ("minecraft:diamond", 1), "Coal compression must produce vanilla diamond")

    # Bellman-Ford in log space: a positive cycle is a repeatable material multiplier.
    # Cover deterministic single-input/single-output conversions plus vanilla compaction.
    # Multi-output ore washing is intentional enrichment and is not classified as a loop.
    metal_nodes = set()
    for metal in ("copper", "iron", "gold"):
        metal_nodes.update(f"minecraft:{name}" for name in (
            f"{metal}_ingot", f"{metal}_block", f"{metal}_nugget", f"raw_{metal}",
            f"raw_{metal}_block", f"{metal}_ore", f"deepslate_{metal}_ore"))
        metal_nodes.update(f"ic2:{name}" for name in (
            f"crushed_{metal}", f"purified_{metal}", f"{metal}_dust", f"small_{metal}_dust",
            f"{metal}_plate", f"dense_{metal}_plate", f"{metal}_casing"))
    edges = []
    for key, recipe in recipes.items():
        if recipe.get("weighted") or isinstance(recipe.get("result"), list):
            continue
        output, output_count = result_stack(recipe.get("result"))
        if output not in metal_nodes:
            continue
        for item, input_count in ingredients(recipe.get("ingredient")):
            if item in metal_nodes and input_count > 0 and output_count > 0:
                edges.append((item, output, math.log(output_count / input_count), key))
    for metal in ("copper", "iron", "gold"):
        for unit, block in ((f"{metal}_ingot", f"{metal}_block"), (f"raw_{metal}", f"raw_{metal}_block"),
                            (f"{metal}_nugget", f"{metal}_ingot")):
            edges.extend(((f"minecraft:{unit}", f"minecraft:{block}", -math.log(9), "vanilla compaction"),
                          (f"minecraft:{block}", f"minecraft:{unit}", math.log(9), "vanilla unpacking")))
    distance = {node: 0.0 for node in metal_nodes}
    for iteration in range(len(metal_nodes)):
        updated = []
        for source, target, gain, key in edges:
            candidate = distance[source] + gain
            if candidate > distance[target] + 1e-9:
                distance[target] = candidate
                updated.append(key)
        if not updated:
            break
        require(iteration < len(metal_nodes) - 1, "Repeatable metal multiplier: " + ", ".join(sorted(set(updated))))
    counts["deterministic_metal_edges"] = len(edges)

    for key, data in resources.items():
        if "/loot_table/" in key:
            counts["loot_tables"] += 1
            for node in walk(data):
                if isinstance(node, dict):
                    require(not ({"conditions", "functions", "function"} & node.keys()), f"{key}: legacy loot fields")
                    require(not isinstance(node.get("condition"), str) or ":" in node["condition"],
                            f"{key}: malformed predicate reference")
                    require(node.get("type") != "minecraft:block_state_property", f"{key}: obsolete block predicate")
                    require(not (node.get("type") == "minecraft:item" and node.get("name") == "minecraft:air"),
                            f"{key}: empty loot must use minecraft:empty, not the air item")
                    if node.get("type") == "minecraft:match_tool":
                        require("enchantments" not in node.get("predicate", {}), f"{key}: legacy enchantment predicate")
        if "/advancement/" in key:
            counts["advancements"] += 1
            for criterion in data.get("criteria", {}).values():
                if criterion.get("trigger") == "minecraft:recipe_unlocked":
                    require("recipes" in criterion.get("conditions", {}), f"{key}: recipe_unlocked needs recipes")
                    references = criterion.get("conditions", {}).get("recipes", [])
                    for reference in references if isinstance(references, list) else [references]:
                        require_recipe_reference(reference, key)
                if criterion.get("trigger") == "minecraft:player_killed_entity":
                    conditions = criterion.get("conditions", {})
                    entity = conditions.get("entity")
                    if isinstance(entity, dict):
                        require(entity.get("type") == "minecraft:entity_properties",
                                f"{key}: killed entity needs the entity_properties condition")
                    source = conditions.get("killing_blow", {}).get("source_entity", {})
                    require("equipment" not in source, f"{key}: entity predicate needs minecraft:equipment")
                for damage_tag in criterion.get("conditions", {}).get("damage", {}).get("type", {}).get("tags", []):
                    tag_id = damage_tag.get("id")
                    if isinstance(tag_id, str) and tag_id.lstrip("#").startswith("ic2:"):
                        namespace, name = tag_id.lstrip("#").split(":", 1)
                        tag_resource = f"data/{namespace}/tags/damage_type/{name}.json"
                        if tag_resource in resources:
                            require(tag_id.startswith("#"), f"{key}: damage tag reference needs # prefix")
            for reference in data.get("rewards", {}).get("recipes", []):
                require_recipe_reference(reference, key)
            for node in walk(data):
                require(not (isinstance(node, dict) and set(node) == {"tag"}), f"{key}: legacy item tag predicate")
        if "/worldgen/" in key:
            counts["worldgen"] += 1
            require("/configured_feature/" not in key, f"{key}: obsolete worldgen registry directory")
            for node in walk(data):
                if isinstance(node, dict):
                    require(not ({"Name", "Properties", "dirt_provider", "force_dirt"} & node.keys()),
                            f"{key}: legacy blockstate/tree fields")
            if "/feature/" in key:
                require("config" not in data, f"{key}: feature configuration must be flattened")
                if data.get("type") == "minecraft:ore":
                    require("copper" not in key, f"{key}: duplicate copper world generation")
            if "/placed_feature/" in key and isinstance(data.get("feature"), str):
                namespace, path = data["feature"].split(":", 1)
                target = f"data/{namespace}/worldgen/feature/{path}.json"
                require(target in resources or target in external_resources, f"{key}: missing feature {data['feature']}")

    for message in sorted(set(errors)):
        print("ERROR:", message, file=sys.stderr)
    print(json.dumps({"status": "failed" if errors else "passed", **counts,
                      "vanilla_registry_checked": bool(minecraft_jar),
                      "fabric_tags_checked": bool(fabric_jar)}, indent=2))
    return 1 if errors else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--minecraft-jar", type=Path)
    parser.add_argument("--fabric-api-jar", type=Path)
    args = parser.parse_args()
    return check_resources(args.root, args.minecraft_jar, args.fabric_api_jar)


if __name__ == "__main__":
    raise SystemExit(main())

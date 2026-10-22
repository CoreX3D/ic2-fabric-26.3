#!/usr/bin/env python3
"""Audit IC2 model links against the exact target client jar, without launching it.

Checks modern item definitions against source registration IDs, model parents and
legacy overrides, blockstate models, inherited texture aliases, and PNG resources.
IC2's four procedural model loaders are interpreted like Ic2ModelLoadingPlugin.
This complements the client render game test; it does not execute model codecs.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from collections import Counter
from functools import lru_cache
from pathlib import Path


BUILTINS = {"minecraft:builtin/generated", "minecraft:builtin/entity"}
LOADERS = {"ic2:be", "ic2:cable", "ic2:wall", "ic2:mask_overlay"}


def identifier(value):
    return value if ":" in value else "minecraft:" + value


def resource_path(value, directory, extension=".json"):
    namespace, path = identifier(value).split(":", 1)
    return f"assets/{namespace}/{directory}/{path}{extension}"


def walk(value):
    yield value
    if isinstance(value, dict):
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def audit(project, minecraft_jar):
    resources = project / "src/main/resources"
    local = {p.relative_to(resources).as_posix(): p for p in resources.rglob("*") if p.is_file()}
    errors = set()
    counts = Counter()
    checked_textures = set()
    checked_roots = set()
    with zipfile.ZipFile(minecraft_jar) as vanilla:
        available = set(vanilla.namelist()) | local.keys()

        @lru_cache(None)
        def read_json(path):
            if path not in available:
                errors.add(f"Missing JSON: {path}")
                return {}
            raw = local[path].read_bytes() if path in local else vanilla.read(path)
            try:
                return json.loads(raw.decode("utf-8-sig"))
            except (UnicodeError, ValueError) as exc:
                errors.add(f"Invalid JSON: {path}: {exc}")
                return {}

        def texture(value, origin):
            if not isinstance(value, str):
                errors.add(f"Unsupported texture value in {origin}: {value}")
                return
            if value.startswith("#"):
                return
            checked_textures.add(identifier(value))
            path = resource_path(value, "textures", ".png")
            if path not in available:
                errors.add(f"Missing texture: {value} (from {origin})")

        def parent(data):
            loader = data.get("loader")
            if loader == "ic2:be":
                return data["id"].replace("block/be/", "block/")
            if loader == "ic2:mask_overlay":
                return data["base"]
            if loader == "ic2:wall":
                return "ic2:block/cf/wall_light_gray"
            return data.get("parent")

        def require_model(value, origin):
            value = identifier(value)
            if value not in BUILTINS and resource_path(value, "models") not in available:
                errors.add(f"Missing model: {value} (from {origin})")
            return value

        def model_chain(value, trail=()):
            value = identifier(value)
            if value in BUILTINS:
                return []
            if value in trail:
                errors.add("Model parent cycle: " + " -> ".join((*trail, value)))
                return []
            data = read_json(resource_path(value, "models"))
            inherited = parent(data)
            return (model_chain(inherited, (*trail, value)) if inherited else []) + [(value, data)]

        def check_root(value):
            value = identifier(value)
            if value in checked_roots:
                return
            checked_roots.add(value)
            chain = model_chain(value)
            slots = {}
            elements = []
            generated = False
            for model, data in chain:
                slots.update(data.get("textures", {}))
                if "elements" in data:
                    elements = data["elements"]
                generated |= identifier(data.get("parent", "")) == "minecraft:builtin/generated"
            required = {face["texture"] for element in elements for face in element.get("faces", {}).values()}
            if "particle" in slots:
                required.add("#particle")
            if generated:
                required.add("#layer0")
                required.update("#" + slot for slot in slots if re.fullmatch(r"layer\d+", slot))
            elif not elements and not any("loader" in data for _, data in chain):
                # Fluids and signs have dedicated renderers. Other concrete roots
                # need inherited geometry: a lost crop parent otherwise passes
                # all file-link checks but renders nothing in the client.
                dedicated = value == "ic2:block/resource/rubber/sign" or value in {
                    "ic2:block/fluid_block_" + name for name in fluids}
                if not dedicated:
                    errors.add(f"Concrete model root has no geometry: {value}")
            for ref in required:
                seen = set()
                resolved = ref
                while resolved.startswith("#"):
                    if resolved in seen:
                        errors.add(f"Texture alias cycle: {ref} (from {value})")
                        break
                    seen.add(resolved)
                    if resolved[1:] not in slots:
                        errors.add(f"Unbound texture slot: {resolved} (from {value})")
                        break
                    resolved = slots[resolved[1:]]
                else:
                    texture(resolved, value)

        item_source = (project / "src/main/java/ic2/core/ref/Ic2Items.java").read_text(encoding="utf-8")
        registered = set(re.findall(r'\bregister\(\s*"([a-z0-9_/]+)"', item_source))
        # Refuse to silently miss a future non-literal registration. One remaining
        # match is the private register(String, T) method declaration itself.
        if len(re.findall(r"\bregister\(", item_source)) != len(registered) + 1:
            errors.add("Ic2Items registration syntax changed: review static ID extraction")
        counts["explicit_registered_items"] = len(registered)
        fluid_source = (project / "src/main/java/ic2/core/ref/Ic2Fluids.java").read_text(encoding="utf-8")
        fluids = set(re.findall(r'\bcreate\(\s*"([a-z0-9_]+)"', fluid_source))
        counts["registered_fluid_buckets"] = len(fluids)
        registered |= {name + "_bucket" for name in fluids}
        definitions = {}
        for path in local:
            if path.startswith("assets/ic2/items/") and path.endswith(".json"):
                item = path[len("assets/ic2/items/"):-5]
                definitions[item] = read_json(path)
                counts["item_definitions"] += 1
                if "model" not in definitions[item]:
                    errors.add(f"Missing modern model field: {path}")
                for node in walk(definitions[item]):
                    if isinstance(node, dict) and "type" in node:
                        # Current definitions use only model and vanilla dye tint.
                        # A new type must be explicitly reviewed against the SDK.
                        if node["type"] not in {"minecraft:model", "minecraft:dye"}:
                            errors.add(f"Unreviewed item codec: {node['type']} (from {path})")
                        if node["type"] == "minecraft:model":
                            check_root(require_model(node["model"], path))
        for item in registered - definitions.keys():
            errors.add(f"Registered item lacks modern definition: ic2:{item}")
        counts["registered_items_with_definitions"] = len(registered & definitions.keys())
        counts["extra_definitions_not_registry_ids"] = len(definitions.keys() - registered)

        for path in local:
            if not path.startswith("assets/ic2/") or not path.endswith(".json"):
                continue
            data = read_json(path)
            if "/models/" in path:
                counts["models"] += 1
                loader = data.get("loader")
                if loader:
                    counts[f"loader:{loader}"] += 1
                    if loader not in LOADERS:
                        errors.add(f"Unsupported model loader: {loader} (from {path})")
                inherited = parent(data)
                if inherited:
                    require_model(inherited, path)
                for variant in data.get("overrides", []):
                    check_root(require_model(variant["model"], path))
                for ref in data.get("textures", {}).values():
                    texture(ref, path)
                if "mask" in data:
                    texture(data["mask"], path)
                # Inspect every graph, including unused templates, for cycles.
                ns, model = path[7:].split("/models/", 1)
                model_chain(ns + ":" + model[:-5])
            elif "/blockstates/" in path:
                counts["blockstates"] += 1
                for node in walk(data):
                    if isinstance(node, dict) and isinstance(node.get("model"), str):
                        check_root(require_model(node["model"], path))
            if "/models/" in path or "/items/" in path or "/blockstates/" in path:
                for value in walk(data):
                    if isinstance(value, str) and value.startswith(("forge:", "neoforge:")):
                        errors.add(f"Forge-only asset reference: {value} (from {path})")

        # Procedural cable models select sprites outside JSON texture maps.
        cable_source = (project / "src/main/java/ic2/core/block/wiring/CableType.java").read_text(encoding="utf-8")
        min_colors = {name: (int(minimum) if minimum.isdecimal() else 2**31 - 1)
                      for name, minimum in re.findall(r"\b([a-z]+)\(\d+, ([\w.]+),", cable_source)}
        colors = re.findall(r'HARD_\w+\("hard_([a-z_]+)"',
                            (project / "src/main/java/ic2/core/block/wiring/CableFoam.java").read_text(encoding="utf-8"))
        for path in local:
            if not path.startswith("assets/ic2/models/") or not path.endswith(".json"):
                continue
            data = read_json(path)
            if data.get("loader") != "ic2:cable":
                continue
            foam = data["foam"]
            if foam != "none":
                texture("ic2:block/cf/" + ("foam" if foam == "soft" else "wall_" + foam[5:]), path)
            else:
                prefix = f"ic2:block/wiring/cable/{data['type']}_cable_{data['insulation']}"
                variants = colors if data["insulation"] >= min_colors[data["type"]] else [None]
                for color in variants:
                    suffix = "_active" if data["active"] and data["type"] in {"detector", "splitter"} else ""
                    texture(prefix + ("_" + color if color else "") + suffix, path)
        counts["checked_model_roots"] = len(checked_roots)
        counts["referenced_textures"] = len(checked_textures)

    return {"minecraft_jar": str(minecraft_jar.resolve()), "counts": dict(sorted(counts.items())),
            "errors": sorted(errors), "passed": not errors,
            "scope": "Static resource links and source registration coverage; client codec/render validation is separate."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--minecraft-jar", type=Path, required=True)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    result = audit(args.project, args.minecraft_jar)
    output = json.dumps(result, indent=2, ensure_ascii=False)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(output + "\n", encoding="utf-8")
    print(output)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())

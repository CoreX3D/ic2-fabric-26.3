"""Check a built production JAR, separately from gameplay/resource validation."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile


def check(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("--sources", type=Path)
    parser.add_argument("--compiled", type=Path,
                        help="Compare every packaged class with this tested main output directory")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    with zipfile.ZipFile(args.jar) as archive:
        names = archive.namelist()
        files = [name for name in names if not name.endswith("/")]
        check(len(names) == len(set(names)), "Duplicate archive entries")
        check(all(not name.startswith("/") and ".." not in Path(name).parts
                  for name in files), "Unsafe archive path")
        metadata = json.loads(archive.read("fabric.mod.json"))
        check(metadata["id"] == "ic2", "Unexpected mod ID")
        check("${" not in metadata["version"], "Unexpanded version placeholder")
        check(metadata["depends"]["minecraft"] == "26.3", "Wrong Minecraft target")
        check(metadata["depends"]["java"] == ">=25", "Wrong Java requirement")
        check(metadata["depends"]["fabric-api"] == ">=0.163.0", "Wrong Fabric API requirement")
        check("LICENSE" in files, "Missing license")
        check("META-INF/neoforge.mods.toml" not in files, "Forge metadata in Fabric artifact")
        leaked = [name for name in files if "/gametest/" in name
                  or name.startswith("ic2/client/gametest/")
                  or name.endswith(".java")]
        check(not leaked, f"Test/source files in production artifact: {leaked}")
        check("assets/ic2/items/default.json" not in files
              and "assets/ic2/items/tool/default.json" not in files,
              "Abstract item parents packaged as standalone item definitions")

        classes = [name for name in files if name.endswith(".class")]
        check(bool(classes), "Artifact contains no classes")
        versions = sorted({struct.unpack(">H", archive.read(name)[6:8])[0]
                           for name in classes})
        check(versions == [69], f"Expected Java 25 class version 69; found {versions}")
        if args.compiled:
            mismatches = [name for name in classes
                          if not (args.compiled / name).is_file()
                          or (args.compiled / name).read_bytes() != archive.read(name)]
            check(not mismatches, f"Packaged classes differ from tested output: {mismatches}")

        result = {
            "status": "passed",
            "jar": str(args.jar.resolve()),
            "version": metadata["version"],
            "minecraft": metadata["depends"]["minecraft"],
            "class_version": versions[0],
            "classes": len(classes),
            "recipes": sum(name.startswith("data/ic2/recipe/") and name.endswith(".json")
                           for name in files),
            "item_definitions": sum(name.startswith("assets/ic2/items/") and name.endswith(".json")
                                    for name in files),
            "classes_match_tested_output": bool(args.compiled),
            "test_files_excluded": True,
            "license_included": True,
            "bytes": args.jar.stat().st_size,
            "sha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
        }

    if args.sources:
        with zipfile.ZipFile(args.sources) as archive:
            check("LICENSE" in archive.namelist(), "Missing sources license")
        result["sources"] = str(args.sources.resolve())
        result["sources_sha256"] = hashlib.sha256(args.sources.read_bytes()).hexdigest()

    text = json.dumps(result, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text + "\n", encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()

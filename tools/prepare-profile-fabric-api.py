#!/usr/bin/env python3
"""Expose an existing Fabric API distribution as an isolated Maven repository.

No network or Gradle cache writes are performed. The distribution and every
declared nested module are copied byte for byte; generated POMs only describe
the supplied production modules. Unbundled development/test modules are not
invented and must be supplied separately by the test harness.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
from pathlib import Path, PurePosixPath
import xml.etree.ElementTree as ET
from zipfile import ZipFile


GROUP = "net.fabricmc.fabric-api"
POM_NS = "http://maven.apache.org/POM/4.0.0"
SAFE_COORDINATE = re.compile(r"[A-Za-z0-9_.+-]+\Z")
HARNESS_MODULES = ("fabric-gametest-api-v1", "fabric-client-gametest-api-v1")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def coordinate(metadata: dict) -> tuple[str, str]:
    artifact = metadata["id"].replace("_", "-")
    version = metadata["version"]
    if not all(isinstance(value, str) and SAFE_COORDINATE.fullmatch(value)
               for value in (artifact, version)):
        raise ValueError(f"Unsupported artifact coordinate: {artifact!r}:{version!r}")
    if not artifact.startswith("fabric-"):
        raise ValueError(f"Expected an official Fabric API module, found {artifact}")
    return artifact, version


def pom(metadata: dict, dependencies: list[tuple[str, str]]) -> bytes:
    artifact, version = coordinate(metadata)
    ET.register_namespace("", POM_NS)
    project = ET.Element(f"{{{POM_NS}}}project")

    def child(parent: ET.Element, name: str, value: str) -> ET.Element:
        result = ET.SubElement(parent, f"{{{POM_NS}}}{name}")
        result.text = value
        return result

    for name, value in (("modelVersion", "4.0.0"), ("groupId", GROUP),
                        ("artifactId", artifact), ("version", version),
                        ("name", metadata.get("name", artifact)),
                        ("description", "Local byte-identical profile distribution; "
                         "POM generated from embedded Fabric metadata.")):
        child(project, name, value)
    if dependencies:
        dependency_root = ET.SubElement(project, f"{{{POM_NS}}}dependencies")
        for dependency_artifact, dependency_version in sorted(dependencies):
            dependency = ET.SubElement(dependency_root, f"{{{POM_NS}}}dependency")
            for name, value in (("groupId", GROUP), ("artifactId", dependency_artifact),
                                ("version", dependency_version), ("scope", "compile")):
                child(dependency, name, value)
    ET.indent(project, space="  ")
    return ET.tostring(project, encoding="utf-8", xml_declaration=True) + b"\n"


def write_identical(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        if path.read_bytes() != content:
            raise ValueError(f"Refusing to replace different existing artifact: {path}")
    else:
        path.write_bytes(content)


def cached_pom_coordinates(path: Path | None) -> dict:
    if path is None:
        return {}
    namespace = {"m": POM_NS}
    document = ET.fromstring(path.read_bytes())
    return {
        node.findtext("m:artifactId", namespaces=namespace):
        node.findtext("m:version", namespaces=namespace)
        for node in document.findall("m:dependencies/m:dependency", namespace)
        if node.findtext("m:groupId", namespaces=namespace) == GROUP
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Existing profile Fabric API JAR")
    parser.add_argument("--repository", type=Path, default=Path("tools/profile-api-maven"))
    parser.add_argument("--manifest", type=Path,
                        default=Path("build/all-book-audit/profile-api-local-repo.json"))
    parser.add_argument("--reference-pom", type=Path,
                        help="Optional cached official aggregate POM for coordinate comparison")
    args = parser.parse_args()
    source = args.source.resolve(strict=True)
    repository = args.repository.resolve()
    source_bytes = source.read_bytes()
    artifacts: dict[tuple[str, str], dict] = {}

    def inspect(content: bytes, source_entry: str, *, aggregate: bool = False) -> dict:
        with ZipFile(io.BytesIO(content)) as archive:
            metadata = json.loads(archive.read("fabric.mod.json"))
            artifact, version = coordinate(metadata)
            if aggregate and artifact != "fabric-api":
                raise ValueError("Source is not the Fabric API aggregate")
            key = (artifact, version)
            if key in artifacts:
                if artifacts[key]["bytes"] != content:
                    raise ValueError(f"Different nested JARs share coordinate {key}")
                return metadata
            if any(existing[0] == artifact and existing[1] != version
                   for existing in artifacts):
                raise ValueError(f"Distribution bundles conflicting versions of {artifact}")
            artifacts[key] = {"metadata": metadata, "bytes": content,
                              "source_entry": source_entry, "aggregate": aggregate}
            declared = [item["file"] for item in metadata.get("jars", [])]
            if len(declared) != len(set(declared)):
                raise ValueError(f"Duplicate bundled JAR declaration in {source_entry}")
            actual = {name for name in archive.namelist()
                      if name.startswith("META-INF/jars/") and name.endswith(".jar")}
            if actual != set(declared):
                raise ValueError(f"Unlisted or missing nested JAR in {source_entry}: "
                                 f"{sorted(actual.symmetric_difference(declared))}")
            for entry in declared:
                nested_metadata = inspect(archive.read(entry), f"{source_entry}!/{entry}")
                nested_artifact, nested_version = coordinate(nested_metadata)
                expected_name = f"{nested_artifact}-{nested_version}.jar"
                if PurePosixPath(entry).name != expected_name:
                    raise ValueError(f"Nested filename disagrees with embedded metadata: {entry}")
            return metadata

    root_metadata = inspect(source_bytes, source.name, aggregate=True)
    root_coordinate = coordinate(root_metadata)
    bundled_coordinates = sorted(key for key in artifacts if key != root_coordinate)
    reference = cached_pom_coordinates(args.reference_pom)
    records = []
    for key, artifact_data in sorted(artifacts.items()):
        artifact, version = key
        directory = repository.joinpath(*GROUP.split("."), artifact, version)
        jar_path = directory / f"{artifact}-{version}.jar"
        pom_path = directory / f"{artifact}-{version}.pom"
        pom_bytes = pom(artifact_data["metadata"],
                        bundled_coordinates if artifact_data["aggregate"] else [])
        write_identical(jar_path, artifact_data["bytes"])
        write_identical(pom_path, pom_bytes)
        records.append({
            "coordinate": f"{GROUP}:{artifact}:{version}",
            "mod_id": artifact_data["metadata"]["id"],
            "source_entry": artifact_data["source_entry"],
            "jar": str(jar_path),
            "jar_sha256": sha256(artifact_data["bytes"]),
            "jar_size": len(artifact_data["bytes"]),
            "copied_byte_identical": jar_path.read_bytes() == artifact_data["bytes"],
            "pom": str(pom_path),
            "pom_sha256": sha256(pom_bytes),
            "cached_reference_version": reference.get(artifact),
        })
    module_ids = {data["metadata"]["id"] for data in artifacts.values()}
    external_dependencies = sorted({dependency
        for data in artifacts.values()
        for dependency in data["metadata"].get("depends", {})
        if dependency not in module_ids and dependency not in ("java", "minecraft", "fabricloader")})
    if source.read_bytes() != source_bytes:
        raise RuntimeError("Source changed while preparing repository")
    report = {
        "source": str(source), "source_sha256": sha256(source_bytes),
        "source_unchanged": True, "repository": str(repository),
        "group": GROUP, "api_version": root_metadata["version"],
        "bundled_module_count": len(bundled_coordinates),
        "reference_pom": str(args.reference_pom.resolve()) if args.reference_pom else None,
        "reference_pom_sha256": sha256(args.reference_pom.read_bytes()) if args.reference_pom else None,
        "unbundled_test_modules": [name for name in HARNESS_MODULES if name not in module_ids],
        "external_mod_dependencies": external_dependencies,
        "pom_policy": "Aggregate depends on all supplied nested modules; individual module POMs "
                      "omit redundant transitives. POMs are generated local metadata, not official Maven downloads.",
        "artifacts": records,
    }
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"api_version": report["api_version"],
                      "bundled_module_count": report["bundled_module_count"],
                      "all_jars_byte_identical": all(record["copied_byte_identical"] for record in records),
                      "external_mod_dependencies": external_dependencies,
                      "unbundled_test_modules": report["unbundled_test_modules"],
                      "manifest": str(args.manifest.resolve())}, indent=2))


if __name__ == "__main__":
    main()

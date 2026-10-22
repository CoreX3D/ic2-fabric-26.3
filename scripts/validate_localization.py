"""Validate Russian language coverage, format arguments, and packaged resources."""

from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import zipfile


FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z%]")
COLOR = re.compile(r"\u00a7[0-9a-fk-or]", re.IGNORECASE)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate translation key: {key}")
        result[key] = value
    return result


def read_json(text):
    return json.loads(text, object_pairs_hook=unique_object)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    lang = args.root / "src/main/resources/assets/ic2/lang"
    en = read_json((lang / "en_us.json").read_text(encoding="utf-8"))
    ru = read_json((lang / "ru_ru.json").read_text(encoding="utf-8"))
    errors = []
    if en.keys() != ru.keys():
        errors.append(f"Key mismatch: missing={sorted(en.keys() - ru.keys())}; "
                      f"extra={sorted(ru.keys() - en.keys())}")
    unchanged = []
    for key in en:
        if key not in ru:
            continue
        value = ru[key]
        if not isinstance(value, str) or not value.strip():
            errors.append(f"Empty/non-string Russian translation: {key}")
            continue
        if "\ufffd" in value:
            errors.append(f"Unicode replacement character in {key}")
        if Counter(FORMAT.findall(en[key])) != Counter(FORMAT.findall(value)):
            errors.append(f"Format arguments differ: {key}: {en[key]!r} -> {value!r}")
        if en[key].count("%") != value.count("%"):
            errors.append(f"Percent signs differ: {key}")
        if Counter(COLOR.findall(en[key])) != Counter(COLOR.findall(value)):
            errors.append(f"Formatting codes differ: {key}")
        if value == en[key]:
            unchanged.append(key)
            # Symbols, technical abbreviations, and the mod brand need no translation.
            words = re.sub(FORMAT, "", value)
            words = re.sub(r"\b(?:IC2|IndustrialCraft\s*2|MFE|MFSU|CESU|EU|HU|Hu|hU|kU|KU|MCW|mB|B|V|C|UU|NBT)\b", "", words)
            words = re.sub(r"\b[XYZ]\b", "", words)
            if re.search(r"[A-Za-z]{2,}", words):
                errors.append(f"Untranslated English text: {key} = {value!r}")
    if args.jar:
        with zipfile.ZipFile(args.jar) as archive:
            for locale, expected in (("en_us", en), ("ru_ru", ru)):
                actual = read_json(archive.read(f"assets/ic2/lang/{locale}.json").decode("utf-8"))
                if actual != expected:
                    errors.append(f"Packaged {locale} differs from source")
    result = {
        "status": "passed" if not errors else "failed",
        "english_keys": len(en),
        "russian_keys": len(ru),
        "unchanged_symbols_and_abbreviations": unchanged,
        "packaged_resources_match": bool(args.jar) and not errors,
        "errors": errors,
    }
    report = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    print(report)
    raise SystemExit(bool(errors))


if __name__ == "__main__":
    main()

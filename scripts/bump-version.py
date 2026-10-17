#!/usr/bin/env python3

import argparse
import re
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description="Update Android app version info from Git tag.")
    parser.add_argument("--tag", required=True, help="Git tag like v0.1.2")
    parser.add_argument("--code", required=True, help="Incrementing versionCode")
    args = parser.parse_args()

    tag = args.tag.strip()
    if not tag.startswith("v"):
        raise ValueError("Tag must start with v, e.g. v0.1.2")

    version_name = tag[1:]
    version_code = int(args.code)

    build_gradle = Path("app/build.gradle.kts")
    text = build_gradle.read_text(encoding="utf-8")

    updated = re.sub(
        r'(versionCode\s*=\s*)\d+',
        lambda match: f"{match.group(1)}{version_code}",
        text,
        count=1,
    )
    updated = re.sub(
        r'(versionName\s*=\s*")([^"]+)(")',
        lambda match: f'{match.group(1)}{version_name}{match.group(3)}',
        updated,
        count=1,
    )

    if updated == text:
        raise RuntimeError("Could not find versionCode/versionName fields in app/build.gradle.kts")

    build_gradle.write_text(updated, encoding="utf-8")
    print(f"Updated Android version to name={version_name}, code={version_code}")


if __name__ == "__main__":
    main()

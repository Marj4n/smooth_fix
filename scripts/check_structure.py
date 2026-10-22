#!/usr/bin/env python3
"""Check source package paths, Fabric entrypoints and mixin resources. No Gradle required."""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOTS = [ROOT / "src/main/java", ROOT / "src/client/java", ROOT / "manager/src/main/java"]
RESOURCE_ROOTS = [ROOT / "src/main/resources", ROOT / "src/client/resources"]
ERRORS = []
WARNINGS = []

for source_root in JAVA_ROOTS:
    if not source_root.is_dir():
        continue
    for java in sorted(source_root.rglob("*.java")):
        # Preserve third-party package namespaces in port/compat repositories.
        relative = java.relative_to(source_root)
        if relative.parts[:2] != ("org", "marj4n") and relative.parts[:2] != ("org", "Marj4n"):
            continue
        match = re.search(r"(?m)^\s*package\s+([\w.]+)\s*;", java.read_text(encoding="utf-8"))
        expected = ".".join(relative.parts[:-1])
        if match is None or match.group(1) != expected:
            ERRORS.append(f"{java.relative_to(ROOT)}: package must be {expected}")
        if match is not None and match.group(1).startswith("org.Marj4n"):
            ERRORS.append(f"{java.relative_to(ROOT)}: lowercase org.marj4n is required")

manifest_file = ROOT / "src/main/resources/fabric.mod.json"
if not manifest_file.is_file():
    ERRORS.append("Missing src/main/resources/fabric.mod.json")
else:
    try:
        manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
        for entry_type, entries in manifest.get("entrypoints", {}).items():
            for entry in entries:
                name = entry if isinstance(entry, str) else entry.get("value", "")
                if not isinstance(name, str) or not re.fullmatch(
                    r"[A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)+", name
                ):
                    continue  # Includes non-Java entrypoint notations if present.
                rel = Path(*name.split(".")).with_suffix(".java")
                if not any((source_root / rel).is_file() for source_root in JAVA_ROOTS):
                    ERRORS.append(f"fabric.mod.json ({entry_type}): missing {name}")
        for mixin in manifest.get("mixins", []):
            name = mixin if isinstance(mixin, str) else mixin.get("config", "")
            if name and not any((res_root / name).is_file() for res_root in RESOURCE_ROOTS):
                ERRORS.append(f"fabric.mod.json: missing mixin config {name}")
            for res_root in RESOURCE_ROOTS:
                cfg = res_root / name if name else None
                if cfg and cfg.is_file():
                    mixin_meta = json.loads(cfg.read_text(encoding="utf-8"))
                    if mixin_meta.get("package", "").startswith("org.Marj4n"):
                        ERRORS.append(f"{cfg.relative_to(ROOT)}: lowercase mixin package required")
    except (json.JSONDecodeError, OSError, AttributeError, TypeError) as exc:
        ERRORS.append(f"Cannot validate Fabric manifest: {exc}")

props = ROOT / "gradle.properties"
if props.exists() and not re.search(r"(?m)^maven_group=org\.marj4n\s*$", props.read_text(encoding="utf-8")):
    ERRORS.append("gradle.properties: maven_group must be org.marj4n")

# Existing upstream-source blockers are reported, not disguised or patched with stubs.
if manifest_file.exists() and '"id": "smooth_quest_gacha"' in manifest_file.read_text(encoding="utf-8"):
    config = ROOT / "src/main/java/org/marj4n/smooth_quest_gacha/config/GachaConfigManager.java"
    if not config.is_file():
        WARNINGS.append("GachaConfigManager.java is absent; a complete compile may fail")
    gradle = (ROOT / "build.gradle").read_text(encoding="utf-8") if (ROOT / "build.gradle").is_file() else ""
    required = re.findall(r'files\(\"(libs/ftb-[^\"]+\.jar)\"\)', gradle)
    missing = [jar for jar in required if not (ROOT / jar).is_file()]
    if missing:
        WARNINGS.append("Missing local FTB JARs: " + ", ".join(missing))

for warning in WARNINGS:
    print("WARNING:", warning)
for error in ERRORS:
    print("ERROR:", error, file=sys.stderr)
if ERRORS:
    sys.exit(1)
print("OK: Java package layout, Fabric entrypoints and mixin resource paths are consistent.")

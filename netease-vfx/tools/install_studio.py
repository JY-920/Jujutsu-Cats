"""Update this addon in an existing MC Studio project, preserving Studio settings.

Does not edit the live game's global behavior/resource pack directories.
"""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path)
    args = parser.parse_args()
    target = args.project.resolve(strict=True)
    metadata = target / "work.mcscfg"
    if not metadata.is_file():
        raise SystemExit("Not an existing MC Studio project")
    before_metadata = metadata.read_bytes()
    source = ROOT / "pack"
    for kind in ("behavior_pack", "resource_pack"):
        a = json.loads((source / kind / "manifest.json").read_text("utf-8"))
        b = json.loads((target / kind / "manifest.json").read_text("utf-8"))
        if a["header"]["uuid"] != b["header"]["uuid"]:
            raise SystemExit("Refusing to replace a different addon: " + kind)
    files = [p for kind in ("behavior_pack", "resource_pack")
             for p in (source / kind).rglob("*") if p.is_file()
             and "__pycache__" not in p.parts and p.suffix not in (".pyc", ".pyo")]
    files += [source / ("world_" + kind + "_packs.json") for kind in ("behavior", "resource")]
    # These owned files were renamed; leaving both creates duplicate controller
    # identifiers whose load order can silently override the new definition.
    retired = [Path("resource_pack/render_controllers/skill_hands.render_controllers.json")]
    backup = ROOT / "build" / "backups" / ("studio-before-" + datetime.now().strftime("%Y%m%d-%H%M%S") + ".zip")
    backup.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(backup, "x", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("work.mcscfg", before_metadata)
        for src in files:
            dst = target / src.relative_to(source)
            if dst.is_file():
                archive.write(dst, src.relative_to(source).as_posix())
        for relative in retired:
            dst = target / relative
            if not (source / relative).exists() and dst.is_file():
                archive.write(dst, relative.as_posix())
    for src in files:
        dst = target / src.relative_to(source)
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        assert hashlib.sha256(src.read_bytes()).digest() == hashlib.sha256(dst.read_bytes()).digest()
    for relative in retired:
        dst = target / relative
        if not (source / relative).exists() and dst.is_file():
            dst.unlink()
    assert metadata.read_bytes() == before_metadata, "Studio metadata must remain untouched"
    print("Updated", len(files), "files in", target)
    print("Backup:", backup)
    print("Studio UID / name / test world settings preserved. Start a new development test to load the update.")


if __name__ == "__main__":
    main()

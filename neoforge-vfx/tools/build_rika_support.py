"""Reauthor combat on the supplied spirit-cat rig, keeping its geometry and dance intact.

The old builder expected a humanoid thorax/arm rig that is not present in the
user's model. The importer is the single combat authoring source for both loaders.
"""
from pathlib import Path
import copy
import json
from import_rika_cat import author

root = Path(__file__).resolve().parents[2]
relative = Path("src/main/resources/assets/jujutsuvfx/models/entity/rika_combat.bbmodel")
model = json.loads((root / "neoforge-vfx" / relative).read_text(encoding="utf-8"))
original = copy.deepcopy(model)
author(model, False)
for key in ("elements", "groups", "outliner", "textures", "resolution"):
    assert model[key] == original[key], f"Combat authoring changed supplied {key}"
for project in ("neoforge-vfx", "forge-vfx"):
    (root / project / relative).write_text(json.dumps(model, separators=(",", ":")), encoding="utf-8")
print("Preserved supplied Rika rig; six melee clips, guard, wave, idle and crawl authored for both loaders")

"""Two native geometry emblems; reuses the established Minecraft material palette."""
import json
from pathlib import Path

from generate_skill_icons import model

ROOT = Path(__file__).resolve().parents[1]
ICONS = {
    "dismantle": [
        "..............w.",
        ".............wr.",
        "...........wwr..",
        "..........wwrr..",
        ".........wwr....",
        "........wwr..r..",
        ".......wwr..wr..",
        "......wwr..wr...",
        ".....wwr..wr....",
        "....wwr..wr..r..",
        "...wwr..wr..wr..",
        "..wwr..wr..wr...",
        ".wwr..wr..wr....",
        ".wr..wr..wr.....",
        "wr..wr..wr......",
        "r...r...r.......",
    ],
    "cleave": [
        "................",
        ".kkkkk....kkkkk.",
        ".krrrk....krrrk.",
        ".krkkk....kkrrk.",
        ".krrrk....krrrk.",
        ".kkkkk....kkkkk.",
        "......r..r......",
        ".....rwwwwr.....",
        "...rwwwwwwwwr...",
        "rwwwwr....rwwwwr",
        "wwrr........rrww",
        ".kkkkk....kkkkk.",
        ".krrrk....krrrk.",
        ".krkkk....kkrrk.",
        ".krrrk....krrrk.",
        ".kkkkk....kkkkk.",
    ],
}


if __name__ == "__main__":
    target = ROOT / "src/main/resources/assets/jujutsuvfx/models/item"
    for name, rows in ICONS.items():
        asset = model(rows)
        # Inventory emblem only; held items are represented by the player's own skin arm.
        for view in ("firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"):
            asset["display"][view] = {"scale": [0, 0, 0]}
        (target / (name + ".json")).write_text(json.dumps(asset, indent=2) + "\n", encoding="utf-8")
        print(f"{name}: {len(asset['elements'])} native color runs")

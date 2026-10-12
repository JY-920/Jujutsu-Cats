"""Build hand-drawn pixel emblems as native item models, using Minecraft's color materials.

No bitmap/download dependency: each horizontal color run becomes one thin, unshaded model element.
Run from any directory to regenerate the assets; the existing cursed_fist model is untouched.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PALETTE = {
    "k": "black", "b": "blue", "d": "cyan", "c": "light_blue", "w": "white",
    "p": "pink", "v": "purple", "m": "magenta", "r": "red", "o": "orange", "y": "yellow",
}

ICONS = {
    "blue": [
        ".......bb",
        "....bbbbb..b",
        "..bbbbbccbb",
        "..bbccccccbb",
        ".bbccdcwwccbb",
        "bbccdwwwwwccb",
        "bccwwwwwwwccbb",
        "bccwwwwwwwwccb",
        "bccwwwwwwwwccb",
        ".bccwwwwwwwccb",
        ".bbccwwwwwccdbb",
        "..bbccccccddcb",
        "...bbddcccbbb",
        "..b.bbbcccbb",
        ".....bbbbb",
        "........b",
    ],
    "red": [
        ".......r",
        "...r...rr...r",
        "....rr.rr..rr",
        "....rropprrr",
        ".rrrropwwporrr",
        "..rropwwwppor",
        "..ropwwwwwporr",
        "rropwwwwwwwporrr",
        "rrropwwwwwporr",
        "..rropwwwporr",
        ".rrropwpporrrr",
        "....rrpprr..rr",
        "...rr.rrr",
        "..rr..rr..rr",
        "......r",
        "......r",
    ],
    "purple": [
        "..w...vv....w",
        ".pw..vmmvv..wp",
        "..mvvppppmvpm",
        "..vmppppppmmv",
        ".vmpwwwwwppmv",
        "vmpwwwwwwwpmv",
        "vmpwwwwwwwwpmv",
        "vmpwwwwwwwwpmvw",
        "vmpwwwwwwwppmv",
        ".vmpwwwwwwpmv",
        "pvmppwwwwppmv",
        "wpvmppppppmv",
        ".mvvmmmmmmvv.p",
        "..w.vvvvvv..wpw",
        "..p..........p",
        "......w",
    ],
    "fuga": [
        "........o",
        ".......oy",
        ".......oyo",
        "......oyyo",
        "...r..oywyo",
        "...ro.oywyo.r",
        "..rooroywwyor",
        "..royoywwwyor",
        ".royyoywwwyyor",
        ".royyywwwwyyor",
        "royyyywwwwyyyor",
        "royywwwwwwwyyor",
        ".roywwwwwyyyor",
        "..roywwwwyyor",
        "...royyyyorr",
        "....roooor",
    ],
    "unlimited_void": [
        "..w...........p",
        ".pwp..vvvv",
        "..w.vvppwwvv",
        "...vppwwwppvv",
        "..vppwkkkkppv",
        ".vppwkkkkkkpv",
        ".vpwkkkkkkkpmv",
        ".vpwkkkkkkkppmv",
        "vvppkkkkkkpwwwpv",
        "mpwwppkkppwwwpv",
        "ppwwwwppwwppmv",
        ".vppwwwwppmmv",
        "..vvppppmvv..w",
        "....vvvvv...pwp",
        ".............w",
        "...p",
    ],
    "malevolent_shrine": [
        "...k........k",
        "..krk..rr..krk",
        "..krrkrrrrkrrk",
        ".krrrorrrrorrrk",
        "krrroooooooorrrk",
        ".roooooooooooor",
        "..krrrroorrrrk",
        "..rooooooooor",
        "..rokwwwwkor",
        "..rokwwwwkor",
        "..rokwwwwkor",
        "..rokkkkkkor",
        "..rokkkkkkor",
        "..rokwwwwkor",
        ".rrorwrrwrorrr",
        "krrrrrrrrrrrrrk",
    ],
}


def model(rows):
    if len(rows) != 16 or any(len(row) > 16 for row in rows):
        raise ValueError("Icons must fit a 16 by 16 pixel grid")
    elements = []
    used = set()
    for row, raw in enumerate(rows):
        pixels = raw.ljust(16, ".")
        x = 0
        while x < 16:
            color = pixels[x]
            end = x + 1
            while end < 16 and pixels[end] == color:
                end += 1
            if color != ".":
                used.add(color)
                elements.append({
                    "from": [x, 15 - row, 7.5], "to": [end, 16 - row, 8.5], "shade": False,
                    "faces": {face: {"uv": [7, 7, 9, 9], "texture": "#" + color}
                              for face in ("north", "south", "east", "west", "up", "down")},
                })
            x = end
    return {
        "gui_light": "front", "ambientocclusion": False,
        "textures": {**{c: f"minecraft:block/{PALETTE[c]}_concrete" for c in sorted(used)},
                     "particle": "minecraft:block/white_concrete"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [.92, .92, .92]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [.5, .5, .5]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [.5, .5, .5]},
            "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1], "scale": [.55, .55, .55]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1], "scale": [.55, .55, .55]},
            "ground": {"translation": [0, 3, 0], "scale": [.5, .5, .5]},
            "fixed": {"rotation": [0, 180, 0], "scale": [.75, .75, .75]},
        },
    }


if __name__ == "__main__":
    target = ROOT / "src/main/resources/assets/jujutsuvfx/models/item"
    target.mkdir(parents=True, exist_ok=True)
    for name, rows in ICONS.items():
        asset = model(rows)
        (target / (name + ".json")).write_text(json.dumps(asset, indent=2) + "\n", encoding="utf-8")
        print(f"{name}: {len(asset['elements'])} native color runs")

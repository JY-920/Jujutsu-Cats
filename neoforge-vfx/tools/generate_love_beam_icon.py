"""Love beam emblem in the existing native 16px item-icon system."""
import json
from pathlib import Path
from generate_skill_icons import model

ROOT = Path(__file__).resolve().parents[2]
ROWS = [
    '................', '.....vvv........', '....vpppv.......',
    '...vpwwwpv......', '..vpwwwwwpv.....', '.vpwwpppwwpv....',
    'vpwwpwwwpwwpv...', 'vpwwwwwwwwwwpppp', 'vpwwwwwwwwwwwwww',
    'vpwwpwwwpwwpvppp', '.vpwwpppwwpv....', '..vpwwwwwpv.....',
    '...vpwwwpv......', '....vpppv.......', '.....vvv........',
    '................',
]

def main():
    assert len(ROWS) == 16 and all(len(r) == 16 for r in ROWS)
    icon = model(ROWS)
    for view in ['firstperson_righthand', 'firstperson_lefthand', 'thirdperson_righthand', 'thirdperson_lefthand']:
        icon['display'][view] = {'scale': [0, 0, 0]}
    for project in ['neoforge-vfx', 'forge-vfx']:
        path = ROOT/project/'src/main/resources/assets/jujutsuvfx/models/item/love_beam.json'
        path.write_text(json.dumps(icon, indent=2)+'\n', encoding='utf-8')
    print('Love beam pixel icon written to both loaders')

if __name__ == '__main__':
    main()

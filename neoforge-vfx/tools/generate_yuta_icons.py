"""Authored 16px emblems, extending the project's native pixel icon system."""
import json
from pathlib import Path
from generate_skill_icons import model

ROOT=Path(__file__).resolve().parents[2]
ICONS={
 'world_cut':[
 '................','...........vv...','.........vvkv...',
 '.......vvkkv....','....vvvkkkv.....','..vvkkkkkv......',
 '.vvkkkpppwwwww..','ppwwwwwwwwwwwwww','wwwwwwwwwwwppp..',
 '..kkkkkkvv......','...kkkvv........','...kkvv.........',
 '..kvv...........','..vv............','................','................'],
 'cursed_speech':[
 '.....vvvvvv.....','...vvmppppmv....','..vmpwwwwwwpmv..',
 '.vmpwkkkkkkwpmv.','.vpwkwkkkkwkwpv.','.vpwkkkwwkkkwpv.',
 '.vpwkkkkkkkkwpv.','.vmpwwwwwwwwpmv.','..vmmppppppmmv..',
 '...vvmmmmmmvv...','..p..vvvvvv..p..','.p.v........v.p.',
 '.p.v..vvvv..v.p.','..p.v......v.p..','...p.vvvvvv.p...',
 '....pppppppp....'],
 # Rika's pale grinning face above raised dancing fists and a magenta heartbeat.
 'yuta_dance':[
 '.....vvvvvv.....','....vwwwwwwv....','...vwkkwwkkwv...',
 '...vwwwwwwwwv...','....vwkkkkwv....','.....vwwwwv.....',
 '.ww...vvvv...ww.','wppw...ww...wppw','wppw..wwww..wppw',
 '.ww.wwwwwwww.ww.','..wwwwwwwwwwww..','....wwwwwwww....',
 '.....wwwwww.....','....ww....ww....','..mmw..mm..wmm..',
 '.m..mmmmm.mm..m.'],
 'yuta_domain':[
 '......ywy.......','..r...ywy...w...','..rr..ywy..ww...',
 '...rrwywywrw....','....wrwywrr.....','...wwrrwrrww....',
 '..ww..ywy..rr...','......ywy.......','..w...ywy....w..',
 '..wk..ywy.w.wk..','.kkkw.ywy.wkkkw.','..w.kkwwwkk.w...',
 '..w..wvwvw..w...','.vwv.wvwvw.vwv..','vvpvvwpvpwvvpvvv',
 '.vvvvvvvvvvvvvv.']}

def main():
 for project in ['neoforge-vfx','forge-vfx']:
  assets=ROOT/project/'src/main/resources/assets/jujutsuvfx'
  for name,rows in ICONS.items():
   assert len(rows)==16 and all(len(r)==16 for r in rows),(name,[len(r) for r in rows])
   asset=model(rows)
   for view in ['firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand']:
    asset['display'][view]={'scale':[0,0,0]}
   (assets/'models/item'/f'{name}.json').write_text(json.dumps(asset,indent=2)+'\n',encoding='utf-8')
  for lang in ['zh_cn','en_us']:
   path=assets/'lang'/f'{lang}.json';data=json.loads(path.read_text(encoding='utf-8'))
   data['item.jujutsuvfx.yuta_domain']='领域展开·真赝相爱' if lang=='zh_cn' else 'Domain Expansion: Authentic Mutual Love'
   data['item.jujutsuvfx.cursed_speech']='咒言' if lang=='zh_cn' else 'Cursed Speech'
   data['item.jujutsuvfx.yuta_dance']='乙骨与里香·双人舞' if lang=='zh_cn' else 'Yuta & Rika: Dance'
   data['effect.jujutsuvfx.technique_amplification']='术式增幅（术式伤害 +25%）' if lang=='zh_cn' else 'Technique Amplification (+25% spell damage)'
   path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 print(f'{len(ICONS)} distinctive native pixel icons written to both loaders')
if __name__=='__main__':main()

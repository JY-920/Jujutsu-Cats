from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ART = Path(__file__).resolve().parents[1] / 'art/shinjuku-characters'
P = ART / 'previews'
FONT = 'C:/Windows/Fonts/msyh.ttc'
BOLD = 'C:/Windows/Fonts/msyhbd.ttc'
BG = (23, 29, 39)
CARD = (33, 41, 54)

def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size)

def place(canvas, source, box):
    im = Image.open(P / source).convert('RGBA')
    bounds = im.getbbox()
    if not bounds:
        raise ValueError(source)
    im = im.crop(bounds)
    im.thumbnail((box[2]-box[0], box[3]-box[1]), Image.Resampling.LANCZOS)
    canvas.paste(im, ((box[0]+box[2]-im.width)//2, box[3]-im.height), im)

def board(filename, cards, columns=3, width=1500, height=980, title='新宿之战 · 角色模型', subtitle='Blockbench 实际模型预览'):
    canvas = Image.new('RGB', (width, height), BG)
    draw = ImageDraw.Draw(canvas)
    draw.text((42, 27), title, font=font(38, True), fill='#f0eade')
    draw.text((44, 84), subtitle, font=font(20), fill='#a6b5c8')
    rows = (len(cards)+columns-1)//columns
    cw = (width-64)//columns
    ch = (height-152)//rows
    for i, (source, label, note) in enumerate(cards):
        x = 24+(i%columns)*cw
        y = 128+(i//columns)*ch
        draw.rounded_rectangle((x+8,y,x+cw-8,y+ch-16), 18, fill=CARD)
        draw.text((x+26,y+21), label, font=font(27, True), fill='#ece7db')
        draw.text((x+26,y+62), note, font=font(16), fill='#a9b8c8')
        place(canvas,source,(x+30,y+105,x+cw-30,y+ch-42))
    canvas.save(P/filename)

board('overview.png',[
    ('gojo-quarter.png','五条悟','史蒂夫体型 · 黑色短袖 / 浅色宽裤'),
    ('sukuna-quarter.png','伏黑宿傩','史蒂夫体型 · 无袖交领衣 / 咒纹'),
    ('mahoraga-quarter.png','魔虚罗','独立体型 · 退魔剑 / 八辐法轮'),
])
board('views-review.png',[
    ('gojo-front.png','五条悟 · 正面','白发 / 六眼 / 腰结'),
    ('sukuna-front.png','宿傩 · 正面','衣领 / 肩部圆环 / 腕部纹路'),
    ('mahoraga-front.png','魔虚罗 · 正面','四片头翼 / 露齿 / 分腿膝裤'),
    ('gojo-back.png','五条悟 · 背面','发束与服装轮廓'),
    ('sukuna-back.png','宿傩 · 背面','史蒂夫双臂骨架'),
    ('mahoraga-back.png','魔虚罗 · 背面','后脑附肢 / 法轮独立骨骼'),
], height=1460, title='多视角检查')
board('actions-review.png',[
    ('gojo-signature.png','五条悟 · 结印','双指抬起'),
    ('sukuna-signature.png','宿傩 · 结印','胸前双掌相接'),
    ('mahoraga-signature.png','魔虚罗 · 适应姿势','剑臂展开 / 法轮旋转'),
    ('gojo-walk.png','五条悟 · 行走','肩 / 肘 / 膝关节'),
    ('sukuna-walk.png','宿傩 · 行走','摆臂与迈步'),
    ('mahoraga-walk.png','魔虚罗 · 行走','剑臂 / 膝裤 / 腰布随动'),
], height=1460, title='动画关键帧检查')

def animation(folder, filename, label, duration):
    images = [Image.open(p).convert('RGBA') for p in sorted((P/folder).glob('*.png'))]
    boxes = [im.getbbox() for im in images]
    union = (min(b[0] for b in boxes),min(b[1] for b in boxes),max(b[2] for b in boxes),max(b[3] for b in boxes))
    frames=[]
    for im in images:
        canvas=Image.new('RGB',(600,800),BG)
        d=ImageDraw.Draw(canvas)
        d.text((28,22),label,font=font(26,True),fill='#ece7db')
        d.text((29,63),'Blockbench 动画时间轴实录',font=font(16),fill='#a6b5c8')
        im=im.crop(union)
        im.thumbnail((548,660),Image.Resampling.LANCZOS)
        canvas.paste(im,((600-im.width)//2,770-im.height),im)
        frames.append(canvas)
    frames[0].save(P/filename,save_all=True,append_images=frames[1:],duration=duration,loop=0,disposal=2)

animation('gojo-signature-frames','gojo-signature.gif','五条悟 · 双指结印',100)
animation('sukuna-signature-frames','sukuna-signature.gif','宿傩 · 双手结印',100)
animation('mahoraga-wheel-frames','mahoraga-wheel.gif','魔虚罗 · 法轮持续旋转',125)

# Use only captured timeline frames; all cards return to their starting poses at four seconds.
strips = [sorted((P/(n+'-signature-frames')).glob('*.png')) for n in ['gojo','sukuna']]
strips.append(sorted((P/'mahoraga-wheel-frames').glob('*.png')))
loaded = [[Image.open(p).convert('RGBA') for p in files] for files in strips]
unions=[]
for frames in loaded:
    b=[im.getbbox() for im in frames]
    unions.append((min(x[0] for x in b),min(x[1] for x in b),max(x[2] for x in b),max(x[3] for x in b)))
combined=[]
for i in range(32):
    canvas=Image.new('RGB',(1200,790),BG)
    d=ImageDraw.Draw(canvas)
    d.text((28,20),'新宿之战 · 动画预览',font=font(29,True),fill='#ece7db')
    d.text((28,64),'Blockbench 时间轴实录 · 五条悟 / 伏黑宿傩 / 魔虚罗',font=font(17),fill='#a6b5c8')
    for col in range(3):
        frame=i if col==2 else min(26,round(i/8*10))
        im=loaded[col][frame].crop(unions[col])
        im.thumbnail((352,624),Image.Resampling.LANCZOS)
        canvas.paste(im,(col*400+(400-im.width)//2,750-im.height),im)
    combined.append(canvas)
combined[0].save(P/'characters-animated.gif',save_all=True,append_images=combined[1:],duration=125,loop=0,disposal=2)
print('Created 3 screenshot boards and 3 actual-timeline GIFs')

from pathlib import Path
from PIL import Image, ImageOps, ImageDraw

art=Path(__file__).resolve().parents[1]/'art/malevolent-shrine'
p=art/'previews'
def paste(canvas,file,box):
    im=ImageOps.contain(Image.open(file).convert('RGBA'),(box[2],box[3]))
    canvas.paste(im,(box[0]+(box[2]-im.width)//2,box[1]+(box[3]-im.height)//2),im)

board=Image.new('RGB',(1800,1000),'#24262b');draw=ImageDraw.Draw(board)
for i,n in enumerate(['quarter','front','right','rear','top','mouth']):
    x=(i%3)*600;y=(i//3)*500
    paste(board,p/(n+'.png'),(x+5,y+30,590,460))
    draw.text((x+14,y+10),n.upper()+' / BLOCKBENCH MCP',fill='white')
board.save(p/'six-views.jpg',quality=94)
for filename,left,label in [('colour-jaw-comparison.jpg',art/'revision-1/quarter.png','PREVIOUS'),('before-after.jpg',p/'before-quarter.png','ORIGINAL')]:
    board=Image.new('RGB',(1600,850),'#24262b');draw=ImageDraw.Draw(board)
    for i,(file,name) in enumerate([(left,label),(p/'quarter.png','REVISED / 0.65')]):
        paste(board,file,(i*800+5,34,790,800));draw.text((i*800+18,12),name,fill='white')
    board.save(p/filename,quality=94)
board=Image.new('RGB',(1600,880),'#24262b');draw=ImageDraw.Draw(board)
for i,(file,name) in enumerate([(p/'quarter.png','COLOUR + PROJECTING JAWS'),(p/'jaw-profile.png','ISOLATED JAW / BLOCKBENCH MCP')]):
    paste(board,file,(i*800+5,32,790,830));draw.text((i*800+18,12),name,fill='white')
board.save(p/'revision-065.jpg',quality=94)

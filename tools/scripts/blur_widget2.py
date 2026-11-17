from PIL import Image, ImageFilter
import os
src=r'D:\lwh\shot_aftercancel.png'; out=r'D:\lwh\site\assets\screenshots\widget.jpg'
im=Image.open(src).convert('RGB')
boxes=[(70,180,285,405),(310,180,525,405),(550,180,790,405),(380,450,700,535)]
for (x1,y1,x2,y2) in boxes:
    region=im.crop((x1,y1,x2,y2))
    small=region.resize((max(1,(x2-x1)//14),max(1,(y2-y1)//14)), Image.NEAREST)
    region=small.resize(((x2-x1),(y2-y1)), Image.NEAREST).filter(ImageFilter.GaussianBlur(5))
    im.paste(region,(x1,y1))
im.save(out,'JPEG',quality=85,optimize=True,progressive=True)
print(os.path.getsize(out), im.size)

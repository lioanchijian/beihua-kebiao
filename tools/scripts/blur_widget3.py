from PIL import Image, ImageFilter, ImageDraw
import os
src=r'D:\lwh\shot_aftercancel.png'; out=r'D:\lwh\site\assets\screenshots\widget.jpg'
im=Image.open(src).convert('RGB')
boxes=[(70,180,285,405),(310,180,525,405),(550,180,790,405),(380,450,700,535)]
for (x1,y1,x2,y2) in boxes:
    region=im.crop((x1,y1,x2,y2)).filter(ImageFilter.GaussianBlur(34))
    im.paste(region,(x1,y1))
im.save(out,'JPEG',quality=85,optimize=True,progressive=True)
print(os.path.getsize(out), im.size)

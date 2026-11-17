from PIL import Image, ImageFilter
import os
src='D:/lwh'; out=r'D:/lwh/site/assets/screenshots'
def save(src_name,out_name,boxes=()):
    im=Image.open(os.path.join(src,src_name)).convert('RGB'); w,h=im.size
    for (x1,y1,x2,y2) in boxes:
        region=im.crop((max(0,x1),max(0,y1),min(w,x2),min(h,y2))).filter(ImageFilter.GaussianBlur(26))
        im.paste(region,(max(0,x1),max(0,y1)))
    im.save(os.path.join(out,out_name),'JPEG',quality=85,optimize=True,progressive=True)
    print(out_name, im.size, os.path.getsize(os.path.join(out,out_name)))
save('shot_week2.png','timetable.jpg')
save('shot_day2.png','day.jpg',[(360,1005,700,1085)])
save('shot_detail.png','detail.jpg',[(360,1005,700,1085),(160,1785,430,1880)])
save('shot_empty.png','empty.jpg')
save('shot_grades_overall.png','grades.jpg')
save('shot_exam2.png','exam.jpg')
save('shot_grab2.png','grab.jpg')
save('shot_settings.png','settings.jpg',[(220,390,440,475),(90,405,215,535)])
save('shot_settings2.png','update.jpg')
save('shot_aftercancel.png','widget.jpg',[(350,445,720,550),(85,190,250,400),(330,190,500,400),(575,190,750,400)])

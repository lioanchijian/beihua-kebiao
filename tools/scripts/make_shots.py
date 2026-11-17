from PIL import Image, ImageFilter
import os
src = 'D:/lwh'
out = r'D:/lwh/site/assets/screenshots'
os.makedirs(out, exist_ok=True)

def save(src_name, out_name, boxes=()):
    im = Image.open(os.path.join(src, src_name)).convert('RGB')
    w, h = im.size
    for (x1, y1, x2, y2) in boxes:
        region = im.crop((max(0,x1), max(0,y1), min(w,x2), min(h,y2)))
        region = region.filter(ImageFilter.GaussianBlur(22))
        im.paste(region, (max(0,x1), max(0,y1)))
    im = im.resize((720, 1600), Image.LANCZOS)
    im.save(os.path.join(out, out_name), 'JPEG', quality=87, optimize=True, progressive=True)
    print(out_name, os.path.getsize(os.path.join(out, out_name)))

save('shot_week2.png', 'timetable.jpg')
save('shot_day2.png', 'day.jpg', [(430, 1015, 660, 1075)])
save('shot_detail.png', 'detail.jpg', [(430, 1015, 660, 1075), (165, 1790, 415, 1875)])
save('shot_empty.png', 'empty.jpg')
save('shot_grades_overall.png', 'grades.jpg')
save('shot_exam2.png', 'exam.jpg')
save('shot_grab2.png', 'grab.jpg')
save('shot_settings.png', 'settings.jpg', [(220, 390, 440, 475), (90, 405, 215, 535)])
save('shot_settings2.png', 'update.jpg')
save('shot_aftercancel.png', 'widget.jpg', [(350, 445, 720, 550)])

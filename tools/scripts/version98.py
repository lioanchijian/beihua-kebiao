import json,io
p=r'D:\lwh\site\version.json'
d=json.load(open(p,encoding='utf-8'))
d.update({'versionCode':98,'versionName':'1.0.9.8','releaseNotes':'1. 新增匿名使用统计（可关闭）\n2. 仅上报随机安装 ID、版本和启动日期，不收集姓名、学号、账号或密码\n3. 设置页新增匿名使用统计开关\n4. 隐私说明同步更新','sha256':'429504897d730ae16e2581b948ab76b4b6484872582d6e66b66b7bdefb4baea5'})
open(p,'w',encoding='utf-8',newline='').write(json.dumps(d,ensure_ascii=False,separators=(',',':')))
print('VERSION98')

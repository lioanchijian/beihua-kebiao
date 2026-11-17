import json,io,html
base=r'D:\lwh\site'
rp=base+r'\assets\changelog.json'
items=json.load(open(rp,encoding='utf-8'))
entry={'version':'1.0.9.8','date':'2026-10-26','changes':['新增匿名使用统计，可随时关闭','仅上报随机安装 ID、版本和启动日期，不收集姓名、学号、账号或密码','设置页新增匿名使用统计开关','隐私说明同步更新']}
if items[0]['version']!='1.0.9.8': items.insert(0,entry)
open(rp,'w',encoding='utf-8',newline='').write(json.dumps(items,ensure_ascii=False,indent=2))
ip=base+r'\index.html'; s=io.open(ip,encoding='utf-8').read()
s=s.replace('v1.0.9.7','v1.0.9.8').replace('1.0.9.7 (97)','1.0.9.8 (98)').replace('2026-10-25','2026-10-26')
parts=[]
for i,item in enumerate(items):
    marker='<div class="timeline-badge">最新</div>' if i==0 else '<div class="timeline-dot"></div>'
    lis=''.join('<li>'+html.escape(x)+'</li>' for x in item['changes'])
    parts.append('<article class="timeline-item'+(' current' if i==0 else '')+'">'+marker+'<div class="timeline-body"><div class="timeline-head"><h3>v'+html.escape(item['version'])+'</h3><time>'+html.escape(item['date'])+'</time></div><ul>'+lis+'</ul></div></article>')
parts.append('<div class="timeline-more">完整更新记录也保存在本地 <a href="CHANGELOG.md" target="_blank">CHANGELOG.md</a></div>')
start=s.index('<div class="timeline" id="timeline">')
end=s.index('        </div>\n      </div>\n    </section>',start)
s=s[:start]+'<div class="timeline" id="timeline">'+''.join(parts)+s[end:]
io.open(ip,'w',encoding='utf-8',newline='').write(s)
lines=['# 北化课表更新日志','','> 本地更新记录，网站会自动读取 assets/changelog.json 渲染完整时间线。','']
for item in items:
    lines.append('## ['+item['version']+'] - '+item['date']); lines.append('')
    for c in item['changes']: lines.append('- '+c)
    lines.append('')
io.open(base+r'\CHANGELOG.md','w',encoding='utf-8',newline='').write('\n'.join(lines))
ap=base+r'\assets\app.js'; a=io.open(ap,encoding='utf-8').read().replace('1.0.9.7','1.0.9.8'); io.open(ap,'w',encoding='utf-8',newline='').write(a)
print('OK',len(items),len(s))
import json,io,html
p=r'D:\lwh\site\index.html'
items=json.load(open(r'D:\lwh\site\assets\changelog.json',encoding='utf-8'))
parts=[]
for i,item in enumerate(items):
    marker='<div class="timeline-badge">最新</div>' if i==0 else '<div class="timeline-dot"></div>'
    lis=''.join('<li>'+html.escape(x)+'</li>' for x in item['changes'])
    parts.append('<article class="timeline-item'+(' current' if i==0 else '')+'">'+marker+'<div class="timeline-body"><div class="timeline-head"><h3>v'+html.escape(item['version'])+'</h3><time>'+html.escape(item['date'])+'</time></div><ul>'+lis+'</ul></div></article>')
parts.append('<div class="timeline-more">完整更新记录也保存在本地 <a href="CHANGELOG.md" target="_blank">CHANGELOG.md</a></div>')
s=io.open(p,encoding='utf-8').read()
old='<div class="timeline" id="timeline"><div class="timeline-loading">正在加载完整更新日志…</div></div>'
new='<div class="timeline" id="timeline">'+''.join(parts)+'</div>'
if old not in s: print('MISS')
s=s.replace(old,new)
io.open(p,'w',encoding='utf-8',newline='').write(s)
print('OK',len(items))

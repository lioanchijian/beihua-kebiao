import json,io,html
base=r'D:\lwh\site'; ip=base+r'\index.html'; s=io.open(ip,encoding='utf-8').read()
s=s.replace('1.0.9.8 (97)','1.0.9.8 (98)')
items=json.load(open(base+r'\assets\changelog.json',encoding='utf-8'))
parts=[]
for i,item in enumerate(items):
    marker='<div class="timeline-badge">最新</div>' if i==0 else '<div class="timeline-dot"></div>'
    lis=''.join('<li>'+html.escape(x)+'</li>' for x in item['changes'])
    parts.append('<article class="timeline-item'+(' current' if i==0 else '')+'">'+marker+'<div class="timeline-body"><div class="timeline-head"><h3>v'+html.escape(item['version'])+'</h3><time>'+html.escape(item['date'])+'</time></div><ul>'+lis+'</ul></div></article>')
parts.append('<div class="timeline-more">完整更新记录也保存在本地 <a href="CHANGELOG.md" target="_blank">CHANGELOG.md</a></div>')
start=s.index('<div class="timeline" id="timeline">'); end=s.index('        </div>\n      </div>\n    </section>',start)
s=s[:start]+'<div class="timeline" id="timeline">'+''.join(parts)+s[end:]
io.open(ip,'w',encoding='utf-8',newline='').write(s)
print('TIMELINE_REGEN',len(s))

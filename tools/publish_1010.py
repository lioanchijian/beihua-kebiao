import json, io, os, shutil, hashlib, zipfile
root = r'D:\lwh\北化课表 - 副本'
site = os.path.join(root, 'website')
apk_src = os.path.join(root, 'release', 'test', 'beihua-kebiao-1.0.9.10-android16-test.apk')
apk_dst = os.path.join(site, 'download', 'beihua-kebiao-latest.apk')
os.makedirs(os.path.dirname(apk_dst), exist_ok=True)
shutil.copy2(apk_src, apk_dst)
sha = hashlib.sha256(open(apk_src, 'rb').read()).hexdigest()
cp = os.path.join(site, 'assets', 'changelog.json')
items = json.load(open(cp, encoding='utf-8'))
items = [x for x in items if x.get('version') != '1.0.9.10']
items.insert(0, {'version': '1.0.9.10', 'date': '2026-10-26', 'changes': ['修复了部分设备UI的显示问题']})
open(cp, 'w', encoding='utf-8', newline='').write(json.dumps(items, ensure_ascii=False, indent=2))
vp = os.path.join(site, 'version.json')
v = json.load(open(vp, encoding='utf-8'))
v.update({'versionCode': 100, 'versionName': '1.0.9.10', 'releaseNotes': '修复了部分设备UI的显示问题', 'sha256': sha})
open(vp, 'w', encoding='utf-8', newline='').write(json.dumps(v, ensure_ascii=False, separators=(',', ':')))
ip = os.path.join(site, 'index.html')
s = io.open(ip, encoding='utf-8').read()
s = s.replace('v1.0.9.8', 'v1.0.9.10').replace('1.0.9.8 (98)', '1.0.9.10 (100)')
start = s.index('<div class="timeline" id="timeline">')
end = s.index('        </div>\n      </div>\n    </section>', start)
import html
parts = []
for i, item in enumerate(items):
    marker = '<div class="timeline-badge">最新</div>' if i == 0 else '<div class="timeline-dot"></div>'
    lis = ''.join('<li>' + html.escape(x) + '</li>' for x in item.get('changes', []))
    parts.append('<article class="timeline-item' + (' current' if i == 0 else '') + '">' + marker + '<div class="timeline-body"><div class="timeline-head"><h3>v' + html.escape(item['version']) + '</h3><time>' + html.escape(item['date']) + '</time></div><ul>' + lis + '</ul></div></article>')
parts.append('<div class="timeline-more">完整更新记录也保存在本地 <a href="CHANGELOG.md" target="_blank">CHANGELOG.md</a></div>')
s = s[:start] + '<div class="timeline" id="timeline">' + ''.join(parts) + s[end:]
io.open(ip, 'w', encoding='utf-8', newline='').write(s)
ap = os.path.join(site, 'assets', 'app.js')
a = io.open(ap, encoding='utf-8').read().replace('1.0.9.8', '1.0.9.10')
io.open(ap, 'w', encoding='utf-8', newline='').write(a)
lines = ['# 北化课表更新日志', '', '> 本地更新记录，网站会自动读取 assets/changelog.json 渲染完整时间线。', '']
for item in items:
    lines.append('## [' + item['version'] + '] - ' + item['date'])
    lines.append('')
    lines += ['- ' + c for c in item.get('changes', [])]
    lines.append('')
io.open(os.path.join(site, 'CHANGELOG.md'), 'w', encoding='utf-8', newline='').write('\n'.join(lines))
zp = os.path.join(site, 'site.zip')
if os.path.exists(zp): os.remove(zp)
with zipfile.ZipFile(zp, 'w', zipfile.ZIP_DEFLATED) as z:
    for b, d, fs in os.walk(site):
        for f in fs:
            if f == 'site.zip': continue
            p = os.path.join(b, f)
            z.write(p, os.path.relpath(p, site).replace('\\', '/'))
print('SHA', sha)
print('SITE_ZIP', os.path.getsize(zp))
print('VERSION', v['versionName'], v['versionCode'])
print('ITEMS', len(items))
print('APK', os.path.getsize(apk_dst))
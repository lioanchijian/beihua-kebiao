import os,zipfile,hashlib
root=r'D:\lwh\北化课表 - 副本'
site=os.path.join(root,'website')
def add_dir(z, base, skip_dirs=(), skip_files=()):
    for b,d,fs in os.walk(base):
        d[:] = [x for x in d if x not in skip_dirs and not x.startswith('.gradle') and x != 'build']
        for f in fs:
            if f in skip_files: continue
            p=os.path.join(b,f)
            if os.path.getsize(p) > 200*1024*1024: continue
            z.write(p, os.path.relpath(p,root).replace('\\','/'))
# website deployment package
wp=os.path.join(root,'packages','北化课表-网站部署包-1.0.9.10.zip')
with zipfile.ZipFile(wp,'w',zipfile.ZIP_DEFLATED) as z:
    for b,d,fs in os.walk(site):
        d[:] = [x for x in d if x not in ('site.zip',)]
        for f in fs:
            if f == 'site.zip': continue
            p=os.path.join(b,f)
            z.write(p, os.path.relpath(p,site).replace('\\','/'))
# full project package
fp=os.path.join(root,'packages','北化课表-完整项目-1.0.9.10.zip')
with zipfile.ZipFile(fp,'w',zipfile.ZIP_DEFLATED) as z:
    for name in ['sources','website','server','tools','release']:
        add_dir(z, os.path.join(root,name), skip_dirs={'archive','build','.gradle','packages'}, skip_files={'local.properties'})
    for name in ['README.md','CHANGELOG.md','.gitignore']:
        p=os.path.join(root,name)
        if os.path.exists(p): z.write(p,name)
for p in [wp,fp]:
    print(os.path.basename(p), os.path.getsize(p), hashlib.sha256(open(p,'rb').read()).hexdigest())
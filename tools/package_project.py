import os, zipfile, pathlib
root = pathlib.Path(r'D:\lwh\北化课表 - 副本')
out = root / 'packages' / '北化课表-完整项目-1.0.9.8.zip'
exclude_parts = {'.git', '.gradle', 'build', '.idea', '.kotlin', '.settings', '__pycache__', 'private', 'node_modules'}
exclude_names = {'local.properties', 'site.zip', '.cursor-tmp-captcha.png', '.cursor-tmp-captcha2.png'}

def add_tree(z, folder, arc_prefix):
    for dirpath, dirnames, filenames in os.walk(folder):
        dirnames[:] = [d for d in dirnames if d not in exclude_parts]
        for name in filenames:
            if name in exclude_names or name.endswith('.pyc'):
                continue
            src = pathlib.Path(dirpath) / name
            rel = src.relative_to(folder)
            z.write(src, str(pathlib.PurePosixPath(arc_prefix) / pathlib.PurePosixPath(*rel.parts)))

with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    z.write(root / 'tools' / 'package_readme.md', 'README.md')
    z.write(root / 'CHANGELOG.md', 'CHANGELOG.md')
    add_tree(z, root / 'sources' / 'android-app', 'android-app')
    add_tree(z, root / 'website', 'website')
    add_tree(z, root / 'server' / 'usage-stats', 'server/usage-stats')
    apk = root / 'release' / 'current' / 'beihua-kebiao-1.0.9.8.apk'
    z.write(apk, 'release/beihua-kebiao-1.0.9.8.apk')
print(out)
print(out.stat().st_size)
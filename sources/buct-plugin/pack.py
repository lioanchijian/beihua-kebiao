import hashlib, json, zipfile, os, sys

base = r'D:\lwh\北化课表\buct-plugin'
manifest_path = os.path.join(base, 'manifest.json')
index_path = os.path.join(base, 'index.js')
out_path = os.path.join(base, 'cn.edu.buct.zf-1.0.0.eduplugin')

with open(index_path, 'rb') as f:
    index_bytes = f.read()
sha = hashlib.sha256(index_bytes).hexdigest()
print('index.js sha256:', sha)

manifest = json.load(open(manifest_path, encoding='utf-8'))
manifest['files']['index.js'] = sha
with open(manifest_path, 'w', encoding='utf-8') as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
manifest_bytes = json.dumps(manifest, ensure_ascii=False, separators=(',', ':')).encode('utf-8')

with zipfile.ZipFile(out_path, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('manifest.json', manifest_bytes)
    z.writestr('index.js', index_bytes)

print('packed:', out_path, os.path.getsize(out_path), 'bytes')
# verify
with zipfile.ZipFile(out_path) as z:
    for n in z.namelist():
        print(' entry:', n, z.getinfo(n).file_size)
    m = json.loads(z.read('manifest.json'))
    assert hashlib.sha256(z.read('index.js')).hexdigest() == m['files']['index.js']
    print('hash ok; manifest keys:', list(m.keys()))

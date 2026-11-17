import pathlib, shutil, datetime
p = pathlib.Path('/etc/nginx/sites-available/beihua')
text = p.read_text(encoding='utf-8')
if 'location /api/' not in text:
    shutil.copyfile(p, str(p) + '.bak.' + datetime.datetime.now().strftime('%Y%m%d%H%M%S'))
    block = '''    location /api/ {
        proxy_pass http://127.0.0.1:8090/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        add_header Cache-Control "no-store";
    }
    location = /stats {
        proxy_pass http://127.0.0.1:8090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    location /stats/ {
        proxy_pass http://127.0.0.1:8090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
'''
    text = text.replace('    location / {', block + '    location / {')
    p.write_text(text, encoding='utf-8')
    print('patched')
else:
    print('already')

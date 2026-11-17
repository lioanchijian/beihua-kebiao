import json,urllib.request,websocket
j=json.load(urllib.request.urlopen('http://127.0.0.1:9333/json/list'))
t=[x for x in j if x.get('type')=='page'][0]
ws=websocket.create_connection(t['webSocketDebuggerUrl'],timeout=10)
def cmd(i,m,p=None):
 ws.send(json.dumps({'id':i,'method':m,'params':p or {}}))
 while True:
  r=json.loads(ws.recv())
  if r.get('id')==i:return r
cmd(1,'Runtime.enable')
expr="""(()=>({w:document.documentElement.clientWidth,sw:document.documentElement.scrollWidth,body:document.body.scrollWidth,wide:[...document.querySelectorAll('*')].filter(e=>e.scrollWidth>e.clientWidth+2&&e.clientWidth>0).slice(0,20).map(e=>[e.tagName,e.className,e.clientWidth,e.scrollWidth])}))()"""
r=cmd(2,'Runtime.evaluate',{'expression':expr,'returnByValue':True})
print(json.dumps(r['result']['result'].get('value'),ensure_ascii=False,indent=2))
ws.close()

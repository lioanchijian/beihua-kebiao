#!/usr/bin/env python3
import json, os, sqlite3, datetime, hmac, html, re, secrets, random
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

BASE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.join(BASE, "stats.db")
TOKEN = open(os.path.join(BASE, "token"), encoding="utf-8").read().strip()

def db():
    c = sqlite3.connect(DB, timeout=10)
    c.row_factory = sqlite3.Row
    c.execute("PRAGMA journal_mode=WAL")
    return c

def init():
    c = db()
    c.executescript("""
    CREATE TABLE IF NOT EXISTS installs (install_id TEXT PRIMARY KEY, first_seen TEXT NOT NULL, last_seen TEXT NOT NULL, version TEXT, platform TEXT);
    CREATE TABLE IF NOT EXISTS daily_active (day TEXT NOT NULL, install_id TEXT NOT NULL, PRIMARY KEY(day, install_id));
    CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY AUTOINCREMENT, event TEXT NOT NULL, install_id TEXT, version TEXT, source TEXT, created_at TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS fb_users(
      install_id TEXT PRIMARY KEY, display_id TEXT UNIQUE NOT NULL, nickname TEXT NOT NULL,
      token TEXT UNIQUE NOT NULL, created_at TEXT NOT NULL, last_seen TEXT,
      version TEXT, banned INTEGER DEFAULT 0);
    CREATE TABLE IF NOT EXISTS fb_messages(
      id INTEGER PRIMARY KEY AUTOINCREMENT, install_id TEXT NOT NULL,
      sender TEXT NOT NULL CHECK(sender IN ('user','admin')), body TEXT NOT NULL,
      created_at TEXT NOT NULL, read_by_user INTEGER DEFAULT 0, read_by_admin INTEGER DEFAULT 0);
    CREATE INDEX IF NOT EXISTS idx_fb_thread ON fb_messages(install_id, id);
    """); c.commit()
    fb_ensure_admin(c)
    c.close()

# ---------------- 匿名反馈 ----------------
FB_ADJ = ["晚风", "薄荷", "汽水", "山楂", "夜航", "白露", "晨雾", "青柠", "海盐", "星野", "林间", "半糖", "橘子", "南巷", "夏至"]
FB_NOUN = ["同学", "旅人", "小鹿", "灯塔", "书签", "云朵", "邮差", "雪团", "麦田", "萤火", "岛屿", "风筝", "松果", "列车", "月亮"]
FB_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # 去掉 0/O/1/I，避免抄错
ADMIN_INSTALL = "admin:liuan"
FB_WELCOME = "嗨，我是柳岸，北化课表的开发者。有问题、建议或者想吐槽的，直接在这儿说就行，我看到会回你～"
FB_MAX_BODY = 1000
FB_MIN_INTERVAL = 3  # 秒，同一会话两次发送的最小间隔

def fb_now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")

def fb_ensure_admin(c):
    row = c.execute("SELECT install_id FROM fb_users WHERE install_id=?", (ADMIN_INSTALL,)).fetchone()
    if row:
        return
    c.execute(
        "INSERT INTO fb_users(install_id, display_id, nickname, token, created_at, last_seen) VALUES(?,?,?,?,?,?)",
        (ADMIN_INSTALL, "BH-ADMIN", "柳岸", secrets.token_urlsafe(32), fb_now(), fb_now()))
    c.commit()

def fb_new_display_id(c):
    for _ in range(30):
        did = "BH-" + "".join(secrets.choice(FB_ALPHABET) for _ in range(6))
        if not c.execute("SELECT 1 FROM fb_users WHERE display_id=?", (did,)).fetchone():
            return did
    return "BH-" + secrets.token_hex(4).upper()

def fb_new_nickname(c):
    for _ in range(30):
        name = random.choice(FB_ADJ) + random.choice(FB_NOUN)
        if not c.execute("SELECT 1 FROM fb_users WHERE nickname=?", (name,)).fetchone():
            return name
    return random.choice(FB_ADJ) + random.choice(FB_NOUN) + str(random.randint(10, 99))

def fb_user_by_token(c, token):
    if not token:
        return None
    return c.execute("SELECT * FROM fb_users WHERE token=?", (token,)).fetchone()

def fb_unread_for_user(c, install_id):
    row = c.execute("SELECT COUNT(*) FROM fb_messages WHERE install_id=? AND sender='admin' AND read_by_user=0", (install_id,)).fetchone()
    return int(row[0] or 0)

def fb_unread_for_admin(c):
    row = c.execute("SELECT COUNT(*) FROM fb_messages WHERE sender='user' AND read_by_admin=0").fetchone()
    return int(row[0] or 0)

def fb_row_messages(c, install_id, since_id=0, limit=200, mark=""):
    rows = c.execute(
        "SELECT id, sender, body, created_at FROM fb_messages WHERE install_id=? AND id>? ORDER BY id LIMIT ?",
        (install_id, since_id, limit)).fetchall()
    if mark == "user":
        c.execute("UPDATE fb_messages SET read_by_user=1 WHERE install_id=? AND sender='admin' AND read_by_user=0", (install_id,))
        c.commit()
    elif mark == "admin":
        c.execute("UPDATE fb_messages SET read_by_admin=1 WHERE install_id=? AND sender='user' AND read_by_admin=0", (install_id,))
        c.commit()
    return [{"id": r[0], "sender": r[1], "body": r[2], "created_at": r[3]} for r in rows]

def fb_add_message(c, install_id, sender, body):
    now = fb_now()
    cur = c.execute(
        "INSERT INTO fb_messages(install_id, sender, body, created_at, read_by_user, read_by_admin) VALUES(?,?,?,?,?,?)",
        (install_id, sender, body, now, 1 if sender == "user" else 0, 1 if sender == "admin" else 0))
    c.commit()
    return cur.lastrowid

def fb_clean_body(raw):
    body = str(raw or "").strip()
    if len(body) > FB_MAX_BODY:
        body = body[:FB_MAX_BODY]
    return body

def fb_clean_nickname(raw):
    name = re.sub(r"\s+", " ", str(raw or "")).strip()
    if not (2 <= len(name) <= 12):
        return None
    return name

def fb_register(c, install_id, version, platform):
    """幂等注册：同一 install_id 反复调用返回同一条身份。"""
    now = fb_now()
    row = c.execute("SELECT * FROM fb_users WHERE install_id=?", (install_id,)).fetchone()
    if row:
        c.execute("UPDATE fb_users SET last_seen=?, version=? WHERE install_id=?", (now, version, install_id))
        c.commit()
        return row
    display_id = fb_new_display_id(c)
    nickname = fb_new_nickname(c)
    token = secrets.token_urlsafe(32)
    c.execute(
        "INSERT INTO fb_users(install_id, display_id, nickname, token, created_at, last_seen, version) VALUES(?,?,?,?,?,?,?)",
        (install_id, display_id, nickname, token, now, now, version))
    c.commit()
    fb_add_message(c, install_id, "admin", FB_WELCOME)
    return c.execute("SELECT * FROM fb_users WHERE install_id=?", (install_id,)).fetchone()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args): pass
    def send_json(self, code, obj):
        raw = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code); self.send_header("Content-Type", "application/json; charset=utf-8"); self.send_header("Cache-Control", "no-store"); self.send_header("Access-Control-Allow-Origin", "*"); self.send_header("Content-Length", str(len(raw))); self.end_headers(); self.wfile.write(raw)
    def send_html(self, code, text):
        raw = text.encode("utf-8"); self.send_response(code); self.send_header("Content-Type", "text/html; charset=utf-8"); self.send_header("Cache-Control", "no-store"); self.send_header("Content-Length", str(len(raw))); self.end_headers(); self.wfile.write(raw)
    def do_OPTIONS(self):
        self.send_response(204); self.send_header("Access-Control-Allow-Origin", "*"); self.send_header("Access-Control-Allow-Methods", "POST, GET, OPTIONS"); self.send_header("Access-Control-Allow-Headers", "Content-Type"); self.end_headers()
    def do_POST(self):
        try:
            n = int(self.headers.get("Content-Length", "0") or "0")
            data = json.loads(self.rfile.read(n) or b"{}")
            path = urlparse(self.path).path
            now = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")
            if path == "/api/stats/ping":
                iid = str(data.get("installId", ""))
                if not re.fullmatch(r"[A-Za-z0-9_-]{16,64}", iid): return self.send_json(400, {"ok": False, "error": "bad install id"})
                version = str(data.get("version", ""))[:32]
                platform = str(data.get("platform", "android"))[:16]
                day = datetime.date.today().isoformat()
                c = db(); c.execute("INSERT INTO installs(install_id, first_seen, last_seen, version, platform) VALUES(?,?,?,?,?) ON CONFLICT(install_id) DO UPDATE SET last_seen=excluded.last_seen, version=excluded.version, platform=excluded.platform", (iid, now, now, version, platform)); c.execute("INSERT OR IGNORE INTO daily_active(day, install_id) VALUES(?,?)", (day, iid)); c.commit(); c.close()
                return self.send_json(200, {"ok": True})
            if path == "/api/stats/download":
                iid = str(data.get("installId", ""))[:64]
                version = str(data.get("version", ""))[:32]
                source = str(data.get("source", "website"))[:32]
                c = db(); c.execute("INSERT INTO events(event, install_id, version, source, created_at) VALUES(?,?,?,?,?)", ("download", iid, version, source, now)); c.commit(); c.close()
                return self.send_json(200, {"ok": True})
            if path == "/api/feedback/register":
                iid = str(data.get("installId", ""))
                if not re.fullmatch(r"[A-Za-z0-9_-]{16,64}", iid):
                    return self.send_json(400, {"ok": False, "error": "bad install id"})
                version = str(data.get("version", ""))[:32]
                c = db()
                try:
                    row = fb_register(c, iid, version, str(data.get("platform", "android"))[:16])
                    return self.send_json(200, {
                        "ok": True,
                        "displayId": row["display_id"],
                        "nickname": row["nickname"],
                        "token": row["token"],
                        "unread": fb_unread_for_user(c, iid),
                        "banned": int(row["banned"] or 0),
                    })
                finally:
                    c.close()
            if path == "/api/feedback/send":
                c = db()
                try:
                    user = fb_user_by_token(c, str(data.get("token", "")))
                    if not user:
                        return self.send_json(401, {"ok": False, "error": "token 无效，请重进反馈页"})
                    iid = user["install_id"]
                    if int(user["banned"] or 0):
                        return self.send_json(403, {"ok": False, "error": "该会话已被关闭"})
                    body = fb_clean_body(data.get("body"))
                    if len(body) < 1:
                        return self.send_json(400, {"ok": False, "error": "内容不能为空"})
                    last = c.execute("SELECT created_at FROM fb_messages WHERE install_id=? AND sender='user' ORDER BY id DESC LIMIT 1", (iid,)).fetchone()
                    if last:
                        try:
                            prev = datetime.datetime.fromisoformat(last[0])
                            if (datetime.datetime.now(datetime.timezone.utc) - prev).total_seconds() < FB_MIN_INTERVAL:
                                return self.send_json(429, {"ok": False, "error": "发送太频繁，稍等一下"})
                        except Exception:
                            pass
                    mid = fb_add_message(c, iid, "user", body)
                    c.execute("UPDATE fb_users SET last_seen=? WHERE install_id=?", (now, iid)); c.commit()
                    return self.send_json(200, {"ok": True, "id": mid})
                finally:
                    c.close()
            if path == "/api/feedback/nickname":
                c = db()
                try:
                    user = fb_user_by_token(c, str(data.get("token", "")))
                    if not user:
                        return self.send_json(401, {"ok": False, "error": "token 无效"})
                    name = fb_clean_nickname(data.get("nickname"))
                    if not name:
                        return self.send_json(400, {"ok": False, "error": "昵称需 2-12 字"})
                    dup = c.execute("SELECT 1 FROM fb_users WHERE nickname=? AND install_id<>?", (name, user["install_id"])).fetchone()
                    if dup:
                        return self.send_json(409, {"ok": False, "error": "这个昵称有人用了，换一个吧"})
                    c.execute("UPDATE fb_users SET nickname=? WHERE install_id=?", (name, user["install_id"])); c.commit()
                    return self.send_json(200, {"ok": True, "nickname": name})
                finally:
                    c.close()
            if path == "/api/feedback/admin/reply":
                if not hmac.compare_digest(str(data.get("token", "")), TOKEN):
                    return self.send_json(403, {"ok": False, "error": "forbidden"})
                iid = str(data.get("install_id", ""))[:64]
                body = fb_clean_body(data.get("body"))
                if not iid or not body:
                    return self.send_json(400, {"ok": False, "error": "参数不完整"})
                c = db()
                try:
                    if not c.execute("SELECT 1 FROM fb_users WHERE install_id=?", (iid,)).fetchone():
                        return self.send_json(404, {"ok": False, "error": "no such user"})
                    mid = fb_add_message(c, iid, "admin", body)
                    return self.send_json(200, {"ok": True, "id": mid})
                finally:
                    c.close()
            return self.send_json(404, {"ok": False})
        except Exception as e:
            return self.send_json(400, {"ok": False, "error": str(e)[:120]})
    def do_GET(self):
        u = urlparse(self.path); q = parse_qs(u.query)
        if u.path == "/health": return self.send_json(200, {"ok": True})
        if u.path == "/api/feedback/thread":
            c = db()
            try:
                user = fb_user_by_token(c, (q.get("token") or [""])[0])
                if not user:
                    return self.send_json(401, {"ok": False, "error": "token 无效"})
                iid = user["install_id"]
                since = int((q.get("since_id") or ["0"])[0] or 0)
                mark = "user" if (q.get("mark_read") or ["0"])[0] == "1" else ""
                msgs = fb_row_messages(c, iid, since, mark=mark)
                return self.send_json(200, {
                    "ok": True, "messages": msgs,
                    "unread": fb_unread_for_user(c, iid),
                    "displayId": user["display_id"], "nickname": user["nickname"]})
            finally:
                c.close()
        if u.path == "/api/feedback/admin/list":
            token = (q.get("token") or [""])[0]
            if not hmac.compare_digest(token, TOKEN):
                return self.send_json(403, {"ok": False, "error": "forbidden"})
            c = db()
            try:
                rows = c.execute("""
                    SELECT u.install_id, u.display_id, u.nickname, u.last_seen, u.version, u.banned,
                           (SELECT COUNT(*) FROM fb_messages m WHERE m.install_id=u.install_id AND m.sender='user' AND m.read_by_admin=0) AS unread,
                           (SELECT body FROM fb_messages m WHERE m.install_id=u.install_id ORDER BY m.id DESC LIMIT 1) AS last_body,
                           (SELECT created_at FROM fb_messages m WHERE m.install_id=u.install_id ORDER BY m.id DESC LIMIT 1) AS last_at,
                           (SELECT MAX(id) FROM fb_messages m WHERE m.install_id=u.install_id) AS last_id
                    FROM fb_users u WHERE u.install_id<>? ORDER BY COALESCE(last_at,'') DESC
                """, (ADMIN_INSTALL,)).fetchall()
                return self.send_json(200, {"ok": True, "unread": fb_unread_for_admin(c), "threads": [{
                    "installId": r[0], "displayId": r[1], "nickname": r[2], "lastSeen": r[3],
                    "version": r[4], "banned": int(r[5] or 0), "unread": int(r[6] or 0),
                    "lastBody": r[7] or "", "lastAt": r[8] or "", "lastId": int(r[9] or 0)} for r in rows]})
            finally:
                c.close()
        if u.path == "/api/feedback/admin/thread":
            token = (q.get("token") or [""])[0]
            if not hmac.compare_digest(token, TOKEN):
                return self.send_json(403, {"ok": False, "error": "forbidden"})
            iid = (q.get("install_id") or [""])[0]
            c = db()
            try:
                user = c.execute("SELECT * FROM fb_users WHERE install_id=?", (iid,)).fetchone()
                if not user:
                    return self.send_json(404, {"ok": False, "error": "no such user"})
                since = int((q.get("since_id") or ["0"])[0] or 0)
                msgs = fb_row_messages(c, iid, since, limit=500, mark="admin")
                return self.send_json(200, {"ok": True, "messages": msgs,
                    "displayId": user["display_id"], "nickname": user["nickname"],
                    "version": user["version"] or "", "lastSeen": user["last_seen"] or ""})
            finally:
                c.close()
        if u.path == "/feedback":
            token = (q.get("token") or [""])[0]
            if not hmac.compare_digest(token, TOKEN):
                return self.send_html(403, "<h1>403</h1><p>反馈后台需要私有 token。</p>")
            return self.send_html(200, fb_admin_page(html.escape(token, quote=True)))
        if u.path not in ("/stats", "/api/stats/summary"): return self.send_json(404, {"ok": False})
        token = (q.get("token") or [""])[0]
        if not hmac.compare_digest(token, TOKEN): return self.send_html(403, "<h1>403</h1><p>统计页需要私有 token。</p>")
        c = db(); today = datetime.date.today().isoformat()
        def one(sql, args=()): return c.execute(sql, args).fetchone()[0]
        total = one("SELECT COUNT(*) FROM installs")
        dau = one("SELECT COUNT(DISTINCT install_id) FROM daily_active WHERE day=?", (today,))
        wau = one("SELECT COUNT(DISTINCT install_id) FROM daily_active WHERE day>=?", ((datetime.date.today()-datetime.timedelta(days=6)).isoformat(),))
        mau = one("SELECT COUNT(DISTINCT install_id) FROM daily_active WHERE day>=?", ((datetime.date.today()-datetime.timedelta(days=29)).isoformat(),))
        downloads = one("SELECT COUNT(*) FROM events WHERE event=\"download\"")
        # 注意：db() 启用 row_factory=Row 后必须转成 tuple，否则 json.dumps 会报不可序列化
        versions = [tuple(r) for r in c.execute("SELECT COALESCE(version, \"unknown\"), COUNT(*) FROM installs GROUP BY version ORDER BY COUNT(*) DESC LIMIT 8").fetchall()]
        trend = [tuple(r) for r in c.execute("SELECT day, COUNT(*) FROM daily_active WHERE day>=? GROUP BY day ORDER BY day", ((datetime.date.today()-datetime.timedelta(days=13)).isoformat(),)).fetchall()]
        c.close()
        if u.path == "/api/stats/summary": return self.send_json(200, {"totalInstalls": total, "dau": dau, "wau": wau, "mau": mau, "downloads": downloads, "versions": versions, "trend": trend})
        rows = "".join(f"<tr><td>{html.escape(str(v))}</td><td>{n}</td></tr>" for v,n in versions) or "<tr><td colspan=2>暂无数据</td></tr>"
        trend_rows = "".join(f"<tr><td>{html.escape(str(d))}</td><td>{n}</td></tr>" for d,n in trend) or "<tr><td colspan=2>暂无数据</td></tr>"
        page = f"""<!doctype html><html lang=zh-CN><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>北化课表 · 使用统计</title><style>body{{background:#0b1220;color:#e5edff;font-family:system-ui,-apple-system,sans-serif;margin:0;padding:28px}}main{{max-width:980px;margin:auto}}.cards{{display:grid;grid-template-columns:repeat(5,1fr);gap:12px}}@media(max-width:700px){{.cards{{grid-template-columns:repeat(2,1fr)}}}}.card{{background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.12);border-radius:16px;padding:18px;backdrop-filter:blur(14px)}}.card b{{font-size:28px;display:block}}.card span{{color:#94a3b8;font-size:12px}}table{{width:100%;border-collapse:collapse;margin:20px 0;background:rgba(255,255,255,.04);border-radius:14px;overflow:hidden}}td,th{{padding:10px 14px;border-bottom:1px solid rgba(255,255,255,.08);text-align:left}}h2{{margin-top:34px}}code{{color:#93c5fd}}</style></head><body><main><h1>北化课表 · 使用统计</h1><p style='color:#94a3b8'>统计仅含随机安装 ID、版本、启动日期和下载点击，不含姓名、学号、账号、密码或 IP。</p><div class=cards><div class=card><b>{total}</b><span>累计安装设备</span></div><div class=card><b>{dau}</b><span>今日活跃</span></div><div class=card><b>{wau}</b><span>7 日活跃</span></div><div class=card><b>{mau}</b><span>30 日活跃</span></div><div class=card><b>{downloads}</b><span>网站下载点击</span></div></div><h2>版本分布</h2><table><tr><th>版本</th><th>设备数</th></tr>{rows}</table><h2>最近 14 天活跃</h2><table><tr><th>日期</th><th>活跃设备</th></tr>{trend_rows}</table><p style='color:#64748b'>数据更新时间：{datetime.datetime.now().strftime('%Y-%m-%d %H:%M')} · 页面每 30 秒自动刷新</p></main><script>setTimeout(()=>location.reload(),30000)</script></body></html>"""
        return self.send_html(200, page)

FB_ADMIN_HTML = """<!doctype html><html lang=zh-CN><head><meta charset=utf-8>
<meta name=viewport content='width=device-width,initial-scale=1'><title>北化课表 · 意见反馈</title>
<style>
*{box-sizing:border-box}
body{background:#0b1220;color:#e5edff;font-family:system-ui,-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;margin:0;height:100vh;overflow:hidden}
main{display:flex;height:100vh}
.side{width:320px;min-width:260px;border-right:1px solid rgba(255,255,255,.10);display:flex;flex-direction:column;background:rgba(255,255,255,.03)}
.side h1{font-size:15px;margin:0;padding:16px 16px 10px;font-weight:600}
.side .hint{color:#7c8ba1;font-size:11px;padding:0 16px 10px;line-height:1.6}
.list{flex:1;overflow:auto}
.item{padding:12px 16px;border-bottom:1px solid rgba(255,255,255,.06);cursor:pointer}
.item:hover{background:rgba(255,255,255,.05)}
.item.active{background:rgba(96,165,250,.14)}
.item .row1{display:flex;align-items:center;gap:8px}
.item .nick{font-weight:600;font-size:13px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.item .badge{background:#ef4444;color:#fff;border-radius:9px;padding:0 6px;font-size:11px;line-height:18px}
.item .sub{color:#7c8ba1;font-size:11px;margin-top:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.chat{flex:1;display:flex;flex-direction:column;min-width:0}
.head{padding:14px 20px;border-bottom:1px solid rgba(255,255,255,.10);display:flex;align-items:center;gap:10px}
.head .who{font-weight:600}
.head .meta{color:#7c8ba1;font-size:11px}
.msgs{flex:1;overflow:auto;padding:18px;display:flex;flex-direction:column;gap:10px}
.msg{max-width:68%;padding:9px 13px;border-radius:14px;font-size:13px;line-height:1.55;white-space:pre-wrap;word-break:break-word}
.msg.user{align-self:flex-start;background:rgba(255,255,255,.09);border-bottom-left-radius:4px}
.msg.admin{align-self:flex-end;background:#2563eb;border-bottom-right-radius:4px}
.msg .t{display:block;font-size:10px;opacity:.6;margin-top:4px}
.composer{border-top:1px solid rgba(255,255,255,.10);padding:12px 16px;display:flex;gap:10px;align-items:flex-end}
textarea{flex:1;background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.12);color:#e5edff;border-radius:12px;padding:10px 12px;font-size:13px;resize:none;height:64px;outline:none;font-family:inherit}
textarea:focus{border-color:#3b82f6}
button{background:#2563eb;color:#fff;border:0;border-radius:12px;padding:0 18px;height:40px;font-size:13px;cursor:pointer}
button:disabled{opacity:.5;cursor:default}
.empty{flex:1;display:flex;align-items:center;justify-content:center;color:#64748b;font-size:13px}
</style></head><body><main>
<div class=side><h1>意见反馈</h1><div class=hint>只收到匿名短号与消息正文，不含姓名、学号、密码。点会话即可回复。</div><div class=list id=list></div></div>
<div class=chat id=chat><div class=empty>从左侧选一个会话来回复</div></div>
</main>
<script>
const TOKEN = "__TOKEN__";
let current = null, lastId = 0, threads = [];
const $ = id => document.getElementById(id);
const fmt = s => { try { const d = new Date(s); return d.toLocaleString('zh-CN',{hour12:false,month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}); } catch(e){ return s; } };
async function api(path){ const r = await fetch(path + (path.includes('?')?'&':'?') + 'token=' + encodeURIComponent(TOKEN)); return r.json(); }
function renderList(){
  const box = $('list'); box.innerHTML='';
  threads.forEach(t => {
    const d = document.createElement('div');
    d.className = 'item' + (current && current.installId === t.installId ? ' active' : '');
    const r1 = document.createElement('div'); r1.className='row1';
    const n = document.createElement('div'); n.className='nick'; n.textContent = t.nickname + ' · ' + t.displayId;
    r1.appendChild(n);
    if (t.unread > 0){ const b=document.createElement('span'); b.className='badge'; b.textContent=t.unread; r1.appendChild(b); }
    const sub = document.createElement('div'); sub.className='sub';
    sub.textContent = (t.lastBody || '暂无消息').slice(0, 40) + (t.lastAt ? ' · ' + fmt(t.lastAt) : '');
    d.appendChild(r1); d.appendChild(sub);
    d.onclick = () => openThread(t);
    box.appendChild(d);
  });
  if (!threads.length){ const e=document.createElement('div'); e.className='item'; e.style.color='#64748b'; e.textContent='还没有人反馈'; box.appendChild(e); }
}
async function loadThreads(){
  try { const d = await api('/api/feedback/admin/list'); if (d.ok){ threads = d.threads; renderList(); if (current){ const t = threads.find(x=>x.installId===current.installId); if (t) current = t; } } } catch(e){}
}
function renderHead(){
  if (!current) return;
  $('chat').innerHTML = `
    <div class=head><div><div class=who></div><div class=meta></div></div></div>
    <div class=msgs id=msgs></div>
    <div class=composer><textarea id=input placeholder="回复这条反馈…（Enter 发送，Shift+Enter 换行）"></textarea><button id=send>发送</button></div>`;
  $('chat').querySelector('.who').textContent = current.nickname + ' · ' + current.displayId;
  $('chat').querySelector('.meta').textContent = '版本 ' + (current.version||'未知') + ' · 最近活跃 ' + fmt(current.lastSeen||'');
  $('send').onclick = sendReply;
  $('input').addEventListener('keydown', e => { if (e.key === 'Enter' && !e.shiftKey){ e.preventDefault(); sendReply(); } });
}
function appendMsgs(list){
  const box = $('msgs'); if (!box) return;
  list.forEach(m => {
    if (box.querySelector('[data-id="'+m.id+'"]')) return;
    const d = document.createElement('div');
    d.className = 'msg ' + (m.sender === 'user' ? 'user' : 'admin');
    d.setAttribute('data-id', m.id);
    const body = document.createElement('div'); body.textContent = m.body;
    const t = document.createElement('span'); t.className='t'; t.textContent = fmt(m.created_at);
    d.appendChild(body); d.appendChild(t); box.appendChild(d);
    if (m.id > lastId) lastId = m.id;
  });
  box.scrollTop = box.scrollHeight;
}
async function openThread(t){
  current = t; lastId = 0; renderList(); renderHead();
  const d = await api('/api/feedback/admin/thread?install_id=' + encodeURIComponent(t.installId));
  if (d.ok){ appendMsgs(d.messages); loadThreads(); }
}
async function sendReply(){
  if (!current) return;
  const input = $('input'); const body = input.value.trim(); if (!body) return;
  input.value = ''; $('send').disabled = true;
  try {
    const r = await fetch('/api/feedback/admin/reply', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({token: TOKEN, install_id: current.installId, body})});
    const d = await r.json();
    if (d.ok){ const t = await api('/api/feedback/admin/thread?install_id=' + encodeURIComponent(current.installId) + '&since_id=' + lastId); if (t.ok) appendMsgs(t.messages); }
    else { alert(d.error || '发送失败'); }
  } catch(e){ alert('网络异常'); }
  $('send').disabled = false;
}
loadThreads(); setInterval(loadThreads, 5000);
setInterval(async () => { if (!current) return; const d = await api('/api/feedback/admin/thread?install_id=' + encodeURIComponent(current.installId) + '&since_id=' + lastId); if (d.ok) appendMsgs(d.messages); }, 5000);
</script></body></html>"""

def fb_admin_page(token):
    return FB_ADMIN_HTML.replace("__TOKEN__", token)


if __name__ == "__main__":
    init()
    ThreadingHTTPServer(("127.0.0.1", 8090), Handler).serve_forever()

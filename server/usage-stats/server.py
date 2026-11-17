#!/usr/bin/env python3
import json, os, sqlite3, datetime, hmac, html, re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

BASE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.join(BASE, "stats.db")
TOKEN = open(os.path.join(BASE, "token"), encoding="utf-8").read().strip()

def db():
    c = sqlite3.connect(DB, timeout=10)
    c.execute("PRAGMA journal_mode=WAL")
    return c

def init():
    c = db()
    c.executescript("""
    CREATE TABLE IF NOT EXISTS installs (install_id TEXT PRIMARY KEY, first_seen TEXT NOT NULL, last_seen TEXT NOT NULL, version TEXT, platform TEXT);
    CREATE TABLE IF NOT EXISTS daily_active (day TEXT NOT NULL, install_id TEXT NOT NULL, PRIMARY KEY(day, install_id));
    CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY AUTOINCREMENT, event TEXT NOT NULL, install_id TEXT, version TEXT, source TEXT, created_at TEXT NOT NULL);
    """); c.commit(); c.close()

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
            return self.send_json(404, {"ok": False})
        except Exception as e:
            return self.send_json(400, {"ok": False, "error": str(e)[:120]})
    def do_GET(self):
        u = urlparse(self.path); q = parse_qs(u.query)
        if u.path == "/health": return self.send_json(200, {"ok": True})
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
        versions = c.execute("SELECT COALESCE(version, \"unknown\"), COUNT(*) FROM installs GROUP BY version ORDER BY COUNT(*) DESC LIMIT 8").fetchall()
        trend = c.execute("SELECT day, COUNT(*) FROM daily_active WHERE day>=? GROUP BY day ORDER BY day", ((datetime.date.today()-datetime.timedelta(days=13)).isoformat(),)).fetchall()
        c.close()
        if u.path == "/api/stats/summary": return self.send_json(200, {"totalInstalls": total, "dau": dau, "wau": wau, "mau": mau, "downloads": downloads, "versions": versions, "trend": trend})
        rows = "".join(f"<tr><td>{html.escape(str(v))}</td><td>{n}</td></tr>" for v,n in versions) or "<tr><td colspan=2>暂无数据</td></tr>"
        trend_rows = "".join(f"<tr><td>{html.escape(str(d))}</td><td>{n}</td></tr>" for d,n in trend) or "<tr><td colspan=2>暂无数据</td></tr>"
        page = f"""<!doctype html><html lang=zh-CN><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>北化课表 · 使用统计</title><style>body{{background:#0b1220;color:#e5edff;font-family:system-ui,-apple-system,sans-serif;margin:0;padding:28px}}main{{max-width:980px;margin:auto}}.cards{{display:grid;grid-template-columns:repeat(5,1fr);gap:12px}}@media(max-width:700px){{.cards{{grid-template-columns:repeat(2,1fr)}}}}.card{{background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.12);border-radius:16px;padding:18px;backdrop-filter:blur(14px)}}.card b{{font-size:28px;display:block}}.card span{{color:#94a3b8;font-size:12px}}table{{width:100%;border-collapse:collapse;margin:20px 0;background:rgba(255,255,255,.04);border-radius:14px;overflow:hidden}}td,th{{padding:10px 14px;border-bottom:1px solid rgba(255,255,255,.08);text-align:left}}h2{{margin-top:34px}}code{{color:#93c5fd}}</style></head><body><main><h1>北化课表 · 使用统计</h1><p style='color:#94a3b8'>统计仅含随机安装 ID、版本、启动日期和下载点击，不含姓名、学号、账号、密码或 IP。</p><div class=cards><div class=card><b>{total}</b><span>累计安装设备</span></div><div class=card><b>{dau}</b><span>今日活跃</span></div><div class=card><b>{wau}</b><span>7 日活跃</span></div><div class=card><b>{mau}</b><span>30 日活跃</span></div><div class=card><b>{downloads}</b><span>网站下载点击</span></div></div><h2>版本分布</h2><table><tr><th>版本</th><th>设备数</th></tr>{rows}</table><h2>最近 14 天活跃</h2><table><tr><th>日期</th><th>活跃设备</th></tr>{trend_rows}</table><p style='color:#64748b'>数据更新时间：{datetime.datetime.now().strftime('%Y-%m-%d %H:%M')} · 页面每 30 秒自动刷新</p></main><script>setTimeout(()=>location.reload(),30000)</script></body></html>"""
        return self.send_html(200, page)

if __name__ == "__main__":
    init()
    ThreadingHTTPServer(("127.0.0.1", 8090), Handler).serve_forever()

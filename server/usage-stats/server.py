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
    CREATE TABLE IF NOT EXISTS friends(
      owner TEXT NOT NULL, friend TEXT NOT NULL, created_at TEXT NOT NULL,
      PRIMARY KEY(owner, friend));
    CREATE TABLE IF NOT EXISTS friend_requests(
      id INTEGER PRIMARY KEY AUTOINCREMENT, from_id TEXT NOT NULL, to_id TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'pending', created_at TEXT NOT NULL,
      UNIQUE(from_id, to_id));
    CREATE TABLE IF NOT EXISTS dm_messages(
      id INTEGER PRIMARY KEY AUTOINCREMENT, from_id TEXT NOT NULL, to_id TEXT NOT NULL,
      kind TEXT NOT NULL DEFAULT 'text', body TEXT NOT NULL, payload TEXT,
      created_at TEXT NOT NULL, read_by_to INTEGER DEFAULT 0);
    CREATE INDEX IF NOT EXISTS idx_dm_pair ON dm_messages(from_id, to_id, id);
    CREATE TABLE IF NOT EXISTS friend_blocks(
      owner TEXT NOT NULL, blocked TEXT NOT NULL, created_at TEXT NOT NULL,
      PRIMARY KEY(owner, blocked));
    CREATE TABLE IF NOT EXISTS friend_reports(
      id INTEGER PRIMARY KEY AUTOINCREMENT, reporter TEXT NOT NULL, target TEXT NOT NULL,
      message_id INTEGER, reason TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '',
      status TEXT NOT NULL DEFAULT 'open', created_at TEXT NOT NULL);
    CREATE INDEX IF NOT EXISTS idx_friend_reports_status ON friend_reports(status, id);
    CREATE TABLE IF NOT EXISTS friend_lookup_log(
      id INTEGER PRIMARY KEY AUTOINCREMENT, owner TEXT NOT NULL, created_at TEXT NOT NULL);
    CREATE INDEX IF NOT EXISTS idx_lookup_owner ON friend_lookup_log(owner, created_at);
    CREATE TABLE IF NOT EXISTS server_config(
      key TEXT PRIMARY KEY, value TEXT NOT NULL);
    """); c.commit()
    # 旧库平滑升级：CREATE TABLE IF NOT EXISTS 不会给已有表补列。
    for table, column, ddl in (
        ("friends", "remark", "TEXT NOT NULL DEFAULT ''"),
        ("fb_users", "admin_note", "TEXT NOT NULL DEFAULT ''"),
    ):
        try:
            c.execute(f"ALTER TABLE {table} ADD COLUMN {column} {ddl}")
            c.commit()
        except sqlite3.DatabaseError:
            c.rollback()
    c.execute("INSERT OR IGNORE INTO server_config(key, value) VALUES('social_enabled', '1')")
    c.commit()
    # 昵称唯一（尽力而为：历史库若已有重名则跳过，不影响启动）
    try:
        c.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_fb_nickname ON fb_users(nickname)")
        c.commit()
    except sqlite3.DatabaseError:
        c.rollback()
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
    """生成一个当前库里不存在的短号。

    短号空间 = 32^6 ≈ 10.7 亿（字母表已去掉 0/O/1/I，避免抄错）。
    这里先查重；真正的"绝不重复"由 display_id 的 UNIQUE 约束 + 插入冲突重试保证。
    """
    for _ in range(30):
        did = "BH-" + "".join(secrets.choice(FB_ALPHABET) for _ in range(6))
        if not c.execute("SELECT 1 FROM fb_users WHERE display_id=?", (did,)).fetchone():
            return did
    # 极端情况（6 位空间接近用尽）：退化为 10 位短号继续保证唯一
    for _ in range(30):
        did = "BH-" + "".join(secrets.choice(FB_ALPHABET) for _ in range(10))
        if not c.execute("SELECT 1 FROM fb_users WHERE display_id=?", (did,)).fetchone():
            return did
    raise RuntimeError("短号空间异常，请检查 fb_users 表")

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

def fb_register(c, install_id, version, platform, tries=12):
    """幂等注册：同一 install_id 反复调用返回同一条身份。

    短号绝不重复由两层保证：
      1) fb_users.display_id 上有 UNIQUE 约束（数据库层硬约束）；
      2) 并发注册时两个请求可能撞同一个短号，插入冲突会抛 IntegrityError，
         这里捕获后重新生成，而不是把 500 抛给用户（token 撞了同样处理）。
    """
    now = fb_now()
    for _ in range(tries):
        row = c.execute("SELECT * FROM fb_users WHERE install_id=?", (install_id,)).fetchone()
        if row:
            c.execute("UPDATE fb_users SET last_seen=?, version=? WHERE install_id=?", (now, version, install_id))
            c.commit()
            return row
        display_id = fb_new_display_id(c)
        nickname = fb_new_nickname(c)
        token = secrets.token_urlsafe(32)
        try:
            c.execute(
                "INSERT INTO fb_users(install_id, display_id, nickname, token, created_at, last_seen, version) VALUES(?,?,?,?,?,?,?)",
                (install_id, display_id, nickname, token, now, now, version))
            c.commit()
        except sqlite3.IntegrityError:
            # 短号/token 被别人抢先占了（或同 install_id 并发注册），重新来一轮
            c.rollback()
            continue
        fb_add_message(c, install_id, "admin", FB_WELCOME)
        return c.execute("SELECT * FROM fb_users WHERE install_id=?", (install_id,)).fetchone()
    raise RuntimeError("短号分配冲突，请重试")


# ---------------- 好友 / 课表互传 ----------------
FR_MAX_TEXT = 1000
FR_MAX_PAYLOAD = 262144      # 课表 JSON 上限 256KB
FR_MIN_INTERVAL = 2          # 秒
FR_RATE_PER_MIN = 20         # 单账号每分钟消息上限
FR_RATE_PER_DAY = 200        # 单账号每天消息上限
FR_LOOKUP_PER_MIN = 20       # 短号预览每分钟上限，防枚举
FR_MAX_REMARK = 12
FR_MAX_REPORT_DETAIL = 300
FR_KINDS = ("text", "schedule")
SCHEDULE_KIND_LABEL = "课表"
FR_REPORT_REASONS = ("骚扰或辱骂", "垃圾广告", "诈骗或索要财物", "其他")


def fr_config_get(c, key, default=""):
    row = c.execute("SELECT value FROM server_config WHERE key=?", (key,)).fetchone()
    return row[0] if row else default


def fr_config_set(c, key, value):
    c.execute(
        "INSERT INTO server_config(key, value) VALUES(?,?) "
        "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
        (key, str(value)))
    c.commit()


def fr_social_enabled(c):
    return fr_config_get(c, "social_enabled", "1") == "1"


def fr_is_banned(user):
    return bool(user and int(user["banned"] or 0))


def fr_blocked(c, a, b):
    """任一方拉黑，双方都不能再发消息或重新添加。"""
    return c.execute(
        "SELECT 1 FROM friend_blocks WHERE (owner=? AND blocked=?) OR (owner=? AND blocked=?)",
        (a, b, b, a)).fetchone() is not None


def fr_rate_limit_message(c, sender, now):
    """按 UTC 时间窗口统计发送量；返回错误文案或 None。"""
    try:
        dt = datetime.datetime.fromisoformat(now)
    except Exception:
        dt = datetime.datetime.now(datetime.timezone.utc)
    minute_start = (dt - datetime.timedelta(minutes=1)).isoformat(timespec="seconds")
    day_start = dt.replace(hour=0, minute=0, second=0, microsecond=0).isoformat(timespec="seconds")
    minute_count = c.execute(
        "SELECT COUNT(*) FROM dm_messages WHERE from_id=? AND created_at>=?",
        (sender, minute_start)).fetchone()[0]
    if int(minute_count or 0) >= FR_RATE_PER_MIN:
        return "一分钟内发太多了，歇一会儿再发"
    day_count = c.execute(
        "SELECT COUNT(*) FROM dm_messages WHERE from_id=? AND created_at>=?",
        (sender, day_start)).fetchone()[0]
    if int(day_count or 0) >= FR_RATE_PER_DAY:
        return "今天发得太多了，明天再聊吧"
    return None


def fr_lookup_allowed(c, owner, now):
    """短号预览限频；超过上限直接拒绝，返回错误文案或 None。"""
    try:
        dt = datetime.datetime.fromisoformat(now)
    except Exception:
        dt = datetime.datetime.now(datetime.timezone.utc)
    cutoff = (dt - datetime.timedelta(minutes=1)).isoformat(timespec="seconds")
    # 顺手清掉一天前的日志，表不会无限增长
    c.execute("DELETE FROM friend_lookup_log WHERE created_at<?",
              ((dt - datetime.timedelta(days=1)).isoformat(timespec="seconds"),))
    count = c.execute(
        "SELECT COUNT(*) FROM friend_lookup_log WHERE owner=? AND created_at>=?",
        (owner, cutoff)).fetchone()[0]
    if int(count or 0) >= FR_LOOKUP_PER_MIN:
        return "短号查得太频繁了，过一分钟再试"
    c.execute("INSERT INTO friend_lookup_log(owner, created_at) VALUES(?,?)", (owner, now))
    c.commit()
    return None


def fr_remark_of(c, owner, friend):
    row = c.execute("SELECT remark FROM friends WHERE owner=? AND friend=?", (owner, friend)).fetchone()
    return (row[0] or "") if row else ""


def fr_target_or_error(c, me, data):
    """查找目标并统一处理封禁/拉黑；返回 (target, error_json) 之一。"""
    target = fr_by_display(c, data.get("displayId"))
    if not target:
        return None, (404, {"ok": False, "error": "没找到这个短号，确认一下是否输错"})
    if target["install_id"] == me["install_id"]:
        return None, (400, {"ok": False, "error": "不能对自己操作呀"})
    if fr_is_banned(target):
        return None, (404, {"ok": False, "error": "没找到这个短号，确认一下是否输错"})
    return target, None


def fr_require_social(c):
    return None if fr_social_enabled(c) else (403, {"ok": False, "error": "好友功能暂时关闭，请稍后再试"})


def fr_by_display(c, display_id):
    did = str(display_id or "").strip().upper()
    if not did:
        return None
    return c.execute("SELECT * FROM fb_users WHERE display_id=?", (did,)).fetchone()


def fr_are_friends(c, a, b):
    return c.execute("SELECT 1 FROM friends WHERE owner=? AND friend=?", (a, b)).fetchone() is not None


def fr_link(c, a, b, now):
    """双向写入好友关系（互为好友），并清掉两边历史请求。"""
    c.execute("INSERT OR IGNORE INTO friends(owner, friend, created_at) VALUES(?,?,?)", (a, b, now))
    c.execute("INSERT OR IGNORE INTO friends(owner, friend, created_at) VALUES(?,?,?)", (b, a, now))
    c.execute("UPDATE friend_requests SET status='accepted' WHERE (from_id=? AND to_id=?) OR (from_id=? AND to_id=?)", (a, b, b, a))
    c.commit()


def fr_unlink(c, a, b):
    c.execute("DELETE FROM friends WHERE (owner=? AND friend=?) OR (owner=? AND friend=?)", (a, b, b, a))
    c.commit()


def fr_unread(c, owner, friend):
    row = c.execute(
        "SELECT COUNT(*) FROM dm_messages WHERE from_id=? AND to_id=? AND read_by_to=0",
        (friend, owner)).fetchone()
    return int(row[0] or 0)


def fr_total_unread(c, owner):
    row = c.execute("SELECT COUNT(*) FROM dm_messages WHERE to_id=? AND read_by_to=0", (owner,)).fetchone()
    return int(row[0] or 0)


def fr_pending_requests(c, owner):
    rows = c.execute("""
        SELECT r.id, u.display_id, u.nickname, r.created_at
        FROM friend_requests r JOIN fb_users u ON u.install_id = r.from_id
        WHERE r.to_id=? AND r.status='pending' ORDER BY r.id DESC LIMIT 50
    """, (owner,)).fetchall()
    return [{"id": r[0], "displayId": r[1], "nickname": r[2], "createdAt": r[3]} for r in rows]


def fr_friend_rows(c, owner):
    rows = c.execute("""
        SELECT u.install_id, u.display_id, u.nickname, u.last_seen, f.remark,
               (SELECT COUNT(*) FROM dm_messages m WHERE m.from_id=u.install_id AND m.to_id=? AND m.read_by_to=0) AS unread,
               (SELECT body FROM dm_messages m
                 WHERE (m.from_id=u.install_id AND m.to_id=?) OR (m.from_id=? AND m.to_id=u.install_id)
                 ORDER BY m.id DESC LIMIT 1) AS last_body,
               (SELECT kind FROM dm_messages m
                 WHERE (m.from_id=u.install_id AND m.to_id=?) OR (m.from_id=? AND m.to_id=u.install_id)
                 ORDER BY m.id DESC LIMIT 1) AS last_kind,
               (SELECT created_at FROM dm_messages m
                 WHERE (m.from_id=u.install_id AND m.to_id=?) OR (m.from_id=? AND m.to_id=u.install_id)
                 ORDER BY m.id DESC LIMIT 1) AS last_at
        FROM friends f JOIN fb_users u ON u.install_id = f.friend
        WHERE f.owner=? ORDER BY COALESCE(last_at, f.created_at) DESC
    """, (owner, owner, owner, owner, owner, owner, owner, owner)).fetchall()
    return [{
        "displayId": r[1], "nickname": r[2], "remark": r[4] or "",
        "lastSeen": r[3] or "", "unread": int(r[5] or 0),
        "lastBody": r[6] or "", "lastKind": r[7] or "", "lastAt": r[8] or "",
    } for r in rows]


def fr_thread(c, owner, friend, since_id=0, limit=300):
    rows = c.execute("""
        SELECT id, from_id, kind, body, payload, created_at
        FROM dm_messages
        WHERE ((from_id=? AND to_id=?) OR (from_id=? AND to_id=?)) AND id>?
        ORDER BY id LIMIT ?
    """, (owner, friend, friend, owner, since_id, limit)).fetchall()
    c.execute("UPDATE dm_messages SET read_by_to=1 WHERE from_id=? AND to_id=? AND read_by_to=0", (friend, owner))
    c.commit()
    return [{
        "id": r[0], "fromMe": r[1] == owner, "kind": r[2],
        "body": r[3], "payload": r[4] or "", "createdAt": r[5],
    } for r in rows]


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
            if path == "/api/feedback/admin/note":
                if not hmac.compare_digest(str(data.get("token", "")), TOKEN):
                    return self.send_json(403, {"ok": False, "error": "forbidden"})
                iid = str(data.get("install_id", ""))[:64]
                note = str(data.get("note", "")).strip()[:200]
                c = db()
                try:
                    if not c.execute("SELECT 1 FROM fb_users WHERE install_id=?", (iid,)).fetchone():
                        return self.send_json(404, {"ok": False, "error": "no such user"})
                    c.execute("UPDATE fb_users SET admin_note=? WHERE install_id=?", (note, iid)); c.commit()
                    return self.send_json(200, {"ok": True, "adminNote": note})
                finally:
                    c.close()
            if path == "/api/feedback/admin/ban":
                if not hmac.compare_digest(str(data.get("token", "")), TOKEN):
                    return self.send_json(403, {"ok": False, "error": "forbidden"})
                iid = str(data.get("install_id", ""))[:64]
                banned = 1 if data.get("banned") else 0
                c = db()
                try:
                    if not c.execute("SELECT 1 FROM fb_users WHERE install_id=?", (iid,)).fetchone():
                        return self.send_json(404, {"ok": False, "error": "no such user"})
                    c.execute("UPDATE fb_users SET banned=? WHERE install_id=?", (banned, iid)); c.commit()
                    return self.send_json(200, {"ok": True, "banned": banned})
                finally:
                    c.close()
            if path == "/api/friend/admin/config":
                if not hmac.compare_digest(str(data.get("token", "")), TOKEN):
                    return self.send_json(403, {"ok": False, "error": "forbidden"})
                enabled = "1" if data.get("enabled") else "0"
                c = db()
                try:
                    fr_config_set(c, "social_enabled", enabled)
                    return self.send_json(200, {"ok": True, "enabled": enabled == "1"})
                finally:
                    c.close()
            if path == "/api/friend/admin/report-action":
                if not hmac.compare_digest(str(data.get("token", "")), TOKEN):
                    return self.send_json(403, {"ok": False, "error": "forbidden"})
                try:
                    rid = int(data.get("reportId", 0))
                except Exception:
                    rid = 0
                action = str(data.get("action", "resolve"))
                c = db()
                try:
                    report = c.execute("SELECT * FROM friend_reports WHERE id=?", (rid,)).fetchone()
                    if not report:
                        return self.send_json(404, {"ok": False, "error": "no such report"})
                    if action == "ban":
                        c.execute("UPDATE fb_users SET banned=1 WHERE install_id=?", (report["target"],))
                        c.execute("UPDATE friend_reports SET status='banned' WHERE id=?", (rid,))
                    else:
                        c.execute("UPDATE friend_reports SET status='resolved' WHERE id=?", (rid,))
                    c.commit()
                    return self.send_json(200, {"ok": True, "status": "banned" if action == "ban" else "resolved"})
                finally:
                    c.close()
            # ---------- 好友：加好友 / 通过请求 ----------
            if path == "/api/friend/request":
                c = db()
                try:
                    me = fb_user_by_token(c, str(data.get("token", "")))
                    if not me:
                        return self.send_json(401, {"ok": False, "error": "token 无效，请重进好友页"})
                    if fr_is_banned(me):
                        return self.send_json(403, {"ok": False, "error": "该账号已被限制使用好友功能"})
                    guard = fr_require_social(c)
                    if guard:
                        return self.send_json(*guard)
                    target, err = fr_target_or_error(c, me, data)
                    if err:
                        return self.send_json(*err)
                    if fr_blocked(c, me["install_id"], target["install_id"]):
                        return self.send_json(403, {"ok": False, "error": "无法添加对方"})
                    if fr_are_friends(c, me["install_id"], target["install_id"]):
                        return self.send_json(200, {"ok": True, "status": "already",
                                                    "nickname": target["nickname"]})
                    reverse = c.execute(
                        "SELECT id FROM friend_requests WHERE from_id=? AND to_id=? AND status='pending'",
                        (target["install_id"], me["install_id"])).fetchone()
                    if reverse:
                        fr_link(c, me["install_id"], target["install_id"], now)
                        return self.send_json(200, {"ok": True, "status": "accepted",
                                                    "nickname": target["nickname"]})
                    exists = c.execute(
                        "SELECT id FROM friend_requests WHERE from_id=? AND to_id=? AND status='pending'",
                        (me["install_id"], target["install_id"])).fetchone()
                    if exists:
                        return self.send_json(200, {"ok": True, "status": "pending",
                                                    "nickname": target["nickname"]})
                    c.execute(
                        "INSERT OR REPLACE INTO friend_requests(from_id, to_id, status, created_at) VALUES(?,?,?,?)",
                        (me["install_id"], target["install_id"], "pending", now))
                    c.commit()
                    return self.send_json(200, {"ok": True, "status": "pending",
                                                "nickname": target["nickname"]})
                finally:
                    c.close()
            if path == "/api/friend/respond":
                c = db()
                try:
                    me = fb_user_by_token(c, str(data.get("token", "")))
                    if not me:
                        return self.send_json(401, {"ok": False, "error": "token 无效"})
                    if fr_is_banned(me):
                        return self.send_json(403, {"ok": False, "error": "该账号已被限制使用好友功能"})
                    guard = fr_require_social(c)
                    if guard:
                        return self.send_json(*guard)
                    try:
                        rid = int(data.get("requestId", 0))
                    except Exception:
                        rid = 0
                    row = c.execute(
                        "SELECT * FROM friend_requests WHERE id=? AND to_id=? AND status='pending'",
                        (rid, me["install_id"])).fetchone()
                    if not row:
                        return self.send_json(404, {"ok": False, "error": "这条请求已经处理过了"})
                    other = c.execute("SELECT * FROM fb_users WHERE install_id=?", (row["from_id"],)).fetchone()
                    if not other or fr_is_banned(other):
                        return self.send_json(404, {"ok": False, "error": "这条请求已经处理过了"})
                    if fr_blocked(c, me["install_id"], other["install_id"]):
                        return self.send_json(403, {"ok": False, "error": "无法添加对方"})
                    if data.get("accept"):
                        fr_link(c, row["from_id"], me["install_id"], now)
                        other = c.execute("SELECT nickname, display_id FROM fb_users WHERE install_id=?",
                                          (row["from_id"],)).fetchone()
                        return self.send_json(200, {"ok": True, "status": "accepted",
                                                    "nickname": other[0], "displayId": other[1]})
                    c.execute("UPDATE friend_requests SET status='rejected' WHERE id=?", (rid,))
                    c.commit()
                    return self.send_json(200, {"ok": True, "status": "rejected"})
                finally:
                    c.close()
            if path == "/api/friend/send":
                c = db()
                try:
                    me = fb_user_by_token(c, str(data.get("token", "")))
                    if not me:
                        return self.send_json(401, {"ok": False, "error": "token 无效"})
                    if fr_is_banned(me):
                        return self.send_json(403, {"ok": False, "error": "该账号已被限制使用好友功能"})
                    guard = fr_require_social(c)
                    if guard:
                        return self.send_json(*guard)
                    target = fr_by_display(c, data.get("displayId"))
                    if not target:
                        return self.send_json(404, {"ok": False, "error": "对方不存在"})
                    if fr_is_banned(target):
                        return self.send_json(403, {"ok": False, "error": "对方已被限制使用好友功能"})
                    if fr_blocked(c, me["install_id"], target["install_id"]):
                        return self.send_json(403, {"ok": False, "error": "你们之间已拉黑，无法发送"})
                    if not fr_are_friends(c, me["install_id"], target["install_id"]):
                        return self.send_json(403, {"ok": False, "error": "还不是好友"})
                    kind = str(data.get("kind", "text"))
                    if kind not in FR_KINDS:
                        return self.send_json(400, {"ok": False, "error": "未知消息类型"})
                    rate_error = fr_rate_limit_message(c, me["install_id"], now)
                    if rate_error:
                        return self.send_json(429, {"ok": False, "error": rate_error})
                    body = str(data.get("body", ""))[:FR_MAX_TEXT]
                    payload = data.get("payload")
                    payload = json.dumps(payload, ensure_ascii=False) if payload is not None else ""
                    if len(payload) > FR_MAX_PAYLOAD:
                        return self.send_json(413, {"ok": False, "error": "课表太大了，换一个学期试试"})
                    if kind == "text" and not body.strip():
                        return self.send_json(400, {"ok": False, "error": "内容不能为空"})
                    last = c.execute(
                        "SELECT created_at FROM dm_messages WHERE from_id=? AND to_id=? ORDER BY id DESC LIMIT 1",
                        (me["install_id"], target["install_id"])).fetchone()
                    if last:
                        try:
                            prev = datetime.datetime.fromisoformat(last[0])
                            if (datetime.datetime.now(datetime.timezone.utc) - prev).total_seconds() < FR_MIN_INTERVAL:
                                return self.send_json(429, {"ok": False, "error": "发太快了，稍等一下"})
                        except Exception:
                            pass
                    cur = c.execute(
                        "INSERT INTO dm_messages(from_id, to_id, kind, body, payload, created_at) VALUES(?,?,?,?,?,?)",
                        (me["install_id"], target["install_id"], kind, body.strip(), payload, now))
                    c.commit()
                    return self.send_json(200, {"ok": True, "id": cur.lastrowid})
                finally:
                    c.close()
            if path == "/api/friend/remove":
                c = db()
                try:
                    me = fb_user_by_token(c, str(data.get("token", "")))
                    if not me:
                        return self.send_json(401, {"ok": False, "error": "token 无效"})
                    target = fr_by_display(c, data.get("displayId"))
                    if not target:
                        return self.send_json(404, {"ok": False, "error": "对方不存在"})
                    fr_unlink(c, me["install_id"], target["install_id"])
                    return self.send_json(200, {"ok": True})
                finally:
                    c.close()
            if path == "/api/friend/remark":
                c = db()
                try:
                    me = fb_user_by_token(c, str(data.get("token", "")))
                    if not me:
                        return self.send_json(401, {"ok": False, "error": "token 无效"})
                    if fr_is_banned(me):
                        return self.send_json(403, {"ok": False, "error": "该账号已被限制使用好友功能"})
                    target = fr_by_display(c, data.get("displayId"))
                    if not target:
                        return self.send_json(404, {"ok": False, "error": "对方不存在"})
                    if not fr_are_friends(c, me["install_id"], target["install_id"]):
                        return self.send_json(403, {"ok": False, "error": "还不是好友"})
                    remark = str(data.get("remark", "")).strip()
                    if len(remark) > FR_MAX_REMARK:
                        return self.send_json(400, {"ok": False, "error": "备注最多 12 个字"})
                    c.execute("UPDATE friends SET remark=? WHERE owner=? AND friend=?",
                              (remark, me["install_id"], target["install_id"]))
                    c.commit()
                    return self.send_json(200, {"ok": True, "remark": remark})
                finally:
                    c.close()
            if path == "/api/friend/report":
                c = db()
                try:
                    me = fb_user_by_token(c, str(data.get("token", "")))
                    if not me:
                        return self.send_json(401, {"ok": False, "error": "token 无效"})
                    if fr_is_banned(me):
                        return self.send_json(403, {"ok": False, "error": "该账号已被限制使用好友功能"})
                    target, err = fr_target_or_error(c, me, data)
                    if err:
                        return self.send_json(*err)
                    reason = str(data.get("reason", "")).strip()[:40]
                    if reason not in FR_REPORT_REASONS:
                        reason = "其他"
                    detail = str(data.get("detail", "")).strip()[:FR_MAX_REPORT_DETAIL]
                    try:
                        mid = int(data.get("messageId", 0) or 0)
                    except Exception:
                        mid = 0
                    if mid:
                        ok_msg = c.execute("""
                            SELECT 1 FROM dm_messages WHERE id=?
                            AND ((from_id=? AND to_id=?) OR (from_id=? AND to_id=?))
                        """, (mid, me["install_id"], target["install_id"],
                              target["install_id"], me["install_id"])).fetchone()
                        if not ok_msg:
                            mid = 0
                    c.execute("""
                        INSERT INTO friend_reports(reporter, target, message_id, reason, detail, created_at)
                        VALUES(?,?,?,?,?,?)
                    """, (me["install_id"], target["install_id"], mid or None,
                          reason, detail, now))
                    c.commit()
                    return self.send_json(200, {"ok": True})
                finally:
                    c.close()
            if path == "/api/friend/block":
                c = db()
                try:
                    me = fb_user_by_token(c, str(data.get("token", "")))
                    if not me:
                        return self.send_json(401, {"ok": False, "error": "token 无效"})
                    if fr_is_banned(me):
                        return self.send_json(403, {"ok": False, "error": "该账号已被限制使用好友功能"})
                    target, err = fr_target_or_error(c, me, data)
                    if err:
                        return self.send_json(*err)
                    c.execute("INSERT OR IGNORE INTO friend_blocks(owner, blocked, created_at) VALUES(?,?,?)",
                              (me["install_id"], target["install_id"], now))
                    c.execute("DELETE FROM friends WHERE (owner=? AND friend=?) OR (owner=? AND friend=?)",
                              (me["install_id"], target["install_id"], target["install_id"], me["install_id"]))
                    c.execute("DELETE FROM friend_requests WHERE (from_id=? AND to_id=?) OR (from_id=? AND to_id=?)",
                              (me["install_id"], target["install_id"], target["install_id"], me["install_id"]))
                    c.commit()
                    return self.send_json(200, {"ok": True})
                finally:
                    c.close()
            if path == "/api/friend/unblock":
                c = db()
                try:
                    me = fb_user_by_token(c, str(data.get("token", "")))
                    if not me:
                        return self.send_json(401, {"ok": False, "error": "token 无效"})
                    target = fr_by_display(c, data.get("displayId"))
                    if not target:
                        return self.send_json(404, {"ok": False, "error": "对方不存在"})
                    c.execute("DELETE FROM friend_blocks WHERE owner=? AND blocked=?",
                              (me["install_id"], target["install_id"]))
                    c.commit()
                    return self.send_json(200, {"ok": True})
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
                           u.admin_note,
                           (SELECT COUNT(*) FROM fb_messages m WHERE m.install_id=u.install_id AND m.sender='user' AND m.read_by_admin=0) AS unread,
                           (SELECT body FROM fb_messages m WHERE m.install_id=u.install_id ORDER BY m.id DESC LIMIT 1) AS last_body,
                           (SELECT created_at FROM fb_messages m WHERE m.install_id=u.install_id ORDER BY m.id DESC LIMIT 1) AS last_at,
                           (SELECT MAX(id) FROM fb_messages m WHERE m.install_id=u.install_id) AS last_id
                    FROM fb_users u WHERE u.install_id<>? ORDER BY COALESCE(last_at,'') DESC
                """, (ADMIN_INSTALL,)).fetchall()
                return self.send_json(200, {"ok": True, "unread": fb_unread_for_admin(c), "threads": [{
                    "installId": r[0], "displayId": r[1], "nickname": r[2], "lastSeen": r[3],
                    "version": r[4], "banned": int(r[5] or 0), "adminNote": r[6] or "",
                    "unread": int(r[7] or 0), "lastBody": r[8] or "", "lastAt": r[9] or "",
                    "lastId": int(r[10] or 0)} for r in rows]})
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
                    "version": user["version"] or "", "lastSeen": user["last_seen"] or "",
                    "banned": int(user["banned"] or 0), "adminNote": user["admin_note"] or ""})
            finally:
                c.close()
        if u.path == "/api/friend/status":
            c = db()
            try:
                return self.send_json(200, {"ok": True, "enabled": fr_social_enabled(c)})
            finally:
                c.close()
        if u.path == "/api/friend/lookup":
            c = db()
            try:
                me = fb_user_by_token(c, (q.get("token") or [""])[0])
                if not me:
                    return self.send_json(401, {"ok": False, "error": "token 无效"})
                if fr_is_banned(me):
                    return self.send_json(403, {"ok": False, "error": "该账号已被限制使用好友功能"})
                guard = fr_require_social(c)
                if guard:
                    return self.send_json(*guard)
                now = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")
                rate_error = fr_lookup_allowed(c, me["install_id"], now)
                if rate_error:
                    return self.send_json(429, {"ok": False, "error": rate_error})
                target = fr_by_display(c, (q.get("displayId") or [""])[0])
                if not target or fr_is_banned(target):
                    return self.send_json(404, {"ok": False, "error": "没找到这个短号，确认一下是否输错"})
                if target["install_id"] == me["install_id"]:
                    return self.send_json(400, {"ok": False, "error": "这是你自己的短号"})
                outgoing = c.execute(
                    "SELECT 1 FROM friend_requests WHERE from_id=? AND to_id=? AND status='pending'",
                    (me["install_id"], target["install_id"])).fetchone() is not None
                incoming = c.execute(
                    "SELECT 1 FROM friend_requests WHERE from_id=? AND to_id=? AND status='pending'",
                    (target["install_id"], me["install_id"])).fetchone() is not None
                return self.send_json(200, {
                    "ok": True,
                    "displayId": target["display_id"], "nickname": target["nickname"],
                    "isFriend": fr_are_friends(c, me["install_id"], target["install_id"]),
                    "requestPending": bool(outgoing or incoming),
                    "blocked": fr_blocked(c, me["install_id"], target["install_id"]),
                })
            finally:
                c.close()
        if u.path == "/api/friend/blocked":
            c = db()
            try:
                me = fb_user_by_token(c, (q.get("token") or [""])[0])
                if not me:
                    return self.send_json(401, {"ok": False, "error": "token 无效"})
                rows = c.execute("""
                    SELECT u.display_id, u.nickname, b.created_at
                    FROM friend_blocks b JOIN fb_users u ON u.install_id=b.blocked
                    WHERE b.owner=? ORDER BY b.created_at DESC LIMIT 100
                """, (me["install_id"],)).fetchall()
                return self.send_json(200, {"ok": True, "blocked": [
                    {"displayId": r[0], "nickname": r[1], "createdAt": r[2]} for r in rows]})
            finally:
                c.close()
        if u.path == "/api/friend/admin/reports":
            token = (q.get("token") or [""])[0]
            if not hmac.compare_digest(token, TOKEN):
                return self.send_json(403, {"ok": False, "error": "forbidden"})
            c = db()
            try:
                rows = c.execute("""
                    SELECT r.id, ru.display_id, ru.nickname, tu.display_id, tu.nickname, tu.banned,
                           r.message_id, r.reason, r.detail, r.status, r.created_at
                    FROM friend_reports r
                    LEFT JOIN fb_users ru ON ru.install_id=r.reporter
                    LEFT JOIN fb_users tu ON tu.install_id=r.target
                    ORDER BY CASE WHEN r.status='open' THEN 0 ELSE 1 END, r.id DESC LIMIT 100
                """).fetchall()
                return self.send_json(200, {
                    "ok": True, "enabled": fr_social_enabled(c),
                    "reports": [{
                        "id": r[0], "reporterDisplay": r[1] or "", "reporterNick": r[2] or "",
                        "targetDisplay": r[3] or "", "targetNick": r[4] or "",
                        "targetBanned": int(r[5] or 0), "messageId": int(r[6] or 0),
                        "reason": r[7] or "", "detail": r[8] or "", "status": r[9] or "",
                        "createdAt": r[10] or ""} for r in rows]})
            finally:
                c.close()
        if u.path == "/api/friend/admin/config":
            token = (q.get("token") or [""])[0]
            if not hmac.compare_digest(token, TOKEN):
                return self.send_json(403, {"ok": False, "error": "forbidden"})
            c = db()
            try:
                return self.send_json(200, {"ok": True, "enabled": fr_social_enabled(c)})
            finally:
                c.close()
        # ---------- 好友 ----------
        if u.path == "/api/friend/list":
            c = db()
            try:
                me = fb_user_by_token(c, (q.get("token") or [""])[0])
                if not me:
                    return self.send_json(401, {"ok": False, "error": "token 无效"})
                if fr_is_banned(me):
                    return self.send_json(403, {"ok": False, "error": "该账号已被限制使用好友功能"})
                iid = me["install_id"]
                return self.send_json(200, {
                    "ok": True,
                    "enabled": fr_social_enabled(c),
                    "me": {"displayId": me["display_id"], "nickname": me["nickname"]},
                    "unread": fr_total_unread(c, iid),
                    "requests": fr_pending_requests(c, iid),
                    "friends": fr_friend_rows(c, iid),
                })
            finally:
                c.close()
        if u.path == "/api/friend/requests":
            c = db()
            try:
                me = fb_user_by_token(c, (q.get("token") or [""])[0])
                if not me:
                    return self.send_json(401, {"ok": False, "error": "token 无效"})
                return self.send_json(200, {"ok": True, "requests": fr_pending_requests(c, me["install_id"])})
            finally:
                c.close()
        if u.path == "/api/friend/thread":
            c = db()
            try:
                me = fb_user_by_token(c, (q.get("token") or [""])[0])
                if not me:
                    return self.send_json(401, {"ok": False, "error": "token 无效"})
                if fr_is_banned(me):
                    return self.send_json(403, {"ok": False, "error": "该账号已被限制使用好友功能"})
                target = fr_by_display(c, (q.get("displayId") or [""])[0])
                if not target:
                    return self.send_json(404, {"ok": False, "error": "对方不存在"})
                if not fr_are_friends(c, me["install_id"], target["install_id"]):
                    return self.send_json(403, {"ok": False, "error": "还不是好友"})
                since = int((q.get("since_id") or ["0"])[0] or 0)
                msgs = fr_thread(c, me["install_id"], target["install_id"], since)
                return self.send_json(200, {
                    "ok": True, "messages": msgs,
                    "nickname": target["nickname"],
                    "remark": fr_remark_of(c, me["install_id"], target["install_id"]),
                    "displayId": target["display_id"],
                    "unread": fr_total_unread(c, me["install_id"]),
                })
            finally:
                c.close()
        if u.path == "/feedback":
            token = (q.get("token") or [""])[0]
            if not hmac.compare_digest(token, TOKEN):
                return self.send_html(403, "<h1>403</h1><p>反馈后台需要私有 token。</p>")
            return self.send_html(200, fb_admin_page(html.escape(token, quote=True)))
        if u.path == "/moderation":
            token = (q.get("token") or [""])[0]
            if not hmac.compare_digest(token, TOKEN):
                return self.send_html(403, "<h1>403</h1><p>治理后台需要私有 token。</p>")
            return self.send_html(200, moderation_page(html.escape(token, quote=True)))
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
.head .actions{margin-left:auto;display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}
button.small{height:30px;padding:0 10px;font-size:12px}
button.danger{background:#dc2626}
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
<div class=side><h1>意见反馈</h1><div class=hint>只收到匿名短号与消息正文，不含姓名、学号、密码。点会话即可回复。<a href="/moderation?token=__TOKEN__" style="color:#93c5fd">好友治理 →</a></div><div class=list id=list></div></div>
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
    <div class=head><div><div class=who></div><div class=meta></div></div>
      <div class=actions><button class="small" id=noteBtn>编辑备注</button><button class="small danger" id=banBtn></button></div></div>
    <div class=msgs id=msgs></div>
    <div class=composer><textarea id=input placeholder="回复这条反馈…（Enter 发送，Shift+Enter 换行）"></textarea><button id=send>发送</button></div>`;
  $('chat').querySelector('.who').textContent = current.nickname + ' · ' + current.displayId;
  updateHead();
  $('noteBtn').onclick = editNote;
  $('banBtn').onclick = toggleBan;
  $('send').onclick = sendReply;
  $('input').addEventListener('keydown', e => { if (e.key === 'Enter' && !e.shiftKey){ e.preventDefault(); sendReply(); } });
}
function updateHead(){
  if (!current) return;
  const meta = $('chat').querySelector('.meta');
  if (meta) meta.textContent = '版本 ' + (current.version||'未知') + ' · 最近活跃 ' + fmt(current.lastSeen||'') + (current.adminNote ? ' · 备注：' + current.adminNote : '');
  const banBtn = $('banBtn');
  if (banBtn) banBtn.textContent = current.banned ? '解封' : '封禁';
}
async function editNote(){
  if (!current) return;
  const note = prompt('给这个用户加后台备注（仅管理员可见，留空可清除）', current.adminNote || '');
  if (note === null) return;
  try {
    const r = await fetch('/api/feedback/admin/note', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({token: TOKEN, install_id: current.installId, note})});
    const d = await r.json();
    if (d.ok){ current.adminNote = d.adminNote || ''; updateHead(); loadThreads(); }
    else alert(d.error || '保存失败');
  } catch(e){ alert('网络异常'); }
}
async function toggleBan(){
  if (!current) return;
  const next = !current.banned;
  if (!confirm((next ? '封禁' : '解封') + '「' + (current.nickname || current.displayId) + '」？封禁后该用户不能再用好友/反馈发送。')) return;
  try {
    const r = await fetch('/api/feedback/admin/ban', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({token: TOKEN, install_id: current.installId, banned: next})});
    const d = await r.json();
    if (d.ok){ current.banned = next ? 1 : 0; updateHead(); loadThreads(); }
    else alert(d.error || '操作失败');
  } catch(e){ alert('网络异常'); }
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


MODERATION_HTML = """<!doctype html><html lang=zh-CN><head><meta charset=utf-8>
<meta name=viewport content='width=device-width,initial-scale=1'><title>北化课表 · 好友治理</title>
<style>
*{box-sizing:border-box}
body{background:#0b1220;color:#e5edff;font-family:system-ui,-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;margin:0;padding:24px}
main{max-width:980px;margin:auto}
h1{font-size:22px;margin:0 0 6px}
.muted{color:#94a3b8;font-size:12px;line-height:1.7}
.card{background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.12);border-radius:16px;padding:16px;margin:18px 0}
.row{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
button{background:#2563eb;color:#fff;border:0;border-radius:10px;padding:8px 12px;font-size:13px;cursor:pointer}
button.danger{background:#dc2626}button.ghost{background:rgba(255,255,255,.10)}
button:disabled{opacity:.5;cursor:default}
.report{border-top:1px solid rgba(255,255,255,.08);padding:12px 0;display:flex;gap:14px;align-items:flex-start}
.report:first-child{border-top:0}
.report .body{flex:1;min-width:0}
.report .title{font-weight:600;font-size:14px}
.report .meta{color:#94a3b8;font-size:12px;margin-top:5px;line-height:1.6;word-break:break-word}
.badge{display:inline-block;border-radius:8px;padding:1px 7px;font-size:11px;background:rgba(239,68,68,.20);color:#fca5a5;margin-left:6px}
.badge.ok{background:rgba(34,197,94,.18);color:#86efac}
.empty{color:#64748b;font-size:13px;padding:10px 0}
a{color:#93c5fd}
</style></head><body><main>
<h1>好友治理</h1>
<div class=muted>处理举报、查看封禁状态、控制好友功能总开关。只展示匿名短号和昵称，不含学号密码。<a href="/feedback?token=__TOKEN__">去意见反馈后台</a></div>
<div class=card><div class=row><b>好友功能：</b><span id=state>加载中…</span><button id=toggle>切换</button></div><div class=muted id=hint></div></div>
<div class=card><div class=row><b>举报记录</b><span class=muted id=count></span></div><div id=list class=empty>加载中…</div></div>
<script>
const TOKEN = "__TOKEN__";
let enabled = true;
const $ = id => document.getElementById(id);
const fmt = s => { try { return new Date(s).toLocaleString('zh-CN',{hour12:false,month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}); } catch(e){ return s; } };
async function api(path){ const r = await fetch(path + (path.includes('?')?'&':'?') + 'token=' + encodeURIComponent(TOKEN)); return r.json(); }
function renderState(){
  $('state').textContent = enabled ? '开启' : '已关闭';
  $('state').className = enabled ? 'badge ok' : 'badge';
  $('toggle').textContent = enabled ? '关闭好友功能' : '开启好友功能';
  $('hint').textContent = enabled ? '关闭后 App 仍能查看历史消息，但不能再添加好友、查短号或发送新消息。' : '当前所有好友写操作会被拒绝，适合临时止血。';
  $('toggle').onclick = async () => {
    $('toggle').disabled = true;
    try {
      const r = await fetch('/api/friend/admin/config', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({token: TOKEN, enabled: !enabled})});
      const d = await r.json();
      if (d.ok){ enabled = d.enabled; renderState(); }
      else alert(d.error || '操作失败');
    } catch(e){ alert('网络异常'); }
    $('toggle').disabled = false;
  };
}
function renderReports(d){
  enabled = d.enabled;
  renderState();
  const reports = d.reports || [];
  $('count').textContent = reports.filter(r => r.status === 'open').length + ' 条待处理';
  const box = $('list'); box.innerHTML = '';
  if (!reports.length){ box.className = 'empty'; box.textContent = '还没有人举报'; return; }
  box.className = '';
  reports.forEach(r => {
    const el = document.createElement('div'); el.className = 'report';
    const body = document.createElement('div'); body.className = 'body';
    const title = document.createElement('div'); title.className = 'title';
    title.textContent = (r.reporterNick || '匿名') + ' 举报 ' + (r.targetNick || '已注销用户');
    if (r.targetBanned) { const b = document.createElement('span'); b.className='badge'; b.textContent='已封禁'; title.appendChild(b); }
    const meta = document.createElement('div'); meta.className = 'meta';
    meta.textContent = '举报人 ' + (r.reporterDisplay || '-') + ' · 被举报人 ' + (r.targetDisplay || '-') +
      ' · ' + (r.reason || '其他') + (r.messageId ? ' · 消息 #' + r.messageId : '') +
      ' · ' + fmt(r.createdAt) + (r.detail ? '\\n' + r.detail : '') +
      ' · 状态：' + ({open:'待处理',banned:'已封禁',resolved:'已处理'}[r.status] || r.status);
    body.appendChild(title); body.appendChild(meta);
    const actions = document.createElement('div');
    if (r.status === 'open' && !r.targetBanned){
      const ban = document.createElement('button'); ban.className='danger'; ban.textContent='封禁并处理';
      ban.onclick = () => act(r.id, 'ban');
      const ok = document.createElement('button'); ok.className='ghost'; ok.textContent='已处理';
      ok.onclick = () => act(r.id, 'resolve');
      actions.appendChild(ban); actions.appendChild(ok);
    }
    el.appendChild(body); el.appendChild(actions); box.appendChild(el);
  });
}
async function act(id, action){
  if (!confirm(action === 'ban' ? '封禁被举报用户并标记为已处理？' : '标记这条举报为已处理？')) return;
  try {
    const r = await fetch('/api/friend/admin/report-action', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({token: TOKEN, reportId: id, action})});
    const d = await r.json();
    if (d.ok) load(); else alert(d.error || '操作失败');
  } catch(e){ alert('网络异常'); }
}
async function load(){
  try { const d = await api('/api/friend/admin/reports'); if (d.ok) renderReports(d); else $('list').textContent = d.error || '加载失败'; } catch(e){ $('list').textContent = '网络异常'; }
}
load(); setInterval(load, 10000);
</script></body></html>"""


def moderation_page(token):
    return MODERATION_HTML.replace("__TOKEN__", token)


class Server(ThreadingHTTPServer):
    """并发更稳：默认 backlog 只有 5，新版本上线瞬间大量注册会被 reset。"""
    daemon_threads = True
    allow_reuse_address = True
    request_queue_size = 256


if __name__ == "__main__":
    init()
    Server(("127.0.0.1", 8090), Handler).serve_forever()

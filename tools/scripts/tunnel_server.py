import http.server, os
ROOT = r"D:\lwh\tunnelroot"
REPORTS = os.path.join(ROOT, "reports")
class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)
    def do_GET(self):
        if self.path == "/next":
            path = os.path.join(ROOT, "next.txt")
            if os.path.exists(path):
                data = open(path, "rb").read()
                os.remove(path)
                self.send_response(200)
                self.send_header("Content-Type", "text/plain; charset=utf-8")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
                return
            self.send_response(204)
            self.end_headers()
            return
        super().do_GET()
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        data = self.rfile.read(length)
        name = os.path.basename(self.path) or "report"
        os.makedirs(REPORTS, exist_ok=True)
        with open(os.path.join(REPORTS, name), "wb") as f:
            f.write(data)
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"OK")
http.server.ThreadingHTTPServer(("127.0.0.1", 8000), Handler).serve_forever()

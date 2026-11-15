"""Tiny local progress dashboard for build_rag.py. Serves on http://localhost:8765 only.

Run from Audio File/ (it reads data/rag/ relative to the current folder):
  .venv/Scripts/python.exe -I "../App Folder/pipeline/dashboard_server.py" 8765 [data/rag]
"""
import http.server, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
RAG = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else os.path.join("data", "rag"))


def read(name, default=None):
    try:
        with open(os.path.join(RAG, name), encoding="utf-8") as f:
            return f.read()
    except OSError:
        return default


class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/api"):
            prog = read("progress.json")
            ev = read("eval_retrieval.json")
            ex = read("experiments.json")
            log = (read("build.log", "") or "").splitlines()[-12:]
            body = json.dumps(dict(progress=json.loads(prog) if prog else None,
                                   eval=json.loads(ev) if ev else None,
                                   exp=json.loads(ex) if ex else None,
                                   log=log, eval_log=(read("eval.log", "") or "")[-3000:]))
            body = body.replace("NaN", "null").encode()   # browsers reject NaN in JSON
            ctype = "application/json"
        else:
            body = open(os.path.join(HERE, "dashboard.html"), "rb").read()
            ctype = "text/html; charset=utf-8"
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a):
        pass


port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
print(f"dashboard on http://localhost:{port}", flush=True)
http.server.ThreadingHTTPServer(("127.0.0.1", port), H).serve_forever()

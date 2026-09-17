"""Servidor do protótipo: serve a pasta prototype/ e aceita POST /save para
gravar capturas (data URL) em store/screenshots. Uso só em desenvolvimento."""
import base64, json, os, sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(ROOT), "store", "screenshots")


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def do_POST(self):
        if self.path != "/save":
            self.send_error(404)
            return
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        name = os.path.basename(body["name"])
        data = body["data"].split(",", 1)[1]
        os.makedirs(OUT, exist_ok=True)
        with open(os.path.join(OUT, name), "wb") as f:
            f.write(base64.b64decode(data))
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"ok":true}')


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()

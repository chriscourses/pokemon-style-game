#!/usr/bin/env python3
"""
Carnage3D – lokaler Webserver für den Browser-Build

Startet einen HTTP-Server der die WASM-Dateien mit korrekten MIME-Types
und COOP/COEP-Headern ausliefert (nötig für SharedArrayBuffer/Threads).

Verwendung:
    python3 web/serve.py          # Port 8080 (Standard)
    python3 web/serve.py 9000     # Port 9000
    ./web/serve.py                # direkt ausführbar (nach chmod +x)

Dann im Browser öffnen:
    http://localhost:8080/carnage3D_wasm.html
"""

import http.server
import socketserver
import os
import sys

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080

MIME_OVERRIDES = {
    ".wasm": "application/wasm",
    ".js":   "application/javascript",
    ".data": "application/octet-stream",
}


class WasmHandler(http.server.SimpleHTTPRequestHandler):
    """HTTP-Handler mit WASM-MIME-Type und Sicherheits-Headern."""

    def end_headers(self):
        # COOP/COEP für SharedArrayBuffer (Emscripten-Threads)
        self.send_header("Cross-Origin-Opener-Policy", "same-origin")
        self.send_header("Cross-Origin-Embedder-Policy", "require-corp")
        # Kein Caching während der Entwicklung
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        super().end_headers()

    def guess_type(self, path):
        _, ext = os.path.splitext(path)
        if ext in MIME_OVERRIDES:
            return MIME_OVERRIDES[ext]
        return super().guess_type(path)

    def log_message(self, fmt, *args):
        # Etwas kompaktere Log-Ausgabe
        sys.stderr.write("  [%s] %s\n" % (self.address_string(), fmt % args))


def main():
    # Immer aus dem web/-Verzeichnis servieren
    web_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(web_dir)

    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("", PORT), WasmHandler) as httpd:
        print()
        print("  ╔══════════════════════════════════════════════════╗")
        print("  ║           Carnage3D – Browser-Server             ║")
        print("  ╠══════════════════════════════════════════════════╣")
        print(f"  ║  URL:  http://localhost:{PORT}/carnage3D_wasm.html  ║")
        print("  ║  Stoppen:  Ctrl+C                                ║")
        print("  ╚══════════════════════════════════════════════════╝")
        print()
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\n  Server gestoppt.")


if __name__ == "__main__":
    main()

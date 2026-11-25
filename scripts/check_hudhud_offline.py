#!/usr/bin/env python3
"""Hudhud answers a typed question offline: no network call anywhere in the engine that reads it (plan v1, hard-zero gate
"a network call during a text answer = 0", Marhala 3; 25 Nov 2026).

The engine is what the shared harness compiles (lesson 93, scripts/shared_harness/README.md): every
shared/src/jvmSharedMain/.../data/Hudhud*.kt plus the files listed in scripts/shared_harness/deps.txt. That closure is
complete by construction: the harness compiles it alone, so a file it needs and does not list fails there ("unresolved
reference"). This check fails when, in that closure (comments and strings left out, as check_hudhud_readonly.py reads):

  1. a network package is imported or named: java.net (URL, URI, HttpURLConnection, Socket, InetAddress, java.net.http's
     HttpClient and WebSocket), javax.net (SSL sockets), okhttp/okhttp3, io.ktor, retrofit, android.net, Firebase,
     org.apache.http, javax.websocket, java.nio's socket channels;
  2. a network type or call is used by its simple name: HttpURLConnection, HttpsURLConnection, URLConnection, URL(...),
     openConnection(, openStream(, Socket, ServerSocket, DatagramSocket, SSLSocket, InetAddress, InetSocketAddress,
     HttpClient, OkHttpClient, WebSocket, SocketChannel, ConnectivityManager, WebView, DownloadManager;
  3. a top-level class, object or function of a NETWORK FILE of the shared module (one that itself imports java.net, javax.net,
     okhttp3, io.ktor or java.net.http: LiveCloud, LiveLines, WifiSync, CustomerLink…) is referenced: such a call reaches the
     network through a file the closure does not list, so rules 1-2 alone would not see it (same package, no import needed).

An exception is written in ALLOWED below by file and rule, with its reason, and is read in review. It is empty: nothing in
the engine needs the network. The app's own Hudhud screens are held by check_hudhud_readonly.py (no HTTP client of his own);
the voice's speech recogniser is Google's, by the owner's yes, and is not a TEXT answer.

    python3 scripts/check_hudhud_offline.py              # the gate (the strings job runs it)
    python3 scripts/check_hudhud_offline.py --list       # also print the closure and the network files' names it watches
"""
import importlib.util
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENGINE = ROOT / "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data"
DEPS = ROOT / "scripts/shared_harness/deps.txt"
SHARED_MAIN = [ROOT / "shared/src" / s for s in ("commonMain", "jvmSharedMain", "androidMain", "desktopMain")]

# (file name, rule) -> why. Empty is the goal; a line here is a decision written down, never a way around the gate.
ALLOWED = {}

PACKAGES = [
    (r"\bjava\.net\b", "java.net"),
    (r"\bjavax\.net\b", "javax.net (SSL sockets)"),
    (r"\bokhttp3?\b", "OkHttp"),
    (r"\bio\.ktor\b", "Ktor"),
    (r"\bretrofit2?\b", "Retrofit"),
    (r"\bandroid\.net\b", "android.net"),
    (r"\bcom\.google\.firebase\b", "Firebase"),
    (r"\borg\.apache\.http\b", "Apache HTTP"),
    (r"\bjavax\.websocket\b", "javax.websocket"),
    (r"\bjava\.nio\.channels\.\w*(?:Socket|Datagram)", "a socket channel"),
]
NAMES = [
    (r"\bHttpsURLConnection\b", "HttpsURLConnection"),
    (r"\bHttpURLConnection\b", "HttpURLConnection"),
    (r"\bURLConnection\b", "URLConnection"),
    (r"(?<![\w.])URL\s*\(", "URL(...)"),
    (r"\.openConnection\s*\(", "openConnection()"),
    (r"\.openStream\s*\(", "openStream()"),
    (r"\b(?:Server|Datagram|SSL)?Socket\b", "a socket"),
    (r"\bInet(?:Socket)?Address\b", "InetAddress"),
    (r"\b(?:Ok)?HttpClient\b", "an HTTP client"),
    (r"\bHttpRequest\b", "an HTTP request"),
    (r"\bWebSocket\b", "a WebSocket"),
    (r"\b\w*SocketChannel\b", "a socket channel"),
    (r"\bConnectivityManager\b", "ConnectivityManager"),
    (r"\bWebView\b", "a WebView"),
    (r"\bDownloadManager\b", "DownloadManager"),
]
# What makes a shared file a NETWORK FILE for rule 3 (its imports, not a word in a comment).
NETWORK_IMPORT = re.compile(r"^\s*import\s+(?:java\.net|javax\.net|okhttp3|io\.ktor|java\.net\.http)\b", re.M)
TOP_DECL = re.compile(r"^(?:(?:internal|public|private|data|sealed|abstract|open|enum|inline|value|actual|expect|fun\s+interface)\s+)*"
                      r"(?:class|object|interface|fun)\s+(?:<[^>]*>\s*)?(?:\w+\.)?([A-Z]\w+)", re.M)


def _readonly():
    spec = importlib.util.spec_from_file_location("check_hudhud_readonly", ROOT / "scripts/check_hudhud_readonly.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


code_only = _readonly().code_only


def closure():
    files = sorted(ENGINE.glob("Hudhud*.kt"))
    for line in DEPS.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            files.append(ROOT / line)
    return files


def network_names(engine_files):
    """Top-level names declared by the shared module's network files (rule 3), each with its file."""
    inside = {p.resolve() for p in engine_files}
    names = {}
    for base in SHARED_MAIN:
        if not base.exists():
            continue
        for path in sorted(base.rglob("*.kt")):
            if path.resolve() in inside:
                continue
            text = path.read_text(encoding="utf-8")
            if NETWORK_IMPORT.search(text):
                for name in TOP_DECL.findall(code_only(text)):
                    names.setdefault(name, path.relative_to(ROOT))
    return names


def check():
    problems = []
    files = closure()
    missing = [f for f in files if not f.exists()]
    if missing:
        return [f"{m.relative_to(ROOT)}: listed in the engine closure but missing (deps.txt or a rename)" for m in missing], files, {}
    if len(files) < 20:
        return [f"only {len(files)} engine files found: the closure moved, update this check"], files, {}
    watched = network_names(files)
    if len(watched) < 5:
        problems.append(f"only {len(watched)} names of network files found (LiveCloud, WifiSync…): rule 3 lost its list, read it")
    name_rules = [(r"\b" + re.escape(n) + r"\b", f"{n} (a network file's, {src})") for n, src in sorted(watched.items())]
    for path in files:
        rel = path.relative_to(ROOT)
        raw = path.read_text(encoding="utf-8")
        lines = code_only(raw).split("\n")
        for rule, pats in (("package", PACKAGES), ("name", NAMES), ("reach", name_rules)):
            for pat, why in pats:
                rx = re.compile(pat)
                for no, line in enumerate(lines, 1):
                    if rx.search(line) and (path.name, why) not in ALLOWED:
                        problems.append(f"{rel}:{no}: {why}")
    return problems, files, watched


def main(argv):
    problems, files, watched = check()
    if "--list" in argv:
        print("engine closure:")
        for f in files:
            print("  " + str(f.relative_to(ROOT)))
        print("names of the shared module's network files (rule 3):")
        for n, src in sorted(watched.items()):
            print(f"  {n}  ({src})")
    if problems:
        print("Hudhud's engine must answer a typed question without the network (hard-zero gate). These lines reach out:")
        for p in problems:
            print("  " + p)
        print("\nSee scripts/check_hudhud_offline.py: a real need is a decision of the owner, written in ALLOWED with its reason.")
        return 1
    print(f"Hudhud offline: {len(files)} engine files, no network package, type or call, and no reference to the "
          f"{len(watched)} names of the shared module's network files")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

#!/usr/bin/env python3
"""The relay's conversation, checked against a running relay (22 Oct 2026).

Every relay the app speaks to — Cloudflare (cloud/worker), the owner's hosting
(web/relay, PHP) and Supabase (supabase/functions/daakiya) — must answer the
same paths the same way, because the app (LiveCloud) has one piece of code
for all of them. This script holds each to it:

    python3 scripts/test_relay.py http://127.0.0.1:8098            # PHP
    python3 scripts/test_relay.py http://127.0.0.1:8000/daakiya    # Supabase's function, served by Deno

It uses a fresh random book each run and touches nothing else. Exit code 0
when every check passed.

Two more modes hold a relay started with lowered limits (23 Oct 2026; never
run them against a deployed relay, whose limits are the real ones):

    --caps   the relay runs with a day limit of 4 parcels, 3000 bytes a book
             and 7000 bytes a day (PHP: RK_RELAY_DAY_LIMIT=4 RK_RELAY_BOOK_BYTES=3000
             RK_RELAY_DAY_BYTES=7000; Supabase: RK_DAY_LIMIT, RK_BOOK_BYTES,
             RK_DAY_BYTES; the Worker: --var DAY_LIMIT:4 BOOK_BYTES:3000 DAY_BYTES:7000)
    --idle   a device not seen for 1.5 s is let go (RK_RELAY_DEVICE_IDLE_MS=1500,
             RK_DEVICE_IDLE_MS=1500; the Worker: --var DEVICE_IDLE_MS:1500
             PRUNE_EVERY_MS:1000, its alarm does it)
    --waris  only the Waris ki Chaabi checks (they also run at the end of the plain run). Its one real-wait
             check runs only when the relay's DAY is short: WARIS_DAY_MS in the relay's environment (PHP and
             Supabase: WARIS_DAY_MS=2000; the Worker: --var WARIS_DAY_MS:2000); else it says so and is skipped.
    --store  the whole store (every book together) holds about 300,000 bytes
             (PHP: RK_RELAY_MAX_STORE_BYTES=300000; Supabase: RK_STORE_BYTES=300000;
             the Worker: --var ACCOUNT_BYTES:300000). Each relay counts in its own
             way (the hosting its files, Supabase the parcels, the Worker SQLite's
             own size reported to its ledger every 64 KiB), so the checks ask only
             what all three promise: parcels go, then "full" (507) within 40 of
             30,000 bytes; a new book is refused "full"; a book already there still
             says hello and is read; once its devices left, a new book is taken.
    --wlimits  the heirs' vaults with their limits lowered (7 Nov 2026): 6 new vaults a
             day, the vaults' own bytes 3,000,000, the books' store 2,000,000 and a DAY
             of 500 ms (PHP: RK_RELAY_NEW_VAULTS_DAY=6 RK_RELAY_WARIS_BYTES=3000000
             RK_RELAY_MAX_STORE_BYTES=2000000 WARIS_DAY_MS=500 RK_RELAY_SWEEP_EVERY=0;
             Supabase: RK_NEW_VAULTS_DAY, RK_WARIS_BYTES, RK_STORE_BYTES, WARIS_DAY_MS
             and an RK_KEEP_SECRET that this script, run with the same, sends back as
             X-RK-Keep; the Worker: --var NEW_VAULTS_DAY:6 WARIS_BYTES:3000000
             ACCOUNT_BYTES:2000000 WARIS_DAY_MS:500). The 7th new vault is refused
             429 waris-day while a vault already there is set up again; chunks go until
             507 waris-full, and a book still takes parcels; 14 DAYs after their setup
             the vaults with neither an heir nor a snapshot are gone, the others stay.
"""
import base64
import json
import os
import secrets
import socket
import ssl
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://127.0.0.1:8098"
MODE = sys.argv[2] if len(sys.argv) > 2 else ""
FAILED = []
LAST = ["(none yet)"]
STEP = ["start"]


def call(method, path, token=None, device=None, body=None, how="both", ctype=None):
    headers = {}
    if token is not None:
        if how in ("both", "auth"):
            headers["Authorization"] = "RK " + token
        if how in ("both", "x"):
            headers["X-RK-Pass"] = token
    if device is not None:
        headers["X-Device"] = device
    if ctype:
        headers["Content-Type"] = ctype
    data = body if body is None or isinstance(body, bytes) else json.dumps(body).encode()
    if data is not None and not ctype:
        headers["Content-Type"] = "application/json" if not isinstance(body, bytes) else "application/octet-stream"
    STEP[0] = "%s %s (%d bytes)" % (method, path.split("/")[-1], len(data or b""))
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"raw": raw[:200].decode("latin-1")}


class Line:
    """A small WebSocket client (RFC 6455, text frames only) for the relay's live line; stdlib only."""

    def __init__(self, path, token, device):
        u = urllib.parse.urlsplit(BASE + path)
        port = u.port or (443 if u.scheme == "https" else 80)
        raw = socket.create_connection((u.hostname, port), timeout=20)
        self.s = ssl.create_default_context().wrap_socket(raw, server_hostname=u.hostname) if u.scheme == "https" else raw
        key = base64.b64encode(os.urandom(16)).decode()
        req = ("GET %s HTTP/1.1\r\nHost: %s\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
               "Sec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\nX-RK-Pass: %s\r\nX-Device: %s\r\n\r\n"
               % (u.path, u.netloc, key, token, device))
        self.s.sendall(req.encode())
        head = b""
        while b"\r\n\r\n" not in head:
            chunk = self.s.recv(1)
            if not chunk:
                break
            head += chunk
        self.status = int(head.split(b" ", 2)[1]) if head.startswith(b"HTTP/") else 0
        self.buf = b""

    def send(self, obj):
        data = json.dumps(obj).encode()
        mask = os.urandom(4)
        n = len(data)
        hdr = bytes([0x81]) + (bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + struct.pack(">H", n) if n < 65536
                                else bytes([0x80 | 127]) + struct.pack(">Q", n))
        self.s.sendall(hdr + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))

    def _read(self, n):
        while len(self.buf) < n:
            chunk = self.s.recv(65536)
            if not chunk:
                raise EOFError("line closed")
            self.buf += chunk
        out, self.buf = self.buf[:n], self.buf[n:]
        return out

    def recv(self):
        """The next text message as JSON; None when the line was closed."""
        while True:
            b0, b1 = self._read(2)
            n = b1 & 0x7F
            if n == 126:
                n = struct.unpack(">H", self._read(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self._read(8))[0]
            payload = self._read(n)
            op = b0 & 0x0F
            if op == 0x8:
                return None
            if op == 0x1:
                return json.loads(payload)

    def close(self):
        try:
            self.s.close()
        except OSError:
            pass


def check(name, cond, detail=""):
    LAST[0] = name
    print(("ok   " if cond else "FAIL ") + name + ("" if cond else "  " + str(detail)))
    if not cond:
        FAILED.append(name)
        # In CI the failure is also an annotation: the job's log is often out of reach, annotations are not.
        if os.environ.get("GITHUB_ACTIONS"):
            print("::error title=relay check::" + name + " -- " + str(detail)[:600].replace("\n", " "))


def caps():
    """Limits lowered at the relay: a refused parcel still brings the page and keeps the ack; bytes are counted."""
    token = secrets.token_hex(32)
    a, b, c = (secrets.token_hex(8) for _ in range(3))
    p = "/v1/b/" + secrets.token_hex(16) + "/"
    call("POST", p + "hello", token, a)
    call("POST", p + "hello", token, b)
    s, ob = call("POST", p + "send", token, b, b"from b")
    check("caps: a parcel goes", s == 200, (s, ob))
    for i in range(3):
        call("POST", p + "round", token, a, {"after": 0, "parcel": base64.b64encode(b"a%d" % i).decode()})
    s, o = call("POST", p + "round", token, a, {"after": 0, "parcel": base64.b64encode(b"one too many").decode()})
    got = [base64.b64decode(x["body"]) for x in (o or {}).get("parcels", [])]
    check("a round past the day's parcels is refused as day-limit", s == 429 and o.get("why") == "day-limit" and o.get("seq") is None, (s, o))
    check("and still brings the others' page", got == [b"from b"], got)
    s, o = call("POST", p + "round", token, a, {"after": ob.get("seq"), "upTo": ob.get("seq"), "parcel": base64.b64encode(b"again").decode()})
    check("a refused round with an ack is refused the same way", s == 429 and o.get("why") == "day-limit", (s, o))
    s, o = call("POST", p + "send", token, a, b"x")
    check("a send past it is refused too", s == 429 and o.get("why") == "day-limit", (s, o))
    s, o = call("POST", p + "hello", token, c)
    s, o = call("GET", p + "take?after=0", token, c)
    got = [base64.b64decode(x["body"]) for x in o.get("parcels", [])]
    check("the refused round's ack was kept (what it acked is gone)", s == 200 and got == [b"a0", b"a1", b"a2"], (s, got))

    # Bytes: 3000 a book, 7000 a day.
    p = "/v1/b/" + secrets.token_hex(16) + "/"
    call("POST", p + "hello", token, a)
    call("POST", p + "hello", token, b)
    s, o = call("POST", p + "send", token, a, b"1" * 2000)
    check("caps: 2000 bytes go", s == 200, (s, o))
    last = o.get("seq")
    s, o = call("POST", p + "send", token, a, b"2" * 2000)
    check("past the book's bytes a parcel is refused as book-full", s == 507 and o.get("why") == "book-full", (s, o))
    s, o = call("POST", p + "round", token, b, {"after": last, "upTo": last})
    s, o = call("POST", p + "send", token, a, b"3" * 2000)
    check("the bytes of parcels taken are free again", s == 200, (s, o))
    last = o.get("seq")
    call("POST", p + "round", token, b, {"after": last, "upTo": last})
    s, o = call("POST", p + "send", token, a, b"4" * 2000)
    last = o.get("seq")
    call("POST", p + "round", token, b, {"after": last, "upTo": last})
    s, o = call("POST", p + "send", token, a, b"5" * 2000)
    check("past the day's bytes a parcel is refused as day-bytes", s == 429 and o.get("why") == "day-bytes", (s, o))
    s, o = call("POST", p + "round", token, a, {"after": 0, "parcel": base64.b64encode(b"6" * 2000).decode()})
    check("and a round says day-bytes with its page", s == 429 and o.get("why") == "day-bytes" and isinstance(o.get("parcels"), list), (s, o))


def idle():
    """A device not seen for the idle period goes with its parcels; a device of the book that comes back says hello again."""
    token = secrets.token_hex(32)
    a, b = (secrets.token_hex(8) for _ in range(2))
    p = "/v1/b/" + secrets.token_hex(16) + "/"
    call("POST", p + "hello", token, a)
    call("POST", p + "hello", token, b)
    s, o = call("POST", p + "send", token, a, b"before a went quiet")
    check("idle: a parcel goes", s == 200, (s, o))
    time.sleep(4)
    call("GET", p + "take?after=0", token, b)  # the hostings sweep on another device's call; the Worker's alarm already did
    s, o = call("GET", p + "take?after=0", token, a)
    check("a device not seen for the idle period is let go", s == 403 and o.get("why") == "say-hello-first", (s, o))
    s, o = call("POST", p + "hello", token, a)
    check("and says hello again", s == 200, (s, o))
    call("POST", p + "hello", token, b)
    s, o = call("GET", p + "take?after=0", token, b)
    check("its parcels went with it", s == 200 and o.get("parcels") == [], (s, o))


def store():
    """The store-wide cap lowered at the relay (23 Oct 2026, phase 2): every book together, not one book."""
    token = secrets.token_hex(32)
    a, b = (secrets.token_hex(8) for _ in range(2))
    p = "/v1/b/" + secrets.token_hex(16) + "/"
    s, o = call("POST", p + "hello", token, a)
    check("store: an empty store takes a book", s == 200, (s, o))
    call("POST", p + "hello", token, b)
    went, refused = 0, None
    for _ in range(40):
        s, o = call("POST", p + "send", token, a, os.urandom(30000))
        if s != 200:
            refused = (s, o)
            break
        went += 1
    check("parcels go until the whole store is full, then are refused as full (507)",
          went > 0 and refused is not None and refused[0] == 507 and refused[1].get("why") == "full", (went, refused))
    # The same size again (nothing was freed in between): a relay that counts to the byte refuses it as well.
    s, o = call("POST", p + "round", token, a, {"after": 0, "parcel": base64.b64encode(os.urandom(30000)).decode()})
    check("a round past it is refused as full and still brings the page",
          s == 507 and o.get("why") == "full" and o.get("seq") is None and isinstance(o.get("parcels"), list), (s, o))
    other, other_token = "/v1/b/" + secrets.token_hex(16) + "/", secrets.token_hex(32)
    s, o = call("POST", other + "hello", other_token, a)
    check("a new book is refused as full while the store is", s == 507 and o.get("why") == "full", (s, o))
    s, o = call("POST", p + "hello", token, b)
    check("a book already there still says hello", s == 200, (s, o))
    s, o = call("GET", p + "take?after=0", token, b)
    check("and its devices still read what it holds", s == 200 and len(o.get("parcels", [])) > 0, (s, len(o.get("parcels", []))))
    call("POST", p + "leave", token, a)
    s, o = call("POST", p + "leave", token, b)
    check("its devices leave", s == 200, (s, o))
    s, o = call("POST", other + "hello", other_token, a)
    check("once the full book went, a new book is taken again", s == 200, (s, o))


def wcall(method, path, token=None, body=None, raw=False):
    """A Waris call (the pass in both headers); raw=True gives the answer's bytes and content type for a chunk."""
    headers = {}
    if token is not None:
        headers["Authorization"] = "RK " + token
        headers["X-RK-Pass"] = token
    data = None
    if isinstance(body, bytes):
        data = body
        headers["Content-Type"] = "application/octet-stream"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    STEP[0] = "%s %s (%d bytes)" % (method, path, len(data or b""))
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            got = r.read()
            if raw:
                return r.status, got, r.headers.get("Content-Type", "")
            return r.status, json.loads(got or b"null")
    except urllib.error.HTTPError as e:
        got = e.read()
        try:
            o = json.loads(got)
        except ValueError:
            o = {"raw": got[:200].decode("latin-1")}
        return (e.code, o, "") if raw else (e.code, o)


def waris():
    """Waris ki Chaabi (heir access, protocol v1, 4 Nov 2026): the 12 routes under /v1/w/<vault>.

    Safe against a deployed relay (one fresh vault, deleted at the end). The real-wait check runs only when the
    relay's DAY is short (WARIS_DAY_MS in the test environment, read back from openAt - askedAt); else it is skipped.
    """
    import hashlib
    sha = lambda t: hashlib.sha256(t.encode()).hexdigest()
    vault = secrets.token_hex(16)
    w = "/v1/w/" + vault
    owner = secrets.token_hex(32)
    h1, h2 = secrets.token_hex(8), secrets.token_hex(8)
    p1, p2 = secrets.token_hex(32), secrets.token_hex(32)
    box1 = base64.b64encode(os.urandom(120)).decode()
    box2 = base64.b64encode(os.urandom(90)).decode()

    s, o = wcall("GET", w + "/status", owner)
    check("waris: an unknown vault is 404 unknown", s == 404 and o.get("why") == "unknown", (s, o))
    s, o = wcall("POST", w + "/setup", owner)
    check("waris: setup makes the vault", s == 200 and o.get("ok") is True and o.get("created") is True, (s, o))
    s, o = wcall("POST", w + "/setup", owner)
    check("waris: setup again with the same pass", s == 200 and o.get("created") is False, (s, o))
    s, o = wcall("POST", w + "/setup", secrets.token_hex(32))
    check("waris: setup with a wrong pass is refused", s == 403 and o.get("why") == "pass", (s, o))
    s, o = wcall("GET", w + "/status")
    check("waris: no pass is refused", s == 403 and o.get("why") == "pass", (s, o))
    s, o = wcall("GET", "/v1/w/NOTHEX" + "0" * 26 + "/status", owner)
    check("waris: a bad vault id is 400", s == 400 and o.get("why") == "bad", (s, o))
    s, o = wcall("PUT", w + "/heir/xyz", owner, {"passHash": sha(p1), "box": box1, "wait": 1})
    check("waris: a bad heir id is 400", s == 400 and o.get("why") == "bad", (s, o))

    s, o = wcall("GET", w + "/status", owner)
    check("waris: a new vault's status", s == 200 and o.get("snap") is None and o.get("heirs") == [] and isinstance(o.get("now"), int), (s, o))

    # The snapshot: three chunks of version 1, byte for byte.
    parts = [os.urandom(524288), os.urandom(300000), os.urandom(7)]
    for i, part in enumerate(parts):
        s, o = wcall("PUT", w + "/snap?v=1&i=%d&n=3" % i, owner, part)
        check("waris: snap chunk %d of 3 is taken" % i, s == 200 and o.get("complete") is (i == 2), (s, o))
    s, o = wcall("GET", w + "/status", owner)
    snap = o.get("snap") or {}
    check("waris: the complete version is the snapshot",
          snap.get("v") == 1 and snap.get("n") == 3 and snap.get("bytes") == sum(map(len, parts)) and isinstance(snap.get("at"), int), o)
    s, o = wcall("PUT", w + "/snap?v=1&i=0&n=1", owner, b"x" * 524289)
    check("waris: a chunk over 512 KiB is refused too-big", s == 413 and o.get("why") == "too-big", (s, o))
    s, o = wcall("PUT", w + "/snap?v=1&i=3&n=3", owner, b"x")
    check("waris: a chunk index past n is 400", s == 400 and o.get("why") == "bad", (s, o))
    s, o = wcall("PUT", w + "/snap?v=0&i=0&n=1", owner, b"x")
    check("waris: version 0 is 400", s == 400 and o.get("why") == "bad", (s, o))
    s, o = wcall("PUT", w + "/snap?v=2&i=0&n=17", owner, b"x")
    check("waris: more than 16 chunks is 400", s == 400 and o.get("why") == "bad", (s, o))
    # The largest version there may be: 16 chunks of 512 KiB, exactly 8 MiB (each chunk is at most 512 KiB and a
    # version at most 16 chunks, so the 8 MiB sum is met exactly, never passed).
    went = None
    for i in range(16):
        s, o = wcall("PUT", w + "/snap?v=5&i=%d&n=16" % i, owner, bytes([i]) * 524288)
        if s != 200:
            went = (i, s, o)
            break
    check("waris: 16 chunks of 512 KiB (8 MiB) make a version", went is None and o.get("complete") is True, (went, o))
    s, o = wcall("GET", w + "/status", owner)
    check("waris: which replaces version 1", (o.get("snap") or {}).get("v") == 5 and (o.get("snap") or {}).get("bytes") == 8388608, o)
    s, o = wcall("PUT", w + "/snap?v=7&i=0&n=2", owner, b"half")
    check("waris: half a new version is not complete", s == 200 and o.get("complete") is False, (s, o))
    s, o = wcall("GET", w + "/status", owner)
    check("waris: and the complete one stays the snapshot", (o.get("snap") or {}).get("v") == 5, o)

    # A smaller final snapshot for the heirs: version 9, 3 chunks; it replaces 5, and 5's and 7's chunks go.
    for i, part in enumerate(parts):
        s, o = wcall("PUT", w + "/snap?v=9&i=%d&n=3" % i, owner, part)
    check("waris: a higher version replaces the old one", s == 200 and o.get("complete") is True, (s, o))
    s, o = wcall("PUT", w + "/snap?v=8&i=0&n=1", owner, b"late")
    check("waris: an older version is refused old", s == 409 and o.get("why") == "old", (s, o))

    # Heirs.
    s, o = wcall("PUT", w + "/heir/" + h1, owner, {"passHash": sha(p1), "box": box1, "wait": 1})
    check("waris: an heir is put", s == 200 and o.get("ok") is True, (s, o))
    s, o = wcall("PUT", w + "/heir/" + h2, owner, {"passHash": sha(p2), "box": box2, "wait": 90})
    check("waris: a second heir is put", s == 200, (s, o))
    s, o = wcall("PUT", w + "/heir/" + h2, owner, {"passHash": sha(p2), "box": box2, "wait": 91})
    check("waris: a wait over 90 days is 400", s == 400 and o.get("why") == "bad", (s, o))
    s, o = wcall("PUT", w + "/heir/" + h2, owner, {"passHash": "zz", "box": box2, "wait": 3})
    check("waris: a bad passHash is 400", s == 400 and o.get("why") == "bad", (s, o))
    s, o = wcall("PUT", w + "/heir/" + h2, owner, {"passHash": sha(p2), "box": "A" * 4100, "wait": 3})
    check("waris: a box over 4096 characters is 400", s == 400 and o.get("why") == "bad", (s, o))
    s, o = wcall("PUT", w + "/heir/" + h2, owner, {"passHash": sha(p2), "box": box2, "wait": 3, "pad": "x" * 20000})
    check("waris: an heir's body over 16 KiB is refused 400 bad", s == 400 and o.get("why") == "bad", (s, o))
    extra = [secrets.token_hex(8) for _ in range(3)]
    for x in extra:
        wcall("PUT", w + "/heir/" + x, owner, {"passHash": sha(secrets.token_hex(32)), "box": box2, "wait": 2})
    s, o = wcall("PUT", w + "/heir/" + secrets.token_hex(8), owner, {"passHash": sha(p2), "box": box2, "wait": 2})
    check("waris: a sixth heir is refused full", s == 409 and o.get("why") == "full", (s, o))
    s, o = wcall("PUT", w + "/heir/" + extra[0], owner, {"passHash": sha(secrets.token_hex(32)), "box": box2, "wait": 3})
    check("waris: a known heir may still be replaced at five", s == 200, (s, o))
    for x in extra:
        wcall("DELETE", w + "/heir/" + x, owner)

    s, o = wcall("GET", w + "/status", owner)
    heirs = {x.get("hid"): x for x in o.get("heirs", [])}
    keys = {"hid", "wait", "lastSeen", "askedAt", "rejectedAt", "allowedAt", "openAt"}
    check("waris: status lists the heirs with every field", s == 200 and set(heirs) == {h1, h2}
          and all(set(x) >= keys for x in heirs.values()) and heirs[h1]["wait"] == 1 and heirs[h1]["askedAt"] is None
          and heirs[h1]["openAt"] is None, o)

    # Passes do not cross: an heir cannot act as the owner, nor the owner as an heir.
    s, o = wcall("GET", w + "/status", p1)
    check("waris: an heir's pass cannot read the status", s == 403 and o.get("why") == "pass", (s, o))
    s, o = wcall("POST", w + "/heir/" + h1 + "/allow", p1)
    check("waris: nor allow itself", s == 403 and o.get("why") == "pass", (s, o))
    s, o = wcall("POST", w + "/heir/" + h1 + "/ask", owner)
    check("waris: the owner's pass cannot ask as an heir", s == 403 and o.get("why") == "pass", (s, o))
    s, o = wcall("POST", w + "/heir/" + h1 + "/ask", p2)
    check("waris: nor another heir's pass", s == 403 and o.get("why") == "pass", (s, o))
    s, o = wcall("POST", w + "/heir/" + secrets.token_hex(8) + "/ask", p1)
    check("waris: an unknown heir is 404 unknown", s == 404 and o.get("why") == "unknown", (s, o))
    s, o = wcall("POST", "/v1/w/" + secrets.token_hex(16) + "/heir/" + h1 + "/ask", p1)
    check("waris: an heir of an unknown vault is 404 unknown", s == 404 and o.get("why") == "unknown", (s, o))

    # Ask, wait, reject, ask again too soon.
    s, o = wcall("POST", w + "/heir/" + h1 + "/alive", p1)
    check("waris: alive before asking", s == 200 and o.get("askedAt") is None and o.get("open") is False and o.get("openAt") is None, (s, o))
    s, o = wcall("POST", w + "/heir/" + h1 + "/ask", p1)
    asked = o.get("askedAt")
    check("waris: an heir asks", s == 200 and isinstance(asked, int) and isinstance(o.get("openAt"), int) and o["openAt"] > asked, (s, o))
    day = (o.get("openAt", 0) - (asked or 0))  # wait is 1 day for this heir
    s, o = wcall("POST", w + "/heir/" + h1 + "/ask", p1)
    check("waris: asking again changes nothing", s == 200 and o.get("askedAt") == asked, (s, o))
    s, o = wcall("GET", w + "/status", owner)
    x = {y["hid"]: y for y in o.get("heirs", [])}.get(h1, {})
    check("waris: the status shows askedAt and openAt", x.get("askedAt") == asked and x.get("openAt") == asked + day
          and isinstance(x.get("lastSeen"), int), x)
    s, o = wcall("GET", w + "/heir/" + h1 + "/open", p1)
    check("waris: open before the wait is 403 waiting", s == 403 and o.get("why") == "waiting" and o.get("openAt") == asked + day, (s, o))
    s, b, ct = wcall("GET", w + "/heir/" + h1 + "/snap?v=9&i=0", p1, raw=True)
    check("waris: a chunk before the wait is 403", s == 403, (s, b))
    s, o = wcall("POST", w + "/heir/" + h1 + "/reject", owner)
    check("waris: the owner rejects", s == 200 and o.get("ok") is True, (s, o))
    s, o = wcall("POST", w + "/heir/" + h1 + "/alive", p1)
    check("waris: alive says rejected", s == 200 and o.get("askedAt") is None and isinstance(o.get("rejectedAt"), int) and o.get("open") is False, (s, o))
    rejected = o.get("rejectedAt")
    s, o = wcall("POST", w + "/heir/" + h1 + "/ask", p1)
    check("waris: asking again within a day is 429 wait", s == 429 and o.get("why") == "wait"
          and o.get("retryAt") == (rejected or 0) + day, (s, o))

    # Allow: open, the box and the snapshot, each chunk byte for byte.
    s, o = wcall("POST", w + "/heir/" + h1 + "/allow", owner)
    check("waris: the owner allows", s == 200, (s, o))
    s, o = wcall("GET", w + "/heir/" + h1 + "/open", p1)
    sn = o.get("snap") or {}
    check("waris: open gives the box and the snapshot", s == 200 and o.get("box") == box1 and sn.get("v") == 9
          and sn.get("n") == 3 and sn.get("bytes") == sum(map(len, parts)), (s, o))
    same = True
    for i, part in enumerate(parts):
        s, b, ct = wcall("GET", w + "/heir/" + h1 + "/snap?v=9&i=%d" % i, p1, raw=True)
        same = same and s == 200 and b == part and ct.startswith("application/octet-stream")
    check("waris: each chunk comes back byte for byte", same, (s, ct))
    s, o, _ = wcall("GET", w + "/heir/" + h1 + "/snap?v=5&i=0", p1, raw=True)
    check("waris: a chunk of an old version is 404 absent", s == 404 and o.get("why") == "absent", (s, o))
    s, o, _ = wcall("GET", w + "/heir/" + h1 + "/snap?v=9&i=5", p1, raw=True)
    check("waris: a chunk that is not there is 404 absent", s == 404 and o.get("why") == "absent", (s, o))
    s, o = wcall("POST", w + "/heir/" + h1 + "/alive", p1)
    check("waris: alive says open", s == 200 and o.get("open") is True and isinstance(o.get("openAt"), int), (s, o))

    # Same passHash keeps the heir's state; a new one resets it.
    s, o = wcall("PUT", w + "/heir/" + h1, owner, {"passHash": sha(p1), "box": box2, "wait": 1})
    s, o = wcall("GET", w + "/heir/" + h1 + "/open", p1)
    check("waris: a re-put with the same pass keeps the heir allowed, with the new box", s == 200 and o.get("box") == box2, (s, o))
    s, o = wcall("PUT", w + "/heir/" + h1, owner, {"passHash": sha(p2), "box": box1, "wait": 1})
    s, o = wcall("GET", w + "/status", owner)
    x = {y["hid"]: y for y in o.get("heirs", [])}.get(h1, {})
    check("waris: a re-put with a new pass resets its state", x.get("allowedAt") is None and x.get("askedAt") is None
          and x.get("rejectedAt") is None and x.get("lastSeen") is None, x)
    s, o = wcall("POST", w + "/heir/" + h1 + "/ask", p1)
    check("waris: the old heir pass no longer works", s == 403 and o.get("why") == "pass", (s, o))

    # A real wait: when the relay's DAY is short (the test environment), ask, sleep past wait x DAY, open.
    if 0 < day <= 10_000:
        s, o = wcall("POST", w + "/heir/" + h1 + "/ask", p2)
        time.sleep(day / 1000.0 + 0.8)
        s, o = wcall("GET", w + "/heir/" + h1 + "/open", p2)
        check("waris: after the wait the heir opens without the owner", s == 200 and o.get("box") == box1
              and (o.get("snap") or {}).get("v") == 9, (s, o))
    else:
        print("--   waris: DAY is %d ms here; the real-wait check needs WARIS_DAY_MS (tests only)" % day)

    s, o = wcall("DELETE", w + "/heir/" + h2, owner)
    check("waris: an heir is deleted", s == 200 and o.get("ok") is True, (s, o))
    s, o = wcall("POST", w + "/heir/" + h2 + "/alive", p2)
    check("waris: a deleted heir is 404 unknown", s == 404 and o.get("why") == "unknown", (s, o))
    s, o = wcall("DELETE", w + "/heir/" + h2, owner)
    check("waris: deleting it again is 404 unknown", s == 404 and o.get("why") == "unknown", (s, o))
    s, o = wcall("DELETE", w, p2)
    check("waris: a wrong pass cannot delete the vault", s == 403 and o.get("why") == "pass", (s, o))
    s, o = wcall("DELETE", w, owner)
    check("waris: the vault is deleted", s == 200 and o.get("ok") is True, (s, o))
    s, o = wcall("GET", w + "/status", owner)
    check("waris: and is unknown after", s == 404 and o.get("why") == "unknown", (s, o))
    s, o = wcall("POST", w + "/heir/" + h1 + "/alive", p2)
    check("waris: its heirs are gone with it", s == 404 and o.get("why") == "unknown", (s, o))


def health(keep=None):
    """The health call; with [keep], the keep-alive pass (X-RK-Keep) that runs the Supabase relay's daily sweep."""
    req = urllib.request.Request(BASE + "/v1/health", headers={"X-RK-Keep": keep} if keep else {})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code


def wlimits():
    """The heirs' vaults with their limits lowered at the relay (7 Nov 2026, R1: anyone may make a vault).

    The relay runs with 6 new vaults a day, the vaults' own bytes at 3,000,000, the books' store at 2,000,000 and a
    DAY of 500 ms, so a bare vault is junk 7 s after its setup (the module's docstring says how each relay is started).
    The vaults' bytes are never the books': with them spent, a book still takes parcels.
    """
    import hashlib
    sha = lambda t: hashlib.sha256(t.encode()).hexdigest()
    started = time.time()
    owner = secrets.token_hex(32)
    box = base64.b64encode(os.urandom(64)).decode()

    # The day's cap: new vaults go until the relay says waris-day; a vault already there is still set up again.
    made, refused = [], None
    for _ in range(9):
        w = "/v1/w/" + secrets.token_hex(16)
        s, o = wcall("POST", w + "/setup", owner)
        if s != 200:
            refused = (s, o)
            break
        made.append(w)
    check("wlimits: 6 new vaults are taken, the 7th is refused 429 waris-day",
          len(made) == 6 and refused is not None and refused[0] == 429 and refused[1].get("why") == "waris-day", (len(made), refused))
    if len(made) < 4:
        return
    va, vs, vd, bare = made[0], made[1], made[2], made[3:]
    s, o = wcall("POST", va + "/setup", owner)
    check("wlimits: a vault already there is set up again past the cap", s == 200 and o.get("created") is False, (s, o))
    s, o = wcall("POST", va + "/setup", secrets.token_hex(32))
    check("wlimits: and a wrong pass there is still refused pass", s == 403 and o.get("why") == "pass", (s, o))

    # The vaults the junk sweep must keep or take: va an heir, vs a snapshot, vd an heir put and taken away again.
    ha, pa = secrets.token_hex(8), secrets.token_hex(32)
    s, o = wcall("PUT", va + "/heir/" + ha, owner, {"passHash": sha(pa), "box": box, "wait": 1})
    check("wlimits: an heir is put", s == 200, (s, o))
    s, o = wcall("PUT", vs + "/snap?v=1&i=0&n=1", owner, b"a small snapshot")
    check("wlimits: a small snapshot is taken", s == 200 and o.get("complete") is True, (s, o))
    hd = secrets.token_hex(8)
    s1, _ = wcall("PUT", vd + "/heir/" + hd, owner, {"passHash": sha(secrets.token_hex(32)), "box": box, "wait": 1})
    s2, o = wcall("DELETE", vd + "/heir/" + hd, owner)
    check("wlimits: an heir is put and taken away again", s1 == 200 and s2 == 200, (s1, s2, o))
    # The relay's DAY, read back as the plain checks do: openAt - askedAt of a one-day heir.
    s, o = wcall("POST", va + "/heir/" + ha + "/ask", pa)
    day = (o.get("openAt") or 0) - (o.get("askedAt") or 0)
    check("wlimits: the relay's DAY is short here (WARIS_DAY_MS)", s == 200 and 0 < day <= 2000, (s, o))

    # The vaults' own bytes: 512 KiB chunks until waris-full (3,000,000 bytes: 5 chunks go), so the vaults then hold
    # more than the books' whole store (2,000,000 here) — counted with the books, they would refuse every book.
    went, full = 0, None
    for i in range(16):
        s, o = wcall("PUT", va + "/snap?v=1&i=%d&n=16" % i, owner, os.urandom(524288))
        if s != 200:
            full = (s, o)
            break
        went += 1
    check("wlimits: chunks go past the books' whole store, then 507 waris-full",
          4 <= went <= 5 and full is not None and full[0] == 507 and full[1].get("why") == "waris-full", (went, full))
    s, o = wcall("PUT", vs + "/snap?v=2&i=0&n=1", owner, os.urandom(524288))
    check("wlimits: so is another vault's chunk (the bytes are every vault's)", s == 507 and o.get("why") == "waris-full", (s, o))

    # The books' store counts no vault: a new book is taken and a parcel bigger than a chunk goes.
    token = secrets.token_hex(32)
    a, b = secrets.token_hex(8), secrets.token_hex(8)
    p = "/v1/b/" + secrets.token_hex(16) + "/"
    s, o = call("POST", p + "hello", token, a)
    check("wlimits: with the vaults' bytes spent, a new book is taken", s == 200, (s, o))
    call("POST", p + "hello", token, b)
    s, o = call("POST", p + "send", token, a, os.urandom(600000))
    check("wlimits: and a parcel goes", s == 200 and isinstance(o.get("seq"), int), (s, o))
    s, o = call("POST", p + "round", token, b, {"after": 0, "parcel": base64.b64encode(os.urandom(1000)).decode()})
    check("wlimits: and a round's parcel", s == 200 and isinstance(o.get("seq"), int) and len(o.get("parcels", [])) == 1,
          (s, o.get("why"), o.get("seq"), len(o.get("parcels", []))))

    # Junk: a vault still bare 14 DAYs after its setup goes (PHP at a health call, Supabase at the keep-alive health
    # call, the Worker at the vault's alarm); one with an heir or a snapshot stays.
    if not 0 < day <= 2000:
        print("--   wlimits: the relay's DAY is not short; the junk checks need WARIS_DAY_MS")
        return
    wait = started + 14 * day / 1000.0 + 0.5 - time.time()
    if wait > 0:
        time.sleep(wait)
    keep = os.environ.get("RK_KEEP_SECRET")
    deadline = time.time() + 20
    while True:
        health(keep)
        gone = [w for w in [vd] + bare if wcall("GET", w + "/status", owner)[0] == 404]
        if len(gone) == 1 + len(bare) or time.time() > deadline:
            break
        time.sleep(0.5)
    check("wlimits: vaults still bare 14 DAYs after their setup are gone", all(w in gone for w in bare), (len(gone), len(bare)))
    check("wlimits: so is one whose only heir was taken away", vd in gone, gone)
    s, o = wcall("GET", va + "/status", owner)
    check("wlimits: a vault with an heir stays", s == 200 and [h.get("hid") for h in o.get("heirs", [])] == [ha], (s, o))
    s, o = wcall("GET", vs + "/status", owner)
    check("wlimits: a vault with a snapshot stays", s == 200 and (o.get("snap") or {}).get("v") == 1, (s, o))


def main():
    if MODE == "--waris":
        return waris()
    if MODE == "--caps":
        return caps()
    if MODE == "--idle":
        return idle()
    if MODE == "--store":
        return store()
    if MODE == "--wlimits":
        return wlimits()
    book = secrets.token_hex(16)
    token = secrets.token_hex(32)
    a, b, c, d = (secrets.token_hex(8) for _ in range(4))
    p = "/v1/b/" + book + "/"

    s, o = call("GET", "/v1/health")
    check("health answers", s == 200 and o.get("ok") is True and o.get("live") is True, (s, o))
    check("health says whether it can push", isinstance(o.get("push"), bool), o)

    s, o = call("GET", p + "take?after=0", token, a)
    check("an unknown book is refused until a hello", s == 403 and o.get("why") == "say-hello-first", (s, o))
    s, o = call("POST", p + "send", token, a, b"x")
    check("a stray parcel never sets a book's pass", s == 403 and o.get("why") == "say-hello-first", (s, o))

    s, o = call("POST", p + "hello", token, a)
    check("the first hello sets the pass", s == 200 and o.get("ok") and o.get("top") == 0 and o.get("others") == [], (s, o))
    s, o = call("POST", p + "hello", secrets.token_hex(32), b)
    check("a wrong pass is refused", s == 403 and o.get("why") == "wrong-pass", (s, o))
    s, o = call("POST", p + "hello", token, b, how="auth")
    check("the pass may come as Authorization alone", s == 200 and [x["device"] for x in o.get("others", [])] == [a], (s, o))
    s, o = call("POST", p + "hello", token, b, how="x")
    check("the pass may come as X-RK-Pass alone", s == 200, (s, o))

    s, o = call("POST", p + "send", token, c, b"x")
    check("a device that never said hello cannot send", s == 403 and o.get("why") == "say-hello-first", (s, o))
    s, o = call("POST", p + "send", None, a, b"x")
    check("no pass, no entry", s == 401, (s, o))
    s, o = call("POST", p + "send", token, "nothex", b"x")
    check("a device id must be 16 hex", s == 400, (s, o))
    s, o = call("GET", p + "send", token, a)
    check("send is POST only", s == 405, (s, o))
    s, o = call("POST", p + "take", token, a, b"")
    check("take is GET only", s == 405, (s, o))
    s, o = call("POST", "/v1/b/" + book + "/steal", token, a, b"x")
    check("an unknown path is 404", s == 404, (s, o))

    one = os.urandom(3000) + b"\x00\x00end"
    two = bytes(range(256)) * 10
    s, o1 = call("POST", p + "send", token, a, one)
    s2, o2 = call("POST", p + "send", token, a, two)
    check("parcels are numbered in order", s == 200 and s2 == 200 and o2["seq"] > o1["seq"], (s, o1, s2, o2))
    s, o = call("POST", p + "send", token, a, b"")
    check("an empty parcel is refused", s == 400, (s, o))
    s, o = call("POST", p + "send", token, a, b"z" * (1024 * 1024 + 1))
    check("a parcel over 1 MB is refused", s == 413, (s, o))

    s, o = call("GET", p + "take?after=0", token, b)
    got = [base64.b64decode(x["body"]) for x in o.get("parcels", [])]
    check("the other device takes them whole, byte for byte", s == 200 and got == [one, two], (s, len(got)))
    check("each says who sent it", all(x["from"] == a for x in o.get("parcels", [])), o.get("parcels"))
    check("no gap and nothing more", o.get("more") is False and o.get("gap") is False, o)
    check("a whole page says how far the book goes", o.get("through") == o2["seq"], o.get("through"))
    s, o = call("GET", p + "take?after=0", token, a)
    check("a device is never given its own parcels", s == 200 and o["parcels"] == [], (s, o))
    check("and may read on past them", o.get("through") == o2["seq"], o.get("through"))
    s, o = call("GET", p + "take?after=" + str(o1["seq"]), token, b)
    check("after= skips what was taken", [x["seq"] for x in o["parcels"]] == [o2["seq"]], o)

    s, o = call("POST", p + "ack", token, b, {"upTo": o2["seq"]})
    check("an ack is taken", s == 200 and o.get("ok"), (s, o))
    s, o = call("POST", p + "ack", token, b, {"upTo": "nope"})
    check("a bad ack is refused", s == 400, (s, o))
    s, o = call("POST", p + "hello", token, d)
    check("a third device says hello", s == 200 and len(o["others"]) == 2, (s, o))
    s, o = call("GET", p + "take?after=0", token, d)
    check("parcels every other device took are gone", s == 200 and o["parcels"] == [], (s, o))

    seqs = []
    for i in range(45):
        s, o = call("POST", p + "send", token, a, ("p%d" % i).encode())
        seqs.append(o.get("seq"))
    s, o = call("GET", p + "take?after=" + str(o2["seq"]), token, b)
    check("a page holds at most 40 and says there is more", len(o["parcels"]) == 40 and o["more"] is True, (len(o["parcels"]), o.get("more")))
    check("a page with more to come says no through", "through" not in o, o.get("through"))
    last = o["parcels"][-1]["seq"]
    s, o = call("GET", p + "take?after=" + str(last), token, b)
    check("the next page holds the rest", len(o["parcels"]) == 5 and o["more"] is False, (len(o["parcels"]), o.get("more")))

    # One request for a whole round (22 Oct 2026): ack + parcel + page.
    s, o = call("POST", p + "round", token, a, {"after": 0, "upTo": None, "parcel": None})
    check("an empty round answers a page and no seq", s == 200 and o.get("seq") is None and o.get("parcels") == [] and o.get("more") is False, (s, o))
    s, o = call("POST", p + "round", token, a, {"after": 0, "parcel": base64.b64encode(b"via round").decode()})
    rseq = o.get("seq")
    check("a round leaves a parcel and says its number", s == 200 and isinstance(rseq, int) and rseq > 0, (s, o))
    s, o = call("POST", p + "round", token, b, {"after": seqs[-1], "upTo": seqs[-1]})
    got = [base64.b64decode(x["body"]) for x in o.get("parcels", [])]
    check("the other device's round acks and takes it", s == 200 and got == [b"via round"] and o["parcels"][0]["seq"] == rseq, (s, o))
    s, o = call("POST", p + "round", token, b, {"after": rseq, "upTo": rseq})
    check("acked in a round, the parcel is gone once the others took it", s == 200 and o["parcels"] == [], (s, o))
    s, o = call("GET", p + "take?after=" + str(seqs[-1]), token, d)
    check("a third device still sees nothing it was not meant to", s == 200 and o["parcels"] == [] or len(o["parcels"]) == 1, (s, o))
    s, o = call("POST", p + "round", token, a, {"after": 0, "parcel": "%%%not-base64"})
    check("a round with a bad parcel is refused", s == 400, (s, o))
    s, o = call("POST", p + "round", token, a, {"after": 0, "upTo": -5})
    check("a round with a bad ack is refused", s == 400, (s, o))
    s, o = call("POST", p + "round", token, a, b"not json")
    check("a round that is not JSON is refused", s == 400, (s, o))

    # The live line (23 Oct 2026, Cloudflare only: the hostings answer 404 and the app keeps to rounds).
    STEP[0] = "live line"
    probe = Line(p + "live", token, a)
    # Supabase's own gateway answers a WebSocket upgrade to a function with 502 before the function sees it
    # (the deployed check, 23 Oct 2026): that is "no line" too. Only a Supabase address may say so; on the
    # Worker a 502 stays a failure.
    no_line = probe.status in (404, 426) or (probe.status == 502 and ".supabase.co" in BASE)
    if no_line:
        probe.close()
        print("--   no live line on this relay (rounds only)")
    else:
        check("a known device opens a live line", probe.status == 101, probe.status)
        probe.close()
        stranger = Line(p + "live", token, secrets.token_hex(8))
        check("a device that never said hello gets no line", stranger.status == 403, stranger.status)
        stranger.close()
        wrong = Line(p + "live", secrets.token_hex(32), a)
        check("nor does a wrong pass", wrong.status == 403, wrong.status)
        wrong.close()
        la, lb = Line(p + "live", token, a), Line(p + "live", token, b)
        la.send({"id": 1, "round": {"after": 0, "upTo": None, "parcel": None}})
        o = la.recv()
        check("a round over the line is answered with its id", o and o.get("id") == 1 and o.get("status") == 200 and o.get("ok"), o)
        la.send({"id": 2, "round": {"after": 0, "upTo": None, "parcel": base64.b64encode(b"over the line").decode()}})
        o = la.recv()
        check("a parcel left over the line is numbered", o and o.get("id") == 2 and isinstance(o.get("seq"), int), o)
        seq = o.get("seq") if o else None
        n = lb.recv()
        check("the other device's line is nudged at once", n == {"t": "new"}, n)
        lb.send({"id": 7, "round": {"after": (seq or 1) - 1, "upTo": None, "parcel": None}})
        o = lb.recv()
        got = [base64.b64decode(x["body"]) for x in (o or {}).get("parcels", [])]
        check("and takes the parcel over its own line", o and o.get("id") == 7 and b"over the line" in got, o)
        la.send({"id": 3, "round": {"after": 0, "parcel": "%%%not-base64"}})
        o = la.recv()
        check("a bad parcel over the line is refused, the line stays", o and o.get("id") == 3 and o.get("status") == 400, o)
        la.send({"id": 4, "round": {"after": 0}})
        o = la.recv()
        check("and the next round still works", o and o.get("id") == 4 and o.get("status") == 200, o)
        la.close(); lb.close()

    s, o = call("POST", p + "wake", token, b, {"token": "f" * 40})
    check("a wake-up address is kept", s == 200 and o.get("wake") is True, (s, o))
    check("and the answer says whether a push can come", isinstance(o.get("push"), bool), o)
    s, o = call("POST", p + "wake", token, b, {"token": None})
    check("and taken away", s == 200 and o.get("wake") is False, (s, o))

    s, o = call("POST", p + "leave", token, a)
    check("a device may leave", s == 200, (s, o))
    s, o = call("GET", p + "take?after=0", token, b)
    check("its parcels go with it", s == 200 and o["parcels"] == [], (s, o))
    s, o = call("GET", p + "take?after=0", token, a)
    check("and it must say hello again", s == 403 and o.get("why") == "say-hello-first", (s, o))

    # Waris ki Chaabi (4 Nov 2026): every relay holds the heirs' vaults the same way.
    waris()


if __name__ == "__main__":
    try:
        main()
        print()
        if FAILED:
            print("%d check(s) failed: %s" % (len(FAILED), ", ".join(FAILED)))
            sys.exit(1)
        print("every check passed against " + BASE + (" " + MODE if MODE else ""))
    except SystemExit:
        raise
    except BaseException as e:  # a crash is said as plainly as a failed check
        import traceback
        where = traceback.extract_tb(e.__traceback__)[-1]
        msg = "%s: %s (line %d: %s)" % (type(e).__name__, str(e)[:400], where.lineno, (where.line or "").strip()[:200])
        print("CRASH " + msg)
        if os.environ.get("GITHUB_ACTIONS"):
            print("::error title=relay check crashed::" + msg + " -- during " + STEP[0] + ", after the check: " + LAST[0]
                  + " -- failed before it: " + (", ".join(FAILED) or "none"))
        sys.exit(2)

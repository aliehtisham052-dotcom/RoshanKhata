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
    --store  the whole store (every book together) holds about 300,000 bytes
             (PHP: RK_RELAY_MAX_STORE_BYTES=300000; Supabase: RK_STORE_BYTES=300000;
             the Worker: --var ACCOUNT_BYTES:300000). Each relay counts in its own
             way (the hosting its files, Supabase the parcels, the Worker SQLite's
             own size reported to its ledger every 64 KiB), so the checks ask only
             what all three promise: parcels go, then "full" (507) within 40 of
             30,000 bytes; a new book is refused "full"; a book already there still
             says hello and is read; once its devices left, a new book is taken.
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


def main():
    if MODE == "--caps":
        return caps()
    if MODE == "--idle":
        return idle()
    if MODE == "--store":
        return store()
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

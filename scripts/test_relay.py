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
"""
import base64
import json
import os
import secrets
import sys
import urllib.error
import urllib.request

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://127.0.0.1:8098"
FAILED = []


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
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"raw": raw[:200].decode("latin-1")}


def check(name, cond, detail=""):
    print(("ok   " if cond else "FAIL ") + name + ("" if cond else "  " + str(detail)))
    if not cond:
        FAILED.append(name)


def main():
    book = secrets.token_hex(16)
    token = secrets.token_hex(32)
    a, b, c, d = (secrets.token_hex(8) for _ in range(4))
    p = "/v1/b/" + book + "/"

    s, o = call("GET", "/v1/health")
    check("health answers", s == 200 and o.get("ok") is True and o.get("live") is True, (s, o))

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
    s, o = call("GET", p + "take?after=0", token, a)
    check("a device is never given its own parcels", s == 200 and o["parcels"] == [], (s, o))
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
    last = o["parcels"][-1]["seq"]
    s, o = call("GET", p + "take?after=" + str(last), token, b)
    check("the next page holds the rest", len(o["parcels"]) == 5 and o["more"] is False, (len(o["parcels"]), o.get("more")))

    s, o = call("POST", p + "wake", token, b, {"token": "f" * 40})
    check("a wake-up address is kept", s == 200 and o.get("wake") is True, (s, o))
    s, o = call("POST", p + "wake", token, b, {"token": None})
    check("and taken away", s == 200 and o.get("wake") is False, (s, o))

    s, o = call("POST", p + "leave", token, a)
    check("a device may leave", s == 200, (s, o))
    s, o = call("GET", p + "take?after=0", token, b)
    check("its parcels go with it", s == 200 and o["parcels"] == [], (s, o))
    s, o = call("GET", p + "take?after=0", token, a)
    check("and it must say hello again", s == 403 and o.get("why") == "say-hello-first", (s, o))

    print()
    if FAILED:
        print("%d check(s) failed: %s" % (len(FAILED), ", ".join(FAILED)))
        sys.exit(1)
    print("every check passed against " + BASE)


if __name__ == "__main__":
    main()

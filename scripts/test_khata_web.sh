#!/usr/bin/env bash
# The customer page's server (web/khata) on PHP's own server, as the review of 22 Oct 2026 asked:
# a page sealed the app's way (Java's AES-GCM) is stored whole and opened the browser's way (WebCrypto,
# in Node); only the app's JSON is taken; a wrong pass, another shop and another copy of the book are
# refused; a page taken back stays taken; the limits hold; a write that fails keeps the old page; the
# store is outside the site; old pages are let go.
#
# And the security review of 7 Nov 2026: a request that makes no page leaves no shop; new shops are
# counted per address (a keyed hash, never the address) and per hour; a shop's new pages a day are
# budgeted, less while it is young; a fifth of the files is kept for older shops; a shop with no live
# page goes; an id let go is written to the list of used ids and never taken again, with no file per
# id; and view.js itself, run in Node: a page opens only under its own link's id (and a page sealed
# before still opens), an older page served again is said and the newest kept, no more is shown than
# the app writes, and "Open in the app" names the app's package. Run from the repository root.
set -euo pipefail
W=$(mktemp -d); PIDS=()
trap 'for p in "${PIDS[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null; done; rm -rf "$W"' EXIT
for s in site site2 site3 site4; do mkdir -p "$W/$s" && cp -r web/khata/. "$W/$s/"; done
mkdir -p "$W/store" "$W/store2" "$W/store3"
php -l "$W/site/api.php"
node --check "$W/site/view.js"

# Seal <key, or - for a new one> <aad> <json>: the key and the sealed page, as the app's Java seals it.
cat > "$W/Seal.java" <<'J'
import javax.crypto.*; import javax.crypto.spec.*; import java.security.*; import java.util.*;
public class Seal { public static void main(String[] a) throws Exception {
  SecureRandom r = new SecureRandom(); byte[] k;
  if (a[0].equals("-")) { k = new byte[16]; r.nextBytes(k); } else k = Base64.getUrlDecoder().decode(a[0]);
  byte[] n = new byte[12]; r.nextBytes(n);
  Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(128, n));
  c.updateAAD(a[1].getBytes("US-ASCII"));
  byte[] ct = c.doFinal(a[2].getBytes("UTF-8"));
  byte[] all = new byte[12 + ct.length];
  System.arraycopy(n, 0, all, 0, 12); System.arraycopy(ct, 0, all, 12, ct.length);
  Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
  System.out.println(e.encodeToString(k)); System.out.println(e.encodeToString(all)); } }
J
(cd "$W" && javac Seal.java)
seal() { (cd "$W" && java Seal "$@" 2>/dev/null); }
hex() { openssl rand -hex "$1"; }

ID=$(hex 16)
OUT=$(seal - "rk-clink-v2|$ID" '{"v":3,"s":"Shop","n":"Ali","p":"c","b":5400,"c":"Rs","k":2,"l":"ur","t":1,"r":1,"q":1,"_":""}')
KEY=$(echo "$OUT" | sed -n 1p); BLOB=$(echo "$OUT" | sed -n 2p)

# Small limits, so each can be met: 3 live pages a shop, 4 new shops an hour.
serve() { # port, site, store, extra env...
  local port=$1 site=$2 store=$3; shift 3
  env RK_KHATA_DATA="$W/$store" RK_KHATA_MAX_LINKS=3 RK_KHATA_NEW_SHOPS_HOUR=4 "$@" php -S 127.0.0.1:$port -t "$W/$site" > "$W/php-$port.log" 2>&1 &
  PIDS+=($!)
  for i in $(seq 1 30); do curl -s -o /dev/null 127.0.0.1:$port/index.html && return 0; sleep 0.2; done
  echo "php did not start on $port"; exit 1
}
serve 8099 site store
U=127.0.0.1:8099
post() { curl -s -X POST -H 'Content-Type: application/json' --data-binary "$1" "${2:-$U}/api.php"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
must() { if ! grep -q -- "$2" <<< "$1"; then echo "FAILED: $3"; echo "  got: $1"; exit 1; fi; }
never() { if grep -q -- "$2" <<< "$1"; then echo "FAILED: $3"; echo "  got: $1"; exit 1; fi; }
body() { # shop token writer put-json drop-json [claim]
  echo "{\"shop\":\"$1\",\"token\":\"$2\",\"writer\":\"$3\",\"put\":$4,\"drop\":$5${6:+,\"claim\":true}}"
}
one() { echo "[{\"id\":\"$1\",\"blob\":\"${2:-AAAA}\"}]"; }   # one page to put
many() { local n=$1 out="" i; for i in $(seq 1 "$n"); do out="$out${out:+,}{\"id\":\"$(hex 16)\",\"blob\":\"AAAA\"}"; done; echo "[$out]"; }

SHOP=$(hex 16); TOK=$(hex 32); W1=$(hex 16); W2=$(hex 16)
SHOP2=$(hex 16); TOK2=$(hex 32); WB=$(hex 16)

# Only the app's own requests.
test "$(code "$U/api.php")" = 405 || { echo "FAILED: a GET is refused"; exit 1; }
test "$(code -X POST --data-binary "$(body $SHOP $TOK $W1 '[]' '[]')" "$U/api.php")" = 415 || { echo "FAILED: a form post is refused"; exit 1; }
test "$(code -X POST -H 'Content-Type: text/plain' --data-binary "$(body $SHOP $TOK $W1 '[]' '[]')" "$U/api.php")" = 415 || { echo "FAILED: text/plain is refused"; exit 1; }
must "$(post '[1,2]')" '"bad-json"' "a list is not a request"

# A request that makes no page leaves no shop behind (7 Nov 2026): nothing kept, nothing counted.
EMPTY=$(hex 16)
must "$(post "$(body $EMPTY $(hex 32) $(hex 16) '[]' "[\"$(hex 16)\"]")")" '"ok":true' "a new shop with no page is answered"
test ! -e "$W/store/s/$EMPTY.json" || { echo "FAILED: a shop that made no page is not kept"; exit 1; }

# The first page, stored whole: the file holds only the sealed page, no mark of its shop.
must "$(post "$(body $SHOP $TOK $W1 "$(one $ID $BLOB)" '[]')")" '"written":1' "the first page is written"
test "$(cat "$W/site/d/$ID.txt")" = "$BLOB" || { echo "FAILED: the page file is the sealed page alone"; exit 1; }
if grep -rq "$(printf '%s' "$TOK" | sha256sum | cut -c1-64)" "$W/site"; then echo "FAILED: a pass is inside the site"; exit 1; fi

# A wrong pass; another copy of the book (another writer) until it claims; the first copy refused after that.
must "$(post "$(body $SHOP $(hex 32) $W1 '[]' '[]')")" 'wrong-pass' "a wrong pass is refused"
must "$(post "$(body $SHOP $TOK $W2 '[]' '[]')")" '"writer"' "another copy of the book is refused"
must "$(post "$(body $SHOP $TOK $W2 '[]' '[]' claim)")" '"ok":true' "a claim moves the pages to that copy"
must "$(post "$(body $SHOP $TOK $W1 '[]' '[]')")" '"writer"' "the first copy is refused once another claimed"
must "$(post "$(body $SHOP $TOK $W1 '[]' '[]' claim)")" '"ok":true' "and claims back"

# Another shop can neither replace the page nor take it away.
R=$(post "$(body $SHOP2 $TOK2 $WB "$(one $ID)" "[\"$ID\"]")")
must "$R" '"written":0' "another shop does not write the page"
must "$R" '"taken"' "and is told the page is taken"
test "$(cat "$W/site/d/$ID.txt")" = "$BLOB" || { echo "FAILED: the page is still the shop's"; exit 1; }

# The customer's browser opens it: the real view.js, in Node, with a page and a browser of its own.
cat > "$W/view.mjs" <<'M'
import fs from "node:fs";
import vm from "node:vm";
const [origin, hash, store, ua] = process.argv.slice(2);
function node(tag) {
  return { tagName: tag, className: "", _text: null, children: [], href: null,
    appendChild(c) { this.children.push(c); return c; },
    set textContent(v) { this._text = v; this.children = []; },
    get textContent() { return this._text != null ? this._text : this.children.map(c => c.textContent).join(""); } };
}
const els = { page: node("div"), foot: node("p"), app: node("div") };
const kept = fs.existsSync(store) ? JSON.parse(fs.readFileSync(store, "utf8")) : {};
const ctx = {
  document: { getElementById: i => els[i] || null, createElement: t => node(t), documentElement: {}, body: {}, title: "" },
  localStorage: { getItem: k => (k in kept ? kept[k] : null), setItem: (k, v) => { kept[k] = String(v); } },
  navigator: { userAgent: ua || "Mozilla/5.0 (Linux; Android 14)", languages: ["en"], language: "en" },
  location: { hash },
  // Node's fetch has no HTTP cache to skip; the page's own options otherwise as given.
  fetch: (u, o) => { const { cache, ...rest } = o || {}; return fetch(new URL(u, origin + "/"), rest); },
  crypto: globalThis.crypto, TextEncoder, TextDecoder, atob, Uint8Array, Promise, setTimeout,
};
ctx.window = ctx;
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(process.env.VIEW_JS, "utf8"), ctx);
await new Promise(r => setTimeout(r, 1200));
const texts = (n, out) => { if (n._text) out.push(n._text); n.children.forEach(c => texts(c, out)); return out; };
const all = (n, f, out) => { if (f(n)) out.push(n); n.children.forEach(c => all(c, f, out)); return out; };
const btn = all(els.app, n => (n.className || "").split(" ").includes("app-btn"), [])[0];
fs.writeFileSync(store, JSON.stringify(kept));
console.log(JSON.stringify({ page: texts(els.page, []), lis: all(els.page, n => n.tagName === "li", []).length, app: btn ? btn.href : null }));
M
view() { VIEW_JS="$1" node "$W/view.mjs" "$2" "$3" "$4" "${5:-}"; }   # view.js, origin, #hash, browser store
V=$(view "$W/site/view.js" "http://$U" "#$ID.$KEY" "$W/browser.json")
must "$V" 'Ali' "the browser opens the page sealed under its own id"
must "$V" '5,400' "and shows its balance"
must "$V" '"lis":0' "a page of the balance alone shows no entries"
# "Open in the app" names Roshan Khata's package: the key goes to no other app that answers roshankhata://.
must "$V" "intent://khata?l=$ID.$KEY#Intent;scheme=roshankhata;package=com.innovation313.roshankhata;" "the app button names the app's package"
must "$V" 'S.browser_fallback_url=https%3A%2F%2Fplay.google.com' "and falls back to the Play Store"
V=$(view "$W/site/view.js" "http://$U" "#$ID.$KEY" "$W/b-desk.json" "Mozilla/5.0 (Windows NT 10.0)")
must "$V" '5,400' "a computer's browser opens the page too"
never "$V" 'intent://' "with no app button off Android"

# A write that cannot be made whole keeps the page as it was, and says so.
mkdir "$W/site/d/$ID.txt.tmp"
R=$(post "$(body $SHOP $TOK $W1 "$(one $ID BBBB)" '[]')")
must "$R" '"disk"' "a failed write is refused, not answered as written"
test "$(cat "$W/site/d/$ID.txt")" = "$BLOB" || { echo "FAILED: a failed write kept the old page"; exit 1; }
rmdir "$W/site/d/$ID.txt.tmp"

# Taken back: a stub stays, no one can take the id again, not even the shop.
must "$(post "$(body $SHOP $TOK $W1 '[]' "[\"$ID\"]")")" '"removed":1' "the shop takes its page back"
test "$(cat "$W/site/d/$ID.txt")" = "!gone" || { echo "FAILED: a page taken back leaves its stub"; exit 1; }
must "$(post "$(body $SHOP $TOK $W1 "$(one $ID $BLOB)" '[]')")" '"gone"' "the shop cannot bring a taken-back id back"
must "$(post "$(body $SHOP2 $TOK2 $WB "$(one $ID)" '[]')")" '"taken"' "nor can another shop take it"
must "$(view "$W/site/view.js" "http://$U" "#$ID.$KEY" "$W/browser.json")" 'no longer shared' "the browser says a page taken back is gone"
# An id taken back before it was ever sent is kept from everyone too.
NEVER=$(hex 16)
must "$(post "$(body $SHOP $TOK $W1 '[]' "[\"$NEVER\"]")")" '"removed":1' "an id never sent can be taken back"
must "$(post "$(body $SHOP2 $TOK2 $WB "$(one $NEVER)" '[]')")" '"taken"' "and no one takes it after"

# The shop's own limit: 3 live pages here.
A=$(hex 16); B=$(hex 16); C=$(hex 16); D=$(hex 16)
R=$(post "$(body $SHOP $TOK $W1 "[{\"id\":\"$A\",\"blob\":\"AAAA\"},{\"id\":\"$B\",\"blob\":\"AAAA\"},{\"id\":\"$C\",\"blob\":\"AAAA\"},{\"id\":\"$D\",\"blob\":\"AAAA\"}]" '[]')")
must "$R" '"written":3' "three pages fit"
must "$R" '"full"' "the fourth is refused as full"

# New shops: 4 an hour here (one made so far; the shops that made no page counted for nothing).
for i in 2 3 4; do must "$(post "$(body $(hex 16) $(hex 32) $(hex 16) "$(one $(hex 16))" '[]')")" '"written":1' "new shop $i"; done
must "$(post "$(body $(hex 16) $(hex 32) $(hex 16) "$(one $(hex 16))" '[]')")" '"busy"' "a fifth new shop in the hour waits"

# Low disk: pages already there are still kept fresh; nothing new is taken.
serve 8098 site store RK_KHATA_MIN_FREE=999999999999999999
L=127.0.0.1:8098
must "$(post "$(body $SHOP $TOK $W1 "$(one $A CCCC)" '[]')" $L)" '"written":1' "a page there is updated on a low disk"
must "$(post "$(body $SHOP $TOK $W1 "$(one $(hex 16) CCCC)" '[]')" $L)" '"disk"' "a new page waits on a low disk"
must "$(post "$(body $(hex 16) $(hex 32) $(hex 16) "$(one $(hex 16))" '[]')" $L)" '"disk"' "a new shop waits on a low disk"

# The owner's switches: closed takes no new shop, halt stops everything.
touch "$W/store/closed"
must "$(post "$(body $(hex 16) $(hex 32) $(hex 16) "$(one $(hex 16))" '[]')")" '"closed"' "a closed server takes no new shop"
must "$(post "$(body $SHOP $TOK $W1 '[]' '[]')")" '"ok":true' "while the shops it has go on"
rm "$W/store/closed"; touch "$W/store/halt"
must "$(post "$(body $SHOP $TOK $W1 '[]' '[]')")" '"halted"' "halt stops every request"
rm "$W/store/halt"

# Pages not refreshed for EXPIRE_DAYS become stubs at the day's sweep; stubs as old go, their ids kept for good.
serve 8097 site store RK_KHATA_EXPIRE_DAYS=0 RK_KHATA_NEW_SHOPS_HOUR=100
E=127.0.0.1:8097
php -r '$f=$argv[1]; $g=json_decode(file_get_contents($f), true); $g["swept"]=""; file_put_contents($f, json_encode($g));' "$W/store/g.json"
sleep 1
must "$(post "$(body $SHOP $TOK $W1 '[]' '[]')" $E)" '"ok":true' "the sweep runs with a request"
test "$(cat "$W/site/d/$B.txt")" = "!gone" || { echo "FAILED: a page not refreshed was let go"; exit 1; }
test "$(code "$U/d/$(hex 16).txt")" = 404 || { echo "FAILED: an unknown page is 404"; exit 1; }
# The stubs older than EXPIRE_DAYS went, and their ids into the list of used ids — no file of their own.
test ! -e "$W/site/d/$ID.txt" || { echo "FAILED: an old stub goes at the sweep"; exit 1; }
grep -qx "$ID" "$W/store/u/${ID:0:2}.txt" || { echo "FAILED: a stub's id is written to the used ids"; exit 1; }
grep -qx "$NEVER" "$W/store/u/${NEVER:0:2}.txt" || { echo "FAILED: a kept id is written to the used ids"; exit 1; }
test "$(ls "$W/store/u" | wc -l)" -le 2 || { echo "FAILED: the used ids are kept by their first two letters, not a file each"; exit 1; }
must "$(post "$(body $(hex 16) $(hex 32) $(hex 16) "$(one $ID)" '[]')" $E)" '"taken"' "an id let go is never taken by a new shop"
must "$(post "$(body $SHOP $TOK $W1 "$(one $ID)" '[]')" $E)" '"taken"' "nor by its own shop"
test ! -e "$W/site/d/$ID.txt" || { echo "FAILED: a used id made no file again"; exit 1; }
must "$(post "$(body $SHOP $TOK $W1 '[]' "[\"$ID\"]")" $E)" '"removed":1' "taking back a used id is answered as done"
test "$(grep -c "$ID" "$W/store/u/${ID:0:2}.txt")" = 1 || { echo "FAILED: a used id is written once"; exit 1; }

# ---------------------------------------------------------------- what one shop, one address and the young may make (7 Nov 2026)

# A young shop: 3 new pages a day here (5 once older), 2 new shops a day from one address.
serve 8096 site2 store2 RK_KHATA_MAX_LINKS=100 RK_KHATA_NEW_SHOPS_HOUR=100 RK_KHATA_NEW_SHOPS_IP=2 RK_KHATA_NEW_PAGES_YOUNG=3 RK_KHATA_NEW_PAGES_DAY=5
Y=127.0.0.1:8096
S3=$(hex 16); T3=$(hex 32); W3=$(hex 16)
R=$(post "$(body $S3 $T3 $W3 "$(many 5)" '[]')" $Y)
must "$R" '"written":3' "a young shop makes its day's new pages"
test "$(grep -o '"later"' <<< "$R" | wc -l)" = 2 || { echo "FAILED: the rest wait for tomorrow"; echo "  got: $R"; exit 1; }
must "$(post "$(body $S3 $T3 $W3 "$(many 1)" '[]')" $Y)" '"later"' "and no more that day"
must "$(post "$(body $S3 $T3 $W3 '[]' "[\"$(hex 16)\"]")" $Y)" '"later"' "ids kept from others count the same"
P3=$(php -r '$s=json_decode(file_get_contents($argv[1]), true); echo array_keys($s["pages"])[0];' "$W/store2/s/$S3.json")
must "$(post "$(body $S3 $T3 $W3 "$(one $P3 DDDD)" '[]')" $Y)" '"written":1' "a page it holds is kept fresh whatever the day's count"
S4=$(hex 16); T4=$(hex 32); W4=$(hex 16)
must "$(post "$(body $S4 $T4 $W4 "$(many 1)" '[]')" $Y)" '"written":1' "a second shop from this address today"
must "$(post "$(body $(hex 16) $(hex 32) $(hex 16) "$(many 1)" '[]')" $Y)" '"busy"' "a third waits for tomorrow"
test -f "$W/store2/ip.json" && ! grep -q '127\.0\.0\.1' "$W/store2/ip.json" || { echo "FAILED: the address is kept only as a keyed hash"; exit 1; }
grep -qE '^[0-9a-f]{64}$' "$W/store2/secret" || { echo "FAILED: the server keeps its own secret for the hash"; exit 1; }
test ! -e "$W/site2/secret" && ! grep -rq "$(cat "$W/store2/secret")" "$W/site2" || { echo "FAILED: the secret is not in the site"; exit 1; }
# Older than YOUNG_DAYS: the larger day.
php -r '$f=$argv[1]; $s=json_decode(file_get_contents($f), true); $s["created"]-=40*86400; file_put_contents($f, json_encode($s));' "$W/store2/s/$S3.json"
R=$(post "$(body $S3 $T3 $W3 "$(many 3)" '[]')" $Y)
must "$R" '"written":2' "an older shop makes more new pages a day"
must "$R" '"later"' "up to its own day"

# A shop with no live page for EMPTY_DAYS goes at the sweep, its ids kept for good.
P4=$(php -r '$s=json_decode(file_get_contents($argv[1]), true); echo array_keys($s["pages"])[0];' "$W/store2/s/$S4.json")
must "$(post "$(body $S4 $T4 $W4 '[]' "[\"$P4\"]")" $Y)" '"removed":1' "the shop takes its last page back"
serve 8095 site2 store2 RK_KHATA_EMPTY_DAYS=0
Z=127.0.0.1:8095
php -r '$f=$argv[1]; $g=json_decode(file_get_contents($f), true); $g["swept"]=""; file_put_contents($f, json_encode($g));' "$W/store2/g.json"
sleep 1
must "$(post "$(body $S3 $T3 $W3 '[]' '[]')" $Z)" '"ok":true' "the sweep runs with a request"
test ! -e "$W/store2/s/$S4.json" || { echo "FAILED: a shop with no live page goes"; exit 1; }
test -e "$W/store2/s/$S3.json" || { echo "FAILED: a shop with live pages stays"; exit 1; }
test ! -e "$W/site2/d/$P4.txt" && grep -qx "$P4" "$W/store2/u/${P4:0:2}.txt" || { echo "FAILED: its ids are kept for good, with no stub"; exit 1; }
must "$(post "$(body $(hex 16) $(hex 32) $(hex 16) "$(one $P4)" '[]')" $Z)" '"taken"' "and no one takes them after"

# A fifth of the files is kept for shops older than YOUNG_DAYS: 10 files here, a young shop stops at 8.
serve 8094 site3 store3 RK_KHATA_MAX_FILES=10 RK_KHATA_MAX_LINKS=100
F=127.0.0.1:8094
S6=$(hex 16); T6=$(hex 32); W6=$(hex 16)
R=$(post "$(body $S6 $T6 $W6 "$(many 10)" '[]')" $F)
must "$R" '"written":8' "a young shop fills the server only so far"
must "$R" '"full"' "and is told it is full"
php -r '$f=$argv[1]; $s=json_decode(file_get_contents($f), true); $s["created"]-=40*86400; file_put_contents($f, json_encode($s));' "$W/store3/s/$S6.json"
R=$(post "$(body $S6 $T6 $W6 "$(many 3)" '[]')" $F)
must "$R" '"written":2' "an older shop has the fifth kept for it"
must "$R" '"full"' "up to the server's own limit"

# ---------------------------------------------------------------- the page in the browser (view.js, 7 Nov 2026)

serve 8093 site4 store3
G=127.0.0.1:8093
put_page() { printf '%s' "$2" > "$W/site4/d/$1.txt"; }   # the hosting's file, as api.php writes it
I1=$(hex 16)
K1=$(seal - x '{}' | sed -n 1p)
page() { seal "$K1" "rk-clink-v2|$I1" "$1" | sed -n 2p; }
put_page "$I1" "$(page '{"v":3,"s":"Shop","n":"Ali","p":"c","b":5400,"c":"Rs","k":2,"l":"en","t":1700000000000,"r":1,"q":200,"_":"   "}')"
must "$(view "$W/site4/view.js" "http://$G" "#$I1.$K1" "$W/b1.json")" '5,400' "a page under its own id opens"
# An older page served again (a lower counter, other figures): the newest this browser saw stays, and it is said.
put_page "$I1" "$(page '{"v":3,"s":"Shop","n":"Ali","p":"c","b":999,"c":"Rs","k":2,"l":"en","t":1700000000000,"r":1,"q":100}')"
V=$(view "$W/site4/view.js" "http://$G" "#$I1.$K1" "$W/b1.json")
must "$V" '5,400' "the newest page seen stays on screen"
never "$V" '999' "the older page's figure is not shown as current"
must "$V" 'older copy of this page' "and the browser says an older page was sent"
# The same page under a lower counter (another phone of the shop's book sent it): nothing to say.
put_page "$I1" "$(page '{"v":3,"s":"Shop","n":"Ali","p":"c","b":5400,"c":"Rs","k":2,"l":"en","t":1700000000000,"r":1,"q":150,"_":" "}')"
never "$(view "$W/site4/view.js" "http://$G" "#$I1.$K1" "$W/b1.json")" 'older copy' "the same page from another phone is not an older one"
# A newer page is taken, and becomes the one kept.
put_page "$I1" "$(page '{"v":3,"s":"Shop","n":"Ali","p":"c","b":7000,"c":"Rs","k":2,"l":"en","t":1700000000000,"r":1,"q":300}')"
V=$(view "$W/site4/view.js" "http://$G" "#$I1.$K1" "$W/b1.json")
must "$V" '7,000' "a newer page is shown"
never "$V" 'older copy' "and is not called older"
grep -q '"q\\":300' "$W/b1.json" || { echo "FAILED: the browser keeps the newest counter"; cat "$W/b1.json"; exit 1; }
# A fresh browser has nothing to compare with: it shows what it is given (the review's own limit, said in the README).
must "$(view "$W/site4/view.js" "http://$G" "#$I1.$K1" "$W/b2.json")" '7,000' "a fresh browser opens the page"
# The right key under another link's id does not open: a page moved from one link to another is wrong.
I2=$(hex 16)
put_page "$I2" "$(seal "$K1" "rk-clink-v2|$I1" '{"v":3,"n":"Moved","b":1,"q":1}' | sed -n 2p)"
must "$(view "$W/site4/view.js" "http://$G" "#$I2.$K1" "$W/b3.json")" 'does not open this page' "a page sealed for another link does not open"
# A page sealed before 7 Nov 2026 (no id in its seal) still opens.
I3=$(hex 16)
put_page "$I3" "$(seal "$K1" "rk-clink-v1" '{"v":2,"s":"Shop","n":"Old Page","p":"c","b":42,"c":"Rs","k":2,"l":"en","t":1,"r":1}' | sed -n 2p)"
must "$(view "$W/site4/view.js" "http://$G" "#$I3.$K1" "$W/b4.json")" 'Old Page' "a page sealed before still opens"
# No more is shown than the shop's app writes: names of 60, goods of 40, three entries, a day between 2000 and 2100.
I4=$(hex 16); LONG=$(printf 'x%.0s' $(seq 1 80))
LINES=$(for i in 1 2 3 4 5; do printf '{"d":1700000000000,"a":%s,"g":1,"m":1,"x":"%s"},' "$i" "$LONG"; done)
put_page "$I4" "$(seal "$K1" "rk-clink-v2|$I4" "{\"v\":3,\"s\":\"$LONG\",\"n\":\"$LONG\",\"p\":\"c\",\"b\":10,\"c\":\"RsRsRsRs\",\"k\":2,\"l\":\"en\",\"t\":99999999999999999,\"r\":1,\"q\":1,\"e\":[${LINES%,}]}" | sed -n 2p)"
V=$(view "$W/site4/view.js" "http://$G" "#$I4.$K1" "$W/b5.json")
must "$V" "\"$(printf 'x%.0s' $(seq 1 60))\"" "a name is cut to 60"
never "$V" "$(printf 'x%.0s' $(seq 1 61))" "nothing longer than the app writes is shown"
must "$V" "\"$(printf 'x%.0s' $(seq 1 40))\"" "goods are cut to 40"
must "$V" '"lis":3' "three entries at most"
must "$V" 'RsRsR 10' "a currency sign of 5"
never "$V" 'Updated' "a day past 2100 is no day"

if grep -qi "warning\|fatal\|notice" "$W"/php-*.log; then echo "FAILED: PHP printed a warning"; cat "$W"/php-*.log; exit 1; fi
echo "customer page server: all checks passed"

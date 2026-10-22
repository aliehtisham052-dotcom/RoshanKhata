#!/usr/bin/env bash
# The customer page's server (web/khata) on PHP's own server, as the review of 22 Oct 2026 asked:
# a page sealed the app's way (Java's AES-GCM) is stored whole and opened the browser's way (WebCrypto,
# in Node); only the app's JSON is taken; a wrong pass, another shop and another copy of the book are
# refused; a page taken back stays taken; the limits hold; a write that fails keeps the old page; the
# store is outside the site; old pages are let go. Run from the repository root.
set -euo pipefail
W=$(mktemp -d); PIDS=()
trap 'for p in "${PIDS[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null; done; rm -rf "$W"' EXIT
mkdir -p "$W/site" "$W/store" && cp -r web/khata/. "$W/site/"
php -l "$W/site/api.php"
node --check "$W/site/view.js"

cat > "$W/Seal.java" <<'J'
import javax.crypto.*; import javax.crypto.spec.*; import java.security.*; import java.util.*;
public class Seal { public static void main(String[] a) throws Exception {
  SecureRandom r = new SecureRandom(); byte[] k = new byte[16]; r.nextBytes(k); byte[] n = new byte[12]; r.nextBytes(n);
  Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(128, n));
  c.updateAAD("rk-clink-v1".getBytes("US-ASCII"));
  byte[] ct = c.doFinal("{\"v\":2,\"s\":\"Shop\",\"n\":\"Ali\",\"p\":\"c\",\"b\":5400,\"c\":\"Rs\",\"k\":2,\"l\":\"ur\",\"t\":1,\"r\":1}".getBytes("UTF-8"));
  byte[] all = new byte[12 + ct.length];
  System.arraycopy(n, 0, all, 0, 12); System.arraycopy(ct, 0, all, 12, ct.length);
  Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
  System.out.println(e.encodeToString(k)); System.out.println(e.encodeToString(all)); } }
J
(cd "$W" && javac Seal.java)
OUT=$(cd "$W" && java Seal 2>/dev/null); KEY=$(echo "$OUT" | sed -n 1p); BLOB=$(echo "$OUT" | sed -n 2p)

# Small limits, so each can be met: 3 live pages a shop, 4 new shops an hour.
serve() { # port, extra env...
  local port=$1; shift
  env RK_KHATA_DATA="$W/store" RK_KHATA_MAX_LINKS=3 RK_KHATA_NEW_SHOPS_HOUR=4 "$@" php -S 127.0.0.1:$port -t "$W/site" > "$W/php-$port.log" 2>&1 &
  PIDS+=($!)
  for i in $(seq 1 30); do curl -s -o /dev/null 127.0.0.1:$port/index.html && return 0; sleep 0.2; done
  echo "php did not start on $port"; exit 1
}
serve 8099
U=127.0.0.1:8099
post() { curl -s -X POST -H 'Content-Type: application/json' --data-binary "$1" "${2:-$U}/api.php"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
must() { if ! grep -q -- "$2" <<< "$1"; then echo "FAILED: $3"; echo "  got: $1"; exit 1; fi; }
hex() { openssl rand -hex "$1"; }
body() { # shop token writer put-json drop-json [claim]
  echo "{\"shop\":\"$1\",\"token\":\"$2\",\"writer\":\"$3\",\"put\":$4,\"drop\":$5${6:+,\"claim\":true}}"
}

ID=$(hex 16); SHOP=$(hex 16); TOK=$(hex 32); W1=$(hex 16); W2=$(hex 16)
SHOP2=$(hex 16); TOK2=$(hex 32); WB=$(hex 16)

# Only the app's own requests.
test "$(code "$U/api.php")" = 405 || { echo "FAILED: a GET is refused"; exit 1; }
test "$(code -X POST --data-binary "$(body $SHOP $TOK $W1 '[]' '[]')" "$U/api.php")" = 415 || { echo "FAILED: a form post is refused"; exit 1; }
test "$(code -X POST -H 'Content-Type: text/plain' --data-binary "$(body $SHOP $TOK $W1 '[]' '[]')" "$U/api.php")" = 415 || { echo "FAILED: text/plain is refused"; exit 1; }
must "$(post '[1,2]')" '"bad-json"' "a list is not a request"

# The first page, stored whole: the file holds only the sealed page, no mark of its shop.
must "$(post "$(body $SHOP $TOK $W1 "[{\"id\":\"$ID\",\"blob\":\"$BLOB\"}]" '[]')")" '"written":1' "the first page is written"
test "$(cat "$W/site/d/$ID.txt")" = "$BLOB" || { echo "FAILED: the page file is the sealed page alone"; exit 1; }
if grep -rq "$(printf '%s' "$TOK" | sha256sum | cut -c1-64)" "$W/site"; then echo "FAILED: a pass is inside the site"; exit 1; fi

# A wrong pass; another copy of the book (another writer) until it claims; the first copy refused after that.
must "$(post "$(body $SHOP $(hex 32) $W1 '[]' '[]')")" 'wrong-pass' "a wrong pass is refused"
must "$(post "$(body $SHOP $TOK $W2 '[]' '[]')")" '"writer"' "another copy of the book is refused"
must "$(post "$(body $SHOP $TOK $W2 '[]' '[]' claim)")" '"ok":true' "a claim moves the pages to that copy"
must "$(post "$(body $SHOP $TOK $W1 '[]' '[]')")" '"writer"' "the first copy is refused once another claimed"
must "$(post "$(body $SHOP $TOK $W1 '[]' '[]' claim)")" '"ok":true' "and claims back"

# Another shop can neither replace the page nor take it away.
R=$(post "$(body $SHOP2 $TOK2 $WB "[{\"id\":\"$ID\",\"blob\":\"AAAA\"}]" "[\"$ID\"]")")
must "$R" '"written":0' "another shop does not write the page"
must "$R" '"taken"' "and is told the page is taken"
test "$(cat "$W/site/d/$ID.txt")" = "$BLOB" || { echo "FAILED: the page is still the shop's"; exit 1; }

# The customer's browser opens it.
cat > "$W/open.mjs" <<M
const t = (await (await fetch("http://$U/d/$ID.txt")).text()).trim();
const u = s => { s = s.replace(/-/g, "+").replace(/_/g, "/"); while (s.length % 4) s += "="; return Uint8Array.from(atob(s), c => c.charCodeAt(0)); };
const b = u(t);
const k = await crypto.subtle.importKey("raw", u("$KEY"), "AES-GCM", false, ["decrypt"]);
const p = await crypto.subtle.decrypt({ name: "AES-GCM", iv: b.slice(0, 12), additionalData: new TextEncoder().encode("rk-clink-v1"), tagLength: 128 }, k, b.slice(12));
const text = new TextDecoder().decode(p); if (!text.includes('"Ali"')) { console.error(text); process.exit(1); } console.log("opened:", text);
M
node "$W/open.mjs"

# A write that cannot be made whole keeps the page as it was, and says so.
mkdir "$W/site/d/$ID.txt.tmp"
R=$(post "$(body $SHOP $TOK $W1 "[{\"id\":\"$ID\",\"blob\":\"BBBB\"}]" '[]')")
must "$R" '"disk"' "a failed write is refused, not answered as written"
test "$(cat "$W/site/d/$ID.txt")" = "$BLOB" || { echo "FAILED: a failed write kept the old page"; exit 1; }
rmdir "$W/site/d/$ID.txt.tmp"

# Taken back: a stub stays, no one can take the id again, not even the shop.
must "$(post "$(body $SHOP $TOK $W1 '[]' "[\"$ID\"]")")" '"removed":1' "the shop takes its page back"
test "$(cat "$W/site/d/$ID.txt")" = "!gone" || { echo "FAILED: a page taken back leaves its stub"; exit 1; }
must "$(post "$(body $SHOP $TOK $W1 "[{\"id\":\"$ID\",\"blob\":\"$BLOB\"}]" '[]')")" '"gone"' "the shop cannot bring a taken-back id back"
must "$(post "$(body $SHOP2 $TOK2 $WB "[{\"id\":\"$ID\",\"blob\":\"AAAA\"}]" '[]')")" '"taken"' "nor can another shop take it"
# An id taken back before it was ever sent is kept from everyone too.
NEVER=$(hex 16)
must "$(post "$(body $SHOP $TOK $W1 '[]' "[\"$NEVER\"]")")" '"removed":1' "an id never sent can be taken back"
must "$(post "$(body $SHOP2 $TOK2 $WB "[{\"id\":\"$NEVER\",\"blob\":\"AAAA\"}]" '[]')")" '"taken"' "and no one takes it after"

# The shop's own limit: 3 live pages here.
A=$(hex 16); B=$(hex 16); C=$(hex 16); D=$(hex 16)
R=$(post "$(body $SHOP $TOK $W1 "[{\"id\":\"$A\",\"blob\":\"AAAA\"},{\"id\":\"$B\",\"blob\":\"AAAA\"},{\"id\":\"$C\",\"blob\":\"AAAA\"},{\"id\":\"$D\",\"blob\":\"AAAA\"}]" '[]')")
must "$R" '"written":3' "three pages fit"
must "$R" '"full"' "the fourth is refused as full"

# New shops: 4 an hour here (two made so far).
for i in 3 4; do must "$(post "$(body $(hex 16) $(hex 32) $(hex 16) '[]' '[]')")" '"ok":true' "new shop $i"; done
must "$(post "$(body $(hex 16) $(hex 32) $(hex 16) '[]' '[]')")" '"busy"' "a fifth new shop in the hour waits"

# Low disk: pages already there are still kept fresh; nothing new is taken.
serve 8098 RK_KHATA_MIN_FREE=999999999999999999
L=127.0.0.1:8098
must "$(post "$(body $SHOP $TOK $W1 "[{\"id\":\"$A\",\"blob\":\"CCCC\"}]" '[]')" $L)" '"written":1' "a page there is updated on a low disk"
must "$(post "$(body $SHOP $TOK $W1 "[{\"id\":\"$(hex 16)\",\"blob\":\"CCCC\"}]" '[]')" $L)" '"disk"' "a new page waits on a low disk"
must "$(post "$(body $(hex 16) $(hex 32) $(hex 16) '[]' '[]')" $L)" '"disk"' "a new shop waits on a low disk"

# The owner's switches: closed takes no new shop, halt stops everything.
touch "$W/store/closed"
must "$(post "$(body $(hex 16) $(hex 32) $(hex 16) '[]' '[]')")" '"closed"' "a closed server takes no new shop"
must "$(post "$(body $SHOP $TOK $W1 '[]' '[]')")" '"ok":true' "while the shops it has go on"
rm "$W/store/closed"; touch "$W/store/halt"
must "$(post "$(body $SHOP $TOK $W1 '[]' '[]')")" '"halted"' "halt stops every request"
rm "$W/store/halt"

# Pages not refreshed for EXPIRE_DAYS become stubs at the day's sweep.
serve 8097 RK_KHATA_EXPIRE_DAYS=0
E=127.0.0.1:8097
php -r '$f=$argv[1]; $g=json_decode(file_get_contents($f), true); $g["swept"]=""; file_put_contents($f, json_encode($g));' "$W/store/g.json"
sleep 1
must "$(post "$(body $SHOP $TOK $W1 '[]' '[]')" $E)" '"ok":true' "the sweep runs with a request"
test "$(cat "$W/site/d/$B.txt")" = "!gone" || { echo "FAILED: a page not refreshed was let go"; exit 1; }
test "$(code "$U/d/$(hex 16).txt")" = 404 || { echo "FAILED: an unknown page is 404"; exit 1; }

if grep -qi "warning\|fatal\|notice" "$W"/php-*.log; then echo "FAILED: PHP printed a warning"; cat "$W"/php-*.log; exit 1; fi
echo "customer page server: all checks passed"

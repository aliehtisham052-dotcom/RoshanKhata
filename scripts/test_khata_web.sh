#!/usr/bin/env bash
# The customer page's server (web/khata) on PHP's own server: a page sealed the app's way (Java's AES-GCM)
# is stored, refused to a wrong pass and to another shop, opened the browser's way (WebCrypto, in Node),
# and removed. Run from the repository root.
set -euo pipefail
W=$(mktemp -d); PHP_PID=""; trap '[ -n "$PHP_PID" ] && kill "$PHP_PID" 2>/dev/null; rm -rf "$W"' EXIT
mkdir -p "$W/site" && cp -r web/khata/. "$W/site/"
php -l "$W/site/api.php"
cat > "$W/Seal.java" <<'J'
import javax.crypto.*; import javax.crypto.spec.*; import java.security.*; import java.util.*;
public class Seal { public static void main(String[] a) throws Exception {
  SecureRandom r = new SecureRandom(); byte[] k = new byte[16]; r.nextBytes(k); byte[] n = new byte[12]; r.nextBytes(n);
  Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(128, n));
  c.updateAAD("rk-clink-v1".getBytes("US-ASCII"));
  byte[] ct = c.doFinal("{\"n\":\"Ali\",\"b\":5400}".getBytes("UTF-8")); byte[] all = new byte[12 + ct.length];
  System.arraycopy(n, 0, all, 0, 12); System.arraycopy(ct, 0, all, 12, ct.length);
  Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
  System.out.println(e.encodeToString(k)); System.out.println(e.encodeToString(all)); } }
J
(cd "$W" && javac Seal.java)
OUT=$(cd "$W" && java Seal 2>/dev/null); KEY=$(echo "$OUT" | sed -n 1p); BLOB=$(echo "$OUT" | sed -n 2p)
php -S 127.0.0.1:8099 -t "$W/site" > "$W/php.log" 2>&1 &
PHP_PID=$!
for i in $(seq 1 20); do curl -s -o /dev/null 127.0.0.1:8099/index.html && break; sleep 0.3; done
ID=$(openssl rand -hex 16); SHOP=$(openssl rand -hex 16); TOK=$(openssl rand -hex 32); SHOP2=$(openssl rand -hex 16)
post() { curl -s -X POST 127.0.0.1:8099/api.php -d "$1"; }
post "{\"shop\":\"$SHOP\",\"token\":\"$TOK\",\"put\":[{\"id\":\"$ID\",\"blob\":\"$BLOB\"}],\"drop\":[]}" | grep -q '"written":1'
post "{\"shop\":\"$SHOP\",\"token\":\"$(openssl rand -hex 32)\",\"put\":[],\"drop\":[]}" | grep -q wrong-pass
post "{\"shop\":\"$SHOP2\",\"token\":\"$TOK\",\"put\":[{\"id\":\"$ID\",\"blob\":\"AAAA\"}],\"drop\":[\"$ID\"]}" | grep -q '"written":0'
cat > "$W/open.mjs" <<M
const t = await (await fetch("http://127.0.0.1:8099/d/$ID.txt")).text();
const u = s => { s = s.replace(/-/g, "+").replace(/_/g, "/"); while (s.length % 4) s += "="; return Uint8Array.from(atob(s), c => c.charCodeAt(0)); };
const b = u(t.slice(t.indexOf("\n") + 1).trim());
const k = await crypto.subtle.importKey("raw", u("$KEY"), "AES-GCM", false, ["decrypt"]);
const p = await crypto.subtle.decrypt({ name: "AES-GCM", iv: b.slice(0, 12), additionalData: new TextEncoder().encode("rk-clink-v1"), tagLength: 128 }, k, b.slice(12));
const text = new TextDecoder().decode(p); if (!text.includes('"Ali"')) { console.error(text); process.exit(1); } console.log("opened:", text);
M
node "$W/open.mjs"
post "{\"shop\":\"$SHOP\",\"token\":\"$TOK\",\"put\":[],\"drop\":[\"$ID\"]}" | grep -q '"removed":1'
test "$(curl -s -o /dev/null -w '%{http_code}' 127.0.0.1:8099/d/$ID.txt)" = 404
echo "customer page server: all checks passed"

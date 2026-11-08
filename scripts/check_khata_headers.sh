#!/usr/bin/env bash
# After uploading web/khata (L47/L48, 8 Nov 2026): the LIVE host must send the headers web/khata/.htaccess asks for.
# Local php -S ignores .htaccess, so test_khata_web.sh cannot see a wrong upload; this checks the real thing.
# Usage: scripts/check_khata_headers.sh [https://khata.ehtisham.shop]
set -u
BASE=${1:-https://khata.ehtisham.shop}
H=$(curl -sSI "$BASE/") || { echo "FAILED: $BASE did not answer"; exit 1; }
bad=0
want() { # header-name  pattern
  if ! grep -qi "^$1:.*$2" <<< "$H"; then echo "MISSING: $1 ($2)"; bad=1; fi
}
want x-content-type-options nosniff
want x-frame-options DENY
want referrer-policy no-referrer
want content-security-policy "frame-ancestors 'none'"
want strict-transport-security "max-age=31536000"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/data/")
[ "$code" = 403 ] || [ "$code" = 404 ] || { echo "FAILED: $BASE/data/ answered $code, not 403/404"; bad=1; }
[ $bad = 0 ] && echo "headers ok on $BASE"
exit $bad

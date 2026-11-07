#!/usr/bin/env bash
# The customer page's files for the hosting, in one zip (web/khata/README.md says where they go):
# every file the page needs, hidden ones included, nothing else (no README, no test data).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
out="${1:-$here/khata-upload.zip}"
cd "$here/web/khata"
rm -f "$out"
zip -q -X "$out" index.html view.js api.php .htaccess d/.htaccess data/.htaccess .well-known/.htaccess .well-known/assetlinks.json
unzip -l "$out"

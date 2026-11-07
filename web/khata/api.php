<?php
/*
 * Roshan Khata — a customer's own page, the shop's side (22 Oct 2026;
 * hardened the same day after the review, and again on 7 Nov 2026).
 *
 * The app sends, every few minutes and only when something changed, the
 * sealed pages of the customers the shop gave a link to. Each page is sealed
 * on the phone with a key that is only in the customer's link (after the #,
 * which no browser sends to a server): this server stores bytes it cannot
 * read, as ready files the customer's browser fetches and opens itself.
 *
 * Checks and balances:
 *  - A shop is known by its id and its pass (the SHA-256 of its token, set by
 *    the shop's first request that makes a page). A page belongs to the shop
 *    that first wrote it; which pages are whose is kept here, never in the
 *    page itself.
 *  - A page taken back leaves a stub ("!gone") that no shop can take again;
 *    pages not refreshed for EXPIRE_DAYS go the same way (the app refreshes
 *    every page monthly). An id is never free again (7 Nov 2026): a stub
 *    let go after EXPIRE_DAYS more, or with its shop, is written first to the
 *    list of used ids (u/), so a link that once showed a balance can never
 *    show another shop's page.
 *  - One copy of the book writes a shop's pages (its writer); another copy
 *    is refused until it claims them (the app asks the owner first).
 *  - No one can use up what everyone needs (7 Nov 2026): a shop makes at most
 *    NEW_PAGES_YOUNG new pages a UTC day while it is younger than YOUNG_DAYS,
 *    NEW_PAGES_DAY after; a fifth of the page files is kept for shops older
 *    than YOUNG_DAYS; new shops come at most NEW_SHOPS_IP a day from one
 *    address (kept only as a keyed hash) and NEW_SHOPS_HOUR an hour in all;
 *    a request that makes no page leaves no shop behind, and a shop with no
 *    live page for EMPTY_DAYS goes.
 *  - Limits per shop (pages, requests a day) and for the whole server (shops,
 *    files, free disk). Nothing is written unless it can be written whole: a
 *    failed write keeps the old page and answers an error.
 *  - Only the app's own requests: JSON with its content type (so a web page
 *    elsewhere cannot make a visitor's browser post here), at most MAX_BODY.
 *  - One request at a time (one lock for the whole store): small shops,
 *    small requests, and no two requests can ever take the same new page.
 *  - No file per id beyond the pages themselves (the hosting counts files).
 *
 * The owner's switches, as empty files: data/halt (or <store>/halt) stops
 * every request; <store>/closed refuses new shops while the others go on.
 */

ini_set('display_errors', '0');
ini_set('log_errors', '1');
error_reporting(E_ALL);

/** A limit, from the environment where the server sets one (the tests do), else its default. */
function limit($name, $default) {
    $v = getenv('RK_KHATA_' . $name);
    return ($v !== false && ctype_digit($v)) ? (int)$v : $default;
}

define('MAX_ITEMS', 200);                                   // pages put or dropped in one request
define('MAX_BLOB', 4096);                                   // characters of one sealed page
define('MAX_BODY', 1048576);                                // bytes of one request
define('MAX_LINKS', limit('MAX_LINKS', 5000));              // live pages of one shop
define('MAX_CALLS', limit('MAX_CALLS', 1500));              // requests of one shop in a UTC day
define('MAX_SHOPS', limit('MAX_SHOPS', 2000));              // shops on this server
define('MAX_FILES', limit('MAX_FILES', 100000));            // page files on this server, stubs too
define('YOUNG_FILES', MAX_FILES - intdiv(MAX_FILES, 5));    // what shops younger than YOUNG_DAYS may fill
define('NEW_SHOPS_HOUR', limit('NEW_SHOPS_HOUR', 60));      // new shops in one hour, all together
define('NEW_SHOPS_IP', limit('NEW_SHOPS_IP', 10));          // new shops from one address (an IPv6 /64) in a UTC day
define('YOUNG_DAYS', limit('YOUNG_DAYS', 30));              // a shop younger than this is young
define('NEW_PAGES_YOUNG', limit('NEW_PAGES_YOUNG', 50));    // new pages (and kept ids) of a young shop in a UTC day
define('NEW_PAGES_DAY', limit('NEW_PAGES_DAY', 500));       // ... and of an older one
define('EMPTY_DAYS', limit('EMPTY_DAYS', 7));               // a shop with no live page this long goes
define('MIN_FREE', limit('MIN_FREE', 200 * 1048576));       // free bytes kept: below them nothing new is written
define('EXPIRE_DAYS', limit('EXPIRE_DAYS', 180));           // a page not refreshed this long goes
define('GONE_MARK', '!gone');                               // what a taken-back page's file holds

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');

function answer($code, $body) {
    http_response_code($code);
    echo json_encode($body);
    exit;
}
function fail($code, $why) { answer($code, ['ok' => false, 'why' => $why]); }

/**
 * Where the shops' passes and page lists live: beside the web root, never
 * served, when the hosting lets us make a folder there; else data/ here,
 * which .htaccess denies.
 */
function store_dir() {
    $env = getenv('RK_KHATA_DATA');
    if ($env !== false && $env !== '') return rtrim($env, '/');
    $root = isset($_SERVER['DOCUMENT_ROOT']) ? realpath($_SERVER['DOCUMENT_ROOT']) : false;
    if ($root) {
        // Beside the hosting's web root, never inside it (7 Nov 2026): a subdomain's own root is a folder in
        // public_html (…/public_html/khata), whose parent is still served — so the folder goes beside public_html.
        $web = $root;
        $at = strpos($root, '/public_html');
        if ($at !== false) $web = substr($root, 0, $at) . '/public_html';
        $outside = dirname($web) . '/rk-khata-data';
        if (is_dir($outside) || @mkdir($outside, 0700, true)) {
            // Never served from there, and denied as well should a hosting ever serve it.
            if (!file_exists("$outside/.htaccess")) @file_put_contents("$outside/.htaccess", "Require all denied\n");
            return $outside;
        }
    }
    return __DIR__ . '/data';
}

function read_json($path) {
    if (!file_exists($path)) return null;
    $raw = @file_get_contents($path);
    if ($raw === false) return false;
    $v = json_decode($raw, true);
    return is_array($v) ? $v : false;
}

/** [text] written whole to [path] or not at all: a temporary file of the right length, then a rename. */
function write_whole($path, $text) {
    $tmp = $path . '.tmp';
    $n = @file_put_contents($tmp, $text);
    if ($n !== strlen($text)) { @unlink($tmp); return false; }
    if (!@rename($tmp, $path)) { @unlink($tmp); return false; }
    return true;
}

/** A new page: created only if no file has that name yet (never over another's), whole or not at all. */
function create_whole($path, $text) {
    $fh = @fopen($path, 'x');
    if (!$fh) return false;
    $n = @fwrite($fh, $text);
    $ok = ($n === strlen($text)) && @fflush($fh);
    @fclose($fh);
    if (!$ok) { @unlink($path); return false; }
    return true;
}

function is_id($v) { return is_string($v) && preg_match('/^[0-9a-f]{32}$/', $v) === 1; }

// ---------------------------------------------------------------- ids never free again (7 Nov 2026)

/**
 * Every page id whose file is gone, one per line, in at most 256 files by the id's first two letters (no file per
 * id: the hosting counts files). Live pages and stubs keep their ids by their own files; an id is written here
 * before its stub goes, and is never taken again by anyone, its own shop included.
 */
$USED = [];

function used_path($store, $id) { return "$store/u/" . substr($id, 0, 2) . '.txt'; }

/** One file of used ids as text, a line break before the first; null when it is there but cannot be read. */
function used_text($f) {
    global $USED;
    if (!array_key_exists($f, $USED)) {
        clearstatcache(true, $f);
        if (!file_exists($f)) $USED[$f] = "\n";
        else { $raw = @file_get_contents($f); $USED[$f] = ($raw === false) ? null : "\n" . $raw; }
    }
    return $USED[$f];
}

/** True when [id] was let go before. A list that cannot be read counts as holding it: never taken as free. */
function is_used($store, $id) {
    $t = used_text(used_path($store, $id));
    return $t === null || strpos($t, "\n$id\n") !== false;
}

/** [id] written to the list of used ids (or found there already); false when it could not be. */
function mark_used($store, $id) {
    global $USED;
    if (!is_dir("$store/u") && !@mkdir("$store/u", 0700, true)) return false;
    $f = used_path($store, $id);
    $t = used_text($f);
    if ($t === null) return false;
    if (strpos($t, "\n$id\n") !== false) return true;
    // A line cut short by a full disk stays apart from this one.
    $line = (substr($t, -1) === "\n" ? '' : "\n") . $id . "\n";
    $n = @file_put_contents($f, $line, FILE_APPEND);
    if ($n !== strlen($line)) { unset($USED[$f]); return false; }
    $USED[$f] = $t . $line;
    return true;
}

// ---------------------------------------------------------------- who asks for a new shop (7 Nov 2026)

/** This server's own secret for hashing addresses: made once, kept in the store, never served. */
function server_secret($store) {
    $f = "$store/secret";
    $v = @file_get_contents($f);
    if (is_string($v) && preg_match('/^[0-9a-f]{64}$/', $v)) return $v;
    $v = bin2hex(random_bytes(32));
    return write_whole($f, $v) ? $v : null;
}

/**
 * The asking address as a short keyed hash: the address itself is never kept, and the hash means nothing without
 * this server's secret. An IPv6 address counts by its /64 (one phone's or one home's whole network); an IPv4 one
 * whole, as a carrier's shared address is (NEW_SHOPS_IP leaves room for the many phones behind it).
 */
function address_key($store) {
    $raw = @inet_pton($_SERVER['REMOTE_ADDR'] ?? '');
    if (!is_string($raw)) $raw = '';
    if (strlen($raw) === 16) {
        $raw = (substr($raw, 0, 12) === "\0\0\0\0\0\0\0\0\0\0\xff\xff") ? substr($raw, 12) : substr($raw, 0, 8);
    }
    $secret = server_secret($store);
    return $secret === null ? null : substr(hash_hmac('sha256', $raw, $secret), 0, 16);
}

/** New shops made today, by address key; a new UTC day starts empty. */
function address_counts($store, $today) {
    $c = read_json("$store/ip.json");
    if (!is_array($c) || ($c['day'] ?? '') !== $today || !is_array($c['n'] ?? null)) $c = ['day' => $today, 'n' => []];
    return $c;
}

// ---------------------------------------------------------------- the daily sweep

/** A shop's record says when it last had no live page ('empty'), or nothing while it has one. */
function note_empty(&$s) {
    if ($s['pages']) unset($s['empty']);
    elseif (!isset($s['empty'])) $s['empty'] = time();
}

/**
 * Once a UTC day: pages not refreshed for EXPIRE_DAYS become stubs; stubs older than that, and a shop with no live
 * page for EMPTY_DAYS with all its stubs, go — each id written to the list of used ids first; the server's counts
 * are taken again from what is there.
 */
function sweep($store, $pages, &$g) {
    $cut = time() - EXPIRE_DAYS * 86400;
    $emptyCut = time() - EMPTY_DAYS * 86400;
    $shops = 0; $files = 0;
    foreach (glob("$store/s/*.json") ?: [] as $f) {
        $s = read_json($f);
        if (!is_array($s)) { $shops++; continue; }
        $changed = false;
        $had = (bool)$s['pages'];
        foreach ($s['pages'] as $id => $at) {
            if ($at < $cut && write_whole("$pages/$id.txt", GONE_MARK)) {
                unset($s['pages'][$id]); $s['gone'][$id] = time(); $changed = true;
            }
        }
        if (!$s['pages'] && !isset($s['empty'])) {
            // Empty from now when its last pages just went; a record from before 7 Nov 2026 with none, since its last request.
            $s['empty'] = $had ? time() : ($s['last'] ?? $s['created']); $changed = true;
        }
        $leaving = !$s['pages'] && $s['empty'] < $emptyCut;
        foreach ($s['gone'] as $id => $at) {
            if ($leaving || $at < $cut) {
                if (!mark_used($store, $id)) continue;
                if (file_exists("$pages/$id.txt") && !@unlink("$pages/$id.txt")) continue;
                unset($s['gone'][$id]); $changed = true;
            }
        }
        if ($leaving && !$s['gone'] && @unlink($f)) continue;
        if ($changed) write_whole($f, json_encode($s));
        $shops++;
        $files += count($s['pages']) + count($s['gone']);
    }
    $g['shops'] = $shops;
    $g['files'] = $files;
}

// ---------------------------------------------------------------- the request

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') fail(405, 'method');
$type = $_SERVER['CONTENT_TYPE'] ?? ($_SERVER['HTTP_CONTENT_TYPE'] ?? '');
if (stripos(trim($type), 'application/json') !== 0) fail(415, 'type');

$store = store_dir();
if (file_exists(__DIR__ . '/data/halt') || file_exists("$store/halt")) fail(503, 'halted');

$raw = file_get_contents('php://input', false, null, 0, MAX_BODY + 1);
if ($raw === false || strlen($raw) > MAX_BODY) fail(413, 'too-big');
$in = json_decode($raw, true);
if (!is_array($in) || ($in !== [] && array_keys($in) === range(0, count($in) - 1))) fail(400, 'bad-json');

$shop   = $in['shop'] ?? '';
$token  = $in['token'] ?? '';
$writer = $in['writer'] ?? '';
$claim  = ($in['claim'] ?? false) === true;
$put    = $in['put'] ?? [];
$drop   = $in['drop'] ?? [];
if (!is_id($shop)) fail(400, 'shop');
if (!is_string($token) || !preg_match('/^[0-9a-f]{64}$/', $token)) fail(401, 'token');
if (!is_id($writer)) fail(400, 'writer-id');
if (!is_array($put) || !is_array($drop) || count($put) + count($drop) > MAX_ITEMS) fail(413, 'too-many');

foreach (["$store/s"] as $d) if (!is_dir($d) && !@mkdir($d, 0700, true)) fail(500, 'store');
if (!file_exists("$store/.htaccess")) @file_put_contents("$store/.htaccess", "Require all denied\n");
$lock = @fopen("$store/lock", 'c');
if (!$lock || !flock($lock, LOCK_EX)) fail(500, 'lock');

$pages = __DIR__ . '/d';
$g = read_json("$store/g.json");
if (!is_array($g)) $g = ['shops' => 0, 'files' => 0, 'hour' => '', 'fresh' => 0, 'swept' => ''];
$today = gmdate('Y-m-d');
$hour  = gmdate('Y-m-d\TH');
if ($g['swept'] !== $today) {
    // Kept at once: a stream of refused requests must not each walk the whole store again.
    sweep($store, $pages, $g); $g['swept'] = $today;
    write_whole("$store/g.json", json_encode($g));
}
$free = @disk_free_space($store);
$low  = ($free !== false && $free < MIN_FREE);

$sfile = "$store/s/$shop.json";
$s = read_json($sfile);
if ($s === false) fail(500, 'record');               // there but unreadable: never taken as a new shop
$pass = hash('sha256', $token);
$isNew = ($s === null);
$from = null;                                        // the asking address's key, for a new shop
$counts = null;
if ($isNew) {
    if (file_exists("$store/closed")) fail(403, 'closed');
    if ($low) fail(507, 'disk');
    if ($g['shops'] >= MAX_SHOPS) fail(503, 'full');
    if ($g['hour'] !== $hour) { $g['hour'] = $hour; $g['fresh'] = 0; }
    if ($g['fresh'] >= NEW_SHOPS_HOUR) fail(429, 'busy');
    $from = address_key($store);
    if ($from === null) fail(500, 'store');
    $counts = address_counts($store, $today);
    if (($counts['n'][$from] ?? 0) >= NEW_SHOPS_IP) fail(429, 'busy');
    $s = ['pass' => $pass, 'writer' => $writer, 'created' => time(), 'last' => time(), 'day' => '', 'calls' => 0, 'made' => 0, 'pages' => [], 'gone' => []];
} elseif (!hash_equals($s['pass'], $pass)) {
    fail(403, 'wrong-pass');
}
if (!hash_equals($s['writer'], $writer)) {
    if (!$claim) fail(409, 'writer');
    $s['writer'] = $writer;                            // this copy of the book writes the pages from now on
}
if ($s['day'] !== $today) { $s['day'] = $today; $s['calls'] = 0; $s['made'] = 0; }
if ($s['calls'] >= MAX_CALLS) fail(429, 'day-limit');
$s['calls']++;
$s['last'] = time();

// What this shop may still make today, and how full it may fill the server: a young shop gets less of both.
$young  = (time() - $s['created']) < YOUNG_DAYS * 86400;
$budget = max(0, ($young ? NEW_PAGES_YOUNG : NEW_PAGES_DAY) - ($s['made'] ?? 0));
$cap    = $young ? YOUNG_FILES : MAX_FILES;

// What is asked, checked before anything is written.
$refused = [];
$writes  = [];                                         // id => blob, for pages this shop holds
$creates = [];                                         // id => blob, for new pages
$seen    = [];
foreach ($put as $p) {
    if (!is_array($p)) { $refused[] = ['id' => '', 'why' => 'bad']; continue; }
    $id = $p['id'] ?? ''; $blob = $p['blob'] ?? '';
    if (!is_id($id)) { $refused[] = ['id' => '', 'why' => 'bad']; continue; }
    if (isset($seen[$id])) continue;
    $seen[$id] = true;
    if (!is_string($blob) || $blob === '' || strlen($blob) > MAX_BLOB || !preg_match('/^[A-Za-z0-9_-]+$/', $blob)) { $refused[] = ['id' => $id, 'why' => 'bad']; continue; }
    if (isset($s['gone'][$id])) { $refused[] = ['id' => $id, 'why' => 'gone']; continue; }
    if (isset($s['pages'][$id])) { $writes[$id] = $blob; continue; }
    if (file_exists("$pages/$id.txt") || is_used($store, $id)) { $refused[] = ['id' => $id, 'why' => 'taken']; continue; }
    if ($low) { $refused[] = ['id' => $id, 'why' => 'disk']; continue; }
    if (count($s['pages']) + count($creates) >= MAX_LINKS || $g['files'] + count($creates) >= $cap) { $refused[] = ['id' => $id, 'why' => 'full']; continue; }
    if (count($creates) >= $budget) { $refused[] = ['id' => $id, 'why' => 'later']; continue; }
    $creates[$id] = $blob;
}
$removes = [];                                         // this shop's pages taken back
$claims  = [];                                         // ids never written, taken back: kept so no one takes them
$removed = 0;
foreach ($drop as $id) {
    if (!is_id($id) || isset($seen[$id])) continue;
    $seen[$id] = true;
    if (isset($s['pages'][$id])) { $removes[] = $id; continue; }
    if (isset($s['gone'][$id]) || file_exists("$pages/$id.txt") || is_used($store, $id)) { $removed++; continue; }   // gone already, or not ours
    // Nothing to take down; the id is kept from everyone where there is room (a new shop keeps none: it holds no page yet).
    if ($isNew || $low || $g['files'] + count($creates) + count($claims) >= $cap) { $removed++; continue; }
    if (count($creates) + count($claims) >= $budget) { $refused[] = ['id' => $id, 'why' => 'later']; continue; }
    $claims[] = $id;
}

// New pages first; each one that cannot be written whole is refused, and nothing of it is kept.
$made = [];
foreach ($creates as $id => $blob) {
    if (create_whole("$pages/$id.txt", $blob)) $made[] = $id;
    else $refused[] = ['id' => $id, 'why' => 'disk'];
}
// A new shop is kept only once it holds a page: a request that made none leaves nothing behind.
if ($isNew && !$made) answer(200, ['ok' => true, 'written' => 0, 'removed' => $removed, 'refused' => $refused]);
$kept = [];
foreach ($claims as $id) {
    if (create_whole("$pages/$id.txt", GONE_MARK)) { $s['gone'][$id] = time(); $kept[] = $id; $removed++; }
    else $refused[] = ['id' => $id, 'why' => 'disk'];
}
foreach ($made as $id) $s['pages'][$id] = time();
$s['made'] = ($s['made'] ?? 0) + count($made) + count($kept);
note_empty($s);
// The shop's record before any existing page changes: if it cannot be written, the new pages go again.
if (!write_whole($sfile, json_encode($s))) {
    foreach ($made as $id) @unlink("$pages/$id.txt");
    foreach ($kept as $id) @unlink("$pages/$id.txt");
    fail(507, 'disk');
}
$written = count($made);
foreach ($writes as $id => $blob) {
    if (write_whole("$pages/$id.txt", $blob)) { $s['pages'][$id] = time(); $written++; }
    else $refused[] = ['id' => $id, 'why' => 'disk'];
}
foreach ($removes as $id) {
    // The page goes; its stub stays, so no other shop can take the id. Without room for a stub, the page goes
    // anyway, and its id is written to the list of used ids instead.
    $f = "$pages/$id.txt";
    $out = write_whole($f, GONE_MARK);
    if (!$out && (@unlink($f) || !file_exists($f))) { mark_used($store, $id); $out = true; }
    if ($out) { unset($s['pages'][$id]); $s['gone'][$id] = time(); $removed++; }
    else $refused[] = ['id' => $id, 'why' => 'disk'];
}
// The times and the stubs. Not kept: the app is told, and sends it all again (every write here can be repeated).
note_empty($s);
if (($writes || $removes) && !write_whole($sfile, json_encode($s))) fail(507, 'disk');
if ($isNew) {
    $g['shops']++; $g['fresh']++;
    // A lost count lets one more shop in from that address today; nothing else rests on it.
    $counts['n'][$from] = ($counts['n'][$from] ?? 0) + 1;
    write_whole("$store/ip.json", json_encode($counts));
}
$g['files'] += count($made) + count($kept);
write_whole("$store/g.json", json_encode($g));          // a lost count is taken again by the next day's sweep

answer(200, ['ok' => true, 'written' => $written, 'removed' => $removed, 'refused' => $refused]);

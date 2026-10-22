<?php
/*
 * Roshan Khata — a customer's own page, the shop's side (22 Oct 2026).
 *
 * The app sends, every few minutes and only when something changed, the
 * sealed pages of the customers the shop gave a link to. Each page is sealed
 * on the phone with a key that is only in the customer's link (after the #,
 * which no browser sends to a server): this server stores bytes it cannot
 * read, as ready files the customer's browser fetches and opens itself.
 *
 * Checks and balances (the owner, 22 Oct): pages are small and few; a shop
 * may hold at most MAX_LINKS links, send at most MAX_CALLS requests a day,
 * at most MAX_ITEMS pages a request, each page at most MAX_BLOB characters.
 * A shop is known by its id; the first request sets its pass (the SHA-256 of
 * its token), and every later request must carry the same token. A page
 * belongs to the shop that first wrote it; no other shop can replace it.
 */

const MAX_ITEMS = 200;
const MAX_BLOB  = 4096;
const MAX_LINKS = 5000;
const MAX_CALLS = 300;
const MAX_BODY  = 1048576;

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function answer($code, $body) {
    http_response_code($code);
    echo json_encode($body);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') answer(405, ['ok' => false, 'why' => 'method']);
if (file_exists(__DIR__ . '/data/halt')) answer(503, ['ok' => false, 'why' => 'halted']);

$raw = file_get_contents('php://input', false, null, 0, MAX_BODY + 1);
if ($raw === false || strlen($raw) > MAX_BODY) answer(413, ['ok' => false, 'why' => 'too-big']);
$in = json_decode($raw, true);
if (!is_array($in)) answer(400, ['ok' => false, 'why' => 'bad-json']);

$shop  = $in['shop'] ?? '';
$token = $in['token'] ?? '';
$put   = $in['put'] ?? [];
$drop  = $in['drop'] ?? [];
if (!is_string($shop) || !preg_match('/^[0-9a-f]{32}$/', $shop)) answer(400, ['ok' => false, 'why' => 'shop']);
if (!is_string($token) || !preg_match('/^[0-9a-f]{64}$/', $token)) answer(401, ['ok' => false, 'why' => 'token']);
if (!is_array($put) || !is_array($drop) || count($put) + count($drop) > MAX_ITEMS) answer(413, ['ok' => false, 'why' => 'too-many']);

$owner = substr(hash('sha256', 'rk-owner:' . $shop), 0, 16);
$dir   = __DIR__ . '/data/s';
if (!is_dir($dir)) mkdir($dir, 0750, true);
$file = "$dir/$shop.json";
$fh = fopen($file, 'c+');
if (!$fh || !flock($fh, LOCK_EX)) answer(500, ['ok' => false, 'why' => 'lock']);
$s = json_decode(stream_get_contents($fh) ?: '', true);
$pass = hash('sha256', $token);
if (!is_array($s)) {
    $s = ['pass' => $pass, 'created' => time(), 'day' => '', 'calls' => 0, 'links' => 0];
} elseif (!hash_equals($s['pass'], $pass)) {
    flock($fh, LOCK_UN); fclose($fh);
    answer(403, ['ok' => false, 'why' => 'wrong-pass']);
}
$today = gmdate('Y-m-d');
if ($s['day'] !== $today) { $s['day'] = $today; $s['calls'] = 0; }
if ($s['calls'] >= MAX_CALLS) { flock($fh, LOCK_UN); fclose($fh); answer(429, ['ok' => false, 'why' => 'day-limit']); }
$s['calls']++;

$pages = __DIR__ . '/d';
$written = 0; $removed = 0; $refused = [];
foreach ($put as $p) {
    $id = $p['id'] ?? ''; $blob = $p['blob'] ?? '';
    if (!is_string($id) || !preg_match('/^[0-9a-f]{32}$/', $id)) { $refused[] = 'id'; continue; }
    if (!is_string($blob) || strlen($blob) > MAX_BLOB || !preg_match('/^[A-Za-z0-9_-]+$/', $blob)) { $refused[] = $id; continue; }
    $path = "$pages/$id.txt";
    if (file_exists($path)) {
        $first = fgets(fopen($path, 'r'));
        if (trim((string)$first) !== $owner) { $refused[] = $id; continue; }
    } else {
        if ($s['links'] >= MAX_LINKS) { $refused[] = $id; continue; }
        $s['links']++;
    }
    $tmp = "$path.tmp";
    file_put_contents($tmp, $owner . "\n" . $blob);
    rename($tmp, $path);
    $written++;
}
foreach ($drop as $id) {
    if (!is_string($id) || !preg_match('/^[0-9a-f]{32}$/', $id)) continue;
    $path = "$pages/$id.txt";
    if (!file_exists($path)) { $removed++; continue; }
    $first = fgets(fopen($path, 'r'));
    if (trim((string)$first) !== $owner) continue;
    unlink($path);
    $s['links'] = max(0, $s['links'] - 1);
    $removed++;
}

ftruncate($fh, 0); rewind($fh);
fwrite($fh, json_encode($s));
fflush($fh); flock($fh, LOCK_UN); fclose($fh);
answer(200, ['ok' => true, 'written' => $written, 'removed' => $removed, 'refused' => $refused]);

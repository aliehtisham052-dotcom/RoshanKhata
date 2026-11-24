#!/usr/bin/env python3
"""Every SharedPreferences file the phone opens says who its keys belong to (BC-250, 24 Nov 2026).

A preference file that every shop shares keeps one shop's keys beside another's; a deleted shop's keys must go with it
and one shop's must never be read by another. So each getSharedPreferences("<name>") in app/src/main must be exactly one of:
  1. a file of its own per shop (the name carries Businesses.suffix / suffixFor / "_b$id", and Businesses.delete
     deletes it by that name);
  2. in BusinessKeys.SHARED_FILES (shared by every shop, keys named per shop, forgotten by Businesses.forgetLeftovers);
  3. PER_SHOP_CLEARED: a shared file with per-shop keys that its OWNER clears when a shop is deleted (the call must be in
     Businesses.kt);
  4. DEVICE_WIDE below, with a one-line reason: it holds the phone's own choice, never a shop's.
A new file that is none of these fails the build until somebody decides which it is. A list entry nobody opens, or one
that is also in SHARED_FILES, fails too, so the lists cannot rot. Run from the repository root; exit 0 when all is decided.
Drift protection, not proof: it reads names, it cannot say that a DEVICE_WIDE reason is true (lesson 125).
"""
import glob, os, re, sys

APP = "app/src/main/java"
BUSINESS_KEYS = "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/BusinessKeys.kt"
BUSINESSES = "app/src/main/java/com/innovation313/roshankhata/data/Businesses.kt"

# 3. shared files with per-shop keys, each cleared by its owner from Businesses.delete (the call text must be there).
PER_SHOP_CLEARED = {
    "roshan_khata_prefs": "DriveBackup.clearLocalState",  # Drive backup marks are keyed "<key>_b<id>"; App Lock's key is the phone's
    "backup_reminder": "BackupReminder.clear",            # the last backup time, one key per shop
    "alert_notes": "AlertNotes.clear",                    # the bell's notes, keyed per shop
    "hudhud_check": "HudhudCheck.forgetBusiness",         # the check log's rows name their shop
}

# 4. the phone's own choices and state, never a shop's.
DEVICE_WIDE = {
    "businesses": "the registry of shops itself (ids, active shop, the highest id ever given)",
    "printer": "which Bluetooth receipt printer this phone used last",
    "home_offers": "which one-time Home offers this phone has answered",
    "payment_radar": "this phone's Payment Radar on/off switch",
    "app_language": "the language this phone's app speaks (the system's per-app language tags)",
    "language": "the setup's language page marks (this phone's first-run choice)",
    "setup": "how far this phone's first-run setup went (SetupProgress)",
    "hudhud": "Hudhud's voice, pace, greeting and what he calls the owner: this phone's, never in a book",
    "coach_marks": "which walkthrough steps this phone has shown",
    "app_rating": "when this phone last offered the Play rating card",
    "feature_intro": "which first-open sheets this phone has shown",
    "install_source": "whether this phone came from a khata link (a flag read once at first start)",
    "screen_security": "this phone's screen-privacy switch",
    "backup_password": "the phone's own saved backup password setting",
    "print_digits": "this phone's choice of native digits on printed sheets",
    "text_size": "this phone's text size (a look, not a shop's)",
    "theme": "this phone's light/dark choice",
    "viewer_mode": "whether this phone is a read-only helper phone (the phone's role)",
    "paisa_voice": "the payment voice's switches and watched apps: this phone's notification access",
    "paisa_tickets": "one-time tickets for a payment notice: short-lived, per notice, device-wide",
    "access_device": "counter mode and the wrong-PIN counts of this device (keys carry the account, lesson 24)",
    "push_wake": "this phone's Firebase wake-up token",
    "mera_khata": "the customer-side list of khata links this phone follows: not any shop's book",
    "balance_privacy": "whether this phone shows balances or dots",
    "usage_log": "how often the app opened on this phone, a count a day (never a shop's)",
    "waris": "the heir flow's notice marks (keyed by the heir) and this phone's heir copy marker",
    "notif_permission_ui": "when this phone last explained the notification permission",
    "motion_counts": "how many times each animation has played on this phone",
    "book_recovery": "marks 'pending:<book file>' for a damaged book; a deleted shop's mark is removed by BookRecovery.forget from Businesses.forgetLeftovers",
}

# A getSharedPreferences(<identifier>) whose name is a variable: (file, identifier) -> why that is all right.
VARIABLE_OK = {
    ("data/Businesses.kt", "name"): "forgetLeftovers walks BusinessKeys.SHARED_FILES, every one of which this script already holds",
}


def kotlin_files():
    return sorted(glob.glob(APP + "/**/*.kt", recursive=True))


def balanced_first_arg(text, open_at):
    """The text of the first argument of the call whose '(' is at open_at."""
    depth = 0
    i = open_at
    while i < len(text):
        c = text[i]
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                return text[open_at + 1:i].strip()
        elif c == "," and depth == 1:
            return text[open_at + 1:i].strip()
        i += 1
    return text[open_at + 1:].strip()


def shared_files():
    s = open(BUSINESS_KEYS, encoding="utf-8").read()
    out = set()
    for name in ("SUFFIXED", "BY_ID", "BY_FILE"):
        m = re.search(r"val " + name + r"\s*=\s*setOf\(([^)]*)\)", s)
        if not m:
            sys.exit(f"cannot read {name} from {BUSINESS_KEYS}")
        out |= set(re.findall(r'"([^"]+)"', m.group(1)))
    return out


def main() -> int:
    shared = shared_files()
    businesses = open(BUSINESSES, encoding="utf-8").read()
    problems, seen, per_shop_base = [], set(), []
    for path in kotlin_files():
        text = open(path, encoding="utf-8").read()
        rel = os.path.relpath(path, APP + "/com/innovation313/roshankhata")
        consts = dict(re.findall(r'(?:const\s+)?val\s+([A-Z_][A-Z_0-9]*)\s*=\s*"([^"$]*)"', text))
        for m in re.finditer(r"getSharedPreferences\(", text):
            line = text.count("\n", 0, m.start()) + 1
            arg = balanced_first_arg(text, m.end() - 1)
            terms = [t.strip() for t in re.split(r"\s\+\s", arg)]
            first = terms[0]
            per_shop = any(re.search(r"Businesses\.suffix\(|suffixFor\(", t) for t in terms[1:]) or bool(re.search(r"_b\$", arg))
            lit = re.fullmatch(r'"([^"]*)"', first)
            tmpl = re.fullmatch(r'"([^"$]*)_b\$\{?\w+\}?"', first)
            if tmpl:
                base, per_shop = tmpl.group(1), True
            elif lit:
                base = lit.group(1)
            elif re.fullmatch(r"[A-Z_][A-Z_0-9]*", first) and first in consts:
                base = consts[first]
            elif re.fullmatch(r"\w+", first):
                if (rel, first) in VARIABLE_OK:
                    continue
                problems.append(f"{rel}:{line}: getSharedPreferences({arg}) names its file by a variable the script cannot read: use a literal or a constant")
                continue
            else:
                problems.append(f"{rel}:{line}: getSharedPreferences({arg}) is not a literal, a constant or a per-shop name")
                continue
            seen.add(base)
            if per_shop:
                per_shop_base.append((rel, line, base))
            elif base in shared:
                pass
            elif base in PER_SHOP_CLEARED:
                pass
            elif base in DEVICE_WIDE:
                pass
            else:
                problems.append(f"{rel}:{line}: preference file \"{base}\" is not decided: per shop by its name, in "
                                f"BusinessKeys.SHARED_FILES, in PER_SHOP_CLEARED, or DEVICE_WIDE with a reason (BC-250)")
    # A file of its own per shop must be deleted with the shop, by that name.
    for rel, line, base in per_shop_base:
        if not re.search(r'deleteSharedPreferences\("' + re.escape(base) + r'("\s*\+\s*BusinessKeys\.suffixFor\(id\)|_b\$id")', businesses):
            problems.append(f"{rel}:{line}: \"{base}\" is a file per shop but Businesses.delete never deletes it by that name")
    for base, call in PER_SHOP_CLEARED.items():
        if base not in seen:
            problems.append(f"PER_SHOP_CLEARED lists \"{base}\", which no code opens any more")
        if call not in businesses:
            problems.append(f"\"{base}\" has per-shop keys cleared by {call}, but Businesses.kt never calls it")
    for base in DEVICE_WIDE:
        if base not in seen:
            problems.append(f"DEVICE_WIDE lists \"{base}\", which no code opens any more")
    for base in set(DEVICE_WIDE) | set(PER_SHOP_CLEARED):
        if base in shared:
            problems.append(f"\"{base}\" is in BusinessKeys.SHARED_FILES, so it must not also be in an allow-list here")
    for base in sorted(shared):
        if base not in seen:
            problems.append(f"BusinessKeys.SHARED_FILES lists \"{base}\", which no code opens any more")
    for p in problems:
        print("::error::" + p)
    if not problems:
        print(f"preference names OK: {len(seen)} files decided "
              f"({len(shared)} shared per shop, {len(PER_SHOP_CLEARED)} cleared by owner, {len(DEVICE_WIDE)} device-wide, {len(per_shop_base)} per-shop opens)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

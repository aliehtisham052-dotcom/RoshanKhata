#!/usr/bin/env python3
"""No Room query names a column or table alias with an SQL keyword (lesson 143, bug class BC-078).

Room parses every @Query / @DatabaseView with its own ANTLR grammar of SQLite, stricter than SQLite itself: an alias
`AS first` ran in SQLite 3.53 and failed `:shared:kspKotlinDesktop` on 15 Nov 2026 ("extraneous input '('"), because
SQLite takes FIRST as an identifier where its grammar allows and Room's grammar does not. A local sqlite3 run proves
nothing about Room, and there is no Gradle in a session to find out early — so this reads every query string in
shared/ and app/ (main and tests) and fails on `AS <word>` where the word, unquoted, is one of SQLite's keywords
(sqlite.org/lang_keywords.html, the 147 of SQLite 3.4x). A quoted alias (`AS "first"`, `AS [first]`, `` AS `first` ``)
is allowed: the grammar takes those as names. Name aliases firstAt, lastAt, total instead.

What it reads: the string literals that follow @Query( / @DatabaseView( up to the annotation's closing parenthesis,
"…" and \"\"\"…\"\"\" alike, joined with + . Drift protection, not proof (lesson 125): Room's grammar has other limits.

    python3 scripts/check_room_aliases.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FOLDERS = ["shared/src", "app/src", "desktop/src"]

SQLITE_KEYWORDS = set("""
ABORT ACTION ADD AFTER ALL ALTER ALWAYS ANALYZE AND AS ASC ATTACH AUTOINCREMENT BEFORE BEGIN BETWEEN BY CASCADE CASE
CAST CHECK COLLATE COLUMN COMMIT CONFLICT CONSTRAINT CREATE CROSS CURRENT CURRENT_DATE CURRENT_TIME CURRENT_TIMESTAMP
DATABASE DEFAULT DEFERRABLE DEFERRED DELETE DESC DETACH DISTINCT DO DROP EACH ELSE END ESCAPE EXCEPT EXCLUDE EXCLUSIVE
EXISTS EXPLAIN FAIL FILTER FIRST FOLLOWING FOR FOREIGN FROM FULL GENERATED GLOB GROUP GROUPS HAVING IF IGNORE IMMEDIATE
IN INDEX INDEXED INITIALLY INNER INSERT INSTEAD INTERSECT INTO IS ISNULL JOIN KEY LAST LEFT LIKE LIMIT MATCH
MATERIALIZED NATURAL NO NOT NOTHING NOTNULL NULL NULLS OF OFFSET ON OR ORDER OTHERS OUTER OVER PARTITION PLAN PRAGMA
PRECEDING PRIMARY QUERY RAISE RANGE RECURSIVE REFERENCES REGEXP REINDEX RELEASE RENAME REPLACE RESTRICT RETURNING RIGHT
ROLLBACK ROW ROWS SAVEPOINT SELECT SET TABLE TEMP TEMPORARY THEN TIES TO TRANSACTION TRIGGER UNBOUNDED UNION UNIQUE
UPDATE USING VACUUM VALUES VIEW VIRTUAL WHEN WHERE WINDOW WITH WITHOUT
""".split())

ANNOTATION = re.compile(r"@(?:Query|DatabaseView|RawQuery)\s*\(")
STRING = re.compile(r'"""(.*?)"""|"((?:[^"\\\n]|\\.)*)"', re.S)
ALIAS = re.compile(r"\bAS\s+([A-Za-z_][A-Za-z0-9_]*)\b", re.I)


def query_strings(text):
    """(offset, sql) for every annotation's argument, its string pieces joined."""
    for m in ANNOTATION.finditer(text):
        i, depth, pieces = m.end(), 1, []
        while i < len(text) and depth:
            s = STRING.match(text, i)
            if s:
                pieces.append(s.group(1) if s.group(1) is not None else s.group(2))
                i = s.end()
                continue
            depth += {"(": 1, ")": -1}.get(text[i], 0)
            i += 1
        yield m.start(), " ".join(pieces)


def problems_in(text, name):
    out = []
    for at, sql in query_strings(text):
        line = text.count("\n", 0, at) + 1
        for a in ALIAS.finditer(sql):
            word = a.group(1)
            if word.upper() in SQLITE_KEYWORDS:
                out.append(f"{name}:{line}: alias 'AS {word}' is an SQL keyword; Room's parser refuses it "
                           f"(name it {word.lower()}At / {word.lower()}Value, or quote it)")
    return out


def main() -> int:
    failures, count = [], 0
    for folder in FOLDERS:
        for path in sorted((ROOT / folder).rglob("*.kt")):
            text = path.read_text(encoding="utf-8")
            if "@Query" not in text and "@DatabaseView" not in text and "@RawQuery" not in text:
                continue
            count += sum(1 for _ in query_strings(text))
            failures += problems_in(text, str(path.relative_to(ROOT)))
    if failures:
        print("\n".join(failures))
        print(f"Room aliases: {len(failures)} alias(es) Room would refuse")
        return 1
    if count < 100:
        print(f"Room aliases: only {count} queries found — the reader has lost them (expected hundreds)")
        return 1
    print(f"Room aliases OK: {count} queries read, no alias is an SQL keyword")
    return 0


if __name__ == "__main__":
    sys.exit(main())

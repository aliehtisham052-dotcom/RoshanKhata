#!/usr/bin/env python3
"""Writes app/google-services.json from the GOOGLE_SERVICES_JSON secret (CI only).

Live sync's wake-up (PushWake, 22 Oct 2026) needs Firebase's config file, which
must never be committed: this repository is public. The owner keeps it as a
GitHub secret; each workflow that builds the phone's app runs this first.

The file is written only when it parses and names a client for every package
this build makes (the Play package and the ".dev" test package): otherwise
Google's Gradle plugin would fail the whole build for one variant. Without the
file the app builds exactly as before, without the wake-up. Never fails the
step; says what it did as a GitHub annotation.
"""
import json
import os
import sys

NEEDED = ["com.innovation313.roshankhata", "com.innovation313.roshankhata.dev"]
OUT = os.path.join("app", "google-services.json")


def main() -> int:
    raw = os.environ.get("GOOGLE_SERVICES_JSON", "").strip().lstrip("\ufeff")
    if not raw:
        print("::notice::No GOOGLE_SERVICES_JSON secret: the app is built without the Firebase wake-up.")
        return 0
    # What the secret looks like, without showing it: a pasted file starts with "{" and runs
    # to about 600-1500 characters; anything else was pasted wrongly (22 Oct 2026).
    shape = f"{len(raw)} characters, starts with {raw[0]!r}, ends with {raw[-1]!r}"
    try:
        data = json.loads(raw)
        packages = {
            c["client_info"]["android_client_info"]["package_name"]
            for c in data.get("client", [])
        }
        project = data["project_info"]["project_id"]
    except (ValueError, KeyError, TypeError) as e:
        print(f"::warning::GOOGLE_SERVICES_JSON is not a google-services.json ({type(e).__name__}; {shape}): built without the wake-up.")
        return 0
    missing = [p for p in NEEDED if p not in packages]
    if missing:
        print(f"::warning::google-services.json has no Firebase app for {', '.join(missing)} "
              f"(it has {', '.join(sorted(packages)) or 'none'}): built without the wake-up.")
        return 0
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(raw)
    print(f"::notice::Firebase wake-up on: project {project}, packages {', '.join(NEEDED)}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

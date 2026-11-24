# Work paused on 24 Nov 2026 (the owner: half the helpers stopped to save the plan's limit)

- **Stage 7 (A1, F2, F3), the assistant's safety:** `stage7-half.patch` holds the unfinished work (not on the tree):
  money-side rules for Farsi, Arabic, Indonesian and plural supplier words; the answer layer's failures told as
  failures (HudhudReadFailure); the sense's load failure marked (HudhudSenseState). NOT run against the floors; the F2
  part throws reads up to the platform's catch, which was not checked, and needs the key `hudhud_read_failed` in nine
  languages. `stage7-progress.md` lists the seven opposite-side holes found and what is left. Apply with
  `git apply docs/wip/stage7-half.patch`, then run the harness and the floors before keeping any of it.
- **Stage 8 (H0), the baseline:** the runner (scripts/hudhud_baseline.sh, scripts/hudhud_baseline_inventory.py,
  scripts/shared_harness/HudhudBaseline.kt) measured the shipped figures once; the five-seed run and the report
  docs/hudhud-baseline-24nov2026.md are not done. F1 (the Hindi and Bengali hints) is done.

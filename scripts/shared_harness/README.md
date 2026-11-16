# The shared harness

A way to compile and test the plain-Kotlin Hudhud engine of `:shared` **without Gradle** (lesson 93 in CLAUDE.md): Maven
and Google's repositories may be closed to a session, but Gradle's own distribution carries the Kotlin compiler and JUnit.
It compiles the engine (every `shared/.../data/Hudhud*.kt` plus the files in `deps.txt`), two stand-ins and the generated
word names, then runs every `Hudhud*Test.kt` and `Voice*Test.kt` of `shared/src/jvmSharedTest` with JUnit.

**It is not CI.** Android, Room and Compose code is not compiled here, `HudhudWordsTest` (a desktop test that reads the
nine strings.xml files) is not run, and a green run here is not a green build. Push and read CI's check-run annotations.

## What it needs

- Java 17 or later and Python 3.
- A folder with `kotlin-compiler-embeddable`, `kotlin-stdlib`, `kotlin-reflect`, `kotlin-script-runtime`, `trove4j`,
  `annotations`, `kotlinx-coroutines-core-jvm`, `junit` 4 and `hamcrest-core` jars. Gradle 8.14's `lib` folder has them all (checked 16 Nov 2026);
  the default is `/opt/gradle/lib`, else set `KOTLIN_LIB=/path/to/gradle/lib`.

## Running it

```bash
scripts/shared_harness/run.sh                               # all tests; the verdict is the summary line
scripts/shared_harness/run.sh path/to/MyNewTest.kt          # plus test files not yet named Hudhud*/Voice*
scripts/shared_harness/run.sh --lists-only                  # only check that every listed file exists
scripts/shared_harness/mutate.sh <file> <name> <old> <new>  # one deliberate breakage, then the file is put back
```

`run.sh` exits 0 when every test passed, 1 when a test failed and 3 when the code did not compile. The whole output is in
`build/shared-harness/tout.txt` (tests) and `clog.txt` (compiler); read the summary line from there, never a cut screen
(lesson 131). Runs on one machine queue behind a lock (`HARNESS_LOCK`, default `/tmp/harness.lock`): a compile takes about
3 GB of memory and a minute. `HARNESS_WORK` moves the build folder.

`mutate.sh` says `killed` (a test caught the breakage), `SURVIVED` (no test did: a class of sentence is missing, lesson
129) or `did not compile` (not a catch: change the breakage). It puts the file back whatever happens.

## The parts

| File | What it is |
| --- | --- |
| `run.sh` | Finds the engine and the tests, generates the word names, compiles, runs. |
| `deps.txt` | The shared files the engine needs besides its own `Hudhud*.kt`, relative to the repository root. A new one shows as `unresolved reference` in `clog.txt`: add it here. |
| `gen_w.py` | Writes the `W` / `WP` / `WA` name objects from `app/src/main/res/values` by the same rule as `:shared:generateWordKeys`. Generated into the build folder, never checked in. |
| `stubs/Words.kt` | The `Words` interface without its Compose parts. |
| `stubs/Hijri.kt` | The `expect fun hijriUmmAlQura` filled with `java.time`'s Hijrah calendar (the desktop's own way). |
| `mutate.sh` | One deliberate breakage at a time. |

The Strings workflow runs `run.sh --lists-only`, so a file moved or renamed fails there and not only on the next local run.

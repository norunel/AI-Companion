# GameTests

GameTests run through Gradle and Java 21. No Minecraft account, display server or
provider credentials are needed. Gradle downloads the initial dependencies.

From the repository root on Linux or macOS:

```sh
./gradlew verifyGameTests
```

On Windows PowerShell, use `.\gradlew.bat verifyGameTests`. Each run starts with an empty world
under `build/gametest-runs/`. It does not use the launcher profile, player saves or
local provider configuration.

## Selecting tests

The default `quick` suite includes non-manual tests whose declared timeout is at
most 2,400 ticks. This is a selection limit, not a wall-clock duration guarantee.
The `full` suite includes all non-manual tests, including endurance scenarios.

```sh
# Every short test, as in GitHub Actions
./gradlew verifyGameTests

# One class, repeated in three independent worlds; continue after failures
./gradlew verifyGameTests '-PgameTestFilter=CompanionTaskTests.*' -PgameTestRepeats=3 --continue

# One method, including tests beyond the quick-suite cutoff
./gradlew verifyGameTests -PgameTestSuite=full '-PgameTestFilter=CompanionGoalTests.methodName'

# All non-manual tests; allow up to two wall-clock hours per server process
./gradlew verifyGameTests -PgameTestSuite=full -PgameTestTimeout=7200
```

Replace `methodName` with an actual test method. Filters match `Class.method`
case-insensitively; `*` matches any text. Quote patterns to prevent shell expansion.
A filter matching no tests fails. Repetitions are limited to 1–20 and run sequentially.
Use `--continue` to finish later repetitions after a failure; the overall build
still fails if any run failed.

`gameTestTimeout` defaults to 900 seconds. Gradle applies it to each test-server
process, including startup and shutdown, and terminates that process if it times
out. Dependency downloads and compilation are outside this limit; GitHub Actions
also has a job timeout covering the whole build.
The test-server heap is capped at 2 GiB on both developer machines and CI.

## Reports and failures

Reports are written to `build/reports/gametests/<suite>-<iteration>/`:

- `selected-tests.txt`: the exact test inventory selected before execution.
- `results.xml`: the Minecraft JUnit-style report, written after the suite finishes.
- `summary.txt`: counts, assertion failures, process exit status and report errors.
- `exit-code.txt`: the exit status captured by Gradle after the server process exits.
- `server.log`: the test-server log, when the server got far enough to create it.

The corresponding world is under `build/gametest-runs/<suite>-<iteration>/`.
These build directories are cleared before each run, so an earlier successful report
cannot hide a failed new run. Copy reports elsewhere before rerunning the same suite
if you need to keep them. Gradle's own output contains compilation and timeout errors.

A successful run requires a zero process exit code, a non-empty selection, results
for exactly the selected tests, and no required-test failures. Missing, partial or
malformed reports fail even if the process exits with zero. Optional failures are
reported separately, following Minecraft's semantics. A finalizer checks the report
even when the test-server task fails or times out; an incomplete process uses exit
code `-1` in the summary.

GitHub Actions runs the quick suite and uploads reports even when tests fail.
The job needs no secrets and has read-only repository permissions. It does not
exercise cloud dialogue, microphone capture, voice playback or the UI.

## Implementation and checks

The development-only Java generator selects tests and uses copies of the existing
structure fixtures in a separate namespace. Test bodies, timeouts and batch hooks
stay unchanged. The generator and copied fixtures are excluded from the release JAR.
The original `runGameTestServer` task still runs the original full suite.

Gradle owns process execution, timeouts and ordering. Java build logic in `buildSrc`
validates the reports. Its JUnit 5 tests run without Minecraft:

```sh
./gradlew buildSrc:test
```

[JUnit 5](https://junit.org/junit5/) is a build-test dependency under the
[Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-v20.html) and is not
shipped with the mod. The existing PowerShell scripts remain available for their
specialized scenarios.

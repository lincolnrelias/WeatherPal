# Implementation verification

Verified during the October 7, 2026 implementation session (America/Sao_Paulo).
The emulator used GMT, while Berlin/Paris/Oslo and Tokyo used their own timezones;
their live forecast window was October 8–14. Dates shown in screenshots are
city-local dates, not an assumption about the developer's local date.

## Environment and scope

- Windows 11, Android Studio JetBrains Runtime 21.0.6, Gradle wrapper 8.13.
- compile/target SDK 36, min SDK 24, Java/Kotlin bytecode 11, core-library desugaring.
- Medium_Phone emulator, Android 16 / API 36, x86_64, adb serial emulator-5554.
- Local Git repository initialized; phase commits recorded. No remote publication.
- Approximately 45–60 minutes of implementation/verification work; no fixed time
  budget was imposed. Commit times record phase completion, not active effort.

## Executed automated checks

These commands completed successfully against the implementation:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

Unit results: **27 tests, 0 failures, 0 errors**:

- ScoringTest: 7; every component boundary immediately below/at/above, label
  boundaries, worked examples/ties, bounded scores, indoor/outdoor complements,
  missing/invalid inputs, valid zero depth preference, snowfall maximum, even/odd
  medians, 11/12 coverage, 23/25-hour days, and city-local windows.
- RemoteTest: 8; request parameters/encoding, HTTP/429 Retry-After, result
  filtering/deduplication, null/missing/partial weather, array/date/unit/timezone
  failures, optional malformed snow units, normalization, transport failure
  classification/cancellation propagation, alternate-address recovery, and repeated
  fall-back hourly labels retained as separate samples.
- RepositoryTest: 3; same-city refresh deduplication, unchanged success timestamps,
  network/validation/storage failure preservation, cancellation before commit, and
  retry after cancellation.
- ViewModelTest: 9; submit-only/Unicode validation, duplicate submissions, stale
  responses, retry of the submitted query, rate-limit blocking, empty/error results
  retaining shortcuts, cached-first display, refresh failure preservation, changed
  and unchanged updates retaining date/equal day objects, uncached retry, navigation
  cancellation, saved state, resume/visible midnight, expired windows, and one queued
  rollover refresh when a request was already active.

Room instrumented results: **5 tests, 0 failures**:

- Insert/update by city ID, deterministic shortcut ordering/eviction ties, capacity
  configuration, and startup pruning.
- SQLite triggers prove an unchanged success does not write day rows, a temperature
  change does not update precipitation, and an unchanged date remains untouched.
- A trigger fails insertion after eviction; transaction rollback restores the old
  header/days/timestamp. Observable relation reads capture complete old/new values.
- File-backed database close/reopen retains data; partial replacement deletes an
  omitted date.
- Eight overlapping city commits remain within the configured two-city capacity.

Lint: **0 errors, 26 warnings** in the final report. Warnings concern available
dependency/tool updates and kapt usage; they do not indicate functional findings.
Versions remain intentionally pinned to the verified compatible set. Kapt's
Kotlin 1.9 processing fallback and the no-processor unit/test-source warnings are
expected with this Kotlin 2.0/Room 2.6 setup.

Generated details: `app/build/test-results/testDebugUnitTest`,
`app/build/reports/androidTests/connected/debug`, and
`app/build/reports/lint-results-debug.html`. Build outputs/reports are ignored by Git
and regenerate with the commands above. Room's schema v1 JSON is committed.

## Ordered phase evidence

1. Foundation: assembled and installed; launcher entered the search placeholder on
   API 36. Compose/serialization/kapt/desugaring resolved with the preserved SDKs.
2. Domain: unit suite passed before adding HTTP/persistence.
3. HTTP: MockWebServer and deterministic response-fixture tests passed.
4. Persistence: unit/repository and focused Room emulator suites passed.
5. Presentation state: injected Clock/dispatcher/fake tests passed before UI wiring.
6. UI: assemble/unit/lint passed, followed by the emulator checks below.
7. Delivery: README, exported schema, verification record, final commands, and local
   phase commits prepared. Optional phase 8 visual refinement was not performed.

## Executed emulator walkthrough

- Fresh launch enters search. Visible submit and the keyboard's Search action both
  submit; live results label the query and disambiguate Berlin and other cities.
- Loaded Berlin, Tokyo, and Paris, then Oslo. The final shortcuts were Oslo, Paris,
  Tokyo; Berlin, the oldest successful update, was evicted. A subsequent Tokyo
  successful update changed the order to Tokyo, Oslo, Paris.
- Berlin and Tokyo rendered weather, four ordered activity labels/reasons, local
  dates, limitations, and last successful update. Numerical scores were absent.
- Tapping October 10 selected its page; swiping selected October 11 in the timeline.
- A successful Tokyo refresh retained October 11. A scrolled future page kept its
  card positions: outdoor at y=845, skiing at y=1406, indoor at y=2089 before/after
  refresh in the same portrait viewport. Weather was published as one snapshot.
- Pulling down on the future page refreshed successfully, retaining October 11 and
  advancing Tokyo's displayed successful-update time to 10:24 local time.
- Airplane mode/Wi-Fi disabled, force-stop, relaunch: search exposed cached Berlin.
  Opening it immediately displayed stored weather, then a nonblocking connection
  error; the previous successful timestamp remained 03:10 Berlin time. Connectivity
  was restored afterward.
- Verified light/dark presentation and 1.3× font scale. Verified landscape rotation
  with a future date selected, followed by portrait restoration; the date remained
  selected. Compacted the header to improve remaining page space in landscape.
- All four activities remained accessible by vertical scrolling. Timeline controls
  use selected-state semantics and 48 dp minimum target height; Material buttons
  provide standard touch targets. About exposes Open-Meteo/GeoNames links and the
  activity limitations.
- Original emulator settings restored: night mode off, font scale 1.0, automatic
  rotation on/portrait, airplane mode off, Wi-Fi on, handwriting setting unset.

The emulator's Gboard stylus onboarding initially intercepted adb-injected typing.
Temporarily disabling handwriting allowed normal keyboard/IME verification; it was
restored afterward. Initial Google Maven DNS resolution and some first live forecast
connections failed. Restarting Gradle resolved dependency downloads. Standard OkHttp
transport recovery now handles alternate addresses/stale pooled connections within
the 20-second call timeout; a MockWebServer regression test verifies address failover.

## Deliberate limits and unverified areas

Malformed/partial responses, rate limiting, no matches, unchanged success, expired
cache, cancellation, and midnight/DST behavior were exercised with fixtures/fakes,
not by manipulating live forecasts or waiting for real midnight. Device process
death restoration is covered through saved-state reconstruction in unit tests;
rotation was exercised on the emulator. A separate physical device, API 24/25 runtime,
TalkBack end-to-end pass, maximum font scale, release signing/minification, production
migrations, screenshot tests, and complete UI automation were not verified. The
heuristics and activity safety/availability are not validated by these tests.

The connected runner removes its installed APKs during cleanup in this environment;
the debug app is reinstalled afterward for local use. Live sample values are
illustrations, not deterministic test expectations.

## October 8, 2026 UI redesign

Redesigned the search and forecast presentation with a coordinated light/dark
palette, editorial typography, photographic welcomes and activity cards,
clearer city navigation, a daily temperature timeline, a compact weather
summary, and optional snow details. Activity cards start collapsed and animate
open on tap. Their state stays with the activity and city/date, including after
refresh, reordering, date navigation, and saved-state restoration.

Executed:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

- 27 unit tests passed with zero failures/errors.
- 10 connected tests passed: the five existing Room tests and five new Compose
  interaction tests. The new tests exercise all cards starting closed, revealing
  and hiding explanations, retained expansion after ranking changes, saved-state
  restoration, and independent per-date state when returning to a previous day.
- Assembly and lint passed. Lint reported zero errors and 26 warnings, concerning
  dependency/tool updates and kapt. Compose's testing BOM matches the existing
  production Compose BOM; no production dependency versions were upgraded.
- Subsequent presentation-only fixes for large text, landscape header density,
  and dark selected-date contrast were compiled and linted again successfully.

Manual API 36 / Medium_Phone walkthrough:

- Submitted Lisbon, inspected disambiguated results, and opened Lisbon District,
  Portugal. Opened the saved city again after reinstalling the debug build.
- Confirmed photographic activity cards were initially closed; expanded Surfing
  and inspected the input-based explanations and limitations. Other cards stayed
  closed. All four activities remained reachable by scrolling.
- Confirmed expanded details survived system theme changes, 1.3× and 2× text, and
  portrait/landscape rotation. At 2× text, navigation and About remain visible;
  weather metrics stack instead of breaking labels into narrow columns.
- Inspected explicit dark colors and fixed the selected date temperature contrast.
- Opened Snow details and verified median snow depth and snowfall, then closed it.
- Original emulator settings were restored after the walkthrough: font 1.0,
  light theme, portrait/automatic rotation, and the unset handwriting setting.
- Reinstalled the final APK for local review. The four generated photographs are
  bundled locally (about 1 MB total) and are visible in the offline test harness.

Screenshots of the redesign are saved under `docs/screenshots/`. The previous
implementation verification above is a historical record; the new focused
Compose tests supplement its originally manual UI coverage. No physical device,
minimum-API runtime, or complete TalkBack walkthrough was added in this session.
Exact image prompts and asset paths are in `ui-redesign.md`.

### Navigation follow-up

Removed the forecast header's refresh button; pull-to-refresh remains available.
When results are visible, both device Back and the toolbar Back control now clear
the search and scroll to the welcome card, preserving recent places. Returning
from a forecast still shows the preceding search results first.

Assembly, all 28 unit tests, and lint passed. The new regression test verifies
that dismissing results clears the saved query and preserves cached recent
places. On the emulator, verified Back after scrolling results, the forecast-to-
results-to-welcome sequence, and the absence of the refresh button. Updated the
primary search, forecast, and activity screenshots and installed the final APK.

## October 8, 2026 engineering-review adjustments

Implemented the recovery fixes and structural adjustments from
`android-engineering-review.md`:

- Refreshes track the owning coroutine. Active waiters recover from owner
  cancellation, re-entry replaces abandoned work before cleanup ends, and
  cancelling a waiter leaves other consumers running. Old cleanup cannot remove
  a replacement. The ViewModel exposes a recoverable error if shared work is
  cancelled while its own request remains active.
- Recent places have an independent retry control. Failed reads preserve prior
  shortcuts; successful re-collection clears the error and resumes updates.
  Forecast retry restarts failed observation without duplicating refresh work.
- Forecast and geocoding repositories retain independent process-scoped
  Retry-After deadlines. Navigation, different cities, and changed queries cannot
  bypass an active cooldown; requests resume at the exact deadline.
- Forecast state explicitly distinguishes Closed/Open and owns city/selection
  once. Named saved-state fields support legacy restoration and invalid-date
  fallback. Remote responsibilities were split, the dispatcher contract moved
  out of DI, and redundant scoring/unused dispatcher fields were removed.
- An immediate-dispatcher regression verifies that observation/refresh jobs are
  assigned before execution, preventing re-entry and stale job references.

Executed with Android Studio JBR 21.0.6 and the existing Android 16 / API 36
Medium_Phone emulator:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

All checks passed: **46 unit tests**, **11 connected tests**, and lint with
**0 errors / 26 existing warnings**. Unit totals are scoring 7, remote 10,
repository 7, ViewModel 19, and presentation 3. Connected coverage retains all
five Room selective-write/transaction tests and five activity-card tests, plus
the recent-places retry control. No tests failed or were skipped. The final
debug APK was reinstalled and launched successfully after connected-test cleanup.

`.github/workflows/android-checks.yml` configures debug assembly, unit tests, and
lint for pushes/pull requests with JDK 21 and SDK 36. Its commands passed locally;
the hosted GitHub job has not been executed in this session. The README includes
the resulting ownership/recovery/cooldown contracts and a manual smoke recipe.

Minimum-API 24/25 runtime coverage, real end-to-end process-death recovery,
physical-device/TalkBack checks, and release/minified behavior remain unverified.
SavedStateHandle reconstruction tests establish serialization behavior, not
complete OS process-death handling. Cooldowns intentionally reset with the app
process. No dependency upgrades or database schema changes were introduced.

## October 8, 2026 snapshot testing

Added Roborazzi 1.39.0 and Robolectric 4.14.1 without upgrading production
dependencies. `WeatherSnapshotTest` renders 17 scenarios in both themes, producing
34 PNG baselines under `app/src/test/snapshots/`. Coverage includes search states,
forecast states, offline content, missing data, landscape, 200% text size, all four
activity images, and expanded card details. Fixtures use a plain Application,
API 35 native graphics, fixed viewport/locale/timezone/clock, and controlled
Compose animation time. No emulator or live services are required.

Executed on Windows 11 with Android Studio JBR 21.0.6 and SDK 36:

```powershell
./gradlew.bat :app:recordRoborazziDebug --tests '*WeatherSnapshotTest*'
./gradlew.bat :app:verifyRoborazziDebug --tests '*WeatherSnapshotTest*'
./gradlew.bat :app:assembleDebug :app:verifyRoborazziDebug :app:lintDebug
./gradlew.bat :app:testDebugUnitTest
```

Recording and a fresh verification passed all 34 snapshot cases. Reviewed both
theme contact sheets and representative full-size images. The combined run
passed **80 tests** (46 existing unit tests and 34 snapshot tests) with no failures
or skips; debug assembly and lint also passed. Lint reported **0 errors / 30
warnings**, including four new dependency-version advisories for the pinned test
tools. Ordinary unit execution passed the 46 existing tests and skipped snapshot
rendering as intended.

Verified the failure path by changing one pixel in a light baseline and temporarily
removing its dark counterpart. The two focused verification cases both failed:
the changed image produced actual/comparison images, and the missing baseline
was rejected. Both reference files were restored byte-for-byte in a `finally`
block before the successful full verification.

Android checks now includes a Windows Server 2022 / JDK 21 snapshot verification
job and uploads reports/differences. The hosted workflow was not executed in this
session; local checks establish Windows 11 behavior. Cross-OS rendering parity
is not assumed. Connected tests were not rerun because application and device
test sources are unchanged. Baseline review/update instructions are in
`snapshot-testing.md`.

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

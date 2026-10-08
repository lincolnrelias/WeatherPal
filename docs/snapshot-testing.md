# Visual snapshot tests

`WeatherSnapshotTest` renders the production Compose screens and activity cards with
Roborazzi 1.39.0 and Robolectric 4.14.1. These versions fit the existing Kotlin 2.0.21
and Compose BOM 2024.12.01 setup. No emulator, API access, database, or application
container is started. The test uses a plain Android `Application` and immutable fixtures.

The suite captures 17 scenarios in both light and dark mode (34 PNG baselines):

- Search welcome, saved places, results, no results, loading, network failure,
  rate limiting, and validation failure.
- Forecast loading, initial failure, ready content, saved content after a refresh
  failure, and a day without data.
- Forecast at 200% font size and in landscape.
- All four activity photos with collapsed cards and an expanded surfing card.

Rendering uses API 35 native graphics, a 411 × 891 dp mdpi viewport (891 × 411 dp
in landscape), US English, UTC, and a fixed clock at October 8, 2026, 12:00 UTC.
The fixture city retains its Europe/Lisbon timezone. Compose's clock advances in
fixed steps, including settling the card expansion before capture. The large-text
case explicitly sets the font scale to 2. Pixel comparisons allow no changed pixels
or color tolerance.

## Verify

Use Windows and JDK 21, with the project's normal Android SDK 36 build setup:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :app:verifyRoborazziDebug --tests '*WeatherSnapshotTest*'
```

Reference images live in `app/src/test/snapshots/` and belong in Git. A missing
baseline or a visual difference fails verification. Ordinary `testDebugUnitTest`
runs skip the snapshot class unless a Roborazzi record, compare, or verify mode
is enabled. This keeps the existing unit suite independent of the host renderer.

The `snapshots` job in [Android checks](../.github/workflows/android-checks.yml)
verifies on `windows-2022` with JDK 21 and uploads HTML reports, actual images,
and comparisons. The existing Linux build/unit/lint job continues to run separately.
Use the Windows environment for both recording and verification: native rendering
is not guaranteed to match across Windows, Linux, and macOS.

## Review and accept a UI change

```powershell
# Generate actual and comparison images without accepting the change.
./gradlew.bat :app:compareRoborazziDebug --tests '*WeatherSnapshotTest*'
```

Open `app/build/reports/roborazzi/index.html` to review the visual differences.
Comparison images stay under `app/build/outputs/roborazzi/`; they are ignored by Git.
After reviewing an intentional change:

```powershell
./gradlew.bat :app:recordRoborazziDebug --tests '*WeatherSnapshotTest*'
./gradlew.bat :app:verifyRoborazziDebug --tests '*WeatherSnapshotTest*'
```

Commit the relevant baseline PNG changes alongside the UI/test changes. Do not
record baselines during CI verification. To focus on one scenario, use a method
filter such as `--tests '*WeatherSnapshotTest.forecastReady*'`.

## Add coverage

Add a test method to `WeatherSnapshotTest` and pass a unique descriptive name to
`capture`. Its theme parameter automatically creates light and dark baselines.
Keep inputs immutable and use the fixture clock. For interactions, advance the
Compose clock with `settle()` before capturing. Avoid remote images, real network
requests, wall-clock sleeps, random values, and time-dependent fixtures.

Snapshots supplement the existing scoring, repository, ViewModel, Room, and
Compose interaction tests. They cover visible rendering at the captured viewport
and scroll position, rather than every possible screen state or device.

The record/compare/verify workflow follows the [Roborazzi documentation](https://github.com/takahirom/roborazzi/blob/1.39.0/README.md).

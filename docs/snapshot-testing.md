# Snapshot maintenance

`WeatherSnapshotTest` captures 17 scenarios in light/dark mode using Robolectric and Roborazzi. The 34 reference images in `app/src/test/snapshots/` are test inputs and belong in Git.

Use **Windows and JDK 21** with the normal SDK 36 build setup. Native graphics can differ across host operating systems. Ordinary unit runs skip rendering unless a Roborazzi mode is enabled.

```powershell
# Verify without accepting changes.
./gradlew.bat :app:verifyRoborazziDebug --tests '*WeatherSnapshotTest*'

# Inspect a proposed UI change.
./gradlew.bat :app:compareRoborazziDebug --tests '*WeatherSnapshotTest*'

# After reviewing and accepting the change, update and verify references.
./gradlew.bat :app:recordRoborazziDebug --tests '*WeatherSnapshotTest*'
./gradlew.bat :app:verifyRoborazziDebug --tests '*WeatherSnapshotTest*'
```

Review `app/build/reports/roborazzi/index.html`; actual/comparison images are under `app/build/outputs/roborazzi/`. Commit intentional baseline changes with their UI/test changes. Missing or changed baselines fail verification. CI verifies references and uploads reports; it does not record replacements.

Add scenarios in [WeatherSnapshotTest](../app/src/test/java/com/example/weatherpal/ui/snapshot/WeatherSnapshotTest.kt) with unique capture names. The theme parameter creates both baselines. Fixtures fix API 35 rendering, viewport, locale, timezone, clock, and animation time; landscape and 200% text cases override the relevant settings. Keep captures independent of network data and real-time waits.

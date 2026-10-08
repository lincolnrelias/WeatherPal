# WeatherPal

Native Android city search and seven-day weather-based activity rankings, implemented from [the agreed specification](docs/implementation-specification.md). Search explicitly, choose a disambiguated city, and browse skiing, surfing, outdoor sightseeing, and indoor sightseeing by city-local date. Recent cached cities work offline. Device light/dark mode, pull-to-refresh, and date-preserving updates are included.

<img src="docs/screenshots/forecast.png" width="320" alt="WeatherPal showing a future date, weather summary, and ranked activities in Tokyo">

## Build and run

Use Android Studio, Android SDK Platform 36, and JDK 17 or newer supported by Gradle 8.13. Verification used Android Studio's JetBrains Runtime **21.0.6**, Gradle **8.13**, Windows 11, and an Android 16 / API 36 x86_64 emulator named Medium_Phone. Java/Kotlin bytecode targets **11**; this is separate from the Gradle runtime JDK. Minimum Android version is **API 24**, with java.time core-library desugaring.

Open this directory in Android Studio, sync Gradle, select the app configuration, and Run. Set your SDK path in untracked `local.properties` (Android Studio normally does this). From PowerShell:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Launch WeatherPal from the launcher. A fresh launch opens search. No API key, backend, GPS, or location permission is needed; the app requests INTERNET permission.

Dependencies are pinned in `gradle/libs.versions.toml`: AGP 8.12.3, Kotlin/Compose compiler/serialization plugin 2.0.21, Compose BOM 2024.12.01, Activity 1.9.3, Lifecycle 2.8.7, Room 2.6.1, Coroutines 1.9.0, kotlinx.serialization 1.7.3, Retrofit 2.11.0, OkHttp/MockWebServer 4.12.0, and desugar_jdk_libs 2.1.4. Room uses kapt; its Kotlin 1.9 processing fallback is expected. This compatible, verified set deliberately preserves compile/target SDK 36 and min SDK 24 rather than selecting the newest libraries.

## Architecture

One app module uses Compose, Material 3, MVVM, StateFlow, and constructor-based manual dependency injection:

- `domain/model`, `domain/repository`, and `domain/scoring` contain Android-independent values, interfaces, scoring, snow aggregation, and city-local date calculations.
- `data/remote` contains separate geocoding and forecast services, typed DTOs, validation, normalization, and transport failure translation.
- `data/local` contains the exported Room v1 schema, transactional relation reads, and cache commits. City IDs identify entries; ISO local dates identify day rows.
- `data/repository` coordinates refreshes, startup capacity pruning, cancellation, and committed success/failure outcomes.
- `ui/search` and `ui/forecast` expose explicit ViewModel state and Compose screens. Presentation renders deterministic reason codes without displaying numerical scores.
- `di/AppContainer` constructs one application-scoped database/client, repositories, Clock, dispatcher provider, cache policy, and saved-state-aware ViewModel factories.

UI depends on repository interfaces. There is no direct UI networking, DAO access, DI framework, or forwarding-use-case layer. Room is the authoritative forecast stream; a network response is never independently published before persistence succeeds.

## API contracts and attribution

Location data: [Open-Meteo Geocoding API](https://open-meteo.com/en/docs/geocoding-api), backed by [GeoNames](https://www.geonames.org/). Forecast data: [Open-Meteo](https://open-meteo.com/) and its [Forecast API documentation](https://open-meteo.com/en/docs). Attribution and links are available in the app's About dialog.

Search uses HTTPS GET `geocoding-api.open-meteo.com/v1/search` with trimmed, library-encoded `name`, `count=10`, `language=en`, and `format=json`. Submission requires at least two Unicode code points. Open-Meteo limits two-character matching; three or more characters give broader matching. Editing text never requests geocoding. Valid results require an ID, nonblank name, finite coordinates within range, and IANA timezone. Invalid entries are filtered, IDs deduplicated in provider order, and all-invalid responses become data errors. Missing results mean no matches. Geocoding responses are not cached.

Forecast requests use HTTPS GET `api.open-meteo.com/v1/forecast` with the selected city's latitude/longitude, explicit IANA `timezone`, `forecast_days=7`, `timeformat=iso8601`, `temperature_unit=celsius`, `wind_speed_unit=kmh`, `precipitation_unit=mm`, `daily=temperature_2m_mean,precipitation_sum,wind_speed_10m_max,snowfall_sum`, and `hourly=snow_depth`. Provider grid coordinates do not replace city identity.

Connect/read/total call timeouts are 10/15/20 seconds. Retrofit calls are cancellable. No application retry loop is used. OkHttp's standard connection recovery can try alternate addresses or recover a stale pooled socket within the same call timeout. HTTP 429 exposes a rate-limit message; Retry-After seconds and HTTP dates disable submission/retry until the injected device Clock reaches that time. Responses and diagnostics are not logged verbosely.

Require a matching timezone, nonempty strictly increasing unique daily date axis, matching lengths for present daily arrays, and supported daily units. Missing arrays/values remain unavailable. Invalid finite/nonnegative samples become unavailable individually. Optional malformed hourly snow data or unsupported depth units make depth unavailable. At least one activity must be computable in the current seven-day window; otherwise preserve the old cache and report failure. Accepted partial responses replace old values without filling gaps from older forecasts.

Reassess [Open-Meteo usage terms](https://open-meteo.com/en/terms) and licensing/access requirements before commercial release. This test's public access configuration is not a production licensing decision.

## Recommendation rules

These are unvalidated product heuristics, not probabilities or safety advice. Internal integer scores are clamped to 0–100 and sorted descending. Ties use skiing, surfing, outdoor, indoor. Unscored activities follow scored activities in that same order. Labels are Very favorable (90–100), Favorable (70–89), Mixed (40–69), Unfavorable (0–39), and Insufficient data.

Inputs: T = mean °C, P = precipitation mm (including snow water equivalent), W = maximum wind km/h, D = accepted median snow depth cm, S = snowfall cm. Temperatures must be finite; other inputs must also be nonnegative. Missing values never become zero.

**Outdoor sightseeing** requires T/P/W and sums:

- Temperature: 45 for 15 ≤ T ≤ 25; 30 for 5 ≤ T < 15 or 25 < T ≤ 30; 10 for 0 ≤ T < 5 or 30 < T ≤ 35; otherwise 0.
- Precipitation: 35 for P < 1; 20 for 1 ≤ P < 5; 5 for 5 ≤ P < 15; otherwise 0.
- Wind: 20 for W ≤ 15; 10 for 15 < W ≤ 30; otherwise 0.

**Surfing weather comfort** requires T/P/W and sums:

- Temperature: 45 for 20 ≤ T ≤ 30; 30 for 15 ≤ T < 20 or 30 < T ≤ 35; 10 for 10 ≤ T < 15; otherwise 0.
- Precipitation: 30 for P < 1; 15 for 1 ≤ P < 5; otherwise 0.
- Wind: 25 for W ≤ 15; 10 for 15 < W ≤ 25; otherwise 0.

**Skiing** requires T/W plus D or S, preferring available D even when zero:

- Depth: 60 for D ≥ 30; 40 for 10 ≤ D < 30; 15 for 0 < D < 10; 0 for D = 0.
- Otherwise snowfall: 45 for S ≥ 10; 30 for 3 ≤ S < 10; 15 for 0 < S < 3; 0 for S = 0.
- Temperature: 25 for −10 ≤ T ≤ 0; 15 for −20 ≤ T < −10 or 0 < T ≤ 5; otherwise 0.
- Wind: 15 for W ≤ 15; 8 for 15 < W ≤ 30; otherwise 0.
- A zero chosen snow signal caps the total at 39. Snowfall-only maximum is 85. Neither snow input usable means Insufficient data.

**Indoor sightseeing** = 100 minus the numeric outdoor score. It expresses the relative appeal of indoor plans as outdoor weather worsens, not an attraction's quality or intrinsic suitability. If outdoor is unscored, indoor is also unscored.

For T=22, P=0, W=10, D=0, scores are skiing 15, surfing 100, outdoor 100, indoor 0; surfing wins the tie. For T=−5, P=2, W=10, D=40, scores are skiing 100, surfing 40, outdoor 40, indoor 60. Explanations use actual input values and scored bands, with the snow source/cap identified. The app displays ranks and labels, not these numbers.

Snow depth comes from hourly meters converted to centimeters. Group original array positions by city-local calendar date, retaining repeated fall-back wall-clock labels. Require at least half the expected hourly instants (rounded up) between consecutive local midnights. Days can have 23/24/25 hours. Take the median of finite nonnegative readings, averaging the two middle values for even counts. Insufficient coverage triggers that date's snowfall fallback; valid zero depth does not.

Favorable weather does not establish resorts, coastline access, waves, tides, water temperature, shore wind direction, terrain, actual slope snowpack, operating conditions, availability, or safety.

## Cache and presentation behavior

Room stores up to three cities by default; `CachePolicy(maxCities=3)` is developer configuration, validated ≥1. Headers contain metadata and the last successful update in UTC epoch milliseconds; day rows use (city ID, ISO local date). The schema is exported under `app/schemas`; destructive migration is not enabled.

Successful validated forecasts insert/update by ID. When full, a new city evicts the smallest successful timestamp, then smallest ID on ties. Shortcuts sort by successful timestamp descending, ID ascending. Opening/searching/typing/failure never changes recency. Startup capacity reduction prunes in a transaction before emitting shortcuts. Overlapping cities serialize writes; same-city callers join one refresh.

Compare exact normalized fields (negative zero canonicalized), excluding provider timing and refresh metadata. Timestamp-only success leaves all day rows untouched. Changed dates update only changed columns; new dates insert and omitted dates delete. Metadata, changed rows, timestamp, and eviction commit atomically. Failed network/validation/storage/cancellation never publishes candidate data or advances the timestamp. Device time can repeat or move backward; timestamps are never artificially incremented.

The screen projects today plus six dates using the city timezone. Uncovered/expired dates are explicitly unavailable. Today describes the whole-day forecast, including elapsed hours. Cached cities show content first and automatically refresh once. Initial errors are full-screen; refresh errors retain content with retry. Shared selected-date state synchronizes timeline and pager. Date/city keys and saveable page scroll state preserve navigation across refreshes. Equal immutable day UI values are reused.

On resume and visible city-local midnight, reconcile the window, retain a still-valid selected date, otherwise select today, and refresh once. A rollover during an active refresh queues one refresh of the new window. Leaving cancels collection/network work and prevents stale publication. Rotation retains ViewModels; platform saved state restores a running city/date and search text after process death. A new launch starts at search.

## Tests and verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:connectedDebugAndroidTest
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The connected suite requires a running API 24+ emulator/device visible to adb. Tests use JUnit, coroutines-test, handwritten fakes, MockWebServer, and focused Room instrumentation. They cover score thresholds, labels, worked examples, missing data, complementarity, medians/DST, URL parameters, response validation, transport recovery, cache eviction/rollback/selective writes/coherence/persistence, submission identity, cancellation, refresh preservation, saved state, and rollover. Coroutine timing uses injected dispatchers/Clock and virtual time rather than real sleeps.

Actual commands, counts, manual checks, environment limitations, and phase evidence are recorded in [docs/verification.md](docs/verification.md). Live API checks supplement deterministic fixtures; they are not test oracles. Full UI automation and snapshot tests are intentionally excluded.

## Assumptions, omissions, and production work

English-only interface, one Android app module, manual DI, public APIs, device-clock recency, no background sync service, no live/debounced search, no GPS, no geocoding cache, no theme toggle, and no ocean/resort integrations. Advanced animation and visual refinement (optional phase 8) are deferred. No GitHub publication or submission has been performed.

Before production: validate scoring with domain experts and users; add actual activity/ocean/resort context if appropriate; account for forecast uncertainty; consider server-authoritative update time; review API usage/licensing and request budgets; implement/test schema migrations, release signing/minification, backup/security policies, localization, dependency upgrades, broader device/accessibility coverage, and monitoring. The small unit/instrumented scope does not establish complete UI coverage.

A native Swift implementation could reproduce the same city/API/date/scoring/cache contracts and fixtures with SwiftUI, URLSession, and native persistence. Shared Kotlin code and an iOS deliverable are outside this scope.

## AI assistance

AI assisted requirements/specification work and generated/edited the implementation, tests, documentation, and verification workflow. The numerical rules come from the accepted specification. Explanations are deterministic code, not live AI generation. Build/test execution and emulator observations are distinguished from assumptions in the verification record. No fixed implementation time budget was imposed; phase commit timestamps document the work session.

# WeatherPal implementation specification

Status: final implementation specification, version 1.0, 2026-10-07.

This document consolidates the user's agreed product decisions and the engineering
defaults needed to implement them. The scoring model was reviewed interactively
and accepted. Implement phases in section 9 in order. A phase is complete only
when its stated verification passes. This document does not claim that the app
has been implemented, built, or tested.

## Contents

1. Source and purpose
2. Requirements from the brief
3. Product behavior and scoring
4. Cache and refresh behavior
5. Architecture and testing decisions
6. Models, interfaces, and persistence
7. API contracts and response validation
8. Presentation state and edge cases
9. Ordered implementation phases
10. Delivery, limitations, and decision rationale

## 1. Source and purpose

Source brief: `C:/Users/linco/Downloads/Native Mobile Engineer Test.pdf`.

The requested work is to collaboratively define requirements, resolve design
decisions, and produce a precise specification with separate, ordered implementation
phases. Requirements in the brief are source material, not authorization to publish
a repository or begin app implementation.

## 2. Requirements from the brief

- Build a native mobile app with city search and weather-based activity rankings
  for seven days.
- Use Open-Meteo Geocoding for city search and Open-Meteo Forecast for weather.
- Rank skiing, surfing, outdoor sightseeing, and indoor sightseeing.
- Separate UI, domain logic, API access, and state management.
- Model states explicitly; handle errors robustly; make presentation and data
  dependencies testable and mockable.
- Include meaningful tests of important logic and state handling.
- Build and run locally.
- The submission deliverables are application code, tests, and a README in a
  GitHub repository.
- README topics: overview; platform/tooling; architecture; build/run instructions;
  test instructions and strategy; API usage; recommendation logic; assumptions;
  trade-offs/omissions; production readiness; cross-platform delivery; AI usage
  disclosure and verification.
- The brief estimates 3-4 hours and values architecture, correctness, testing, and
  communication above visual polish and feature volume. An actual implementation
  time budget has not been agreed.

## 3. Agreed product decisions

### 3.1 Search and daily results

- User journey: search for a city, select a matching location, view daily rankings.
- City search runs only on explicit submission, not as the user types. This is
  the agreed current scope; live search and debounce behavior are excluded.
- Submission uses the current query. Editing text alone must not issue a
  geocoding request or change cached forecast content.
- Launch into the search screen, not directly into a previously viewed city's
  forecast.
- List latest searched city shortcuts on the search screen. Shortcuts open the
  corresponding cached city's daily carousel and trigger the agreed automatic
  background refresh.
- These shortcuts expose the configured cache contents (three cities by default)
  so cached forecasts remain accessible offline.
- Order shortcuts by last-successfully-updated timestamp descending, newest
  first. Reuse the same timestamp that determines cache eviction; do not track a
  separate selection/search-recency timestamp. Rationale: one consistent recency
  definition and no unnecessary metadata.
- A successful automatic or explicit refresh may reorder shortcuts. Merely
  opening a city, editing/submitting a query, or a failed refresh cannot reorder
  them. Present them as recent cached cities rather than implying a strict
  history of search submissions.
- The keyboard search action and visible search button invoke the same submission
  operation. Section 8 defines validation and repeated-submission behavior.
- Display seven days: today and the following six days, using the selected city's
  local dates.
- Each day ranks all four activities using that day's forecast.
- Today's ranking describes the whole day's forecast, including elapsed hours.

#### Agreed daily navigation and refresh behavior

- Show a horizontal timeline of seven clickable city-local dates above a
  horizontal carousel of daily activity data.
- Each carousel page represents one date and contains that day's weather summary
  and ranked activities, including labels and explanations.
- Swiping the carousel selects its date in the timeline; tapping a timeline date
  navigates the carousel to the matching page.
- Both controls use one shared selected-date state; they must not maintain
  independent selections that can diverge.
- Refreshing updates the selected city's forecast without resetting navigation
  or other unaffected UI state. Keep the selected city and date, carousel position,
  and relevant scroll state intact while those identities remain valid.
- Use city ID and local date as stable content identities, rather than treating a
  page index as the identity of a forecast date.
- Keep existing content visible during refresh, with refresh progress modeled
  separately from initial loading.
- Publish a complete, coherent forecast snapshot in one atomic content update;
  never expose partially updated dates, weather inputs, rankings, or explanations.
- Apply content changes only where values changed; do not rebuild or replace
  unchanged content solely because a refresh occurred. Successful refresh metadata
  may change even when all forecast content is unchanged.
- An error leaves the previous content and last successful update timestamp
  intact while updating refresh/error state appropriately.
- On city-local date rollover, recompute the seven-day window. Preserve the
  selected date if it remains in the window; otherwise select today. Reconcile
  on resume and while the forecast screen remains visible across midnight.
- Fully expired caches show all seven dates as unavailable, retain city identity
  and the last successful update timestamp, and provide a refresh/retry action.
  State preservation must not cause stale dates to masquerade as current dates.

### 3.2 Recommendations

- Rank based only on weather.
- Use deterministic internal numerical scores on a 0-100 scale for ordering.
- Show rankings and suitability labels rather than numerical scores.
- Include short weather-based explanations.
- Weather inputs are temperature, precipitation, snowfall, wind, and snow depth.
  Exact API variables and normalized units are specified below and in section 7.
- Skiing favors cold and snowy conditions; outdoor sightseeing favors mild, dry,
  calmer weather; indoor sightseeing becomes relatively more attractive in
  unpleasant outdoor weather. Surfing is a limited weather-comfort assessment.
- Suitability labels: Very favorable for scores 90-100; Favorable for 70-89;
  Mixed for 40-69; Unfavorable for 0-39. The boundaries are inclusive and
  non-overlapping.
- For skiing, prefer snow depth when usable for the forecast date; otherwise use
  forecast snowfall. A valid zero snow depth is available data, not a missing
  value, and must not trigger the fallback.
- If neither snow depth nor snowfall is usable, do not assume zero or invent a
  snow signal. Show Insufficient data for skiing.
- Use the accepted component-point formulas in section 3.3, clamp to 0-100,
  derive explanations from the rules, and sort unscored activities last.

#### Verified snow data contract

Open-Meteo's [Forecast documentation](https://open-meteo.com/en/docs), checked
2026-10-07, documents hourly `snow_depth` in meters and daily `snowfall_sum` in
centimeters. Request hourly snow depth alongside daily forecast fields; normalize
units explicitly before scoring. Snow depth is not listed as a daily variable in
the documented daily parameter definitions.

Use the median of valid hourly snow-depth readings for each city-local calendar
date. For an even reading count, average the two middle sorted values. Require at
least half the expected hourly readings for that local day, rounded up; compute
the expected count from the city's timezone, rather than assuming every day has
24 hours. This accounts for daylight-saving transitions. A valid reading is finite
and nonnegative. Missing or unusable depth triggers the snowfall fallback for
that date only; zero does not. Snow depth remains a modeled value for the forecast
location and does not verify resort or slope conditions.

### 3.3 Accepted scoring rules

The accepted thresholds below are product heuristics for this exercise, not
scientifically validated activity recommendations. Scores are sums of component
points, not probabilities. All scores are integers, clamped to 0-100.
All input intervals below are exhaustive and non-overlapping. Explanations must
describe the actual inputs and rules, without claiming activity availability or
safety. Weather fields used by a score must be valid; missing values do not become
zero. Rank unscored activities after scored activities.

Daily inputs:

- T: `temperature_2m_mean`, degrees Celsius.
- P: `precipitation_sum`, millimeters, including rain and snow.
- W: `wind_speed_10m_max`, kilometers per hour.
- D: accepted daily median snow depth, converted from meters to centimeters.
- S: `snowfall_sum`, centimeters.

Finite temperatures are required. Precipitation, wind, snowfall, and snow depth
must also be nonnegative. Scoring validation is separate from response-structure
validation and must allow missing optional snow data without losing other scores.

#### Outdoor sightseeing

Score = temperature points + precipitation points + wind points.

- Temperature: 45 if 15 <= T <= 25; 30 if 5 <= T < 15 or 25 < T <= 30;
  10 if 0 <= T < 5 or 30 < T <= 35; otherwise 0.
- Precipitation: 35 if P < 1; 20 if 1 <= P < 5; 5 if 5 <= P < 15;
  otherwise 0.
- Wind: 20 if W <= 15; 10 if 15 < W <= 30; otherwise 0.
- Require T, P, and W.

#### Surfing weather comfort

Score = temperature points + precipitation points + wind points.

- Temperature: 45 if 20 <= T <= 30; 30 if 15 <= T < 20 or 30 < T <= 35;
  10 if 10 <= T < 15; otherwise 0.
- Precipitation: 30 if P < 1; 15 if 1 <= P < 5; otherwise 0.
- Wind: 25 if W <= 15; 10 if 15 < W <= 25; otherwise 0.
- Require T, P, and W.
- Explain that this measures land-weather comfort; it does not assess waves,
  water temperature, wind direction relative to shore, tides, or surfability.

#### Skiing

Score = snow points + temperature points + wind points.

- When D is usable: snow points are 60 if D >= 30; 40 if 10 <= D < 30;
  15 if 0 < D < 10; 0 if D = 0.
- Otherwise, when S is usable: snow points are 45 if S >= 10;
  30 if 3 <= S < 10; 15 if 0 < S < 3; 0 if S = 0.
- Temperature: 25 if -10 <= T <= 0; 15 if -20 <= T < -10 or
  0 < T <= 5; otherwise 0.
- Wind: 15 if W <= 15; 8 if 15 < W <= 30; otherwise 0.
- If the chosen snow signal is zero, cap the final score at 39, since the
  preferred signal provides no evidence of snow cover or fresh snowfall.
- Require T, W, and either usable D or usable S.
- Identify the snow source in the explanation. Snowfall-only scoring deliberately
  has a lower maximum (85) because it cannot establish a snow base.

#### Indoor sightseeing

- Agreed decision: score = 100 minus the outdoor sightseeing score, using the
  same required inputs. Derive this from the numeric score before ranking; never
  derive it from the outdoor label or position.
- Interpret this as the relative appeal of choosing indoor plans because of the
  weather, not the intrinsic quality or comfort of an indoor attraction.
- Pleasant outdoor weather therefore lowers this score; poor outdoor weather
  raises it. This is a deliberate product assumption, not a claim that indoor
  activities become intrinsically less suitable in good weather.
- Document the relative scoring relationship in the final specification and
  README; make the in-app explanation clear, for example: "Indoor plans are more
  appealing because rain makes outdoor sightseeing less comfortable."
- If outdoor sightseeing cannot be scored due to missing required inputs, indoor
  sightseeing must also show Insufficient data. Never infer a score of 100 from
  an absent outdoor score.

#### Ordering and examples

- Sort by unrounded internal score descending. These rules produce
  integer scores, so no display rounding is required.
- For equal scores, use the source brief's fixed order: skiing, surfing, outdoor
  sightseeing, indoor sightseeing. Apply the same order among unscored activities.
- Example: T = 22, P = 0, W = 10, D = 0 gives skiing 15 (Unfavorable),
  surfing 100 (Very favorable), outdoor 100 (Very favorable), indoor 0
  (Unfavorable). Surfing precedes outdoor because of the fixed tie-break.
- Example: T = -5, P = 2, W = 10, D = 40 gives skiing 100 (Very favorable),
  surfing 40 (Mixed), outdoor 40 (Mixed), indoor 60 (Mixed).
- Example: if both snow inputs are missing but T/P/W are valid, skiing is
  Insufficient data; the other three activities remain scored.

### 3.4 Suitability limitations

- A favorable ranking does not establish that an activity is available or safe.
- Weather-only rankings do not verify ski facilities, terrain, actual slope
  snowpack, coastline
  access, surf spots, waves, tides, or operating conditions.
- Document these limitations in this specification and the README, and provide a
  concise explanation in the app.

### 3.5 Optional features selected from the brief

- Include persistent offline caching, pull-to-refresh, and dark mode.
- Exclude snapshot tests and full UI test coverage; meaningful unit tests remain
  required.
- Defer advanced UI polish and animation until the functional scope is complete.
- Follow the device's light/dark setting. No in-app theme switch is in scope.

## 4. Cache and refresh decisions

### 4.1 Capacity

- Cache up to three cities by default. This supersedes the earlier single-city
  decision.
- Capacity is developer-defined configuration, not a user-facing setting.
- Each entry consists of the city ID as its unique key, the city/forecast data as
  its value, and a last-successfully-updated timestamp.
- Use the selected Open-Meteo geocoding result's city ID as the key; city names
  alone must not identify entries.
- On a validated successful forecast response, look up the city ID. If present,
  update that entry and its successful-update timestamp. If absent and capacity
  remains, insert a new entry. If absent and full, evict the entry with the oldest
  successful-update timestamp and insert the new entry.
- Eviction is based on successful data updates, not search typing or selection
  recency. Viewing cached data alone does not advance its timestamp.
- Failed updates do not insert entries, evict entries, or advance timestamps.
- Define `CachePolicy.maxCities: Int = 3` in the manual DI composition root;
  inject it into the repository. Require a value >= 1. Tests supply smaller
  capacities. Do not add a settings screen or remote configuration service.
- Break equal timestamps by city ID ascending for both shortcut ordering and
  eviction. Shortcut sort is timestamp descending, then city ID ascending;
  eviction sort is timestamp ascending, then city ID ascending.
- If developer configuration reduces capacity, prune excess entries in a startup
  transaction before publishing shortcuts. This configuration maintenance is
  separate from failed forecast requests, which never evict anything.

### 4.2 Date correctness

- Align cached forecasts with the selected city's current local date window.
- Explicitly show dates without cached forecast data as unavailable.
- Never substitute another date's forecast for an unavailable date.
- Opening a cached city immediately shows its available cached data and triggers
  an automatic background forecast refresh. Pull-to-refresh also remains
  available for explicit updates.
- Background refresh follows the same atomic update, state preservation,
  successful timestamp, and error rules as explicit refresh.
- When offline or when the request otherwise fails, retain cached data and the
  last successful timestamp, stop refresh progress, and expose the refresh error.
- Opening the city alone does not change its successful-update timestamp; only
  successful validation and commit of a refresh may advance it.
- Application startup opens search with cached city shortcuts, as specified in
  section 3.1. Fully expired caches follow the unavailable-day presentation
  defined in that section.

### 4.3 Successful refresh

- Replace only forecast data that actually changed.
- A successful refresh updates the last-successfully-updated timestamp even if
  forecast content is unchanged.
- An existing-key update is a logical replacement: retain unchanged content and
  write changed content plus successful-update metadata. It must not require
  rewriting unchanged forecast records merely to update the timestamp.
- Commit changed data, successful-update metadata, and any required eviction
  atomically so a failed storage operation cannot leave a partially updated cache.
- Complete validation, content comparison, and ranking derivation before
  publishing the resulting content snapshot. Cache observers must not see an
  intermediate data/metadata combination; the UI must not combine weather from
  one snapshot with rankings from another.
- Preserve the agreed carousel and timeline state across updates. Loading,
  refresh status, and errors are explicit presentation states and must not reset
  existing successful content.
- An unchanged forecast must not be treated as a refresh failure.
- Compare normalized domain fields by city ID and local date, excluding refresh
  timestamps and provider generation metadata. Section 6 defines persistence and
  publication semantics.

### 4.4 Failed refresh

- A failed refresh must not advance the last-successfully-updated timestamp.
- Preserve usable cached data; represent the failure explicitly.
- Validate responses using section 7 before committing so invalid responses
  cannot silently replace valid data.
- With cached content, show a non-blocking error and retry action while keeping
  content accessible. With no content, show a full error/retry state.
- Cancellation is not a user-visible failure and never advances the timestamp.
- Track request identity and selected city. Older search/forecast responses must
  not overwrite newer results or another city's visible state. Deduplicate
  concurrent refreshes for the same city; do not allow out-of-order commits.

## 5. Technical decisions

### 5.1 Manual dependency injection

- Use manual dependency injection.
- Rationale agreed with the user: make dependencies easy to replace in tests for
  this engineering test.
- Keep dependency construction separate from business logic and presentation.
- No DI framework is selected.

### 5.2 Code organization and stack

The current project is an Android/Kotlin scaffold. Use one app module with separate
UI, domain, and data packages and manual dependency injection. Rationale: keep
scoring independent of Android, data access replaceable, and presentation logic
testable without introducing extra Gradle module overhead.

Use Jetpack Compose with Material 3, MVVM, Coroutines/StateFlow, and repository
interfaces. These choices were accepted with the engineering defaults. Use Room
for persistent storage and Retrofit with OkHttp and kotlinx.serialization for
HTTP/JSON. Room transactions support coherent updates and cache queries; typed
HTTP DTOs keep transport details outside the domain.

Use JUnit and kotlinx-coroutines-test with handwritten fakes for the main unit
suite. Use MockWebServer for HTTP contract checks and a small instrumented Room
suite for persistence/transaction checks. A DI or mocking framework is not needed.

Keep the existing SDK baseline (minSdk 24, compile/target SDK 36) unless dependency
verification establishes a concrete incompatibility. Keep dependency versions
explicit in `gradle/libs.versions.toml`. Resolve and pin a compatible set in phase
1; do not use dynamic versions or assume that the newest release works with the
existing Kotlin/AGP baseline. Use Kotlin's Compose compiler plugin at the same
version as Kotlin. Enable core-library desugaring for java.time on API 24/25.
Document the actual Gradle JDK used; the Java/Kotlin bytecode target is a separate
choice and is not a statement that the Gradle daemon can run on JDK 11.

Package responsibilities under `com.example.weatherpal`:

- `domain/model`: city, forecast, recommendation, and failure types; no Android,
  Room, Retrofit, or Compose imports.
- `domain/repository`: interfaces used by presentation.
- `domain/scoring`: pure scoring, labels, explanations, ordering, snow aggregation,
  and date-window calculations.
- `data/remote`: API interfaces, DTOs, JSON configuration, and mappers.
- `data/local`: Room entities, DAOs, database, and transactional cache operations.
- `data/repository`: repository implementations and refresh coordination.
- `ui/search`, `ui/forecast`, `ui/theme`: screens, ViewModels, explicit state, theme.
- `di`: application-scoped composition root and ViewModel factories.

Pass repositories, a `Clock`, dispatcher providers, and cache policy through
constructors. Keep Activities/Composables free of dependency construction and
scoring logic. Use one application-scoped database and HTTP client. ViewModels
receive interfaces, not DAOs or Retrofit services. Do not add a use-case class
for every forwarding method; extract a use case only for meaningful orchestration.

### 5.3 Verification scope

- Unit-test exact score boundaries, labels, ties, missing inputs, indoor/outdoor
  complementarity, snow-depth preference, fallback, and hourly aggregation.
- Test city-ID cache lookup, capacity, timestamp ordering, eviction, unchanged
  successful updates, failed requests, and atomic commit rollback.
- Test search submission, cancellation/stale responses, refresh progress and
  errors, carousel/timeline synchronization, date rollover, and preservation of
  selected date across changed and unchanged refreshes.
- Verify local persistence with a restart and inspect the critical user flow on
  a device/emulator in light and dark modes. Full UI coverage and snapshots remain
  excluded.
- Each implementation phase must list concrete acceptance criteria and the
  commands/manual checks that verify them. Do not call checks passed without
  actually running them.

## 6. Models, interfaces, and persistence

### 6.1 Domain contracts

- `City`: ID (Long), name, optional administrative region and country, latitude,
  longitude, and IANA timezone ID. Display name, region when present, and country
  to disambiguate results. Names are labels; IDs are identity.
- `DailyWeather`: LocalDate; nullable mean temperature Celsius, precipitation mm,
  maximum wind km/h, median snow depth cm, daily snowfall cm; snow-depth valid and
  expected sample counts. Null means unavailable, not zero.
- `ForecastSnapshot`: city, immutable days keyed by LocalDate, lastSuccessfulUpdate
  as Instant. Exclude provider execution-time metadata from semantic equality.
- `Activity`: SKIING, SURFING, OUTDOOR_SIGHTSEEING, INDOOR_SIGHTSEEING, in that fixed
  tie-breaking order.
- `Recommendation`: activity, nullable integer score, label, immutable reason
  codes/parameters, and snow source where relevant. Scores are not displayed in
  the app. Translate reason codes to strings in the UI; do not persist prose.
- `ForecastDayUi`: local date, Available(weather summary, four recommendations)
  or Unavailable. A present day with missing scoring inputs can show its available
  weather values and Insufficient data for affected activities.
- `AppFailure`: validation, network/unreachable, timeout, HTTP/rate-limited,
  malformed response, unusable forecast, or storage failure. Preserve diagnostic
  detail internally; present actionable, nontechnical messages. Cancellation is
  propagated as cancellation, not converted into AppFailure.

Repository interfaces expose these operations:

- `CityRepository.search(query): SearchOutcome` as a suspend operation.
- `WeatherRepository.observeCachedCities(): Flow<List<CachedCitySummary>>`.
- `WeatherRepository.observeForecast(cityId): Flow<ForecastSnapshot?>`.
- `WeatherRepository.refresh(city): RefreshOutcome` as a suspend operation,
  returning committed success (content changed or unchanged) or a typed failure.
- Pure `ActivityScorer.rank(day)` and date-window/snow aggregation functions.

Use sealed outcomes rather than string errors. Repositories translate transport
and database exceptions into domain failures. UI cannot call networking directly.

### 6.2 Persistent schema

Use two Room entities in database schema version 1:

1. `CachedCity`: city ID primary key, display metadata, coordinates, timezone,
   lastSuccessfulUpdateEpochMillis. This is the cache header and eviction key.
2. `CachedForecastDay`: composite primary key (cityId, localDate ISO string),
   normalized weather columns from DailyWeather and depth coverage counts;
   foreign key cityId referencing CachedCity with cascade deletion.

Keep timestamps in UTC epoch milliseconds. Format them in the selected city's
timezone and identify them as the last successful update, not forecast generation
time. Use an injected Clock; never substitute an HTTP generationtime field.

Read a city header and its day rows in one transaction, including observable
reads, so consumers receive a coherent snapshot. Do not persist numerical scores
or UI state in weather rows; derive recommendations using the current scoring
rules. Export Room schema files to the repository; do not enable destructive
migration as a default future upgrade strategy.

### 6.3 Refresh transaction and comparison

For a successful response, execute this order:

1. Fetch outside the database transaction. Validate and normalize using section 7.
2. Derive and validate recommendations for the candidate forecast. Check request
   cancellation/generation before starting a commit.
3. Enter a serialized cache write transaction. Re-read existing city/header/days
   and capacity inside it; do not make eviction decisions from a stale read.
4. For a new city at capacity, delete the oldest entry using section 4.1's ordering.
   Insert the new header and its day rows in the same transaction.
5. For an existing city, compare normalized fields. Insert new dates, delete dates
   absent from the accepted replacement snapshot, and update only changed weather
   columns in affected dates. Leave unchanged day records untouched. Apply the
   same changed-field rule to city metadata.
6. Set the header's lastSuccessfulUpdate to Clock.instant() for both changed and
   unchanged successful responses. The timestamp is persisted only if the entire
   transaction commits. If it has the same millisecond value as before, keep that
   truthful value; do not manufacture a later time.
7. Publish the committed immutable snapshot, deriving all visible weather,
   rankings, labels, and explanations as one presentation content value. A failed
   transaction publishes no candidate content or timestamp.

Compare exact normalized numeric values and nullable presence, canonicalizing
negative zero to zero. Do not compare raw JSON order, provider generation duration,
or refresh timestamps to decide whether weather changed. Do not introduce an
arbitrary floating-point tolerance that hides real forecast changes.

Cache operations must remain capacity-safe when distinct city updates overlap.
An unchanged refresh writes metadata only. Reuse equal immutable day UI values;
a changed timestamp/status can emit new screen state without replacing equal
weather/ranking values. This promises semantic state preservation, not that Compose
will perform zero recompositions or that object identity is persisted across
process restarts.

### 6.4 Fresh response versus previously cached values

Treat an accepted response as the replacement forecast snapshot for that city.
Do not fill fresh missing fields with old values and then present the entire
result under a new successful-update timestamp. An omitted date becomes
Unavailable; explicit missing inputs remain missing. Delete old out-of-window
rows at successful replacement. During a failed refresh, retain the old snapshot
unchanged and project only its currently relevant dates for display.

Cached city entries with expired forecasts remain accessible until normal
capacity eviction. No expiry-based city eviction or background synchronization
service is required. Clock corrections can affect timestamp ordering; timestamps
reflect the device clock. Document server-authoritative time as a production concern.

## 7. API contracts and response validation

### 7.1 City search

GET `https://geocoding-api.open-meteo.com/v1/search` with:

- `name`: trimmed submitted query, URL-encoded by the HTTP library.
- `count=10`, `language=en`, `format=json`.

Missing or empty results is a successful no-results state. Ignore unknown JSON
fields and tolerate absent optional display metadata. A selectable result needs
a valid ID/name, finite latitude in [-90,90], longitude in [-180,180], and a valid
IANA timezone. Filter unusable individual entries; if the provider returns only
malformed entries, show a data error rather than suggesting no matching cities.
Deduplicate valid results by city ID while preserving provider order. Never
automatically choose the first city, even if only one result is returned.

### 7.2 Forecast request

GET `https://api.open-meteo.com/v1/forecast` for the selected City's coordinates:

- `latitude`, `longitude` from that City.
- `timezone` set explicitly to the City's IANA timezone.
- `forecast_days=7`, `timeformat=iso8601`.
- `temperature_unit=celsius`, `wind_speed_unit=kmh`, `precipitation_unit=mm`.
- `daily=temperature_2m_mean,precipitation_sum,wind_speed_10m_max,snowfall_sum`.
- `hourly=snow_depth`.

Use forecast coordinates only as provider metadata: a forecast grid point can
differ from requested city coordinates. It is not a replacement city identity.
Request context associates the response with its selected city ID.

Snow-depth meters must become centimeters before scoring. Precipitation includes
snow water equivalent, whereas snowfall is measured separately in centimeters;
these are not interchangeable units. Daily time strings are LocalDates, not UTC
midnights. Group hourly ISO local-time labels by their date in the requested city
timezone; never convert them through the device timezone. Compute the expected
number of hourly instants between consecutive local midnights for the coverage
rule (23/24/25 for ordinary daylight-saving transitions). Use original hourly
array positions as samples; do not collapse repeated wall-clock labels during a
fall-back transition. The accepted median is over valid returned samples, not an
interpolated or resampled series.

### 7.3 Accepted, partial, and rejected responses

- Require HTTP success, decodable JSON, a valid matching requested timezone, and
  a nonempty daily date axis with valid, strictly increasing, unique dates.
- Any present daily value array must match the daily date-axis length. Missing
  arrays/null values are unavailable inputs; length mismatches are malformed.
- A usable hourly snow-depth block needs a parseable hourly date-time axis and a
  matching values array. Absent/unusable optional hourly snow data makes depth
  unavailable and triggers per-date snowfall fallback; it does not by itself
  invalidate otherwise useful daily weather.
- Reject invalid required structure, unsupported daily units, and responses with
  no computable activity for any date in the current city-local seven-day window.
  Invalid individual numeric samples become unavailable inputs. Unsupported snow
  depth units make that optional signal unavailable rather than assuming meters.
- A structurally valid partial forecast with at least one computable activity in
  the current window is an accepted success. Commit it atomically, advance the
  timestamp, and show missing dates/inputs explicitly. Do not claim full coverage.
- Never interpolate missing daily data, reuse another day's data, or invent
  zero-valued weather to make a score computable.
- If all data is unusable or the response is malformed, preserve the previous
  cache and timestamp and expose a refresh error.

### 7.4 Transport and attribution

Use HTTPS, INTERNET permission, suspend HTTP calls, and cancellable structured
coroutines. Set connect timeout 10 seconds, read timeout 15 seconds, and total
call timeout 20 seconds. Do not implement automatic application retry loops;
retry is explicit through submission, opening a cached city, or refresh/retry.
HTTP 429 gets a clear rate-limit message; respect Retry-After when supplied by
disabling explicit retry until that time. Redact verbose response logging from
release builds. No location permission, GPS feature, backend, or API secret is
required for the test's public API usage.

Provide visible Open-Meteo attribution and GeoNames attribution for location
data, with links in an app information/limitations dialog and README. Commercial
deployment must reassess API access/usage terms; do not claim that a test's access
configuration is sufficient for all production usage.

Reference contracts, checked 2026-10-07:

- [Open-Meteo Geocoding](https://open-meteo.com/en/docs/geocoding-api).
- [Open-Meteo Forecast](https://open-meteo.com/en/docs).
- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations).
- [Room persistence](https://developer.android.com/training/data-storage/room).
- [Room release compatibility](https://developer.android.com/jetpack/androidx/releases/room).
- [Kotlin Compose compiler plugin](https://kotlinlang.org/docs/whatsnew20.html).

## 8. Presentation state and edge cases

### 8.1 Search screen

Keep query text and last-submitted query separate. Model search status as Idle,
Loading(requestId, submittedQuery), Results, Empty, or Error; cached shortcuts
remain separately available in all states.

- Trim surrounding whitespace on submit. Reject fewer than two Unicode code
  points with an inline validation message and no request. Two-character matching
  is limited by the API; explain this in API usage notes.
- Typing updates query text only. Search button and IME action submit identically.
- Ignore repeated identical submissions while that query is in flight. A different
  submitted query cancels/supersedes the old request and advances request identity.
  Only the latest submitted request may publish results or errors.
- After completion, explicit resubmission may search again. A retry resubmits the
  failed submitted query, not silently changed text. Label results by submitted
  query while the input is edited.
- Show a concise empty state for no matches. Keep recent cached-city shortcuts
  available during search failures and while offline; geocoding results themselves
  are not cached in this scope.
- Selecting any result always opens its city. If already cached, use the cached
  display path; otherwise show initial forecast loading. A failed new-city load
  neither creates a shortcut nor evicts a valid city.

### 8.2 Forecast screen

Represent content and refresh independently. Content is InitialLoading,
Ready(city, seven day models, selectedDate, lastSuccessfulUpdate), or
InitialError(city, failure). Refresh status is Idle, Refreshing, or Failed.
Ready may contain unavailable days, including an entirely expired cache.

- Entering a city chooses today. Navigating between dates uses one selectedDate
  shared by timeline and pager. Keep page keys as city ID + LocalDate.
- On cached entry, publish Ready immediately and request one background refresh.
  On uncached entry, load once and publish Ready only after an accepted commit.
- Pull-to-refresh and retry use the same refresh operation. Repeated requests for
  the same city while a refresh is active join/ignore that in-flight operation;
  they do not start a second request or a later stale commit.
- On success, retain selection and scroll positions by date, replace only changed
  content values, update the timestamp, clear prior refresh error, and stop progress.
- On failure with Ready, retain Ready and display a non-blocking error/retry banner.
  On failure without content, show InitialError with retry and Back to search.
- Switching cities or leaving the forecast screen cancels its outstanding request
  and collection. Guard publication and commit against cancellation/stale request
  identity. A late callback must not revive the old city screen or its refresh.
- Room reads are the authoritative content stream; a network response does not
  independently publish a second content stream before persistence succeeds.
- Orientation changes retain ViewModel state. Save selected city identity/date
  and search text with platform saved state. Do not issue duplicate refreshes
  merely because Compose recomposes or the device rotates.
- A fresh app launch opens search. Restoration of an already-running navigation
  session after process death may restore its selected city/date; if a cache entry
  is unavailable, use the saved City metadata for loading and handle failure.

### 8.3 Dates, accessibility, and theme

Recompute today using Clock and city timezone on entry, on resume, and at the next
city-local midnight while visible. At rollover, retain selectedDate if valid;
otherwise select today. Project seven dates even when cache coverage is shorter.
Trigger one background refresh for a newly rolled-over window while visible;
avoid repeating it on every recomposition/resume during the same open session.

Use device-following Material light/dark themes, readable contrast, scalable text,
48 dp touch targets, labeled search/retry controls, and selected-state descriptions
for dates. Timeline clicks provide an alternative to swiping for assistive input.
Do not communicate suitability or cache/error status by color alone. Standard
pager motion is allowed; advanced animation remains deferred.

Show city, region/country, city-local date, mean temperature, precipitation, wind,
four activity labels/explanations, and last successful update. Display missing
weather values as unavailable, never as 0. Format display numbers consistently
(temperature/precipitation/wind to one decimal), without rounding scoring inputs.
Keep a concise weather-only limitation visible and offer the detailed dialog.

Explanation selection is deterministic: provide a compact reason for each scored
component using its actual value/band, plus snow source and zero-snow cap where
relevant. Indoor text describes the relative outdoor-weather trade-off. It may
list three concise reasons; do not introduce free-form AI-generated explanations.

## 9. Ordered implementation phases

Work in this sequence. Tests belong to the phase introducing behavior, rather
than a final catch-up phase. Commit boundaries should follow these phases when
practical. No phase requires parallel agents or extra Gradle modules.

### Phase 1 - Buildable application foundation

Prerequisite: this specification.

Deliverables: inspect the scaffold and repository instructions; establish local
version control if absent; resolve/pin compatible dependencies; enable Compose,
serialization, Room annotation processing, and java.time desugaring; add launcher
Activity, application composition root, theme, INTERNET permission, and placeholder
search route. Preserve SDK settings unless a documented compatibility issue
requires a change. Include dependency versions in the version catalog.

Acceptance: app assembles, launches into search, and follows device theme. No API
request is issued by typing or launching. Run `./gradlew.bat :app:assembleDebug`;
record the actual JDK/SDK prerequisites. This phase does not implement scoring or
cache behavior.

### Phase 2 - Pure domain and recommendation engine

Prerequisite: phase 1.

Deliverables: domain models, repository interfaces, Clock-based local date window,
snow median/coverage calculation, all score components, labels, explanations,
tie-breaking, and missing-input behavior. Use fixtures; no HTTP or database access
in domain tests.

Acceptance: unit tests cover every threshold immediately below/at/above its
boundary; scores remain 0-100; indoor + outdoor equals 100 whenever scored; skiing
prefers valid zero depth over snowfall; snowfall-only maximum is 85; missing data
does not become zero; ordering is deterministic. Cover even/odd medians, 11 versus
12 valid samples on a 24-hour day, and coverage on 23/25-hour days. Verify the
worked examples in section 3.3. Run `./gradlew.bat :app:testDebugUnitTest`.

### Phase 3 - HTTP contracts and normalization

Prerequisite: phase 2.

Deliverables: separate geocoding/forecast services and DTOs; serialization/mapping;
timeouts; typed failures; response validation and snow-unit conversion. Capture
representative fixtures without relying on live forecasts for deterministic tests.

Acceptance: MockWebServer verifies submitted parameters, query encoding,
coordinates/timezone, requested fields/units, and HTTP error mapping. Fixture tests
cover absent geocoding results/metadata, malformed arrays, optional missing depth,
null values, partial forecasts, invalid units, and timezone/date interpretation.
Cancellation is propagated. Run the unit suite. A manual live request can confirm
service integration but cannot replace fixture assertions.

### Phase 4 - Persistent transactional cache and repository

Prerequisite: phases 2-3.

Deliverables: Room schema/DAOs and coherent observable snapshot reads; injected
capacity; exact field comparisons; atomic commit/eviction; successful timestamp
updates; same-city refresh coordination and serialized cache commits.

Acceptance: test insert/update by ID, unchanged content with a newer success
timestamp, changed-day/column updates leaving other records intact, oldest-first
eviction, deterministic ties, configurable capacity, and capacity reduction.
Network/validation/storage failures preserve old content/timestamps and do not
evict. Force a transaction failure after an eviction attempt and verify rollback.
Verify observer snapshots never combine old/new tables. Reopen the database and
verify persistence. Run unit tests and the focused Room instrumented suite via
`./gradlew.bat :app:connectedDebugAndroidTest` on a configured emulator/device.

### Phase 5 - ViewModels and state transitions

Prerequisite: phase 4.

Deliverables: search and forecast ViewModels, explicit state/outcomes, request
identity/cancellation, shared selected date, refresh status separate from content,
saved-state handling, midnight/resume reconciliation, and manual factories.

Acceptance: fake-repository tests verify submit-only search, duplicate submit
handling, old-response rejection, immediate cached content, automatic refresh,
same-city request deduplication, uncached loading/retry, failure preservation,
unchanged/changed successful refresh timestamps, selection retention, cancellation
on navigation, and rollover fallback to today. Run the unit suite with injected
dispatchers and Clock; do not use real sleeps for coroutine timing assertions.

### Phase 6 - Search, timeline, carousel, and refresh UI

Prerequisite: phase 5.

Deliverables: search input/button/IME action, matching-city list, cached shortcuts,
navigation, seven-day timeline, horizontal daily pager, weather summary, ranked
labels/reasons, unavailable dates, refresh gesture/progress, errors/retry,
timestamps, attribution/limitations, and accessible light/dark presentation.

Acceptance: manually verify the complete flow and both directions of timeline/
pager synchronization. Refresh while viewing a future date: keep that date and
its scroll position while replacing content atomically. Check font scaling,
touch targets, rotation, light/dark theme changes, and keyboard operation. All four
activities remain represented; scores are internal. Run assemble, unit tests,
and `./gradlew.bat :app:lintDebug`. Full UI coverage and snapshot tests are excluded.

### Phase 7 - Integration and delivery verification

Prerequisite: phase 6.

Deliverables: complete README, phase test evidence, build/run instructions, and
submission-ready local repository. Address discovered functional defects before
adding polish.

Acceptance walkthrough:

1. Fresh launch -> search -> submit -> choose a disambiguated city -> seven dates.
2. Load three cities; update one; verify shortcut order. Load a fourth; verify the
   oldest successful-update entry is evicted.
3. Restart offline; open a retained shortcut; verify cached content, unavailable
   uncovered dates, failed automatic refresh, and unchanged timestamp.
4. Restore connectivity; pull to refresh while on a future date; verify selection
   preservation and one coherent update. Exercise an unchanged successful response
   with a deterministic fixture and verify timestamp-only update.
5. Exercise malformed/partial responses, no search matches, rate limiting, expired
   cache, and request cancellation with controlled fixtures/fakes.
6. Verify device timezone differs from city timezone and a midnight rollover;
   confirm dates and missing-day presentation remain correct.

Run `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` and the
focused instrumented suite. Record actual results and any environment limitations;
never describe unrun tests as passing. GitHub publication/submission is a separate
user-authorized action; preparing the repository does not publish it.

### Phase 8 - Deferred visual refinement, only if chosen later

Prerequisite: phase 7 acceptance passes and the user chooses to include refinement.

Possible scope: advanced visual polish and animation. Keep existing navigation,
refresh/state semantics, accessibility, and tests intact. Snapshot tests and full
UI test coverage remain excluded unless the scope is explicitly changed. This
phase is not required for functional completion.

## 10. Delivery, limitations, and decision rationale

### 10.1 Required README sections

Include overview; Android/tooling and pinned versions; architecture/package and
dependency boundaries; build/run prerequisites and commands; test commands,
strategy, and actual verification; API requests, attribution, and usage notes;
exact scoring rules and worked examples; snow aggregation/fallback; indoor scoring
relative to outdoor; cache identity/capacity/timestamps/eviction/atomic updates;
state preservation and error behavior; assumptions; trade-offs and omissions;
production-readiness notes; cross-platform delivery; and AI assistance disclosure.

Production notes must identify unvalidated scoring heuristics, device-clock
dependence, forecast uncertainty, missing ocean/resort information, migration and
release hardening work, API usage terms, and the intentionally limited test/UI
scope. Do not present heuristics or the test app as a safety assessment.

Cross-platform notes should explain that a Swift implementation can reproduce the
same API/data/scoring contracts and fixtures using native presentation/persistence
tools; shared Kotlin code or an iOS deliverable is not part of this scope.

Disclose AI assistance in requirements/specification work and any later code/test
work actually performed. Verification evidence must distinguish executed tests,
manual checks, and unverified assumptions.

### 10.2 Key decisions and reasons

- Android/Kotlin uses the existing native scaffold; no backend is required.
- Daily rankings make changing conditions and explanations visible by date.
- City-local today plus six days defines the window; today's whole-day summary is
  intentionally not a remaining-hours recommendation.
- Weather-only scores keep scope aligned with the two required APIs. Snow depth
  improves the skiing proxy but does not establish resort/slope conditions.
- Numeric internals make sorting deterministic; labels avoid implying scientific
  precision in the UI. The thresholds remain explicit and testable.
- Indoor score is deliberately relative to outdoor sightseeing, not attraction
  quality. This assumption is visible in explanations and documentation.
- Manual DI makes repositories, time, dispatchers, and policy easy to replace in
  tests. One module avoids build overhead while packages enforce responsibilities.
- Persistent keyed storage supports offline use and atomic changes. One success
  timestamp defines both shortcut order and eviction; no selection timestamp.
- Developer-configured capacity defaults to three and has no user settings flow.
- Submit-only search avoids unnecessary calls and keeps interaction explicit.
- Cached-first display plus automatic refresh balances immediate access and fresh
  results; errors preserve usable content and truthful timestamps.
- Carousel/timeline navigation uses stable local-date identity so refreshes do not
  reset the selected day or silently show a different date.
- Dark mode follows the device. Caching, pull-to-refresh, and dark mode are in
  scope; advanced polish is deferred; snapshots/full UI coverage are excluded.

### 10.3 Completion boundary

The specification is complete for implementation planning. Dependency resolution
and pinning is a defined phase-1 deliverable, not evidence of a verified build at
specification time. The brief's 3-4-hour estimate remains contextual: the selected
bonus features add work, and this document does not promise completion within that
estimate. Record actual implementation time and trade-offs honestly.

Functional implementation is complete only after phases 1-7 pass their acceptance
criteria and the README accurately reports the resulting app. Phase 8 is optional.

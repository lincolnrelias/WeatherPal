# WeatherPal UI redesign

Implemented October 8, 2026 (America/Sao_Paulo).

## Experience and visual direction

The navigation remains deliberately focused: search for a city, disambiguate it,
then compare seven city-local dates. A labeled Cities back action returns to
search; device Back does the same. From search results, device Back or the toolbar
Back control clears the search and returns to the welcome card at the top, with
recent places preserved. Forecast updates remain available through pull-to-refresh,
without a separate refresh button. About remains available from either screen.

A warm off-white and forest-green palette, editorial serif display type,
consistent line icons, rounded surfaces, and photographic activity cards create
a cohesive travel-oriented interface. Both light and dark system themes have
explicit text, surface, outline, error, and accent colors.

Search opens with a scenic welcome, a clearly labeled city field with a clear
control, keyboard search support, explicit loading and validation feedback,
disambiguated matches, and saved recent places with update timestamps.

The forecast leads with the city, a seven-day strip with daily mean temperatures,
and a concise whole-day weather overview. Precipitation and maximum wind remain
visible. Snow depth and snowfall are available under Snow details. A compass is
used as a decorative planning motif: the API does not supply cloud cover or
hourly conditions, so the design does not invent sunny/cloudy forecasts.

Activity cards show their rank, full activity name, suitability text, a photo,
and an expansion affordance. All cards start collapsed. Tapping the full header
animates their weather explanations and activity-specific limitations into view.
Expansion is keyed to activity within the city/date page, survives ranking
changes and refreshes, and is restored when returning to a date or recreating the
screen. Color is supplemented by suitability text and expanded/collapsed
accessibility semantics. Missing dates have an explicit unavailable state.

Short viewports and larger font scales use a compact city/date header. At larger
font scales, the temperature display shrinks and the decorative compass is
omitted to leave room for the actual data. Date tiles and activity headers grow
with their content. Search and forecast have capped widths on larger screens.

## Bundled imagery

Generated using the built-in imagegen tool, then optimized to 1200-pixel-wide
JPEGs at quality 87. The four files total about 1 MB and require no runtime image
network requests. Image overlays are rendered by Compose so they stay sharp and
consistent with the UI. Images are illustrative, not location-specific evidence.

Assets in `app/src/main/res/drawable-nodpi/`:

- `activity_skiing.jpg`
- `activity_surfing.jpg`
- `activity_outdoor.jpg` (also used by the search welcome)
- `activity_indoor.jpg`

The final generation prompt set follows.

### Shared prompt for skiing, surfing and outdoor sightseeing

Use case: photorealistic-natural. Asset type: wide photographic background for a
premium Android weather activity card. Primary request: [scene below]
Composition: horizontal landscape image, about 3:2. Important scene details
remain visible in the upper and right half when cropped to a wide 2.5:1 card.
Style: exceptionally natural editorial travel photograph, tactile detail,
restrained colors, sophisticated and inviting. Constraints: photograph only;
no typography, labels, collage, borders, gradients, icons, logo or watermark.
This is aspirational activity imagery, not a claim about a specific location.

Skiing scene: A lone skier seen from behind carving down a pristine alpine slope
beneath dramatic snow-covered mountains and crisp pale blue sky. Pine forests
in the valley. The skier is small, right of center, wearing burnt orange. Cold
whites, slate blues, subtle warm sunshine.

Surfing scene: A single surfer with a cream surfboard at the waterline of a
tranquil turquoise cove, viewed from slightly elevated beach level. Natural
rolling waves, distant green headland, glowing soft afternoon light. Surfer small
and right of center. Teal water and sand colors.

Outdoor scene: A beautiful pedestrian street in an old Mediterranean town with
pale limestone architecture, leafy trees, terracotta roofs and distant
mountains. One or two tiny pedestrians, abundant warm natural afternoon light,
elegant editorial travel photography. The view opens toward the right.

### Indoor sightseeing prompt

Use case: photorealistic-natural. Asset type: landscape photographic backdrop
for an Android indoor sightseeing card. Scene: an elegant empty art museum
interior, warm limestone walls and grand arches, oak benches, large framed
abstract landscape paintings, a glowing skylight. Only architecture and
paintings, no people or statues. Composition: wide landscape, important
architecture in upper and right half, warm afternoon light, quiet atmosphere.
Natural sophisticated editorial architectural photograph. No text, typography,
logos, collage or watermarks.

## Verification

See the October 8 UI redesign entry in `verification.md` for executed checks,
interaction tests, screenshots, and remaining device-coverage limits.

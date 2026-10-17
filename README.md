# NordPrice for Android

Android port of the iOS [NordPrice](https://github.com/Pixeelix/Elektrihind) app (v4.1): Nord Pool
day-ahead electricity prices for Estonia, Latvia, Lithuania and Finland, in 15-minute or hourly
intervals, with a price-history browser.

Kotlin + Jetpack Compose, single activity, no DI framework. The visual design is the iOS design —
same palette, cards, chart and four-level price colours — while navigation and controls behave the
Android way (bottom bar, sheets, Material date picker).

## Build and run

```bash
./gradlew assembleDebug            # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # data-layer and rule tests
./gradlew installDebug
```

Requirements: JDK 17+ (Android Studio's bundled JBR works), Android SDK platform 36 and
build-tools 35.0.0. `local.properties` must point at the SDK:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

Gradle 8.14.5 via the wrapper; AGP 8.13.2, Kotlin 2.2.21, Compose BOM 2025.12.01.

`minSdk 26` (Android 8.0), `targetSdk 36`, `applicationId koodipardik.nordprice` — one id for debug
and release, so Firebase and AdMob need only one Android app registered, the same way the iOS
bundle id covers both configurations.

## What is ported

| Screen | iOS | Android |
| --- | --- | --- |
| Today | `TodayView` | `PriceScreen(Day.TODAY)` — current price, min/avg/max, chart |
| Tomorrow | `TomorrowView` | `PriceScreen(Day.TOMORROW)` — min–max range, chart, "from 15:00" state |
| History | `HistoryView` | `HistoryScreen` — week/month/year, period average, cheapest/dearest, chart |
| Settings | `SettingsView` | `SettingsScreen` — same sections, rows and icons |
| Widget | `NordPriceWidgetExtension` (small + medium) | `NordPriceWidget` (Glance), one resizable provider that adapts to its cell span |
| iPad / large window | `DashboardView` + `HistoryView`'s wide layout | `PriceDashboard` and the wide `HistoryScreen`: cards in a 340 dp column beside each chart, Today and Tomorrow merged into one "Prices" tab |

Core rules carried over unchanged: `€/MWh` → unit conversion (÷1000, ÷1, ÷10) with a comma decimal
separator regardless of locale, per-market VAT (EE 24 %, LV 21 %, LT 21 %, FI 25,5 %), price-level
thresholds on the **raw** €/MWh figure (50 / 100 / 200), DST-aware 23/24/25-hour days, the
day-completeness rule that decides what may be cached, hourly averaging, and the history
month-at-a-time fetch with completed months cached on disk and the running month refetched daily.

Data comes from `https://dashboard.elering.ee/api/nps/price` (Elering), on plain
`HttpURLConnection` — the API is one GET returning JSON, so no networking library is pulled in.

## Deliberate differences from iOS

* **Controls are Material, the look is iOS.** Region / language / unit / chart pickers are bottom
  sheets rather than `confirmationDialog` or `navigationLink`; the history date picker is the
  Material calendar dialog rather than a graphical `DatePicker`; switches, segmented buttons and the
  bottom bar are Material 3 components in the app's palette.
* **Chart x-axis padding.** iOS declares `chartXScale(domain: -1...(count-1))`, which on a numeric
  axis cuts the last bar in half at the plot edge; Swift Charts hides this with its band padding.
  The port uses `-0.5 ...(count-0.5)` so the first and last bars get equal room.
* **Connectivity.** iOS uses `NWPathMonitor`'s `.satisfied`. `NET_CAPABILITY_VALIDATED` is a
  stricter thing (Google's own probe succeeded), so the port checks only for an internet-capable
  network — see `NetworkMonitor.currentlyOnline()`.
* **`PriceUnit` instead of `Unit`.** The iOS enum is called `Unit`; importing a class named `Unit`
  shadows `kotlin.Unit` and breaks every `() -> Unit` in the file.
* **Sparse API answers.** The iOS decoder requires all four markets to be present; Elering answers
  with `null` for a market with no data, so every list is optional here.

Not ported yet: AdMob banners and UMP consent, Firebase Analytics, and threshold notifications
(FCM + the existing `syncNotificationDevice` Cloud Function).

## Large screens

The tablet layout switches on `smallestScreenWidthDp >= 600`, which — like the iOS check for a
regular width *and* a regular height — keeps a phone in landscape on the phone layout, because
`smallestScreenWidthDp` ignores rotation.

## Widget

One Glance provider (`NordPriceWidgetReceiver`) rather than two `systemSmall`/`systemMedium`
families: an Android widget is resizable, so `LocalSize` picks the medium treatment above ~250 dp
and the small one below. Same content as the iOS widget — "Current price", the interval time, the
price with its unit, and the day's curve.

Differences worth knowing:

* **No App Group.** The widget's receiver runs in the app's own process and reads the same price
  cache and `AppSettings` the app writes. iOS had to mirror every setting into shared defaults.
* **No timeline.** WidgetKit can walk a pre-computed entry list forward by itself; a RemoteViews
  widget only changes when something updates it. `WidgetRefresh` runs a 15-minute periodic worker
  and pushes an immediate update when a complete day lands in the cache or a relevant setting
  changes. A day/night switch therefore shows up on the next update rather than instantly.
* **The curve is a bitmap.** Everything in the chart, including its axis labels, is painted into one
  image by `WidgetChartRenderer`; Glance has no positioned text inside a widget. It paints the same
  `ChartScene` the app renders live, which is what keeps the two in step — see below.
* **The iOS widget ignores the "colourful chart" switch**, so this one does too.

Settings › DEBUG › *Widget preview* (debug builds) renders the widget's own snapshot, chart style
and bitmap painter at both sizes inside the app.

## Tests

`./gradlew testDebugUnitTest` covers the parts that must not drift from iOS:
`PriceApiTest` runs the real payload Elering returned on 2026-10-17
(`app/src/test/resources/elering_2026-10-17.json`) through a loopback HTTP stub — request window
format, gzip, all four markets, the `null`-market case and non-200 handling. `CoreRulesTest` covers
the day-completeness rule (including a 25-hour DST Sunday), hourly averaging, UTC day intervals,
unit dividers and decimals, price levels and VAT rates. `WidgetSnapshotTest` covers what the widget
decides to show — interval selection, hourly averaging, VAT applied to the price but not to the bar
colour, and the empty-data case.

## Chart scene

`ui/chart/ChartScene.kt` computes the chart's geometry, colours, ticks and labels once; the app
paints the result with Compose (`ChartCanvas`) and the widget paints it into a bitmap
(`WidgetChartRenderer`). The iOS app keeps the chart logic twice — once in the app target, once in
the extension — and its own comments describe what happens when the two copies drift. Differences
between the two charts here are data (`ChartStyles.day` vs `.widget`), not a second implementation.

**Note on testing against the live API:** on a corporate network with a TLS forward proxy, every
HTTPS connection is re-signed by an internal root CA. macOS trusts it (login keychain), a clean
Android emulator does not, so on-device fetches fail with *"Trust anchor for certification path not
found"* even though the code is fine. Verify data-layer behaviour with the tests, and the live
fetch on a device on a normal network — or install the proxy's root CA into the AVD.

## Debug sample data

Settings has a **DEBUG → Sample data** switch in debug builds only, the port of the iOS
screenshot mode (`AppRuntimeConfiguration.usesSamplePriceData`). Prices and history then come from
`data/SampleData.kt`, no network is touched, and ads would be hidden in iOS. The numbers are
recognisable: today's sample curve peaks at 183 €/MWh.

# CLAUDE.md

Guidance for Claude Code (and humans) working on this repository.

## What this is

The Android port of the iOS NordPrice app (`/Users/martinpi/App/Elektrihind`). Prices, design and
behaviour are expected to match the iOS app; `README.md` lists the deliberate differences and should
be updated whenever one is added.

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug          # build the APK
./gradlew testDebugUnitTest      # run tests — this repo HAS tests, unlike the iOS app
./gradlew installDebug
```

There is no test *app* (no instrumentation tests); the tests are plain JVM tests over the data
layer and the pure rules in `core/`.

## Layout

```
app/src/main/java/koodipardik/nordprice/
  core/        pure domain: Region, Language, Day, ChartType, ChartResolution, PriceLevel,
               PriceUnit + PriceFormat, MarketDay (DST-aware day bounds, UTC ISO-8601),
               PriceMath (hourly averaging), DayCoverage (is a day complete?)
  data/        PriceApi, PriceCache (files), PriceRepository, HistoryRepository,
               NetworkMonitor, SampleData
  settings/    AppSettings — one DataStore-backed StateFlow<Settings>
  ui/
    Layout.kt  rememberIsTablet() + the 340dp side column used by both wide layouts
    theme/     Color.kt holds both palettes verbatim from the iOS asset catalog
    chart/     ChartScene.kt (shared geometry + rules), ChartCanvas.kt (Compose renderer),
               ChartStyles.kt (day / history / widget variants), ChartCards.kt, MonotoneSpline.kt
    components/ cards, flags, hero price card, sheets, connection-lost screen
    price/     PriceScreen + ChartViewModel + CurrentPriceViewModel
    history/   HistoryScreen + HistoryViewModel
    settings/  SettingsScreen
  widget/      Glance widget: NordPriceWidget, WidgetSnapshotBuilder (pure), WidgetChartRenderer
               (scene → Bitmap), WidgetRefresh (WorkManager), WidgetPreview (debug)

`AppContainer` (composition root) is created in `NordPriceApplication` and reaches composables
through `LocalContainer`; the current `Settings` through `LocalSettings`. Four long-lived
collaborators — that is the whole DI story, keep it that way.

## Rules this codebase follows

* **Phone and tablet share their building blocks, not their layouts.** `DayCards`, `DayChart`,
  `HistoryCards` and `ChartHeader` are composed by both arrangements; adding a card means adding it
  once.
* **Match iOS unless the platform disagrees.** Behavioural quirks are ported on purpose, with a
  comment saying so: the current-bar index is a proportion-of-day estimate (as on iOS, wrong on two
  DST days a year), the decimal separator is always a comma regardless of locale, and day bounds
  use the *device* zone while labels use the *region* zone.
* **Raw €/MWh is the source of truth.** Unit conversion and VAT are applied at display time. Price
  level colours use the raw figure, so switching from `€/kWh` to `cent/kWh` never recolours a bar.
* **New copy goes through resources**, in all four languages: `values/` (English default) plus
  `values-et/`, `values-fi/`, `values-ru/`. The in-app language is a per-app locale override
  (`AppCompatDelegate.setApplicationLocales`), which is why `MainActivity` extends
  `AppCompatActivity` and why the manifest carries `AppLocalesMetadataHolderService`.
* **Every non-obvious decision gets a comment explaining why**, not what. Prefer deleting a
  fallback that quietly hides a failure over keeping one.
* The chart is drawn by hand on a `Canvas`; grid/bars/curves are canvas primitives while axis labels
  and the "Now" capsule are real composables on top, so they follow the user's font scale.

## Phase 2/3 notes

* AdMob needs Android ad unit IDs (the iOS ones do not transfer); the Google Mobile Ads Gradle
  dependency, UMP for consent, and a "Privacy options" entry in Settings › About.
* Notifications can reuse the existing Firebase project (`elektrihind-76019`) and the
  `syncNotificationDevice` Callable Function: the payload already carries `platform` and
  `fcmToken`, and FCM is the native transport on Android. `POST_NOTIFICATIONS` (API 33+) has to be
  requested before a threshold toggle turns on. Registering the Android app in the Firebase console
  (with the debug and release signing SHA-1) is a manual step.
* Glance widgets are done — see `widget/`. Widget updates come from `WidgetRefresh` (a 15-minute
  worker plus pushes on cache and setting changes); hook new refresh reasons there rather than
  adding receivers.
* Phase 2 (AdMob, Firebase) is in progress on the user's side; the reserved seams are the banner
  slot at the bottom of `PriceScreen`, `AppSettings.onSettingsChanged`, and `google-services.json`
  in `app/` (gitignored).

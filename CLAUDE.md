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

Release work is documented in README › *Cutting a release*: `bundleRelease` is what Play takes,
`lintRelease` is what gates `check`, and the upload key and the AdMob ids are deliberately not in
this repo.

## Layout

```
app/src/main/java/com/koodipardik/nordprice/
  core/        pure domain: Region, Language, Day, ChartType, ChartResolution, PriceLevel,
               PriceUnit + PriceFormat, MarketDay (DST-aware day bounds, UTC ISO-8601),
               PriceMath (hourly averaging), DayCoverage (is a day complete?)
  data/        PriceApi, PriceCache (files), PriceRepository, HistoryRepository,
               NetworkMonitor, SampleData
  settings/    AppSettings — one DataStore-backed StateFlow<Settings>
  notifications/ DeviceRegistration (pure payload), NotificationSync (FCM token → anonymous Auth →
               syncNotificationDevice), NordPriceMessagingService (onNewToken + push guards),
               PriceAlertNotifier (one rendering path for FCM and debug alerts)
  ui/
    Layout.kt  rememberIsTablet() + the 340dp side column used by both wide layouts
    LayoutBudget.kt  the measured content height, the StackTier/StackPlan rules that spend it, and the
               StackSizes (title, hero, unit, card text, chart floor) each tier draws at
    theme/     Color.kt holds both palettes verbatim from the iOS asset catalog
    chart/     ChartScene.kt (shared geometry + rules), ChartCanvas.kt (Compose renderer),
               ChartStyles.kt (day / history / widget variants), ChartCards.kt, MonotoneSpline.kt
    components/ cards, flags, hero price card, sheets, connection-lost screen
    price/     PriceScreen + ChartViewModel + CurrentPriceViewModel
    history/   HistoryScreen + HistoryViewModel
    settings/  SettingsScreen
    ads/       AdPlacement.kt (the port of iOS AdUnit: placement → unit id, plus the is-this-build-
               configured gate), AdBanner.kt (AnchorBanner), ConsentManager.kt (UMP) — one copy of each
               in src/main, since ads ship in both build types; only the AdMob app id differs per type,
               as a manifest override in app/src/debug/AndroidManifest.xml
  widget/      Glance widget: NordPriceWidget, WidgetSnapshotBuilder (pure), WidgetChartRenderer
               (scene → Bitmap), WidgetRefresh (WorkManager), WidgetPreview (debug)

`AppContainer` (composition root) is created in `NordPriceApplication` and reaches composables
through `LocalContainer`; the current `Settings` through `LocalSettings`. Four long-lived
collaborators — that is the whole DI story, keep it that way.

## Rules this codebase follows

* **Phone and tablet share their building blocks, not their layouts.** `DayCards`, `DayChart`,
  `HistoryCards` and `ChartHeader` are composed by both arrangements; adding a card means adding it
  once.
* **Heights are budgeted, never assumed.** A screen asks `stackPlanFor(kind, measuredHeightDp)` for its
  `StackPlan` and draws at `StackTheme.sizes`; cards must not hardcode a size that a tier is meant to
  set, and no new block may go on a price or history stack without adding its height to that file's
  arithmetic. A `Column` of children with minimums does not compress when the window is short — it lays
  the overflow out past the bottom edge, where it is invisible and unreachable.
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
* **`src/main` has to compile against every build type.** Where debug and release genuinely behave
  differently, the differing file lives twice — `src/debug/java` and `src/release/java` — behind one
  API that `src/main` composes against, and the release build is what proves the pair still agrees, so a
  stub that drifts is a compile error rather than a runtime surprise. **Prefer a build-type *value* over
  a build-type *file*** — a manifest override or `BuildConfig.DEBUG` beats a duplicate — and delete the
  second copy the moment the difference is gone. Nothing lives twice today: `ui/ads/` was the one user of
  this rule and collapsed into `src/main` when ads started shipping, leaving the debug manifest override
  as the only per-build-type ad difference.
* **Every non-obvious decision gets a comment explaining why**, not what. Prefer deleting a
  fallback that quietly hides a failure over keeping one.
* The chart is drawn by hand on a `Canvas`; grid/bars/curves are canvas primitives while axis labels
  and the "Now" capsule are real composables on top, so they follow the user's font scale.

## Phase 2/3 notes

* Notifications are done — see `notifications/`. Same Firebase project (`elektrihind-76019`), same
  `syncNotificationDevice` Callable Function, in `europe-west1` (the client default is `us-central1`).
  Three things that are not visible from this repo: the backend's `platform` validator had to be
  widened to accept `"android"` in `~/App/Elektrihind/functions/src/index.ts` (deployed 2026-10-19),
  and against the older build an Android client gets `invalid-argument` rather than a crash; the
  device row is keyed by the Firebase Auth **uid**, so the anonymous sign-in is load-bearing and not
  decorative; and the payload's enum strings are validated against lists in that file, which
  `DeviceRegistrationTest` pins.
* Glance widgets are done — see `widget/`. Widget updates come from `WidgetRefresh` (a 15-minute
  worker plus pushes on cache and setting changes); hook new refresh reasons there rather than
  adding receivers.
* **Ads are ported and ship in both build types** — `play-services-ads` and UMP are plain
  `implementation` dependencies, and `AnchorBanner` / `ConsentManager` are single files in
  `src/main/…/ui/ads/`. Do not resurrect the release no-ops in `src/release/java/…`; there is no longer a
  debug-only behaviour there to stub. The ids are in, so release serves real ads: the Android app record
  (`ca-app-pub-5431783362632568~3036192953`) in `src/main/AndroidManifest.xml` and one unit id per live
  placement in `AdPlacement` — created 2026-10-27, and not interchangeable with the iOS unit ids under the
  same publisher prefix, because an AdMob unit is bound to one app on one platform. Debug asks Google's
  sample app id (manifest override) for Google's test unit (`BuildConfig.DEBUG`), so testing the path
  never spends an impression.
* **An unconfigured build is ad-free, not broken.** `AdPlacement.isConfigured()` checks the *merged* app
  id against AdMob's shape plus one non-blank unit id (debug exempt from the second half), and
  `ConsentManager.gatherConsent` returns on false before UMP is reached — no consent form shown by a
  build that would request nothing. `resolveUnitId` returns null per blank placement rather than falling
  back to the test unit: test ads in production is how accounts get suspended. Both say so at `Log.e`,
  which R8 keeps.
* **Keep the SDK's `MobileAdsInitProvider` removed.** `src/main/AndroidManifest.xml` carries a
  `tools:node="remove"` for it, and two things depend on that line: a `ContentProvider` runs at process
  attach, so with auto-init on the SDK starts on every launch whether or not consent was given; and it
  throws `IllegalStateException: Invalid application ID` at launch on a placeholder id, which is the
  crash this repo turned into an ad-free screen. Auto-init off also means `canRequestAds` may only be
  published from the `MobileAds.initialize` callback — an `AdView` that requests before the SDK is ready
  gets an error, not a fill.
* Four banner slots are live: three on `PriceScreen` (today / tomorrow-pending / tomorrow-ready, phone and
  tablet share those units) and `AdPlacement.HISTORY` below the history graph in both of its
  arrangements. `SettingsScreen` had a fifth, Android-only, and no longer composes one — iOS has never
  bannered Settings, so the screens agree again; `ConsentManager` still feeds the "Privacy options" row
  there, which is a GDPR affordance rather than an ad. Adding a slot means an `AdPlacement` entry with
  its own console unit *and* an `AnchorBanner(...)` call — the enum alone draws nothing. Console-side
  work, including the per-app UMP consent message without which nothing serves in the EEA, is in
  README › *Ads*.
* **State the user would miss on a new phone goes in the settings DataStore, never in
  `SharedPreferences`.** `backup_rules.xml` and `data_extraction_rules.xml` exclude the whole
  `shared_prefs` domain on purpose, because that is where Firebase Auth, FCM and
  `NotificationSync.installationId` keep their credentials. The DataStore under `files/` is still
  backed up, and `AppShell` re-applies the stored language to `AppCompatDelegate` on launch, so
  nothing user-visible is lost.
* Used: `AppSettings.onSettingsChanged`, which is what pushes every settings change to the backend.
  `app/google-services.json` (gitignored) is required for every build since the google-services
  plugin landed — a fresh clone fails until it is downloaded for `elektrihind-76019`.

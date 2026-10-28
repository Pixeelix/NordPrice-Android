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

Gradle 8.14.5 via the wrapper; AGP 8.13.2, Kotlin 2.2.21, Compose BOM 2025.12.01, Firebase BoM
34.19.0 (held back from 35.x — see the comment in `gradle/libs.versions.toml`). Building requires
`app/google-services.json` (gitignored) for the registered package name; get it from the Firebase
console for `elektrihind-76019`.

`minSdk 26` (Android 8.0), `targetSdk 36`, `applicationId com.koodipardik.nordprice` — one id for
debug and release, so Firebase and AdMob need only one Android app registered, the same way the iOS
bundle id covers both configurations.

The id keeps the conventional `com.` prefix even though the iOS bundle id does not
(`koodipardik.Elektrihind`); `namespace`, `applicationId` and every `package` declaration are the
same string, deliberately.

## Cutting a release

```bash
./gradlew testDebugUnitTest      # must pass
./gradlew lintRelease            # must be error-free; `./gradlew check` runs this too
./gradlew bundleRelease          # Play takes the .aab, never an APK
```

Four things have to be true before that bundle goes to production. The first is now enforced by the
build itself; the rest are facts about other people's consoles and about a device you have to hold, so
they are listed rather than assumed:

* **A real signing key, which is the one item here that only a human can do.** The repo signs release
  with `app/keystore.properties` when that file exists and with the Android debug key when it does
  not. `bundleRelease` refuses to run without it, because that file is the upload; `assembleRelease`
  and `installRelease` do not, because testing a minified release build on your own phone is not a
  publication. The override is `-PallowDebugSignedBundle` and it is for CI that has no key yet.

  ```bash
  keytool -genkeypair -v -keystore ~/.nordprice/nordprice-upload.jks \
    -alias nordprice-upload -keyalg RSA -keysize 2048 -validity 10000
  cp app/keystore.properties.example app/keystore.properties   # then fill in the four keys
  ./gradlew signingReport          # release must say `Config: upload`, not `Config: debug`
  "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs \
    app/build/outputs/apk/release/app-release.apk    # and the DN must not be CN=Android Debug
  ```

  The `.jks` stays out of the repo (`*.jks` is gitignored) and gets a backup you can actually restore:
  with Play App Signing a lost *upload* key is replaceable from the console, an upload key that leaked
  into git history is a rotation, and a lost app signing key with App Signing turned off is an app you
  can never update. `versionCode` goes up by one for every upload; `versionName` stays the human-facing
  4.x, extended to `4.x.y` when a release is a step on top of the iOS parity line rather than a new one
  (4.1 → 4.1.1 is the build that turned AdMob on for release).
* **Ads are in this bundle, so the AdMob ids in it have to be real.** `play-services-ads` and UMP are
  ordinary `implementation` dependencies now, and the release AAB therefore carries the SDK, the
  `AD_ID` permission and whatever app id `app/src/main/AndroidManifest.xml` declares (see *Ads* below).
  Both halves of the configuration are in: the app id in `app/src/main/AndroidManifest.xml`
  (`ca-app-pub-5431783362632568~3036192953`, the Android record) and one unit id per live slot in
  `AdPlacement` — four of them. Google's sample id and test unit cannot leak into release — the first is
  overridden only for debug, the second only under `BuildConfig.DEBUG` — and serving test ads from
  production is how accounts get suspended.

  What a *missing* id does is deliberately mild and deliberate: while either half is absent,
  `AdPlacement.isConfigured` reports false, `ConsentManager.gatherConsent` returns before UMP is touched,
  and every banner collapses — an ad-free app rather than a crash, and rather than a consent form shown
  for nothing. Both failure paths log at `Log.e` under `NordPriceAds`, which R8 keeps, and the message
  names both halves, so a device running a misconfigured build says which one is wrong.
* **Data Safety filled in from the payload, not from memory.** `DeviceRegistration.toPayload` is
  exactly what leaves the device on every launch and every settings change: FCM registration token,
  Firebase Auth anonymous uid, installation UUID, region, language, unit, chart resolution, both
  alert toggles with their €/MWh thresholds, app version and build. The ad SDK is the second half of
  the answer now: `AD_ID` is in the merged manifest, so Data Safety has to declare the advertising ID
  as collected and shared for advertising, and the privacy policy URL stops being a Play-only formality
  — AdMob will not approve the app record without one. See the checklist in *Ads*.
* **The release build run on a real device.** `bundleRelease` succeeding is not the same as the app
  working: R8 and resource shrinking can quietly break the serialization models, the Glance widget
  and the FCM path, and none of those are covered by the JVM tests. Install it
  (`bundletool build-apks --connected-device`, or the internal testing track) and check a live fetch,
  region/unit/language changes, the widget on the launcher, and one alert with the app foregrounded
  and one backgrounded. Ads have the same argument in stronger form — consent, SDK init and a real fill
  are all things a JVM test cannot see — so also check the form appears, that a banner actually loads
  under Today, under Tomorrow (both its no-data and its data-ready unit) and under the history graph, and
  that Settings › About offers "Privacy options" afterwards even though Settings itself shows no banner.
  Keep `app/build/outputs/mapping/release/mapping.txt`; with no Crashlytics in
  this app it is what turns a Play Vitals stack trace into something readable.

Auto Backup is deliberately narrower than `allowBackup="true"` suggests. `backup_rules.xml` and
`data_extraction_rules.xml` exclude the whole `shared_prefs` domain, where the anonymous Auth refresh
token, the FCM token and the installation UUID live; the settings DataStore and the price cache under
`files/` still reach a new device. Without that exclusion a second restored phone is the *same*
device to the backend — the row is keyed by the Auth uid — and it quietly takes over the first one's
alerts. Check it once on a device, since the Firebase SDKs pick their own filenames:
`adb shell run-as com.koodipardik.nordprice ls shared_prefs files/datastore`, then
`adb shell bmgr backupnow com.koodipardik.nordprice` and confirm no `shared_prefs` file is in what the
backup agent reports.

Four things no build can check, all in someone else's console: the listing itself (title, full
description, screenshots, category, contact email), the **Data Safety form** filled in from that
payload, **Play App Signing** enrolment for `com.koodipardik.nordprice`, and the API-key restriction in
Google Cloud Console. For that last one the fingerprint that matters is Play's *app signing*
certificate, shown on the console's App signing page, not your upload key: the upload key never reaches
a device, so restricting the key to it would lock out every real install. Add the debug fingerprint as
well or development builds lose Firebase, and re-test anonymous Auth, FCM registration and the
`syncNotificationDevice` call from a release build once the restriction is on.

## What is ported

| Screen | iOS | Android |
| --- | --- | --- |
| Today | `TodayView` | `PriceScreen(Day.TODAY)` — current price, min/avg/max, chart |
| Tomorrow | `TomorrowView` | `PriceScreen(Day.TOMORROW)` — min–max range, chart, "from 15:00" state |
| History | `HistoryView` | `HistoryScreen` — week/month/year, period average, cheapest/dearest, chart |
| Settings | `SettingsView` | `SettingsScreen` — same sections, rows and icons |
| Widget | `NordPriceWidgetExtension` (small + medium) | `NordPriceWidget` (Glance), one resizable provider that adapts to its cell span |
| iPad / large window | `DashboardView` + `HistoryView`'s wide layout | `PriceDashboard` and the wide `HistoryScreen`: cards in a 340 dp column beside each chart, Today and Tomorrow merged into one "Prices" tab |
| Notifications | `NotificationService` (`NotificationService.swift`) | `notifications/` — FCM token → anonymous Auth → the same `syncNotificationDevice` Callable Function, in `europe-west1` |

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
* **Alert taps open the app, not the Tomorrow view.** iOS routes the same tap straight to
  `AppNavigation.shared.openTomorrowPrices()` (`NordPriceApp.swift:74`). The Compose shell keeps its
  tab index in local state, so there is nothing to route into; a shared navigation holder is the fix.
* **Background alerts land in FCM's own channel.** The function sends a bare `notification` payload,
  which the system draws itself while the app is backgrounded — into its default channel, not the
  app's `price_alerts` one. Foreground alerts do use ours. Matching them needs
  `android: { notification: { channel_id: "price_alerts" } }` in `sendPriceNotification`.

Not ported yet: Firebase Analytics (the iOS app does not use it either, so it stays out). AdMob
banners and UMP consent are ported and ship in both build types — see *Ads* below. Threshold
notifications are ported — see `notifications/`.

## Ads

Banners ship in every build type. `AnchorBanner` and `ConsentManager` are each one file in
`src/main/java/…/ui/ads/`: the debug/release copies this repo carried while ads were a debug-only
feature existed only to keep the SDK out of the launch bundle, and once the SDK ships there is one
behaviour left to implement, so there is one copy of it. What still differs per build type is which
AdMob ids a build may ask with, and that difference lives in the two manifests and one
`BuildConfig.DEBUG` branch rather than in duplicated code.

* **Unit ids** — in `AdPlacement`, the port of iOS `AdUnit`. `resolveUnitId()` returns Google's public
  test banner in any debug build whatever the enum holds, and the placement's production id otherwise;
  a blank production id means no banner and a `Log.e`, never a fallback to the test unit.
* **App id** — `com.google.android.gms.ads.APPLICATION_ID` in `app/src/main/AndroidManifest.xml`, which
  is the only place the SDK reads it from, so it cannot sit next to the unit ids.
  `app/src/debug/AndroidManifest.xml` overrides that one key with Google's sample app id
  (`tools:replace`), because sample app id and test unit id are a pair. Merged, `src/main` is what a
  release build installs and the sample id cannot reach it; `src/debug` is what a debug build installs
  and the real ids are what it never sees.
* **The gate** — `AdPlacement.isConfigured()` reads the app id back out of the *merged* manifest and
  requires AdMob's shape (`ca-app-pub-<16 digits>~<digits>`) plus at least one unit id (debug is exempt
  from the second half, since it asks for the test unit by design). `ConsentManager.gatherConsent`
  returns on false, before UMP is touched, which is what makes both an unconfigured release build and
  this repository's default state a plain ad-free app: no consent form asked for nothing, and no gap in
  the layout.
* **Auto-init off** — `src/main/AndroidManifest.xml` removes the `MobileAdsInitProvider` the SDK
  contributes for itself. That provider is a `ContentProvider`, so it ran at process attach: before
  `MainActivity.onResume`, before consent, and fatally on a malformed or absent app id
  (`IllegalStateException: Invalid application ID` — a crash on launch for a build that should simply be
  ad-free, which is what this repo shipped until the Android app record existed). Removing it makes the consent rule in
  `ConsentManager` actually true (nothing starts the SDK until `canRequestAds` says so) and the
  unconfigured state inert rather than crashing. The price is that `MobileAds.initialize` must be called
  by hand *and* finish first, which is why `canRequestAds` is published from the initialization callback
  rather than assigned from UMP's verdict: an `AdView` that requests before the SDK is ready gets an
  error instead of a fill.
* **Hiding** — a banner draws nothing while `Settings.useSampleData` is on (the offline mode, which is
  also what iOS's `hidesAdBanners` means in screenshots), while consent is unresolved, and while its
  placement has no unit id. There is no separate "no ads" switch: iOS compiles its debug one out of
  release builds, so a copy of it here would be a debug-only affordance.

The slots, their units, and what iOS banners where:

| Placement | Where it draws | Android unit | iOS unit |
| --- | --- | --- | --- |
| `TODAY` | Price screen on the Today tab, phone and tablet | `…/4073730424` | `todayBanner` |
| `TOMORROW_PENDING` | Price screen on Tomorrow, before tomorrow's prices exist | `…/9534152697` | `tomorrowNoDataBanner` |
| `TOMORROW_READY` | Price screen on Tomorrow, with data | `…/7186978928` | `tomorrowDataBanner` |
| `HISTORY` | Below the history graph, in both arrangements | `…/3850369144` | `historyBanner` |

Settings shows no banner, on either platform. Android had carried a fourth, Android-only slot at the
bottom of Settings that iOS never had; it is gone, so the two screens agree again. The "Privacy options"
row in Settings › About stays — that is a GDPR affordance, not an ad, and it is where a user goes to
change the consent every one of the slots above depends on.

What the console side looks like now, and what is still owed there:

1. **AdMob app record for the Android package** `com.koodipardik.nordprice` — done 2026-10-27
   (`ca-app-pub-5431783362632568~3036192953`, declared in `src/main/AndroidManifest.xml`). The iOS record
   is the same publisher but a different app, and AdMob units do not cross platforms.
2. **One banner unit per live placement**, named after the placements above — done 2026-10-27 for the
   four that draw, in `AdPlacement`. Separate units rather than one shared is what iOS does; it is what
   makes per-placement fill rate and revenue readable, and therefore tunable or killable one at a time.
3. **Automatic refresh left on**, at 30–60 s per unit — a console setting, one per ad unit. Manual refresh
   would be the worse choice for this layout specifically: `loadAd()` on a filled `AdView` clears it, and
   the banner derives its height from having an ad, so a manual cycle would collapse and re-expand the
   chart every time. The SDK's own refresh swaps the creative inside the same height. Too fast is its own
   problem — each refresh is a billed impression, and viewability prices eCPM.
4. **No eCPM floor** — AdMob's "Standard" pricing. A floor trades fill for price, and only the product of
   the two is revenue; at this traffic level the optimum is not measurable, and the failure is invisible
   because a no-fill banner collapses to zero height and looks like a clean screen. Revisit from the
   per-unit report after a few weeks, one unit at a time, against the untouched units as control.
5. **A consent message published for the Android app record.** UMP messages are configured per app, not
   per account: the iOS app's GDPR message says nothing about the Android one. Until an Android message
   exists and is published, `canRequestAds` stays false inside the EEA and no banner ever serves there —
   the symptom is a quietly ad-free app in Estonia and Finland, not an error anywhere. **This is the item
   most likely to be forgotten, and the only one that can make a correctly configured build serve
   nothing.**
6. **AdMob test devices** for any phone used to poke at release builds; debug builds need nothing, they
   already ask for the test unit.
7. **`app-ads.txt`** on the developer domain for the publisher id. AdMob checks it before serving.
8. **Play Console**: Data Safety declaring the advertising ID as collected and shared for advertising,
   the listing's "Contains ads" question answered yes, and a privacy policy URL — AdMob requires the
   policy on the app record itself, not only in Play.
9. Consent answers are stored by the ads SDK under `shared_prefs`, which `backup_rules.xml` excludes on
   purpose. A restored phone is asked again. That is the correct failure: consent is not a setting to
   migrate, and the exclusion that keeps it out of the backup is the same one that keeps the Firebase
   Auth credentials off a second device.

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

**DEBUG → Send test notification** is Android-only: iOS has no equivalent affordance, its
`sendTestNotification` Callable Function gets called from the Firebase CLI. It asks the backend to
push a real FCM message to this device's stored token. The reason it exists here is that a
successful registration only proves the device *row* was written — the first real alert arrives
whenever `checkTomorrowPrices` next finds a crossing price, which can be days. This proves the
delivery path (token → device → `onMessageReceived` → notification channel) in one tap, and reports
the server's own reason when it fails. One thing to remember when it appears to do nothing:
`NordPriceMessagingService` refuses to post while both alert switches are off, because a push already
in flight when someone disables alerts must not land. Turn one on first.

**DEBUG → *Send test: high price* / *Send test: low price*** are the port of the iOS debug buttons
(`scheduleDebugPriceNotification`): they post the same alert **locally**, through the same
`PriceAlertNotifier` a received push uses — same channel, same per-direction id, production
copy with fixed sample numbers, in the app's language. Unlike the backend-backed row they need no
FCM token, which is how the alert path gets exercised on a proxy-blocked emulator (an AVD behind a
TLS-intercepting corporate network never gets a token: Play services pins its certificates).
Deliberately these two ignore the in-app alert switches — only the OS notification permission gates
them, and a missing one is reported in the row instead of an inert button.

# Debug high/low price alert buttons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Settings › DEBUG rows that post the high-price and low-price alert as a local notification, mirroring iOS `scheduleDebugPriceNotification`, so the alert renders on an emulator that cannot receive FCM.

**Architecture:** Extract the notification-rendering code from `NordPriceMessagingService` into a shared `PriceAlertNotifier` object; both a real push and a debug tap run through it. Two debug-gated `OptionRow`s in `SettingsScreen` compose the production copy from resources and call the notifier. No network, no FCM token involved.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), NotificationCompat, Android resources (4 locales). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-27-debug-price-alert-buttons-design.md`

## Global Constraints

- `minSdk 26`, `targetSdk 36`; `applicationId com.koodipardik.nordprice`.
- Every new user-visible string goes through resources in all four languages: `values/` (English default), `values-et/`, `values-fi/`, `values-ru/`.
- Match iOS unless the platform disagrees; non-obvious decisions get a comment explaining *why*.
- `src/main` must compile in every build type; debug-only *behavior* is gated by `BuildConfig.DEBUG` at the call site, the existing pattern (`NotificationSync.sendTestPush` is in main).
- Build env: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` before `./gradlew`.
- Commit message style: plain capitalized sentence, no `feat:` prefix (see `git log`). **Commit only the paths named by the task** — the working tree carries unrelated staged work; never `git add -A`.
- Production payload contract (do not typo, comments must cite it): `data.type` is `price_threshold_max` / `price_threshold_min` (`~/App/Elektrihind/functions/src/index.ts:378`), notification id is `type.hashCode()` (one alert per direction per market day).

## Review Focus

No automated UI/notification tests exist in this repo (JVM tests cover pure data/rules only), so these are pinned by the verification steps in Tasks 3–4 instead:

1. **OS notification permission denied** → the tapped row shows the existing `hint_notifications_denied` text in its value slot; nothing is posted silently.
2. **Both in-app alert switches off** → the debug alert still posts (the both-off suppression stays in `onMessageReceived`, it guards pushes in flight, not an explicit developer tap — iOS parity).
3. **Repeat tap of the same direction** → the notification is replaced (same id), the shade count for that alert type stays 1.
4. **Locale with reordered placeholders** → all four body strings use positional `%1$s %2$s %3$s`; `assembleDebug` fails on non-positional multi-substitution formats, and the four-language render is spot-checked.
5. **Tap-through** → tapping the posted alert opens `MainActivity` (the shared builder's `contentIntent`; FLAG_IMMUTABLE survives the move).

---

### Task 1: `PriceAlertNotifier` — shared rendering path

**Files:**
- Create: `app/src/main/java/com/koodipardik/nordprice/notifications/PriceAlertNotifier.kt`
- Modify: `app/src/main/java/com/koodipardik/nordprice/notifications/NordPriceMessagingService.kt`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `object PriceAlertNotifier` with
  `const val TYPE_MAX = "price_threshold_max"`, `const val TYPE_MIN = "price_threshold_min"`, and
  `fun show(context: Context, title: String?, body: String?, type: String)` — posts on channel `NotificationSync.CHANNEL_ID` with id `type.hashCode()`; catches `SecurityException` (permission revoked mid-post) and logs at `w` under tag `NordPriceNotifications`. Task 3 calls `show` with non-null title/body; nulls only reach here from the FCM path's guards.

- [ ] **Step 1: Create `PriceAlertNotifier.kt`**

Move the `PendingIntent` construction, `NotificationCompat.Builder` chain (`smallIcon` `R.drawable.ic_stat_nordprice`, `BigTextStyle`, `PRIORITY_DEFAULT`, `setAutoCancel`, `setContentIntent`), the `notify(id, …)` with id `(message.data["type"] ?: "").hashCode()` semantics, and the `SecurityException` catch verbatim out of `NordPriceMessagingService.onMessageReceived` (lines 57–86) into

```kotlin
object PriceAlertNotifier {
    const val TYPE_MAX = "price_threshold_max"
    const val TYPE_MIN = "price_threshold_min"

    fun show(context: Context, title: String?, body: String?, type: String) { … }
}
```

The `type` parameter replaces the `message.data["type"]` lookup: `show` computes `id = type.hashCode()`. Keep the moved code's comments that explain *why* (tap opens app because Compose shell has no shared nav state; one alert per direction per market day). Add a short doc comment on the object: single rendering path for real pushes and the debug mirror. Comment on the constants citing the backend spelling source (`functions/src/index.ts` `data.type`), per the repo's "explain the why" rule.

- [ ] **Step 2: Rewrite the service to delegate**

`onMessageReceived` keeps its two guards and the `appContainer.settings` check verbatim, then calls `PriceAlertNotifier.show(this, title, body, message.data["type"] ?: "")`. Delete the now-unused imports (`PendingIntent`, `Intent`, `NotificationCompat`, `NotificationManagerCompat`, `MainActivity`, `R`) from the service. `NordPriceMessagingService`'s private companion keeps `TAG` only if still used (the two remaining `Log.w` lines); otherwise drop it — the tag constant must stay reachable from `PriceAlertNotifier` (its own private const is fine; same string value).

- [ ] **Step 3: Build and run the existing tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all existing tests pass (none asserts on the moved code; a compile error means an import or receiver was left behind).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/koodipardik/nordprice/notifications/PriceAlertNotifier.kt app/src/main/java/com/koodipardik/nordprice/notifications/NordPriceMessagingService.kt
git commit -m "Route price alerts through one shared notifier"
```

---

### Task 2: Debug copy in four languages

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (append after the existing `debug_test_notification_sent` line)
- Modify: `app/src/main/res/values-et/strings.xml`, `app/src/main/res/values-fi/strings.xml`, `app/src/main/res/values-ru/strings.xml`

**Interfaces:**
- Consumes: nothing.
- Produces: six string resource ids per locale — `debug_alert_high`, `debug_alert_low`, `debug_alert_max_title`, `debug_alert_min_title`, `debug_alert_max_body` (`%1$s` price, `%2$s` time, `%3$s` threshold), `debug_alert_min_body` (same order) — used by Task 3.

- [ ] **Step 1: Add the six English strings to `values/strings.xml`**

Exact values (titles and body templates are copied verbatim from the production `functions/src/index.ts:92-105,467-489`, so the debug mirror shows what the server would send; placeholders are positional because languages reorder them):

```xml
<string name="debug_alert_high">Send test: high price</string>
<string name="debug_alert_low">Send test: low price</string>
<string name="debug_alert_max_title">High electricity price</string>
<string name="debug_alert_min_title">Low electricity price</string>
<string name="debug_alert_max_body">Tomorrow\'s peak %1$s at %2$s exceeds your set limit (%3$s).</string>
<string name="debug_alert_min_body">Tomorrow\'s low %1$s at %2$s is below your set limit (%3$s).</string>
```

- [ ] **Step 2: Add the et/fi/ru strings**

Estonian and the titles/bodies follow iOS (`SettingsView.swift` labels; the backend's localized sentences — sample numbers appear in Task 3, not in the format strings):

- `values-et`: `Saada test: kõrge hind` / `Saada test: madal hind` / `Kõrge elektrihind` / `Madal elektrihind` / `Homne kõrgeim hind %1$s kell %2$s ületab sinu seatud piiri (%3$s).` / `Homne madalaim hind %1$s kell %2$s on alla sinu seatud piiri (%3$s).`
- `values-fi`: `Lähetä testi: korkea hinta` / `Lähetä testi: matala hinta` / `Korkea sähkön hinta` / `Matala sähkön hinta` / `Huomisen korkein hinta %1$s klo %2$s ylittää asettamasi rajan (%3$s).` / `Huomisen alin hinta %1$s klo %2$s alittaa asettamasi rajan (%3$s).`
- `values-ru`: `Отправить тест: высокая цена` / `Отправить тест: низкая цена` / `Высокая цена на электроэнергию` / `Низкая цена на электроэнергию` / `Завтрашний пик %1$s в %2$s превышает заданный лимит (%3$s).` / `Завтрашний минимум %1$s в %2$s ниже заданного лимита (%3$s).`

- [ ] **Step 3: Verify resources compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL — aapt's multi-substitution check enforces the positional form (Review Focus 4).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-et/strings.xml app/src/main/res/values-fi/strings.xml app/src/main/res/values-ru/strings.xml
git commit -m "Add four-language copy for the debug price alerts"
```

---

### Task 3: The two debug rows

**Files:**
- Modify: `app/src/main/java/com/koodipardik/nordprice/ui/settings/SettingsScreen.kt` (DEBUG section after the existing `Divider()` at line 221; new private composable near the other row composables; imports)

**Interfaces:**
- Consumes: `PriceAlertNotifier.show(TYPE_*)` (Task 1), `debug_alert_*` strings (Task 2), `OptionRow(icon, tint, label, value, onClick)` (file-private, line 373), existing `hint_notifications_denied` and `debug_test_notification_sent`.
- Produces: nothing later tasks need.

- [ ] **Step 1: Add the post helper as a file-private function**

```kotlin
/**
 * Debug-only local mirror of the production price-threshold push — the port of iOS
 * `scheduleDebugPriceNotification` (NotificationService.swift:98). Local, not FCM: the alert
 * must render even where a token cannot be minted, and it exercises the same channel, builder
 * and per-direction id through the same `PriceAlertNotifier` a real push uses. It deliberately
 * ignores the two in-app alert switches (those suppress pushes already in flight, not a tap a
 * developer just made) and gates only on the OS permission, so a missing one is a visible
 * message rather than an inert button — the same reason iOS returns status text.
 */
private fun postDebugPriceAlert(context: Context, isHigh: Boolean): String
```

Behavior: `NotificationManagerCompat.from(context).areNotificationsEnabled()` false → return `context.getString(R.string.hint_notifications_denied)`. Otherwise build

```kotlin
val (price, time, threshold) = if (isHigh) Triple("0,161 €/kWh", "18:00", "0,150 €/kWh")
                               else Triple("0,021 €/kWh", "03:00", "0,040 €/kWh")
```

— iOS's fixed sample values, comma decimal per the repo rule (deriving them from current settings would mean porting the server's unit/VAT formatting into a debug row — spec's recorded call). Title/body from `debug_alert_max_*` / `debug_alert_min_*` (`getString(body, price, time, threshold)`), then `PriceAlertNotifier.show(context, title, body, if (isHigh) PriceAlertNotifier.TYPE_MAX else PriceAlertNotifier.TYPE_MIN)` and return `context.getString(R.string.debug_test_notification_sent)` (et "Teavitus saadeti" reads true for a local post too — no new status copy).

- [ ] **Step 2: Add the two rows inside the `if (BuildConfig.DEBUG)` section**

After the widget-preview `Divider()`, before the existing test-push `OptionRow`: `Divider()` then two `OptionRow`s — icons `Icons.Filled.ArrowCircleUp` / `Icons.Filled.ArrowCircleDown` (the AutoMirrored pair doesn't exist for these; material-icons-extended is already a dependency), tints `Color(0xFFF43F5E)` / `Color(0xFF10B981)` matching the app's max/min alert red and green — labels `stringResource(R.string.debug_alert_high)` / `…low)`, each row carrying its own result state (`var highAlertResult` / `lowAlertResult`, `mutableStateOf("")` alongside `testPushResult` — per-row values because `OptionRow`'s only text slot besides the label is its trailing value, and each button's result belongs on its own row), `onClick = { highAlertResult = postDebugPriceAlert(context, isHigh = true) }` (and low). `context`: `val context = LocalContext.current` next to `coroutineScope` (line 112). New imports: `android.content.Context`, `androidx.core.app.NotificationManagerCompat`, `com.koodipardik.nordprice.notifications.PriceAlertNotifier`.

- [ ] **Step 3: Build, test, install**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew assembleDebug testDebugUnitTest && ./gradlew installDebug`
Expected: BUILD SUCCESSFUL; install lands on the connected `emulator-5556`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/koodipardik/nordprice/ui/settings/SettingsScreen.kt
git commit -m "Post debug high and low price alerts locally from Settings"
```

---

### Task 4: On-emulator verification and docs

**Files:**
- Modify: `README.md` (Debug sample data section), `CLAUDE.md` (Layout: `notifications/` line; Phase 2/3 notes), `docs/superpowers/specs/2026-10-27-debug-price-alert-buttons-design.md` (Status line)

**Interfaces:**
- Consumes: everything above, installed on `emulator-5556`.
- Produces: verified feature + docs; no code interfaces.

- [ ] **Step 1: Post both alerts and verify them in the shade**

```bash
adb -s emulator-5556 shell am start -n com.koodipardik.nordprice/.MainActivity
# navigate: tap the Settings bottom tab, scroll to DEBUG, tap both rows
adb -s emulator-5556 shell uiautomator dump /sdcard/ui.xml && adb -s emulator-5556 shell cat /sdcard/ui.xml   # find the two rows' bounds, tap via input tap
adb -s emulator-5556 shell dumpsys notification --noredact | grep -c "price_alerts"    # ≥ 2 channel records posted
adb -s emulator-5556 shell dumpsys notification --noredact | grep -oE "(High electricity price|Low electricity price|Tomorrow's peak [^ ]+ at [0-9:]+|0,161|0,021)" | sort -u
```

Expected: one `price_alerts` notification per direction with title and body from the current locale. Re-tap the high row: the count for its id stays 1 (Review Focus 3 — `type.hashCode()` replaces).

- [ ] **Step 2: Permission-denied path (Review Focus 1)**

```bash
adb -s emulator-5556 shell pm revoke com.koodipardik.nordprice android.permission.POST_NOTIFICATIONS
# tap a row → the row's value slot shows "Notifications are disabled…" and
adb -s emulator-5556 shell dumpsys notification | grep -c price_alerts   # unchanged
adb -s emulator-5556 shell pm grant com.koodipardik.nordprice android.permission.POST_NOTIFICATIONS
```

- [ ] **Step 3: Tap-through + both-switches-off posting (Review Focus 5 and 2)**

Turn both alert toggles off in Settings › Notifications, tap a debug row → alert still posts (only OS permission gates). Pull down the shade, tap the alert → app opens (`adb -s emulator-5556 shell dumpsys window | grep mCurrentFocus` shows `com.koodipardik.nordprice/.MainActivity`), then restore the toggles.

- [ ] **Step 4: One non-English render check (Review Focus 4)**

Settings › Language → Eesti, tap a row, confirm the alert body shows the et sentence with the sample numbers in the right slots (kell/time between price and piir). Switch back to English.

- [ ] **Step 5: Docs**

README › *Debug sample data* section, after the `Send test notification` paragraph: two sentences noting the new rows post the same alerts **locally** (the port of the iOS debug buttons) and that — unlike the backend-backed row — they work without an FCM token, which is how the alert path gets exercised on a proxy-blocked emulator. CLAUDE.md: extend the `notifications/` layout line with `PriceAlertNotifier (one rendering path for FCM and debug alerts)`; add to the Phase 2/3 notes that the debug rows bypass the delivery path on purpose. Spec doc: `Status:` → `approved 2026-10-27, implemented`.

- [ ] **Step 6: Full gate + commit**

```bash
./gradlew testDebugUnitTest lintRelease assembleDebug
git add README.md CLAUDE.md docs/superpowers/
git commit -m "Document the debug price-alert rows"
```

Expected: lint error-free (the four new strings in all locales keep the translation lint quiet; `MissingTranslation` on any locale slip fails here).

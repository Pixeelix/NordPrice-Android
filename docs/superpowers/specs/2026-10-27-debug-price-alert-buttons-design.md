# Debug "high / low price" alert buttons — design

**Date:** 2026-10-27 · **Size:** small change · **Status:** approved 2026-10-27, implemented

## Intent

iOS's Settings › DEBUG has two buttons — *Saada test: kõrge hind* / *Saada test: madal hind*
(`SettingsView.swift:156-164` → `NotificationService.scheduleDebugPriceNotification`,
`NotificationService.swift:98-136`) — that make the price-alert banner appear **immediately,
without any push transport**: they post a *local* notification mirroring the production push's
copy and payload, because a simulator cannot receive APNs. Port the same affordance to Android.

It matters doubly here: on this machine the emulator currently cannot obtain an FCM token at all
(`FirebaseInstanceId: Token retrieval failed: SERVICE_NOT_AVAILABLE` — the corporate TLS proxy
breaks Play services' cert-pinned registration). The existing *Send test notification* row needs
that token; a local mirror needs nothing but notification permission.

## Goals

- Two debug rows that post the high-price and low-price alert instantly, on emulator and device,
  with no network, no FCM token, and no backend round trip.
- The posted alert renders through the **same display code** a real push uses (channel, BigText,
  priority, tap PendingIntent, one-notification-per-direction id), so what the developer sees is
  what production shows.
- Visible status feedback like iOS — including an explicit message when notification permission
  is missing, so the button never looks inert.

## Non-goals

- Not a test of the FCM **delivery** path — that is what the existing backend-backed
  *Send test notification* row is for. These two rows test rendering and interaction only.
- No sample values derived from current settings; no fake `data` extras on the tap intent
  (Android has no tap-routing yet — README › *Alert taps open the app*).

## Approaches considered

1. **Chosen — local notification through a shared notifier extracted from
   `NordPriceMessagingService`:** exact iOS parity in behavior, exercises the real rendering code,
   works offline.
2. Backend `checkTomorrowPrices` trigger (direct FCM v1 sends to the emulator's token, which I
   explored first): rejected — impossible while the token can't be minted, sends only on real
   threshold crossings with per-day dedupe, and would touch the shared production collection.
3. Duplicating the `NotificationCompat.Builder` inside the debug row: rejected — a second copy
   is exactly what drifts; the repo's own rule is shared building blocks.

## The change

- **`notifications/PriceAlertNotifier.kt`** (new): `object PriceAlertNotifier` with
  `TYPE_MAX = "price_threshold_max"` / `TYPE_MIN = "price_threshold_min"` and
  `show(context, title, body, type)` — the builder + `PendingIntent` + `notify(type.hashCode())`
  + `SecurityException` catch, moved verbatim out of `NordPriceMessagingService.onMessageReceived`.
  Lives in `src/main`: the service uses it there, and `SettingsScreen` — already gated by
  `BuildConfig.DEBUG` — calls it like it already calls `NotificationSync.sendTestPush()`.
- **`NordPriceMessagingService`** keeps its guards (empty-payload log, both-alerts-off
  suppression) and then delegates to the notifier. Suppression deliberately does **not** move
  into the notifier: it exists for pushes in flight when the user flips alerts off; an explicit
  developer tap is not that. iOS likewise checks only OS authorization.
- **`SettingsScreen`** DEBUG section: two `OptionRow`s (`ArrowCircleUp` / `ArrowCircleDown`, the
  AutoMirrored-adjacent equivalents of iOS's `arrow.up.circle.fill` / `arrow.down.circle.fill`),
  sharing one result line. Before posting: `NotificationManagerCompat.areNotificationsEnabled()`
  — when false, show `hint_notifications_denied` instead of dropping silently.
- **Copy** in all four locale files (repo rule; iOS's hardcoded Estonian is the deliberate
  deviation). Titles and body templates are the production `functions/src/index.ts` strings with
  positional placeholders; sample values are iOS's fixed ones (high 0,161 €/kWh vs 0,150 at 18:00;
  low 0,021 vs 0,040 at 03:00). ~11 new strings per locale.
- **Docs**: one README sentence under the debug affordances; the `notifications/` line in
  CLAUDE.md's layout gets the new file.

## Calls I made (flag if wrong)

- Alert text follows the **app's current language**, not iOS's always-Estonian.
- The debug alert **ignores the two in-app alert switches** (only OS permission gates it).
- **Fixed sample numbers** in €/kWh with the comma decimal, not values computed from settings —
  computing them would mean porting the server's unit/VAT formatting into a debug row.
- Notification id = `type.hashCode()`, so a debug alert for a direction replaces the real push
  for that direction — the production replace-semantics, deliberately inherited.

## Verification

`./gradlew assembleDebug && ./gradlew testDebugUnitTest` (no regression; JVM tests don't cover UI),
then on the running emulator: tap both rows, confirm two alerts in the shade on the
`price_alerts` channel with correct titles/bodies, tapping opens the app, and re-tapping a row
replaces rather than stacks.

package com.koodipardik.nordprice.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What a card-over-chart stack does when the window is shorter than it wants to be.
 *
 * Compose has no opinion here, which is the whole of the bug this file exists to fix: a `Column` whose
 * children carry minimums hands `weight(1f)` nothing, the child keeps its floor anyway, and everything
 * past the bottom edge is drawn off-screen where no gesture reaches it. A floor without an escape
 * hatch is just clipping with extra steps, so the three tiers are an order of sacrifice — the ad slot
 * goes first, then the cards compress, and only a window that cannot hold the compact stack at all
 * starts scrolling. The chart's floor is what never moves.
 *
 * iOS needs none of this: its phone stacks shrink themselves and only reach for a scroll view at
 * accessibility text sizes (`scrollsAtAccessibilitySizes`). A foldable's cover screen turned sideways —
 * 415 dp of height — is a shape iPhone-sized SwiftUI never has to draw.
 */
enum class StackTier { Full, Compact, Scroll }

/** A screen's decision, as data: which sizes to draw at, and whether the ad slot is in the layout. */
data class StackPlan(val tier: StackTier, val showBanner: Boolean)

/** Each screen shape differs in what sits around the chart, so they differ in what they need. */
enum class StackKind {
    /** Title, hero price, min/avg/max, chart: `PriceScreen`. */
    Price,

    /** Title, hero average, cheapest/dearest, period selector, chart: `HistoryScreen`. */
    History,

    /** Both days at once, each a card column beside its chart: the tablet `PriceDashboard`. */
    Dashboard,
}

/** The ad slot's own share: the 50 dp inline-adaptive cap iOS documents, plus its 8 dp spacer. */
const val BannerSlotHeightDp = 58

// Every figure below is the sum of its parts at density 1, so it can be checked against the
// composables it describes. sp text is counted as its dp line box (size × 1.35).

/** `ScreenTitle` 10 + 43 + 12, hero card 8 + 44 + 62 + 32 + 8, gap, min/avg/max, gap, 220 floor, 8. */
private const val PriceFullDp = 535

/** The same stack with [CompactStackSizes]: 46 + 121 + 10 + 60 + 10 + 160 + 6. */
private const val PriceCompactDp = 413

/** Price's stack plus the history-only blocks: the dated min/max card and the period selector. */
private const val HistoryFullDp = 643

/** [HistoryFullDp] at [CompactStackSizes]. */
private const val HistoryCompactDp = 481

/** Two rows at [FullStackSizes] (each the taller of its card column and its chart card), their two
 * 20 dp gaps and the 8 + 8 dp of row padding. */
private const val DashboardFullDp = 668

/** The same two rows at [CompactStackSizes]: 2 × 244 + 40 + 16. */
private const val DashboardCompactDp = 544

/**
 * The plan for [availableHeightDp] — the height the screen actually has, measured, not the display's.
 *
 * A zero means nothing has been measured yet, and guessing the compact tier would repaint the whole
 * screen one frame in; the first frame therefore draws the size the window claims it has.
 */
fun stackPlanFor(kind: StackKind, availableHeightDp: Int, bannerHeightDp: Int = BannerSlotHeightDp): StackPlan {
    val (full, compact) = when (kind) {
        StackKind.Price -> PriceFullDp to PriceCompactDp
        StackKind.History -> HistoryFullDp to HistoryCompactDp
        StackKind.Dashboard -> DashboardFullDp to DashboardCompactDp
    }
    return when {
        availableHeightDp <= 0 -> StackPlan(StackTier.Full, showBanner = true)
        availableHeightDp >= full + bannerHeightDp -> StackPlan(StackTier.Full, showBanner = true)
        availableHeightDp >= full -> StackPlan(StackTier.Full, showBanner = false)
        availableHeightDp >= compact + bannerHeightDp -> StackPlan(StackTier.Compact, showBanner = true)
        availableHeightDp >= compact -> StackPlan(StackTier.Compact, showBanner = false)
        else -> StackPlan(StackTier.Scroll, showBanner = true)
    }
}

/**
 * The space below the cards, as a modifier the current column can actually honour: the leftover in a
 * fixed-height column, a concrete floor once the column scrolls and has no leftover to speak of.
 */
@Composable
internal fun ColumnScope.leftoverOrFloor(plan: StackPlan, floor: Dp): Modifier =
    if (plan.tier == StackTier.Scroll) {
        Modifier.fillMaxWidth().height(floor)
    } else {
        Modifier.fillMaxWidth().weight(1f)
    }

/** [leftoverOrFloor] for one row of the two-row dashboard, which is only as wide as the row already is. */
@Composable
internal fun ColumnScope.rowHeightOrFloor(plan: StackPlan, floor: Dp): Modifier =
    if (plan.tier == StackTier.Scroll) Modifier.height(floor) else Modifier.weight(1f)

/** The sizes one tier draws at. Read through [StackTheme], never handed down by hand. */
data class StackSizes(
    val titleSize: TextUnit,
    val titleGap: Dp,
    /** The hero card's fixed-height value box — the number that dominates the screen. */
    val heroValueHeight: Dp,
    val unitTextSize: TextUnit,
    /** Min/avg/max and cheapest/dearest body text. */
    val cardTextSize: TextUnit,
    /** Rhythm between the cards, and the floor under the chart that the tiers exist to protect. */
    val cardGap: Dp,
    val chartFloor: Dp,
) {
    /**
     * What one row of the tablet dashboard needs: its chart card — the [chartFloor] plot plus the
     * 44 dp header and the legend under it — against the ~295 dp the card column wants at the same
     * tier. The chart card is the taller of the two at both tiers, which is why it is the one quoted.
     */
    val dashboardRowFloor: Dp get() = chartFloor + 86.dp
}

private val FullStackSizes = StackSizes(
    titleSize = 32.sp,
    titleGap = 12.dp,
    heroValueHeight = 62.dp,
    unitTextSize = 24.sp,
    cardTextSize = 18.sp,
    cardGap = 12.dp,
    chartFloor = 220.dp,
)

/**
 * ~120 dp lighter than [FullStackSizes] and still the same design language: the price stays the
 * largest thing on the screen, the chart keeps its floor, and what gives is the air around them.
 */
private val CompactStackSizes = StackSizes(
    titleSize = 24.sp,
    titleGap = 8.dp,
    heroValueHeight = 44.dp,
    unitTextSize = 18.sp,
    cardTextSize = 15.sp,
    cardGap = 10.dp,
    chartFloor = 160.dp,
)

/**
 * The height the screen has to draw into, in dp, measured from the Scaffold's content box. 0 until
 * the first layout pass. [LocalConfiguration.screenHeightDp] is the guess this replaces: it counts the
 * system bars differently depending on release and edge-to-edge, and it says nothing about split
 * screen, where the window is whatever the neighbouring app left.
 */
val LocalContentHeightDp = staticCompositionLocalOf { 0 }

/** The current screen's decision. [AnchorBanner] reads it, which is how the ad gives way. */
val LocalStackPlan = staticCompositionLocalOf { StackPlan(StackTier.Full, showBanner = true) }

/** The plan for [kind], from the height the shell measured. Cheap enough to recompute. */
@Composable @ReadOnlyComposable
fun currentStackPlan(kind: StackKind): StackPlan = stackPlanFor(kind, LocalContentHeightDp.current)

/** The type sizes and floors the current window can afford, spelled like [AppTheme]. */
object StackTheme {
    val plan: StackPlan
        @Composable @ReadOnlyComposable get() = LocalStackPlan.current

    val sizes: StackSizes
        @Composable @ReadOnlyComposable get() = LocalStackPlan.current.sizes()

    /** False once the chart needs the ad's 58 dp to keep its own floor. */
    val showBanner: Boolean
        @Composable @ReadOnlyComposable get() = LocalStackPlan.current.showBanner
}

/** The tier's sizes, so the plan and the type scale cannot drift apart. */
fun StackPlan.sizes(): StackSizes = when (tier) {
    StackTier.Full, StackTier.Scroll -> FullStackSizes
    StackTier.Compact -> CompactStackSizes
}

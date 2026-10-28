package com.koodipardik.nordprice

import com.koodipardik.nordprice.ui.StackKind
import com.koodipardik.nordprice.ui.StackTier.Compact
import com.koodipardik.nordprice.ui.StackTier.Full
import com.koodipardik.nordprice.ui.StackTier.Scroll
import com.koodipardik.nordprice.ui.stackPlanFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What each screen gives up, and in which order, when the window is shorter than the stack wants.
 *
 * The order is the one that keeps the chart whole: the ad goes first, then the cards get compact, and
 * only a window that cannot hold the compact stack at all starts scrolling. Pixel heights are the
 * foldable's own: 932 dp of cover screen upright, 415 dp of it turned sideways, and about 440 dp of
 * the inner screen when it is split with another app.
 */
class StackPlanTest {

    private fun plan(heightDp: Int, kind: StackKind = StackKind.Price) =
        stackPlanFor(kind, heightDp, bannerHeightDp = 58)

    @Test
    fun `an upright phone fits the full stack and the banner`() {
        assertEquals(Full to true, plan(932).let { it.tier to it.showBanner })
    }

    @Test
    fun `a window with room for the cards but not for the ad keeps the chart and drops the ad`() {
        // 560 dp: the full-size stack lands at ~534, and the ad would push the chart off the bottom.
        assertEquals(Full to false, plan(560).let { it.tier to it.showBanner })
    }

    @Test
    fun `a sideways phone compacts its cards rather than shrinking its chart`() {
        // 415 dp of cover screen: title + hero + min/avg/max at full size plus the chart's floor is
        // more than the window, so the cards give ~130 dp and the ad gives its 58.
        assertEquals(Compact to false, plan(415).let { it.tier to it.showBanner })
    }

    @Test
    fun `compact cards still pay for the ad when there is room for both`() {
        assertEquals(Compact to true, plan(500).let { it.tier to it.showBanner })
    }

    @Test
    fun `a window too short even for compact cards scrolls instead of clipping`() {
        // Half of a sideways cover screen, and Samsung's smaller floating windows: nothing may be
        // unreachable here, so the whole stack becomes scrollable at its full size.
        assertEquals(Scroll to true, plan(240).let { it.tier to it.showBanner })
    }

    @Test
    fun `the history stack carries its own numbers`() {
        // The average card, the dated cheapest/dearest card and the period selector all sit above the
        // graph: ~643 dp of it. A sideways cover screen cannot hold even the compact version, so the
        // history page is the one that scrolls.
        assertEquals(Full to true, plan(932, StackKind.History).let { it.tier to it.showBanner })
        assertEquals(Compact to true, plan(560, StackKind.History).let { it.tier to it.showBanner })
        assertEquals(Compact to false, plan(500, StackKind.History).let { it.tier to it.showBanner })
        assertEquals(Scroll to true, plan(415, StackKind.History).let { it.tier to it.showBanner })
    }

    @Test
    fun `the tablet dashboard counts both rows and the gap between them`() {
        // The Fold's inner screen upright (~779 dp once the bars are paid for) carries two full rows
        // and the ad; the same dashboard on a 601 dp tablet turned sideways carries neither.
        assertEquals(Full to true, plan(779, StackKind.Dashboard).let { it.tier to it.showBanner })
        assertEquals(Compact to true, plan(620, StackKind.Dashboard).let { it.tier to it.showBanner })
        assertEquals(Scroll to true, plan(493, StackKind.Dashboard).let { it.tier to it.showBanner })
    }

    @Test
    fun `an unmeasured window is treated as the shape it says it has`() {
        // LocalConfiguration is available before the first layout pass; a zero must not fall through
        // to the scrollable tier and flash a different screen on the first frame.
        assertEquals(Full, plan(0).tier)
    }
}

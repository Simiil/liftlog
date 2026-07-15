package de.simiil.liftlog.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendTest {
    private val day = 86_400_000L
    private val now = 1_000_000_000_000L // fixed "today"

    private fun pointsEndingNow(
        values: List<Double>,
        everyDays: Int,
    ): List<TrendPoint> {
        val n = values.size
        return values.mapIndexed { i, v -> TrendPoint(now - (n - 1 - i).toLong() * everyDays * day, v) }
    }

    @Test fun risingSeries_isUpAroundFourPercent() {
        // 100 → 104 linearly over 5 weekly points ⇒ +4.0% ↑
        val pts = pointsEndingNow(listOf(100.0, 101.0, 102.0, 103.0, 104.0), everyDays = 7)
        val r = trend(pts, now) as TrendResult.Ok
        assertEquals(TrendDirection.UP, r.direction)
        assertEquals(4.0, r.percent, 0.001)
    }

    @Test fun flatNoisySeries_isFlat() {
        val pts = pointsEndingNow(listOf(100.0, 99.5, 100.5, 99.8, 100.2), everyDays = 7)
        val r = trend(pts, now) as TrendResult.Ok
        assertEquals(TrendDirection.FLAT, r.direction)
        assertTrue(kotlin.math.abs(r.percent) <= 1.0)
    }

    @Test fun twoSessions_isInsufficient() {
        val pts = pointsEndingNow(listOf(100.0, 102.0), everyDays = 7)
        assertEquals(TrendResult.Insufficient, trend(pts, now))
    }

    @Test fun windowDays_restrictsTheWindow() {
        // 5 weekly points span 28 days. A 90-day window sees all 5 (Ok); a 7-day window
        // sees only the last two (< 3) ⇒ Insufficient. Drives the detail range selector.
        val pts = pointsEndingNow(listOf(100.0, 101.0, 102.0, 103.0, 104.0), everyDays = 7)
        assertTrue(trend(pts, now, windowDays = 90) is TrendResult.Ok)
        assertEquals(TrendResult.Insufficient, trend(pts, now, windowDays = 7))
    }

    @Test fun lastPointThirtyDaysOld_isStale() {
        // newest point 30 days ago ⇒ stale, ~4 weeks
        val pts =
            listOf(
                TrendPoint(now - 44 * day, 100.0),
                TrendPoint(now - 37 * day, 101.0),
                TrendPoint(now - 30 * day, 102.0),
            )
        val r = trend(pts, now) as TrendResult.Stale
        assertEquals(4, r.weeks)
    }

    @Test fun empty_isInsufficient() = assertEquals(TrendResult.Insufficient, trend(emptyList(), now))
}

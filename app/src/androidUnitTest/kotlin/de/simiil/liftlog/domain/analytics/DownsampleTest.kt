package de.simiil.liftlog.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownsampleTest {
    private val day = 86_400_000L

    @Test fun underThreshold_returnsInput() {
        val pts = (0 until 10).map { TrendPoint(it.toLong() * day, it.toDouble()) }
        assertEquals(pts, downsample(pts, Aggregation.MAX, maxPoints = 200))
    }

    @Test fun overThreshold_bucketsByWeek_maxAggregation() {
        // 3 points in the same ISO week ⇒ one bucket carrying the MAX value
        // base = 50 days from epoch = 1970-02-20 (Friday); +1=Sat, +2=Sun; all in ISO week 8
        val base = 50L * day
        val pts =
            listOf(
                TrendPoint(base, 5.0),
                TrendPoint(base + day, 9.0),
                TrendPoint(base + 2 * day, 7.0),
            )
        val out = downsample(pts, Aggregation.MAX, maxPoints = 2)
        assertEquals(1, out.size)
        assertEquals(9.0, out.first().value, 0.0)
    }

    @Test fun overThreshold_sumAggregation_sumsBucket() {
        val base = 50L * day
        val pts = listOf(TrendPoint(base, 5.0), TrendPoint(base + day, 9.0), TrendPoint(base + 2 * day, 7.0))
        val out = downsample(pts, Aggregation.SUM, maxPoints = 2)
        assertEquals(1, out.size)
        assertEquals(21.0, out.first().value, 0.0)
    }

    @Test fun bucketsAreChronological() {
        val pts = (0 until 400).map { TrendPoint(it.toLong() * day, it.toDouble()) }
        val out = downsample(pts, Aggregation.MAX, maxPoints = 200)
        assertTrue(out.size < pts.size)
        assertTrue(out.zipWithNext().all { (a, b) -> a.timeMillis < b.timeMillis })
    }
}

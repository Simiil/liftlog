package de.simiil.liftlog.domain.analytics

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.IsoFields

enum class Aggregation { MAX, SUM }

/**
 * Caps a series at [maxPoints] by bucketing into ISO weeks (04-analytics-spec §7).
 * MAX for e1RM/top-set, SUM for volume. Bucket timestamp = the bucket's last point.
 * Returns the input unchanged when already within the cap.
 */
fun downsample(
    points: List<TrendPoint>,
    aggregation: Aggregation,
    maxPoints: Int = 200,
): List<TrendPoint> {
    if (points.size <= maxPoints) return points
    val sorted = points.sortedBy { it.timeMillis }
    val buckets = LinkedHashMap<Long, MutableList<TrendPoint>>()
    for (p in sorted) {
        val date = Instant.ofEpochMilli(p.timeMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val key = date.get(IsoFields.WEEK_BASED_YEAR) * 100L + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        buckets.getOrPut(key) { mutableListOf() }.add(p)
    }
    return buckets.values.map { bucket ->
        val value =
            when (aggregation) {
                Aggregation.MAX -> bucket.maxOf { it.value }
                Aggregation.SUM -> bucket.sumOf { it.value }
            }
        TrendPoint(bucket.last().timeMillis, value)
    }
}

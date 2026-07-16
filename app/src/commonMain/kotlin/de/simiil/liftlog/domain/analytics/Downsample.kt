package de.simiil.liftlog.domain.analytics

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
        val date = Instant.fromEpochMilliseconds(p.timeMillis).toLocalDateTime(TimeZone.UTC).date
        val key = isoWeekKey(date)
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

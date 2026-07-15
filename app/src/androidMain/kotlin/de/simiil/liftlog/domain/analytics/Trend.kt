package de.simiil.liftlog.domain.analytics

import kotlin.math.roundToInt

enum class TrendDirection { UP, FLAT, DOWN }

data class TrendPoint(
    val timeMillis: Long,
    val value: Double,
)

sealed interface TrendResult {
    /** Fitted-endpoint percent change over the window + classified direction. */
    data class Ok(
        val percent: Double,
        val direction: TrendDirection,
    ) : TrendResult

    /** Not trained recently (> 21 days since the last point). */
    data class Stale(
        val weeks: Int,
    ) : TrendResult

    /** Fewer than 3 points in the trend window. */
    data object Insufficient : TrendResult
}

private const val DAY = 86_400_000.0

/** ±1% direction classification (04-analytics-spec §3) — shared with muscle-balance aggregation. */
fun trendDirection(percent: Double): TrendDirection =
    when {
        percent > 1.0 -> TrendDirection.UP
        percent < -1.0 -> TrendDirection.DOWN
        else -> TrendDirection.FLAT
    }

/**
 * OLS trend over the trailing [windowDays] days of the LAST point (04-analytics-spec §3 uses 90).
 * Stale if the last point is > 21 days before [nowMillis] (recency signal, independent of the
 * window); insufficient if < 3 points in-window.
 */
fun trend(
    points: List<TrendPoint>,
    nowMillis: Long,
    windowDays: Long = 90,
): TrendResult {
    if (points.isEmpty()) return TrendResult.Insufficient
    val lastT = points.maxOf { it.timeMillis }
    val daysSinceLast = (nowMillis - lastT) / DAY
    if (daysSinceLast > 21) return TrendResult.Stale((daysSinceLast / 7).roundToInt())

    val window = points.filter { it.timeMillis >= lastT - windowDays * DAY }.sortedBy { it.timeMillis }
    if (window.size < 3) return TrendResult.Insufficient

    val t0 = window.first().timeMillis
    val xs = window.map { (it.timeMillis - t0) / DAY }
    val ys = window.map { it.value }
    val n = xs.size
    val mx = xs.average()
    val my = ys.average()
    var num = 0.0
    var den = 0.0
    for (i in 0 until n) {
        num += (xs[i] - mx) * (ys[i] - my)
        den += (xs[i] - mx) * (xs[i] - mx)
    }
    val b = if (den != 0.0) num / den else 0.0
    val a = my - b * mx

    fun f(x: Double) = a + b * x
    val fStart = f(xs.first())
    val fEnd = f(xs.last())
    val percent = if (fStart != 0.0) (fEnd - fStart) / fStart * 100 else 0.0
    return TrendResult.Ok(percent, trendDirection(percent))
}

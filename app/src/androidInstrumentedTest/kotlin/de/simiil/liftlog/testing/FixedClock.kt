package de.simiil.liftlog.testing

import kotlin.time.Clock
import kotlin.time.Instant

/** Mirrors the unit-test [de.simiil.liftlog.testing.FixedClock] (src/test) — androidTest is a
 *  separate source set with no visibility into src/test, so it needs its own copy. */
class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

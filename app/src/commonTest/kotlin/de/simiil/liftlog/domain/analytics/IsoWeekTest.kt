package de.simiil.liftlog.domain.analytics

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class IsoWeekTest {
    @Test fun midYear() {
        assertEquals(202623L, isoWeekKey(LocalDate(2026, 6, 4)))
    } // Thu, week 23

    @Test fun jan1InPrevYearsWeek() {
        assertEquals(202053L, isoWeekKey(LocalDate(2021, 1, 1)))
    } // Fri → 2020-W53

    @Test fun dec31InNextYearsWeek() {
        assertEquals(202501L, isoWeekKey(LocalDate(2024, 12, 30)))
    } // Mon → 2025-W01

    @Test fun week1Boundary() {
        assertEquals(202601L, isoWeekKey(LocalDate(2025, 12, 29)))
    } // Mon → 2026-W01
}

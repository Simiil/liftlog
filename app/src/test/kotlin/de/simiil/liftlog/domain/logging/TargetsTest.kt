package de.simiil.liftlog.domain.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetsTest {
    @Test fun bothNull_isNull() = assertNull(Targets.repRangeHint(null, null))
    @Test fun range_usesEnDash() = assertEquals("8–12", Targets.repRangeHint(8, 12))
    @Test fun equalBounds_singleNumber() = assertEquals("8", Targets.repRangeHint(8, 8))
    @Test fun onlyMin_singleNumber() = assertEquals("8", Targets.repRangeHint(8, null))
    @Test fun onlyMax_singleNumber() = assertEquals("12", Targets.repRangeHint(null, 12))
}

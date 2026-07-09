package de.simiil.liftlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForceTest {
    @Test fun fromStorageValue_recognizesAllNames() {
        Force.entries.forEach { assertEquals(it, Force.fromStorageValue(it.name)) }
    }

    @Test fun fromStorageValue_unknownOrNull_isNull() {
        assertNull(Force.fromStorageValue("???"))
        assertNull(Force.fromStorageValue(null))
    }
}

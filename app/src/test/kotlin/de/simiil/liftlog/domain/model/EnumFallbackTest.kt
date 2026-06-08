package de.simiil.liftlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EnumFallbackTest {
    @Test fun muscleGroup_knownValue_parses() =
        assertEquals(MuscleGroup.CHEST, MuscleGroup.fromStorageValue("CHEST"))

    @Test fun muscleGroup_unknownOrNull_fallsBackToOther() {
        assertEquals(MuscleGroup.OTHER, MuscleGroup.fromStorageValue("KETTLE_TOSS"))
        assertEquals(MuscleGroup.OTHER, MuscleGroup.fromStorageValue(null))
    }

    @Test fun equipment_knownValue_parses() =
        assertEquals(Equipment.BARBELL, Equipment.fromStorageValue("BARBELL"))

    @Test fun equipment_unknownOrNull_fallsBackToMachine() {
        assertEquals(Equipment.MACHINE, Equipment.fromStorageValue("RESISTANCE_BAND"))
        assertEquals(Equipment.MACHINE, Equipment.fromStorageValue(null))
    }
}

package de.simiil.liftlog.data.db

import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test fun muscleGroup_roundTrips() {
        MuscleGroup.entries.forEach { assertEquals(it, c.toMuscleGroup(c.fromMuscleGroup(it))) }
    }
    @Test fun equipment_roundTrips() {
        Equipment.entries.forEach { assertEquals(it, c.toEquipment(c.fromEquipment(it))) }
    }
    @Test fun unknownStrings_fallBack() {
        assertEquals(MuscleGroup.OTHER, c.toMuscleGroup("???"))
        assertEquals(Equipment.MACHINE, c.toEquipment("???"))
    }
}

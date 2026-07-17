package de.simiil.liftlog.data.db

import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Force
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        assertEquals(Equipment.OTHER, c.toEquipment("???"))
    }

    @Test fun force_roundTrips() {
        Force.entries.forEach { assertEquals(it, c.toForce(c.fromForce(it))) }
        assertNull(c.toForce(c.fromForce(null)))
    }

    @Test fun muscleGroupList_roundTrips() {
        val lists =
            listOf(
                emptyList(),
                listOf(MuscleGroup.BACK),
                listOf(MuscleGroup.BACK, MuscleGroup.BICEPS, MuscleGroup.FOREARMS),
            )
        lists.forEach { assertEquals(it, c.toMuscleGroupList(c.fromMuscleGroupList(it))) }
    }

    @Test fun muscleGroupList_emptyEncodesAsEmptyJsonArray() {
        // The v2→v3 migration backfills existing rows with '[]' — lock the encoding.
        assertEquals("[]", c.fromMuscleGroupList(emptyList()))
    }

    @Test fun muscleGroupList_unknownNameFallsBackToOther() {
        assertEquals(listOf(MuscleGroup.OTHER), c.toMuscleGroupList("""["WINGS"]"""))
    }

    @Test fun muscleGroupList_malformedCellDegradesToEmpty() {
        assertEquals(emptyList<MuscleGroup>(), c.toMuscleGroupList("not json"))
        assertEquals(emptyList<MuscleGroup>(), c.toMuscleGroupList(""))
    }
}

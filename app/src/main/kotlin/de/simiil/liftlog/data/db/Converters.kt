package de.simiil.liftlog.data.db

import androidx.room.TypeConverter
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup

/** Enums persist as their [Enum.name]; unknown strings fall back (see enum docs). */
class Converters {
    @TypeConverter fun fromMuscleGroup(value: MuscleGroup): String = value.name
    @TypeConverter fun toMuscleGroup(value: String): MuscleGroup = MuscleGroup.fromStorageValue(value)
    @TypeConverter fun fromEquipment(value: Equipment): String = value.name
    @TypeConverter fun toEquipment(value: String): Equipment = Equipment.fromStorageValue(value)
}

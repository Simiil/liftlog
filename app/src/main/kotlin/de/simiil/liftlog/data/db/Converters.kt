package de.simiil.liftlog.data.db

import androidx.room.TypeConverter
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Force
import de.simiil.liftlog.domain.model.MuscleGroup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Enums persist as their [Enum.name]; unknown strings fall back (see enum docs).
 *  Enum lists persist as JSON string arrays (`["BACK","BICEPS"]`); malformed cells degrade to empty. */
class Converters {
    @TypeConverter fun fromMuscleGroup(value: MuscleGroup): String = value.name

    @TypeConverter fun toMuscleGroup(value: String): MuscleGroup = MuscleGroup.fromStorageValue(value)

    @TypeConverter fun fromEquipment(value: Equipment): String = value.name

    @TypeConverter fun toEquipment(value: String): Equipment = Equipment.fromStorageValue(value)

    @TypeConverter fun fromForce(value: Force?): String? = value?.name

    @TypeConverter fun toForce(value: String?): Force? = Force.fromStorageValue(value)

    @TypeConverter fun fromMuscleGroupList(value: List<MuscleGroup>): String = Json.encodeToString(value.map { it.name })

    @TypeConverter fun toMuscleGroupList(value: String): List<MuscleGroup> =
        try {
            Json.decodeFromString<List<String>>(value).map { MuscleGroup.fromStorageValue(it) }
        } catch (e: SerializationException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
}

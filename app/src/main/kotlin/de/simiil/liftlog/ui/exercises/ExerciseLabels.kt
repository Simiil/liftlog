package de.simiil.liftlog.ui.exercises

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup

@Composable
fun muscleGroupLabel(muscleGroup: MuscleGroup): String =
    stringResource(
        when (muscleGroup) {
            MuscleGroup.CHEST -> R.string.muscle_chest
            MuscleGroup.BACK -> R.string.muscle_back
            MuscleGroup.SHOULDERS -> R.string.muscle_shoulders
            MuscleGroup.BICEPS -> R.string.muscle_biceps
            MuscleGroup.TRICEPS -> R.string.muscle_triceps
            MuscleGroup.QUADS -> R.string.muscle_quads
            MuscleGroup.HAMSTRINGS -> R.string.muscle_hamstrings
            MuscleGroup.GLUTES -> R.string.muscle_glutes
            MuscleGroup.CALVES -> R.string.muscle_calves
            MuscleGroup.ABS -> R.string.muscle_abs
            MuscleGroup.FOREARMS -> R.string.muscle_forearms
            MuscleGroup.OTHER -> R.string.muscle_other
        },
    )

@Composable
fun equipmentLabel(equipment: Equipment): String =
    stringResource(
        when (equipment) {
            Equipment.BARBELL -> R.string.equipment_barbell
            Equipment.DUMBBELL -> R.string.equipment_dumbbell
            Equipment.MACHINE -> R.string.equipment_machine
            Equipment.CABLE -> R.string.equipment_cable
            Equipment.BODYWEIGHT -> R.string.equipment_bodyweight
        },
    )

@Composable
fun exerciseDisplayName(
    id: String,
    fallbackName: String,
): String = BuiltInExerciseNames.resById[id]?.let { stringResource(it) } ?: fallbackName

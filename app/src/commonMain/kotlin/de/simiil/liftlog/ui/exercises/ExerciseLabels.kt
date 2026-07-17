package de.simiil.liftlog.ui.exercises

import androidx.compose.runtime.Composable
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.equipment_bands
import liftlog.app.generated.resources.equipment_barbell
import liftlog.app.generated.resources.equipment_bodyweight
import liftlog.app.generated.resources.equipment_cable
import liftlog.app.generated.resources.equipment_dumbbell
import liftlog.app.generated.resources.equipment_exercise_ball
import liftlog.app.generated.resources.equipment_foam_roller
import liftlog.app.generated.resources.equipment_kettlebell
import liftlog.app.generated.resources.equipment_machine
import liftlog.app.generated.resources.equipment_medicine_ball
import liftlog.app.generated.resources.equipment_other
import liftlog.app.generated.resources.muscle_abs
import liftlog.app.generated.resources.muscle_back
import liftlog.app.generated.resources.muscle_biceps
import liftlog.app.generated.resources.muscle_calves
import liftlog.app.generated.resources.muscle_chest
import liftlog.app.generated.resources.muscle_forearms
import liftlog.app.generated.resources.muscle_glutes
import liftlog.app.generated.resources.muscle_hamstrings
import liftlog.app.generated.resources.muscle_other
import liftlog.app.generated.resources.muscle_quads
import liftlog.app.generated.resources.muscle_shoulders
import liftlog.app.generated.resources.muscle_triceps
import org.jetbrains.compose.resources.stringResource

@Composable
fun muscleGroupLabel(muscleGroup: MuscleGroup): String =
    stringResource(
        when (muscleGroup) {
            MuscleGroup.CHEST -> Res.string.muscle_chest
            MuscleGroup.BACK -> Res.string.muscle_back
            MuscleGroup.SHOULDERS -> Res.string.muscle_shoulders
            MuscleGroup.BICEPS -> Res.string.muscle_biceps
            MuscleGroup.TRICEPS -> Res.string.muscle_triceps
            MuscleGroup.QUADS -> Res.string.muscle_quads
            MuscleGroup.HAMSTRINGS -> Res.string.muscle_hamstrings
            MuscleGroup.GLUTES -> Res.string.muscle_glutes
            MuscleGroup.CALVES -> Res.string.muscle_calves
            MuscleGroup.ABS -> Res.string.muscle_abs
            MuscleGroup.FOREARMS -> Res.string.muscle_forearms
            MuscleGroup.OTHER -> Res.string.muscle_other
        },
    )

@Composable
fun equipmentLabel(equipment: Equipment): String =
    stringResource(
        when (equipment) {
            Equipment.BARBELL -> Res.string.equipment_barbell
            Equipment.DUMBBELL -> Res.string.equipment_dumbbell
            Equipment.MACHINE -> Res.string.equipment_machine
            Equipment.CABLE -> Res.string.equipment_cable
            Equipment.BODYWEIGHT -> Res.string.equipment_bodyweight
            Equipment.KETTLEBELL -> Res.string.equipment_kettlebell
            Equipment.MEDICINE_BALL -> Res.string.equipment_medicine_ball
            Equipment.FOAM_ROLLER -> Res.string.equipment_foam_roller
            Equipment.BANDS -> Res.string.equipment_bands
            Equipment.EXERCISE_BALL -> Res.string.equipment_exercise_ball
            Equipment.OTHER -> Res.string.equipment_other
        },
    )

@Composable
fun exerciseDisplayName(
    id: String,
    fallbackName: String,
): String = BuiltInExerciseNames.resById[id]?.let { stringResource(it) } ?: fallbackName

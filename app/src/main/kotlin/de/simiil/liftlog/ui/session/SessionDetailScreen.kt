package de.simiil.liftlog.ui.session

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.units.Weights
import de.simiil.liftlog.ui.components.LoggedSetRow
import de.simiil.liftlog.ui.exercises.muscleGroupLabel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val name = uiState.name ?: stringResource(R.string.session_untitled)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.loading -> {
                Box(
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.exercises.isEmpty() -> {
                Box(
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.session_detail_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                val totalSets = uiState.exercises.sumOf { it.sets.size }
                val volumeKg = uiState.exercises.sumOf { e -> e.sets.sumOf { it.weightKg * it.reps } }
                LazyColumn(
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.startedAt?.let { startedAt ->
                        item(key = "datestrip") { DateStrip(startedAt) }
                    }
                    item(key = "summary") {
                        SummaryStrip(
                            startedAt = uiState.startedAt,
                            endedAt = uiState.endedAt,
                            totalSets = totalSets,
                            volumeKg = volumeKg,
                        )
                    }
                    item(key = "hint") {
                        Text(
                            text = stringResource(R.string.session_detail_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(uiState.exercises, key = { it.sessionExerciseId }) { exercise ->
                        DetailCard(
                            exercise = exercise,
                            unit = uiState.unit,
                            editingSetId = uiState.editingSetId,
                            onLongPress = viewModel::onLongPressSet,
                            onSave = viewModel::onEditSetSave,
                            onDelete = viewModel::onDeleteSet,
                            onCollapse = viewModel::onCollapseEdit,
                        )
                    }
                    uiState.startedAt?.let { startedAt ->
                        item(key = "foot") {
                            Text(
                                text = stringResource(
                                    R.string.session_detail_foot,
                                    name,
                                    relativeDate(startedAt),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Date strip: "Mon 2 Jun · 2026 · started 18:30" ────────────────────────────
private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

@Composable
private fun DateStrip(startedAt: Instant) {
    val ldt = startedAt.atZone(ZoneId.systemDefault())
    Text(
        text = stringResource(
            R.string.session_detail_started,
            DATE_FMT.format(ldt),
            ldt.year,
            TIME_FMT.format(ldt),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

// ── Summary stat strip: duration · sets · volume ──────────────────────────────
@Composable
private fun SummaryStrip(
    startedAt: Instant?,
    endedAt: Instant?,
    totalSets: Int,
    volumeKg: Double,
) {
    val duration = if (startedAt != null && endedAt != null) {
        val sec = Duration.between(startedAt, endedAt).seconds.coerceAtLeast(0)
        stringResource(R.string.session_duration_value, sec / 60, sec % 60)
    } else {
        "—"
    }
    val volume = stringResource(
        R.string.session_stat_volume_value,
        String.format(Locale.US, "%.1f", volumeKg / 1000.0),
    )
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryStat(duration, stringResource(R.string.session_stat_duration), Modifier.weight(1f))
            SummaryStat(totalSets.toString(), stringResource(R.string.session_stat_sets), Modifier.weight(1f))
            SummaryStat(volume, stringResource(R.string.session_stat_volume), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── One exercise card: title, sub-line, logged set rows ───────────────────────
@Composable
private fun DetailCard(
    exercise: DetailExerciseUi,
    unit: de.simiil.liftlog.domain.model.WeightUnit,
    editingSetId: String?,
    onLongPress: (String) -> Unit,
    onSave: (String, Double, Int, Double?, String?) -> Unit,
    onDelete: (String) -> Unit,
    onCollapse: () -> Unit,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = exerciseSubtitle(exercise, unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                exercise.sets.forEachIndexed { index, set ->
                    LoggedSetRow(
                        index = index + 1,
                        set = set,
                        unit = unit,
                        expanded = editingSetId == set.id,
                        onLongPress = { onLongPress(set.id) },
                        onSave = { w, r, rpe, note -> onSave(set.id, w, r, rpe, note) },
                        onDelete = { onDelete(set.id) },
                        onCollapse = onCollapse,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** "top 85 kg · 3 sets · Chest" — or "Bodyweight · 3 sets · Back" for bodyweight lifts. */
@Composable
private fun exerciseSubtitle(exercise: DetailExerciseUi, unit: de.simiil.liftlog.domain.model.WeightUnit): String {
    val lead = if (exercise.equipment == Equipment.BODYWEIGHT) {
        stringResource(R.string.session_detail_bodyweight)
    } else {
        val topKg = exercise.sets.maxOfOrNull { it.weightKg } ?: 0.0
        stringResource(R.string.session_detail_top_weight, Weights.format(topKg, unit), Weights.label(unit))
    }
    val sets = pluralStringResource(R.plurals.set_count, exercise.sets.size, exercise.sets.size)
    return stringResource(R.string.session_detail_ex_sub, lead, sets, muscleGroupLabel(exercise.muscleGroup))
}

@Composable
private fun relativeDate(startedAt: Instant): String =
    DateUtils.getRelativeTimeSpanString(
        startedAt.toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

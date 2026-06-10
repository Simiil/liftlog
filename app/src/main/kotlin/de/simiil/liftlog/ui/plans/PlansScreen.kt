package de.simiil.liftlog.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.ui.UiTestTags
import de.simiil.liftlog.ui.exercises.muscleGroupLabel
import de.simiil.liftlog.ui.theme.LiftLogTheme

// ─── Screen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(
    onEditPlan: (String) -> Unit,
    onNewPlan: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlansViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlansContent(
        plans = uiState.plans,
        loading = uiState.loading,
        onEditPlan = onEditPlan,
        onNewPlan = onNewPlan,
        onStartDay = { templateId -> viewModel.startDay(templateId, onOpenSession) },
        modifier = modifier,
    )
}

// ─── Stateless content (easier to preview) ───────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlansContent(
    plans: List<PlanCardUi>,
    loading: Boolean,
    onEditPlan: (String) -> Unit,
    onNewPlan: () -> Unit,
    onStartDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_plans)) },
                actions = {
                    IconButton(
                        onClick = onNewPlan,
                        modifier = Modifier.testTag(UiTestTags.PLANS_CREATE),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.plans_create),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!loading && plans.isEmpty()) {
            PlansEmptyState(
                onNewPlan = onNewPlan,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(plans, key = { it.id }) { plan ->
                    PlanGroupCard(
                        plan = plan,
                        onEditPlan = { onEditPlan(plan.id) },
                        onStartDay = onStartDay,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    NewPlanButton(
                        onClick = onNewPlan,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ─── Plan group card (header + day rows) ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanGroupCard(
    plan: PlanCardUi,
    onEditPlan: () -> Unit,
    onStartDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // .plan-group: surfaceContainerHigh, radius 22dp, padding 8/8/12dp.
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        ) {
            // .plan-group-head — whole-width button → edit plan
            val editCd = stringResource(R.string.plan_edit_cd, plan.name)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onEditPlan)
                    .testTag(UiTestTags.PLAN_ROW)
                    .semantics { contentDescription = editCd }
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.plan_group_sub,
                            pluralStringResource(R.plurals.plan_days_count, plan.days.size, plan.days.size),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null, // group head already carries the "Edit X" description
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }

            // .plan-row per day
            plan.days.forEach { day ->
                PlanDayRow(
                    day = day,
                    onStart = { onStartDay(day.templateId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PlanDayRow(
    day: PlanDayUi,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startCd = stringResource(R.string.plan_start_day_cd, day.name)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .testTag(UiTestTags.PLAN_DAY_ROW)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // .plan-row-main → start day
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onStart)
                // Merge day name + subtitle into one screen-reader node (F-01).
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = day.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = daySubtitle(day),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // .row-play — 48dp circular primary/onPrimary play button → start day (F-08)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onStart)
                .testTag(UiTestTags.PLAN_DAY_START)
                .semantics { contentDescription = startCd },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** "N exercises[ · group · group · group]" — mirrors the mockup's plan-row-sub. */
@Composable
private fun daySubtitle(day: PlanDayUi): String {
    val count = pluralStringResource(R.plurals.exercise_count, day.exerciseCount, day.exerciseCount)
    if (day.muscleGroups.isEmpty()) return count
    val groups = day.muscleGroups.map { muscleGroupLabel(it) }.joinToString(" · ")
    return "$count · $groups"
}

// ─── New-plan ghost button ────────────────────────────────────────────────────

@Composable
private fun NewPlanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(100.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.plans_create),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun PlansEmptyState(
    onNewPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.plans_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.plans_empty_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.height(24.dp))
        NewPlanButton(
            onClick = onNewPlan,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────────

private val previewPlans = listOf(
    PlanCardUi(
        id = "1",
        name = "Push Pull Legs",
        days = listOf(
            PlanDayUi("d1", "Push Day", 5, listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)),
            PlanDayUi("d2", "Pull Day", 5, listOf(MuscleGroup.BACK, MuscleGroup.BICEPS)),
            PlanDayUi("d3", "Leg Day", 4, listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES)),
        ),
    ),
    PlanCardUi(
        id = "2",
        name = "Upper / Lower",
        days = listOf(
            PlanDayUi("d4", "Upper A", 6, listOf(MuscleGroup.CHEST, MuscleGroup.BACK)),
            PlanDayUi("d5", "Lower A", 0, emptyList()),
        ),
    ),
)

@Preview(name = "Plans — empty (light)", showBackground = true)
@Composable
private fun PreviewPlansEmptyLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        PlansContent(plans = emptyList(), loading = false, onEditPlan = {}, onNewPlan = {}, onStartDay = {})
    }
}

@Preview(name = "Plans — empty (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewPlansEmptyDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        PlansContent(plans = emptyList(), loading = false, onEditPlan = {}, onNewPlan = {}, onStartDay = {})
    }
}

@Preview(name = "Plans — populated (light)", showBackground = true)
@Composable
private fun PreviewPlansPopulatedLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        PlansContent(plans = previewPlans, loading = false, onEditPlan = {}, onNewPlan = {}, onStartDay = {})
    }
}

@Preview(name = "Plans — populated (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewPlansPopulatedDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        PlansContent(plans = previewPlans, loading = false, onEditPlan = {}, onNewPlan = {}, onStartDay = {})
    }
}

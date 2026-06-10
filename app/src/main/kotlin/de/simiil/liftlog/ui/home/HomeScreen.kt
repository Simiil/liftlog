package de.simiil.liftlog.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.ui.UiTestTags
import de.simiil.liftlog.ui.components.dashedBorder
import de.simiil.liftlog.ui.exercises.muscleGroupLabel
import de.simiil.liftlog.ui.theme.LiftLogTheme
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // First-launch only when there's nothing to act on: no live/finished sessions AND no plans.
    // Once a plan exists, show the normal Home so its template chips are reachable.
    val firstLaunch = uiState.resume == null && uiState.recent.isEmpty() && !uiState.hasPlans

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_open),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (firstLaunch) {
            FirstLaunch(
                onStart = { viewModel.startOrResume(onOpenSession) },
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        } else {
            HomeContent(
                uiState = uiState,
                onResume = { id -> onOpenSession(id) },
                onStartEmpty = { viewModel.startOrResume(onOpenSession) },
                onStartFromTemplate = { templateId ->
                    viewModel.startFromTemplate(templateId, onOpenSession)
                },
                onOpenSessionDetail = onOpenSessionDetail,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onResume: (String) -> Unit,
    onStartEmpty: () -> Unit,
    onStartFromTemplate: (String) -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
    ) {
        uiState.resume?.let { resume ->
            item(key = "resume_card") {
                ResumeCard(
                    resume = resume,
                    onClick = { onResume(resume.sessionId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag(UiTestTags.HOME_RESUME_CARD),
                )
            }
        }

        item(key = "start_section_header") {
            SectionHeader(stringResource(R.string.home_start_training))
        }

        item(key = "start_grid") {
            TemplateChipGrid(
                chips = uiState.templates,
                onChipClick = onStartFromTemplate,
                onStartEmpty = onStartEmpty,
            )
        }

        item(key = "recent_section_header") {
            SectionHeader(stringResource(R.string.home_recent))
        }

        if (uiState.recent.isEmpty()) {
            item(key = "no_history") {
                Text(
                    text = stringResource(R.string.home_no_history),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        } else {
            itemsIndexed(uiState.recent, key = { _, s -> s.sessionId }) { index, session ->
                RecentSessionItem(
                    session = session,
                    showDivider = index < uiState.recent.lastIndex,
                    onClick = { onOpenSessionDetail(session.sessionId) },
                )
            }
        }
    }
}

@Composable
private fun ResumeCard(
    resume: ResumeCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elapsedMinutes = Duration.between(resume.startedAt, Instant.now()).toMinutes()
    val name = resume.name ?: stringResource(R.string.session_untitled)

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_resume_label, name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.home_resume_meta,
                        resume.exerciseCount,
                        elapsedMinutes,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.home_live),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EmptySessionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .dashedBorder(
                color = MaterialTheme.colorScheme.outline,
                width = 1.5.dp,
                cornerRadius = 20.dp,
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_start_empty),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 2-column grid of template chips + the trailing empty-session card. Built as explicit
 * rows of two so every cell is the same width AND height (a lone trailing cell stays
 * half-width, mirroring the design's `grid-template-columns: 1fr 1fr`). When [chips] is
 * empty the grid holds only the empty-session card.
 */
@Composable
private fun TemplateChipGrid(
    chips: List<TemplateChipUi>,
    onChipClick: (String) -> Unit,
    onStartEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Template chips followed by the empty-session card (null = the empty card).
    val cells: List<TemplateChipUi?> = chips + null
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        cells.chunked(2).forEach { rowCells ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowCells.forEach { cell ->
                    if (cell == null) {
                        EmptySessionCard(
                            onClick = onStartEmpty,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag(UiTestTags.HOME_START_EMPTY),
                        )
                    } else {
                        TemplateChip(
                            chip = cell,
                            onClick = { onChipClick(cell.templateId) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
                // Pad an incomplete row so a lone cell keeps its half-width column.
                if (rowCells.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TemplateChip(
    chip: TemplateChipUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cd = stringResource(R.string.home_template_chip_cd, chip.name)
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 96.dp)
            .testTag(UiTestTags.HOME_TEMPLATE_CHIP)
            .semantics { contentDescription = cd },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = chip.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.exercise_count,
                    chip.exerciseCount,
                    chip.exerciseCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (chip.muscleGroups.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = chip.muscleGroups.map { muscleGroupLabel(it) }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 12.dp),
    )
}

@Composable
private fun RecentSessionItem(
    session: RecentSessionUi,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = session.name ?: stringResource(R.string.session_untitled)
    val relativeDate = DateUtils.getRelativeTimeSpanString(
        session.startedAt.toEpochMilli(),
        Instant.now().toEpochMilli(),
        DateUtils.DAY_IN_MILLIS,
    ).toString()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                // Merge name + date + set count into one TalkBack node (F-01).
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 6.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = relativeDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = pluralStringResource(R.plurals.set_count, session.setCount, session.setCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun FirstLaunch(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.home_first_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_first_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(30.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(UiTestTags.HOME_START_EMPTY),
            shape = RoundedCornerShape(100.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.home_first_start),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.home_first_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────────────────────────────────────

@Preview(name = "Home – chips + empty card (light)", showBackground = true)
@Composable
private fun PreviewHomeWithChips() {
    LiftLogTheme {
        HomeContent(
            uiState = HomeUiState(
                templates = listOf(
                    TemplateChipUi("t1", "Push", 5, listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)),
                    TemplateChipUi("t2", "Pull", 5, listOf(MuscleGroup.BACK, MuscleGroup.BICEPS)),
                    TemplateChipUi("t3", "Legs", 4, listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES)),
                ),
                recent = emptyList(),
            ),
            onResume = {},
            onStartEmpty = {},
            onStartFromTemplate = {},
            onOpenSessionDetail = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "Home – no plans, empty card only (light)", showBackground = true)
@Composable
private fun PreviewHomeNoPlans() {
    LiftLogTheme {
        HomeContent(
            uiState = HomeUiState(
                templates = emptyList(),
                recent = emptyList(),
            ),
            onResume = {},
            onStartEmpty = {},
            onStartFromTemplate = {},
            onOpenSessionDetail = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}


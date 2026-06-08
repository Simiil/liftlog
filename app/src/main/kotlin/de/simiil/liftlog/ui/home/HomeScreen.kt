package de.simiil.liftlog.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Resume card
            uiState.resume?.let { resume ->
                item(key = "resume_card") {
                    ResumeCard(
                        resume = resume,
                        onClick = { onOpenSession(resume.sessionId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Start training section
            item(key = "start_section_header") {
                SectionHeader(
                    text = stringResource(R.string.home_start_training),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item(key = "start_empty") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.home_start_empty)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.startOrResume(onOpenSession) }
                        .padding(horizontal = 0.dp),
                )
            }

            // Recent section
            item(key = "recent_section_header") {
                SectionHeader(
                    text = stringResource(R.string.home_recent),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (uiState.recent.isEmpty()) {
                item(key = "no_history") {
                    Text(
                        text = stringResource(R.string.home_no_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(uiState.recent, key = { it.sessionId }) { session ->
                    RecentSessionItem(
                        session = session,
                        onClick = { onOpenSessionDetail(session.sessionId) },
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${stringResource(R.string.home_resume)} — $name",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_resume_summary, resume.exerciseCount, elapsedMinutes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun RecentSessionItem(
    session: RecentSessionUi,
    onClick: () -> Unit,
) {
    val name = session.name ?: stringResource(R.string.session_untitled)
    val relativeDate = DateUtils.getRelativeTimeSpanString(
        session.startedAt.toEpochMilli(),
        Instant.now().toEpochMilli(),
        DateUtils.DAY_IN_MILLIS,
    ).toString()
    val supportingText = "$relativeDate · ${stringResource(R.string.home_set_count, session.setCount)}"

    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(supportingText) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

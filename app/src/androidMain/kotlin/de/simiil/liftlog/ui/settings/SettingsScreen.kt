package de.simiil.liftlog.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.domain.format.LocaleFormatters
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.domain.repository.InvalidReason
import kotlinx.datetime.TimeZone
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.backup_cancel
import liftlog.app.generated.resources.backup_dialog_ok
import liftlog.app.generated.resources.backup_error_corrupt
import liftlog.app.generated.resources.backup_error_live_session
import liftlog.app.generated.resources.backup_error_newer
import liftlog.app.generated.resources.backup_error_title
import liftlog.app.generated.resources.backup_export_failed
import liftlog.app.generated.resources.backup_exported
import liftlog.app.generated.resources.backup_import_confirm_button
import liftlog.app.generated.resources.backup_import_confirm_message
import liftlog.app.generated.resources.backup_import_confirm_title
import liftlog.app.generated.resources.backup_imported
import liftlog.app.generated.resources.navigate_back
import liftlog.app.generated.resources.settings_data_label
import liftlog.app.generated.resources.settings_export
import liftlog.app.generated.resources.settings_export_desc
import liftlog.app.generated.resources.settings_import
import liftlog.app.generated.resources.settings_import_desc
import liftlog.app.generated.resources.settings_seed_demo
import liftlog.app.generated.resources.settings_theme_label
import liftlog.app.generated.resources.settings_title
import liftlog.app.generated.resources.theme_dark
import liftlog.app.generated.resources.theme_light
import liftlog.app.generated.resources.theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formatters = koinInject<LocaleFormatters>()
    val snackbarHostState = remember { SnackbarHostState() }

    val launchExport =
        rememberCreateDocumentLauncher("application/json") { handle -> handle?.let(viewModel::export) }
    val launchImport =
        rememberOpenDocumentLauncher(listOf("application/json")) { handle -> handle?.let(viewModel::prepareImport) }

    val exported = stringResource(Res.string.backup_exported)
    val exportFailed = stringResource(Res.string.backup_export_failed)
    val imported = stringResource(Res.string.backup_imported)
    LaunchedEffect(uiState.message) {
        val text =
            when (uiState.message) {
                SettingsMessage.EXPORTED -> exported
                SettingsMessage.EXPORT_FAILED -> exportFailed
                SettingsMessage.IMPORTED -> imported
                null -> null
            }
        if (text != null) {
            try {
                snackbarHostState.showSnackbar(text)
            } finally {
                viewModel.consumeMessage()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(Res.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.selectableGroup()) {
                SectionHeader(Res.string.settings_theme_label)
                ThemeOptionRow(Res.string.theme_system, ThemePreference.SYSTEM, uiState.theme, viewModel::onThemeSelected)
                ThemeOptionRow(Res.string.theme_light, ThemePreference.LIGHT, uiState.theme, viewModel::onThemeSelected)
                ThemeOptionRow(Res.string.theme_dark, ThemePreference.DARK, uiState.theme, viewModel::onThemeSelected)
            }

            SectionHeader(Res.string.settings_data_label)
            ActionRow(
                icon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                titleRes = Res.string.settings_export,
                descRes = Res.string.settings_export_desc,
                onClick = { launchExport(viewModel.defaultExportFileName()) },
            )
            ActionRow(
                icon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                titleRes = Res.string.settings_import,
                descRes = Res.string.settings_import_desc,
                onClick = { launchImport() },
            )

            if (de.simiil.liftlog.BuildConfig.DEBUG) {
                TextButton(
                    onClick = viewModel::seedDemoData,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(Res.string.settings_seed_demo)) }
            }
        }
    }

    uiState.pendingImport?.let { summary ->
        val date =
            remember(summary) {
                formatters.mediumDate(
                    summary.exportedAt,
                    TimeZone.currentSystemDefault(),
                )
            }
        AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            title = { Text(stringResource(Res.string.backup_import_confirm_title)) },
            text = {
                Text(stringResource(Res.string.backup_import_confirm_message, date, summary.sessions, summary.exercises))
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) {
                    Text(stringResource(Res.string.backup_import_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissImport) { Text(stringResource(Res.string.backup_cancel)) }
            },
        )
    }

    uiState.dialog?.let { dialog ->
        val body =
            when (dialog) {
                SettingsDialog.LiveSession -> stringResource(Res.string.backup_error_live_session)
                is SettingsDialog.Newer -> stringResource(Res.string.backup_error_newer)
                is SettingsDialog.Invalid ->
                    when (dialog.reason) {
                        InvalidReason.MALFORMED, InvalidReason.MISSING_FIELDS, InvalidReason.BAD_TIMESTAMP,
                        InvalidReason.FK_ORPHAN, InvalidReason.UNKNOWN_ENUM,
                        -> stringResource(Res.string.backup_error_corrupt)
                    }
            }
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(Res.string.backup_error_title)) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(Res.string.backup_dialog_ok)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(labelRes: StringResource) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() },
    )
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    titleRes: StringResource,
    descRes: StringResource,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp) // comfortable tap target (a11y §7 floor is 48dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(Modifier.padding(start = 16.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    labelRes: StringResource,
    option: ThemePreference,
    currentSelection: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(
                    selected = option == currentSelection,
                    onClick = { onSelect(option) },
                    role = Role.RadioButton,
                ).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option == currentSelection, onClick = null)
        Text(text = stringResource(labelRes), modifier = Modifier.padding(start = 12.dp))
    }
}

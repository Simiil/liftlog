package de.simiil.liftlog.ui.settings

import androidx.compose.runtime.Composable

/**
 * Platform document-picker launchers, seamed so `SettingsScreen` doesn't reference
 * `androidx.activity.result` (Android-only) directly. Android actuals wrap
 * `rememberLauncherForActivityResult` verbatim; iOS actuals are honest no-ops until M8 wires
 * `UIDocumentPickerViewController` — nothing on iOS calls the returned launcher in M7.
 */
@Composable
expect fun rememberCreateDocumentLauncher(
    mimeType: String,
    onResult: (DocumentHandle?) -> Unit,
): (suggestedName: String) -> Unit

@Composable
expect fun rememberOpenDocumentLauncher(
    mimeTypes: List<String>,
    onResult: (DocumentHandle?) -> Unit,
): () -> Unit

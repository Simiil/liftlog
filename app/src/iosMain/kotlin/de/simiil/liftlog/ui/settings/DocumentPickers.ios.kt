package de.simiil.liftlog.ui.settings

import androidx.compose.runtime.Composable

@Composable
actual fun rememberCreateDocumentLauncher(
    mimeType: String,
    onResult: (DocumentHandle?) -> Unit,
): (suggestedName: String) -> Unit =
    { _ ->
        // M8: UIDocumentPickerViewController (export flow) — no picker exists on iOS in M7.
    }

@Composable
actual fun rememberOpenDocumentLauncher(
    mimeTypes: List<String>,
    onResult: (DocumentHandle?) -> Unit,
): () -> Unit =
    {
        // M8: UIDocumentPickerViewController (import flow) — no picker exists on iOS in M7.
    }

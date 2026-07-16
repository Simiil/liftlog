package de.simiil.liftlog.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
actual fun rememberCreateDocumentLauncher(
    mimeType: String,
    onResult: (DocumentHandle?) -> Unit,
): (suggestedName: String) -> Unit {
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(mimeType),
        ) { uri -> onResult(uri?.let(::DocumentHandle)) }
    return { suggestedName -> launcher.launch(suggestedName) }
}

@Composable
actual fun rememberOpenDocumentLauncher(
    mimeTypes: List<String>,
    onResult: (DocumentHandle?) -> Unit,
): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> onResult(uri?.let(::DocumentHandle)) }
    return { launcher.launch(mimeTypes.toTypedArray()) }
}

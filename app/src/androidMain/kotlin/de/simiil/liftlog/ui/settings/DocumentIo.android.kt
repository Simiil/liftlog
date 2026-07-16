package de.simiil.liftlog.ui.settings

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class DocumentHandle(
    val uri: Uri,
)

class AndroidDocumentIo(
    private val context: Context,
) : DocumentIo {
    override suspend fun readText(handle: DocumentHandle): String =
        withContext(Dispatchers.IO) {
            context.contentResolver
                .openInputStream(handle.uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Cannot open ${handle.uri} for reading")
        }

    override suspend fun writeText(
        handle: DocumentHandle,
        text: String,
    ) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(handle.uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Cannot open ${handle.uri} for writing")
    }
}

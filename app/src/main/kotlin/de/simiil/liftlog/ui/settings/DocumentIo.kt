package de.simiil.liftlog.ui.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Reads/writes text to a SAF document Uri. Isolated so the ViewModel stays testable and the
 *  repository/domain never sees android.net.Uri. */
interface DocumentIo {
    suspend fun readText(uri: Uri): String
    suspend fun writeText(uri: Uri, text: String)
}

class AndroidDocumentIo @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentIo {
    override suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Cannot open $uri for reading")
    }

    override suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Cannot open $uri for writing")
    }
}

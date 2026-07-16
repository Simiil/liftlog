package de.simiil.liftlog.ui.settings

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL

actual class DocumentHandle(
    val url: NSURL,
)

/**
 * Reads/writes text to a document [NSURL] via Foundation's `NSString` file APIs. Compile-only in
 * M7 (no Xcode toolchain here to run on a simulator/device — the klib compile is the gate); M8
 * verifies against a real `UIDocumentPickerViewController` URL, including the
 * security-scoped-resource access such URLs require
 * (`startAccessingSecurityScopedResource`/`stopAccessingSecurityScopedResource`).
 */
@OptIn(ExperimentalForeignApi::class)
class IosDocumentIo : DocumentIo {
    override suspend fun readText(handle: DocumentHandle): String =
        withContext(Dispatchers.Default) {
            NSString.stringWithContentsOfURL(handle.url, NSUTF8StringEncoding, null)
                ?: error("Cannot open ${handle.url} for reading")
        }

    override suspend fun writeText(
        handle: DocumentHandle,
        text: String,
    ) = withContext(Dispatchers.Default) {
        // Kotlin's static type checker doesn't know Kotlin String and Foundation NSString are the
        // same object at runtime (toll-free bridging), hence the (harmless, suppressed) "can never
        // succeed" cast warning below.
        @Suppress("CAST_NEVER_SUCCEEDS")
        val ok = (text as NSString).writeToURL(handle.url, true, NSUTF8StringEncoding, null)
        if (!ok) error("Cannot open ${handle.url} for writing")
    }
}

package de.simiil.liftlog.ui.settings

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL

/**
 * Opaque iOS handle to a document the user picked (import) or is about to create (export).
 *
 * [onWritten] resolves the export design fork with Android. Android's `CreateDocument` returns a URI
 * and the ViewModel then writes to it; iOS's `UIDocumentPickerViewController(forExportingURLs:)`
 * instead needs the file to exist *before* it is presented. So the create-document launcher points
 * [url] at a temp file and stashes a callback here that presents the export picker; [IosDocumentIo]
 * `.writeText` invokes it once the temp file is on disk. Import handles (and any non-export write)
 * leave it null. The hook lives on the handle — not on the common launcher signature — so the
 * expect/actual seam and the Android actuals stay untouched (nothing in common code constructs or
 * inspects a `DocumentHandle`).
 */
actual class DocumentHandle(
    val url: NSURL,
    val onWritten: (() -> Unit)? = null,
)

/**
 * Reads/writes text to a document [NSURL] via Foundation's `NSString` file APIs, wrapping each I/O in
 * security-scoped resource access: URLs returned by `UIDocumentPickerViewController` point outside the
 * app sandbox and require `startAccessingSecurityScopedResource`/`stopAccessingSecurityScopedResource`
 * bracketing to be readable/writable. Temp-directory URLs (the export flow) return `false` from
 * `start…` and are accessible regardless, so the same bracket is harmless there.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDocumentIo : DocumentIo {
    override suspend fun readText(handle: DocumentHandle): String =
        withContext(Dispatchers.Default) {
            val scoped = handle.url.startAccessingSecurityScopedResource()
            try {
                NSString.stringWithContentsOfURL(handle.url, NSUTF8StringEncoding, null)
                    ?: error("Cannot open ${handle.url} for reading")
            } finally {
                if (scoped) handle.url.stopAccessingSecurityScopedResource()
            }
        }

    override suspend fun writeText(
        handle: DocumentHandle,
        text: String,
    ) {
        withContext(Dispatchers.Default) {
            val scoped = handle.url.startAccessingSecurityScopedResource()
            try {
                // Kotlin String and Foundation NSString are the same object at runtime (toll-free
                // bridging), so the static "can never succeed" cast warning below is harmless.
                @Suppress("CAST_NEVER_SUCCEEDS")
                val ok = (text as NSString).writeToURL(handle.url, true, NSUTF8StringEncoding, null)
                if (!ok) error("Cannot open ${handle.url} for writing")
            } finally {
                if (scoped) handle.url.stopAccessingSecurityScopedResource()
            }
        }
        // The temp export file now exists on disk — hand it to the picker for placement.
        // No-op for import handles and any other write (onWritten is null there).
        handle.onWritten?.invoke()
    }
}

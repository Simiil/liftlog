package de.simiil.liftlog.ui.settings

/**
 * Opaque, platform-specific handle to a document the user picked (or is about to create) via the
 * platform's native document picker. Android: wraps an `android.net.Uri` (SAF). iOS: wraps an
 * `NSURL` returned by `UIDocumentPickerViewController` (M8).
 *
 * A thin wrapper rather than `actual typealias DocumentHandle = Uri`: `android.net.Uri`
 * redeclares `toString()` as `abstract`, which the expect/actual modality checker rejects unless
 * the expect class (and every actual, including iOS's concrete one) matches that abstractness —
 * not worth it for an opaque handle nothing in common code introspects.
 */
expect class DocumentHandle

/** Reads/writes text to a document referenced by a [DocumentHandle]. Isolated so the
 *  ViewModel stays testable and the repository/domain never sees a platform document type. */
interface DocumentIo {
    suspend fun readText(handle: DocumentHandle): String

    suspend fun writeText(
        handle: DocumentHandle,
        text: String,
    )
}

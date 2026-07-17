package de.simiil.liftlog.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Real iOS document pickers backed by `UIDocumentPickerViewController` (M8, #47), the SAF-equivalent
 * for the backup export/import flow driven from `SettingsScreen`.
 *
 * Design fork vs. Android: Android's `CreateDocument` returns a URI and the ViewModel then writes to
 * it, but iOS's `UIDocumentPickerViewController(forExportingURLs:)` needs the file to exist first. So
 * the create-document launcher writes to a temp file via the normal ViewModel -> `DocumentIo` path and
 * only presents the export picker once that write completes, signalled by [DocumentHandle.onWritten]
 * (invoked by `IosDocumentIo.writeText`). This keeps the common launcher signature and the Android
 * actuals untouched. Import is the direct direction: present the open picker and hand the
 * delegate-selected URL back through `onResult`.
 *
 * `UIDocumentPickerViewController.delegate` is a weak reference, so the delegate object is retained in
 * a [remember]-held [DelegateHolder] for the lifetime of the composition.
 */
@Composable
actual fun rememberCreateDocumentLauncher(
    mimeType: String,
    onResult: (DocumentHandle?) -> Unit,
): (suggestedName: String) -> Unit {
    val holder = remember { DelegateHolder() }
    return { suggestedName ->
        val tmpUrl = NSURL.fileURLWithPath(NSTemporaryDirectory() + suggestedName)
        // The ViewModel writes the export JSON to tmpUrl; once written, onWritten presents the
        // export picker so the user can place the file via Files.
        onResult(
            DocumentHandle(tmpUrl) {
                presentPicker(holder, onPicked = {}) {
                    UIDocumentPickerViewController(forExportingURLs = listOf(tmpUrl), asCopy = true)
                }
            },
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberOpenDocumentLauncher(
    mimeTypes: List<String>,
    onResult: (DocumentHandle?) -> Unit,
): () -> Unit {
    val holder = remember { DelegateHolder() }
    return {
        val types = mimeTypes.mapNotNull { UTType.typeWithMIMEType(it) }.ifEmpty { listOf(UTTypeJSON) }
        presentPicker(holder, onPicked = { url -> onResult(url?.let { DocumentHandle(it) }) }) {
            UIDocumentPickerViewController(forOpeningContentTypes = types)
        }
    }
}

/** Retains the (weakly-referenced) picker delegate for the composition's lifetime. */
private class DelegateHolder {
    var delegate: UIDocumentPickerDelegateProtocol? = null
}

@OptIn(ExperimentalForeignApi::class)
private fun presentPicker(
    holder: DelegateHolder,
    onPicked: (NSURL?) -> Unit,
    buildPicker: () -> UIDocumentPickerViewController,
) {
    dispatch_async(dispatch_get_main_queue()) {
        val delegate = PickerDelegate(onPicked)
        holder.delegate = delegate
        val picker = buildPicker()
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

@Suppress("DEPRECATION") // keyWindow: the brief's specified presentation host; adequate for a single-window app
private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}

private class PickerDelegate(
    private val onPicked: (NSURL?) -> Unit,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        onPicked(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(null)
    }
}

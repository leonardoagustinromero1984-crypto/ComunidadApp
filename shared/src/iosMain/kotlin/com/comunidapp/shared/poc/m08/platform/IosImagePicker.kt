package com.comunidapp.shared.poc.m08.platform

import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.poc.m08.model.ImagePickResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Real iOS Photo Picker → [FileRef].
 *
 * Apple types stay in iosMain only.
 * CI marks IOS_PICKER_RUNTIME = NOT_AUTOMATED (system sheet needs human interaction).
 */
@OptIn(ExperimentalForeignApi::class)
class IosImagePicker : ImagePicker {
    override suspend fun pickImage(): ImagePickResult = withContext(Dispatchers.Main) {
        val presenter = topViewController()
            ?: return@withContext ImagePickResult.Failure("NO_ROOT_VIEW_CONTROLLER")

        suspendCancellableCoroutine { continuation ->
            lateinit var delegate: PHPickerViewControllerDelegateProtocol
            delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                override fun picker(
                    picker: PHPickerViewController,
                    didFinishPicking: List<*>
                ) {
                    picker.dismissViewControllerAnimated(true, completion = null)
                    PickerDelegateHolder.current = null
                    val picked = didFinishPicking.firstOrNull() as? PHPickerResult
                    if (picked == null) {
                        if (continuation.isActive) {
                            continuation.resume(ImagePickResult.Cancelled)
                        }
                        return
                    }
                    loadFileRef(picked) { mapped ->
                        if (continuation.isActive) {
                            continuation.resume(mapped)
                        }
                    }
                }
            }

            val configuration = PHPickerConfiguration()
            configuration.selectionLimit = 1
            configuration.filter = PHPickerFilter.imagesFilter

            val picker = PHPickerViewController(configuration)
            picker.delegate = delegate
            PickerDelegateHolder.current = delegate
            continuation.invokeOnCancellation {
                PickerDelegateHolder.current = null
            }
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/** Binary/name compatibility with the earlier POC scaffold. */
typealias IosImagePickerScaffold = IosImagePicker

private object PickerDelegateHolder {
    var current: PHPickerViewControllerDelegateProtocol? = null
}

@OptIn(ExperimentalForeignApi::class)
private fun topViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    val root = app.keyWindow?.rootViewController ?: return null
    var current: UIViewController = root
    while (true) {
        val presented = current.presentedViewController ?: return current
        current = presented
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun loadFileRef(
    result: PHPickerResult,
    onDone: (ImagePickResult) -> Unit
) {
    val provider = result.itemProvider
    if (!provider.hasItemConformingToTypeIdentifier("public.image")) {
        onDone(ImagePickResult.Failure("NOT_AN_IMAGE"))
        return
    }
    provider.loadFileRepresentationForTypeIdentifier("public.image") { url: NSURL?, error: NSError? ->
        if (error != null) {
            onDone(ImagePickResult.Failure(error.localizedDescription))
            return@loadFileRepresentationForTypeIdentifier
        }
        if (url == null) {
            onDone(ImagePickResult.Cancelled)
            return@loadFileRepresentationForTypeIdentifier
        }
        val name = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: "ios-image.jpg"
        val size = fileSizeBytes(url)
        if (size <= 0L) {
            onDone(ImagePickResult.Failure("SIZE_INVALID"))
            return@loadFileRepresentationForTypeIdentifier
        }
        onDone(
            ImagePickResult.Success(
                FileRef(
                    name = name,
                    mimeType = mimeFromPath(name),
                    sizeBytes = size,
                    platformIdentifier = url.absoluteString ?: name
                )
            )
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun fileSizeBytes(url: NSURL): Long {
    val path = url.path ?: return -1L
    val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return -1L
    val number = attrs[NSFileSize] as? NSNumber ?: return -1L
    return number.longValue
}

private fun mimeFromPath(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".heic") -> "image/heic"
        lower.endsWith(".heif") -> "image/heif"
        else -> "image/jpeg"
    }
}

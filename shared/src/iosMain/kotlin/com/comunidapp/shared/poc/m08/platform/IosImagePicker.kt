package com.comunidapp.shared.poc.m08.platform

import com.comunidapp.shared.poc.m08.model.ImagePickResult

/**
 * iOS picker scaffold for Mac/Xcode validation.
 *
 * IOS_PICKER_IMPLEMENTATION = STRUCTURE_READY_PENDING_XCODE
 *
 * On a Mac, replace [pickImage] with PHPickerViewController / UIDocumentPicker
 * and map to FileRef (name, UTI/mime, size, opaque platformIdentifier).
 * Do not call this from Windows and treat success as real iOS runtime.
 */
class IosImagePickerScaffold : ImagePicker {
    override suspend fun pickImage(): ImagePickResult =
        ImagePickResult.Failure(
            "IOS_PICKER_STRUCTURE_READY_PENDING_XCODE"
        )
}

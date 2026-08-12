# LeoVer iOS host (KMP POC)

This folder is a **placeholder** for the iOS application entry that will embed the
`LeoVerShared` framework produced by `:shared`.

## Status on Windows CI / Cursor

`IOS_RUNTIME_VALIDATION = PENDING_MAC_XCODE`

Xcode is required to:
1. Create an Xcode project / SwiftUI wrapper, or use Compose Multiplatform iOS template.
2. Link the `shared` framework (`iosArm64` / `iosSimulatorArm64`).
3. Host either `M22PocApp` (POC 1) or `M08PocApp` (POC 2) from a Compose UIViewController.
4. Wire `IosImagePickerScaffold` → real PHPicker / document picker (`STRUCTURE_READY_PENDING_XCODE`).

## POC 2 picker

```text
IOS_PICKER_IMPLEMENTATION = STRUCTURE_READY_PENDING_XCODE
```

Common code depends only on `ImagePicker` + `FileRef`. Android uses Photo Picker;
iOS must map platform URLs to the same `FileRef` fields without leaking into common.

## Intended entry (conceptual)

```swift
// IOSApp.swift — to be created on macOS with Xcode
import LeoVerShared
// Host MainViewController { M08PocApp(repository, IosImagePickerScaffold(), ...) }
```

Do not treat this README as a compiled iOS binary.

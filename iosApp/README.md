# LeoVer iOS host (KMP POC)

Minimal SwiftUI shell that embeds shared Compose via `PocIosViewController()`.

## Layout

```text
iosApp/
  iosApp.xcodeproj
  iosApp/
    iOSApp.swift
    ContentView.swift   → LeoVerShared.PocIosViewController()
    Info.plist
```

## What Swift owns

Only the UIKit/SwiftUI host. POC screens stay in `:shared` Compose:

- Launcher (M22 / M08)
- M22 Catalog POC
- M08 Media + Navigation + FileRef picker

## Build (macOS / Xcode / CI)

Xcode run-script phase (before Compile Sources):

```bash
cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode --no-configuration-cache
```

Requires `ENABLE_USER_SCRIPT_SANDBOXING = NO`.

Bundle id: `com.comunidapp.leover.kmppoc`  
Scheme/target: `LeoVerKmpPoc`

## Status

- Windows: cannot build/run iOS
- Cloud gate: `.github/workflows/kmp-ios-validation.yml` (`workflow_dispatch`)
- `IOS_PICKER_RUNTIME` in CI = `NOT_AUTOMATED` (system PHPicker sheet)

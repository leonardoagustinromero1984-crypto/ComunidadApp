import SwiftUI
import UIKit
import LeoVerShared

/// Thin SwiftUI shell — POC UI lives in shared Compose (`PocIosViewController`).
struct ContentView: View {
    var body: some View {
        ComposePocHost()
            .ignoresSafeArea(.all)
    }
}

struct ComposePocHost: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        PocIosEntryKt.PocIosViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

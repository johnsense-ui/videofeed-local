import SwiftUI
import Shared

@main
struct iosAppApp: App {
    init() {
        IOSKoinHelperKt.doInitKoin(baseUrl: "https://2d85-201-37-119-33.ngrok-free.app")
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

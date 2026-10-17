import SwiftUI
import Shared

@main
struct iosAppApp: App {
    init() {
        // 本地视频库。改地址就改这里：
        //   局域网   http://172.20.10.3:6688
        //   Tailscale http://100.78.220.84:6688
        IOSKoinHelperKt.doInitKoin(baseUrl: "http://172.20.10.3:6688")
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

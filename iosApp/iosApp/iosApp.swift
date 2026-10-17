import SwiftUI
import Shared

@main
struct iosAppApp: App {
    init() {
        // 地址优先级：UserDefaults（App 里齿轮改过的） > 默认值
        let saved = UserDefaults.standard.string(forKey: "dzc.serverBase") ?? ""
        let base = saved.isEmpty ? "http://172.29.8.196:6688" : saved
        IOSKoinHelperKt.setServerBase(url: base)
        IOSKoinHelperKt.doInitKoin(baseUrl: base)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

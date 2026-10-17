import SwiftUI

struct ContentView: View {
    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            FeedView(
                onNavigateToLogin: { path.append("login") },
                onNavigateToUpload: { path.append("upload") }
            )
            .navigationDestination(for: String.self) { route in
                switch route {
                case "login":
                    LoginView(onSuccess: { path.removeLast() })
                case "register":
                    RegisterView(onSuccess: { path.removeLast() })
                case "upload":
                    UploadView(onSuccess: { path.removeLast() })
                default:
                    EmptyView()
                }
            }
        }
    }
}

import SwiftUI
import Shared

@Observable
final class LoginViewHost {
    var state: LoginState
    let vm: LoginViewModel

    init() {
        let vm = IOSKoinHelperKt.loginViewModel()
        self.vm = vm
        self.state = vm.currentState()
    }
}

struct LoginView: View {
    @State private var host = LoginViewHost()
    let onSuccess: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Text("Sign In").font(.largeTitle).bold()

            TextField("Email", text: Binding(
                get: { host.state.email },
                set: { host.vm.onAction(action: LoginActionOnEmailChange(email: $0)) }
            ))
            .keyboardType(.emailAddress)
            .autocapitalization(.none)
            .textFieldStyle(.roundedBorder)

            SecureField("Password", text: Binding(
                get: { host.state.password },
                set: { host.vm.onAction(action: LoginActionOnPasswordChange(password: $0)) }
            ))
            .textFieldStyle(.roundedBorder)

            if let error = host.state.error {
                Text(error).foregroundColor(.red).font(.caption)
            }

            Button(action: { host.vm.onAction(action: LoginActionOnSubmit()) }) {
                if host.state.isLoading {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    Text("Sign In").frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(host.state.isLoading)
        }
        .padding()
        .onAppear {
            host.vm.observeState { s in host.state = s }
            host.vm.observeEvents { event in
                if event is LoginEventNavigateToFeed { onSuccess() }
            }
        }
        .onDisappear { host.vm.dispose() }
    }
}

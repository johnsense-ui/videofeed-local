import SwiftUI
import Shared

@Observable
final class RegisterViewHost {
    var state: RegisterState
    let vm: RegisterViewModel

    init() {
        let vm = IOSKoinHelperKt.registerViewModel()
        self.vm = vm
        self.state = vm.currentState()
    }
}

struct RegisterView: View {
    @State private var host = RegisterViewHost()
    let onSuccess: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Text("Create Account").font(.largeTitle).bold()

            TextField("Email", text: Binding(
                get: { host.state.email },
                set: { host.vm.onAction(action: RegisterActionOnEmailChange(email: $0)) }
            ))
            .keyboardType(.emailAddress)
            .autocapitalization(.none)
            .textFieldStyle(.roundedBorder)

            SecureField("Password", text: Binding(
                get: { host.state.password },
                set: { host.vm.onAction(action: RegisterActionOnPasswordChange(password: $0)) }
            ))
            .textFieldStyle(.roundedBorder)

            if let error = host.state.error {
                Text(error).foregroundColor(.red).font(.caption)
            }

            Button(action: { host.vm.onAction(action: RegisterActionOnSubmit()) }) {
                if host.state.isLoading {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    Text("Create Account").frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(host.state.isLoading)
        }
        .padding()
        .onAppear {
            host.vm.observeState { s in host.state = s }
            host.vm.observeEvents { event in
                if event is RegisterEventNavigateToFeed { onSuccess() }
            }
        }
        .onDisappear { host.vm.dispose() }
    }
}

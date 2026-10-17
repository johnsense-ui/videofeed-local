import SwiftUI
import PhotosUI
import Shared

@Observable
final class UploadViewHost {
    var state: UploadState
    let vm: UploadViewModel

    init() {
        let vm = IOSKoinHelperKt.uploadViewModel()
        self.vm = vm
        self.state = vm.currentState()
    }
}

struct UploadView: View {
    @State private var host = UploadViewHost()
    @State private var pickerItem: PhotosPickerItem? = nil
    let onSuccess: () -> Void

    private var isLoading: Bool {
        host.state.status is UploadStatusPresigning
            || host.state.status is UploadStatusUploading
            || host.state.status is UploadStatusFinalizing
    }

    private var canSubmit: Bool {
        !isLoading && host.state.selectedFilename != nil && !host.state.title.isEmpty
    }

    var body: some View {
        VStack(spacing: 24) {
            Text("Upload Video").font(.title).bold()

            PhotosPicker(selection: $pickerItem, matching: .videos, photoLibrary: .shared()) {
                Label(host.state.selectedFilename ?? "Select Video", systemImage: "video.badge.plus")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(isLoading)
            .onChange(of: pickerItem) { _, item in
                guard let item else { return }
                Task { await loadVideo(from: item) }
            }

            TextField("Title", text: Binding(
                get: { host.state.title },
                set: { host.vm.onAction(action: UploadActionOnTitleChange(title: $0)) }
            ))
            .textFieldStyle(.roundedBorder)
            .disabled(isLoading)

            Group {
                if let uploading = host.state.status as? UploadStatusUploading {
                    VStack(spacing: 8) {
                        ProgressView(value: Double(uploading.progress))
                        Text("Uploading… \(Int(uploading.progress * 100))%").font(.caption)
                    }
                } else if host.state.status is UploadStatusPresigning {
                    HStack { ProgressView(); Text("Preparing upload…") }
                } else if host.state.status is UploadStatusFinalizing {
                    HStack { ProgressView(); Text("Saving…") }
                } else if host.state.status is UploadStatusDone {
                    Text("Upload complete!").foregroundColor(.green)
                } else if let error = host.state.status as? UploadStatusError {
                    Text(error.message).foregroundColor(.red).font(.caption)
                }
            }

            Button(action: { host.vm.onAction(action: UploadActionOnSubmit()) }) {
                Text("Upload").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!canSubmit)

            Spacer()
        }
        .padding()
        .onAppear {
            host.vm.observeState { s in host.state = s }
            host.vm.observeEvents { event in
                if event is UploadEventNavigateToFeed { onSuccess() }
            }
        }
        .onDisappear { host.vm.dispose() }
    }

    private func loadVideo(from item: PhotosPickerItem) async {
        guard let url = try? await item.loadTransferable(type: URL.self),
              let data = try? Data(contentsOf: url) else { return }
        let bytes = [UInt8](data)
        let kotlinBytes = KotlinByteArray(size: Int32(bytes.count))
        for (i, b) in bytes.enumerated() {
            kotlinBytes.set(index: Int32(i), value: Int8(bitPattern: b))
        }
        host.vm.onAction(action: UploadActionOnFileSelected(
            bytes: kotlinBytes,
            filename: url.lastPathComponent,
            mimeType: "video/mp4"
        ))
    }
}

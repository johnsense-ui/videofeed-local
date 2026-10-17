import SwiftUI
import AVFoundation
import Shared

@Observable
final class FeedViewHost {
    var state: FeedState
    let vm: FeedViewModel

    init() {
        let vm = IOSKoinHelperKt.feedViewModel()
        self.vm = vm
        self.state = vm.currentState()
    }
}

struct FeedView: View {
    @State private var host = FeedViewHost()
    @State private var currentIndex: Int? = 0
    @State private var players: [String: AVPlayer] = [:]
    private let preloader = VideoPreloader()

    let onNavigateToLogin: () -> Void
    let onNavigateToUpload: () -> Void

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .topTrailing) {
                if host.state.videos.isEmpty {
                    ProgressView()
                        .tint(.white)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.black)
                } else {
                    ScrollView(.vertical) {
                        LazyVStack(spacing: 0) {
                            ForEach(Array(host.state.videos.enumerated()), id: \.element.id) { index, video in
                                VideoItemView(
                                    video: video,
                                    player: player(for: video),
                                    isVisible: index == (currentIndex ?? 0),
                                    size: geo.size
                                )
                                .containerRelativeFrame([.horizontal, .vertical])
                                .id(index)
                            }
                        }
                        .scrollTargetLayout()
                    }
                    .scrollTargetBehavior(.paging)
                    .scrollPosition(id: $currentIndex)
                    .ignoresSafeArea()
                    .onChange(of: currentIndex) { _, newIndex in
                        guard let newIndex else { return }
                        host.vm.onAction(action: FeedActionOnVideoVisible(index: Int32(newIndex)))
                        prefetchNext(from: newIndex)
                    }
                }

                Button(action: onNavigateToUpload) {
                    Image(systemName: "plus.circle.fill").font(.title).padding()
                }
                .foregroundColor(.white)
            }
        }
        .background(Color.black)
        .onAppear {
            host.vm.observeState { s in host.state = s }
            host.vm.observeEvents { event in
                if event is FeedEventNavigateToLogin { onNavigateToLogin() }
                else if event is FeedEventNavigateToUpload { onNavigateToUpload() }
            }
        }
        .onDisappear { host.vm.dispose() }
    }

    private func player(for video: VideoUi) -> AVPlayer {
        if let existing = players[video.id] { return existing }
        let url = URL(string: video.cdnUrl)!
        let player: AVPlayer
        if let cached = preloader.asset(for: url) {
            player = AVPlayer(playerItem: AVPlayerItem(asset: cached))
        } else {
            player = AVPlayer(url: url)
        }
        players[video.id] = player
        return player
    }

    private func prefetchNext(from index: Int) {
        let urls = (1...2).compactMap { offset -> URL? in
            let next = index + offset
            guard next < host.state.videos.count else { return nil }
            return URL(string: host.state.videos[next].cdnUrl)
        }
        Task { await preloader.preload(urls: urls) }
    }
}

struct VideoItemView: View {
    let video: VideoUi
    let player: AVPlayer
    let isVisible: Bool
    let size: CGSize
    @State private var isPlaying = true
    @State private var showIcon = false

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            VideoPlayerView(player: player)
                .frame(width: size.width, height: size.height)
                .onAppear { if isVisible { player.play(); isPlaying = true } }
                .onDisappear { player.pause() }
                .onChange(of: isVisible) { _, visible in
                    if visible { player.play(); isPlaying = true } else { player.pause() }
                }
                .onTapGesture { togglePlayback() }

            if showIcon {
                Image(systemName: isPlaying ? "play.fill" : "pause.fill")
                    .font(.system(size: 60))
                    .foregroundColor(.white.opacity(0.8))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(video.title).font(.headline).foregroundColor(.white)
                Text(video.uploaderName).font(.subheadline).foregroundColor(.white.opacity(0.8))
            }
            .padding()
            .padding(.bottom, 40)
        }
    }

    private func togglePlayback() {
        isPlaying.toggle()
        if isPlaying { player.play() } else { player.pause() }
        showIcon = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { showIcon = false }
    }
}

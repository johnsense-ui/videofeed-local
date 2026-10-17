import SwiftUI
import AVFoundation
import Shared

/// 本地视频库地址（和 iosApp.swift 里 initKoin 的 baseUrl 保持一致）
enum LocalAPI {
    static let base = "http://172.20.10.3:6688"

    static func author(key: String) -> URL? {
        let k = key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? key
        return URL(string: "\(base)/api/author?key=\(k)")
    }

    static func authorVideos(key: String, limit: Int = 30) -> URL? {
        let k = key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? key
        return URL(string: "\(base)/api/videos?author=\(k)&limit=\(limit)&sort=newest")
    }
}

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
    @State private var authorSheet: AuthorRef?
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
                                    size: geo.size,
                                    onAuthorTap: {
                                        authorSheet = AuthorRef(key: video.authorId, name: video.uploaderName)
                                    }
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
        .sheet(item: $authorSheet) { ref in
            AuthorSheetView(authorKey: ref.key, fallbackName: ref.name)
        }
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
        guard let url = URL(string: video.cdnUrl) else { return AVPlayer() }
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

// MARK: - 单条视频

struct VideoItemView: View {
    let video: VideoUi
    let player: AVPlayer
    let isVisible: Bool
    let size: CGSize
    var onAuthorTap: () -> Void = {}

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

            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 6) {
                    Button(action: onAuthorTap) {
                        HStack(spacing: 6) {
                            Image(systemName: "person.crop.circle.fill")
                                .font(.system(size: 22))
                            Text("@" + video.uploaderName)
                                .font(.subheadline).bold()
                        }
                        .foregroundColor(.white)
                    }
                    .buttonStyle(.plain)

                    Text(video.title)
                        .font(.subheadline)
                        .foregroundColor(.white)
                        .lineLimit(3)

                    if video.authorId.isEmpty == false {
                        Text("查看作者主页")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.6))
                            .onTapGesture(perform: onAuthorTap)
                    }
                }
                .padding(.leading, 16)
                .padding(.bottom, 48)

                Spacer(minLength: 0)

                VStack(spacing: 18) {
                    actionItem(
                        system: "heart.fill",
                        text: NumberText.short(Int(video.likeCount)),
                        tint: Color(red: 0.996, green: 0.173, blue: 0.333)
                    )
                    actionItem(system: "bubble.right.fill", text: "", tint: .white)
                    actionItem(system: "arrowshape.turn.up.right.fill", text: "", tint: .white)
                }
                .padding(.trailing, 12)
                .padding(.bottom, 60)
            }
        }
    }

    private func actionItem(system: String, text: String, tint: Color) -> some View {
        VStack(spacing: 4) {
            Image(systemName: system)
                .font(.system(size: 27, weight: .semibold))
                .foregroundColor(tint)
            if text.isEmpty == false {
                Text(text)
                    .font(.caption2).bold()
                    .foregroundColor(.white)
            }
        }
    }

    private func togglePlayback() {
        isPlaying.toggle()
        if isPlaying { player.play() } else { player.pause() }
        showIcon = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { showIcon = false }
    }
}

// MARK: - 作者主页

struct AuthorRef: Identifiable {
    let key: String
    let name: String
    var id: String { key }
}

struct AuthorVideoRow: Identifiable {
    let id: Int
    let title: String
    let digg: Int
}

@Observable
final class AuthorLoader {
    var name: String = ""
    var works: Int = 0
    var likes: Int = 0
    var videos: [AuthorVideoRow] = []
    var isLoading = false
    var errorText: String?

    func load(key: String, fallbackName: String) {
        name = fallbackName
        isLoading = true
        errorText = nil

        var profileDone = false
        var videosDone = false
        let done = {
            if profileDone && videosDone {
                DispatchQueue.main.async { self.isLoading = false }
            }
        }

        if let url = LocalAPI.author(key: key) {
            URLSession.shared.dataTask(with: url) { data, _, _ in
                defer { profileDone = true; done() }
                guard let data = data else { return }
                struct AuthorResp: Decodable {
                    let name: String?
                    let works: Int?
                    let likes: Int?
                }
                if let resp = try? JSONDecoder().decode(AuthorResp.self, from: data) {
                    DispatchQueue.main.async {
                        if let n = resp.name, n.isEmpty == false { self.name = n }
                        self.works = resp.works ?? 0
                        self.likes = resp.likes ?? 0
                    }
                }
            }.resume()
        } else {
            profileDone = true
            done()
        }

        if let url = LocalAPI.authorVideos(key: key) {
            URLSession.shared.dataTask(with: url) { data, _, _ in
                defer { videosDone = true; done() }
                guard let data = data else { return }
                struct Resp: Decodable { let items: [Item] }
                struct Item: Decodable {
                    let id: Int
                    let title: String?
                    let caption: String?
                    let digg: Int?
                }
                if let resp = try? JSONDecoder().decode(Resp.self, from: data) {
                    let rows = resp.items.map {
                        AuthorVideoRow(id: $0.id,
                                       title: ($0.caption?.isEmpty == false ? $0.caption! : ($0.title ?? "")),
                                       digg: $0.digg ?? 0)
                    }
                    DispatchQueue.main.async { self.videos = rows }
                }
            }.resume()
        } else {
            videosDone = true
            done()
        }
    }
}

struct AuthorSheetView: View {
    let authorKey: String
    let fallbackName: String

    @State private var loader = AuthorLoader()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()
                VStack(spacing: 0) {
                    VStack(spacing: 8) {
                        Image(systemName: "person.crop.circle.fill")
                            .font(.system(size: 64))
                            .foregroundColor(.white.opacity(0.85))
                        Text("@" + (loader.name.isEmpty ? fallbackName : loader.name))
                            .font(.title3).bold()
                            .foregroundColor(.white)
                        HStack(spacing: 24) {
                            stat("作品", "\(loader.works)")
                            stat("获赞", NumberText.short(loader.likes))
                        }
                        .padding(.top, 4)
                    }
                    .padding(.vertical, 24)

                    if loader.isLoading {
                        ProgressView().tint(.white).padding()
                    }

                    List(loader.videos) { v in
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(v.title.isEmpty ? "(无文案)" : v.title)
                                    .font(.subheadline)
                                    .lineLimit(2)
                                Text("❤ " + NumberText.short(v.digg))
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("作者主页")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("关闭") { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { loader.load(key: authorKey, fallbackName: fallbackName) }
    }

    private func stat(_ title: String, _ value: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.headline).foregroundColor(.white)
            Text(title).font(.caption).foregroundColor(.white.opacity(0.6))
        }
    }
}

// MARK: - 数字缩写

enum NumberText {
    static func short(_ n: Int) -> String {
        switch n {
        case 0: return "0"
        case 1..<10_000: return "\(n)"
        case 10_000..<100_000_000:
            return String(format: "%.1fw", Double(n) / 10_000)
        default:
            return String(format: "%.1f亿", Double(n) / 100_000_000)
        }
    }
}

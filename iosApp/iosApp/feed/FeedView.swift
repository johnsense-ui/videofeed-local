import SwiftUI
import AVFoundation
import Shared

/// 本地视频库地址：存 UserDefaults，运行时可改，不用重新编译 ipa
enum LocalAPI {
    static let defaultsKey = "dzc.serverBase"
    static let fallback = "http://172.29.8.196:6688"

    static var base: String {
        get {
            let v = UserDefaults.standard.string(forKey: defaultsKey) ?? ""
            return v.isEmpty ? fallback : v
        }
        set {
            var t = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
            while t.hasSuffix("/") { t.removeLast() }
            UserDefaults.standard.set(t, forKey: defaultsKey)
            IOSKoinHelperKt.setServerBase(url: t)
        }
    }

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

    func refresh() {
        vm.onAction(action: FeedActionOnRefresh())
    }
}

struct FeedView: View {
    @State private var host = FeedViewHost()
    @State private var currentIndex: Int? = 0
    @State private var players: [String: AVPlayer] = [:]
    @State private var authorSheet: AuthorRef?
    @State private var showServerSheet = false
    @State private var savedBase = LocalAPI.base
    private let preloader = VideoPreloader()

    let onNavigateToLogin: () -> Void
    let onNavigateToUpload: () -> Void

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                if host.state.videos.isEmpty {
                    emptyState
                } else {
                    feed(geo: geo)
                }

                HStack {
                    Button { showServerSheet = true } label: {
                        Image(systemName: "gearshape.fill")
                            .font(.title3)
                            .foregroundColor(.white)
                            .padding(10)
                            .background(Color.black.opacity(0.35))
                            .clipShape(Circle())
                    }
                    Spacer()
                    Button(action: onNavigateToUpload) {
                        Image(systemName: "plus.circle.fill")
                            .font(.title)
                            .foregroundColor(.white)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
            }
        }
        .background(Color.black)
        .sheet(item: $authorSheet) { ref in
            AuthorSheetView(authorKey: ref.key, fallbackName: ref.name)
        }
        .sheet(isPresented: $showServerSheet) {
            ServerSheet(current: savedBase) { newBase in
                LocalAPI.base = newBase
                savedBase = newBase
                host.refresh()
            }
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

    @ViewBuilder
    private var emptyState: some View {
        VStack(spacing: 18) {
            Spacer()
            if host.state.isLoading {
                ProgressView().tint(.white)
                Text("正在连接 \(savedBase)")
                    .font(.footnote)
                    .foregroundColor(.white.opacity(0.6))
            } else {
                Image(systemName: "wifi.exclamationmark")
                    .font(.system(size: 46))
                    .foregroundColor(.white.opacity(0.7))
                Text(host.state.error ?? "没拿到视频")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                Text(savedBase)
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.5))
                Button("改服务器地址") { showServerSheet = true }
                    .padding(.horizontal, 18).padding(.vertical, 10)
                    .background(Color.white.opacity(0.12))
                    .clipShape(Capsule())
                    .foregroundColor(.white)
                Button("重试") {
                    host.refresh()
                }
                .foregroundColor(Color(red: 0.996, green: 0.173, blue: 0.333))
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }

    private func feed(geo: GeometryProxy) -> some View {
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

// MARK: - 服务器设置

struct ServerSheet: View {
    let current: String
    var onSaved: (String) -> Void

    @State private var text: String = ""
    @State private var status: String = ""
    @State private var probing = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("服务器地址") {
                    TextField("http://192.168.1.10:6688", text: $text)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                    Button(probing ? "测试中…" : "测试连接") { probe() }
                        .disabled(probing)
                    if status.isEmpty == false {
                        Text(status).font(.footnote).foregroundColor(.secondary)
                    }
                }
                Section("常用") {
                    Button("局域网  172.29.8.196:6688") { text = "http://172.29.8.196:6688" }
                    Button("Tailscale  100.78.220.84:6688") { text = "http://100.78.220.84:6688" }
                }
            }
            .navigationTitle("视频库地址")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        var v = text.trimmingCharacters(in: .whitespacesAndNewlines)
                        if v.isEmpty { v = current }
                        if v.hasPrefix("http://") == false && v.hasPrefix("https://") == false {
                            v = "http://" + v
                        }
                        onSaved(v)
                        dismiss()
                    }
                }
            }
        }
        .onAppear { if text.isEmpty { text = current } }
    }

    private func probe() {
        var v = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if v.hasPrefix("http") == false { v = "http://" + v }
        while v.hasSuffix("/") { v.removeLast() }
        guard let url = URL(string: v + "/api/health") else {
            status = "地址格式不对"
            return
        }
        probing = true
        status = "正在连 \(v) …"
        var req = URLRequest(url: url)
        req.timeoutInterval = 8
        URLSession.shared.dataTask(with: req) { data, resp, err in
            DispatchQueue.main.async {
                probing = false
                if err == nil, (resp as? HTTPURLResponse)?.statusCode == 200, data != nil {
                    status = "连上了 ✓"
                } else {
                    status = "连不上：\(err?.localizedDescription ?? "HTTP \((resp as? HTTPURLResponse)?.statusCode ?? -1)")"
                }
            }
        }.resume()
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

    func load(key: String, fallbackName: String) {
        name = fallbackName
        isLoading = true
        var pending = 2
        let finish = { if pending == 0 { DispatchQueue.main.async { self.isLoading = false } } }

        if let url = LocalAPI.author(key: key) {
            URLSession.shared.dataTask(with: url) { data, _, _ in
                defer { pending -= 1; finish() }
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
            pending -= 1; finish()
        }

        if let url = LocalAPI.authorVideos(key: key) {
            URLSession.shared.dataTask(with: url) { data, _, _ in
                defer { pending -= 1; finish() }
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
            pending -= 1; finish()
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
                        VStack(alignment: .leading, spacing: 4) {
                            Text(v.title.isEmpty ? "(无文案)" : v.title)
                                .font(.subheadline)
                                .lineLimit(2)
                            Text("❤ " + NumberText.short(v.digg))
                                .font(.caption)
                                .foregroundColor(.secondary)
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

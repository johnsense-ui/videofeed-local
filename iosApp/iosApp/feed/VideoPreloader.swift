import Foundation
import AVFoundation

actor VideoPreloader {
    private var loadedAssets: [String: AVAsset] = [:]

    func preload(urls: [URL]) async {
        for url in urls {
            let key = url.absoluteString
            guard loadedAssets[key] == nil else { continue }
            let asset = AVAsset(url: url)
            do {
                _ = try await asset.load(.isPlayable)
                _ = try await asset.load(.duration)
                loadedAssets[key] = asset
            } catch { }
        }
    }

    func asset(for url: URL) -> AVAsset? {
        loadedAssets[url.absoluteString]
    }
}

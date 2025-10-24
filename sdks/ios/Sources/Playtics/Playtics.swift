import Foundation
import CryptoKit
import Compression

public struct PlayticsOptions {
    public let apiKey: String
    public let endpoint: URL
    public let projectId: String
    public var deviceId: String?
    public var flushIntervalSec: TimeInterval = 5
    public var maxBatch: Int = 50
    public var maxQueueBytes: Int = 512_000
    public var sessionGapSec: TimeInterval = 30*60
    public var hmacSecret: String? = nil // not recommended in client
    public var debug: Bool = false

    public init(apiKey: String, endpoint: URL, projectId: String) {
        self.apiKey = apiKey; self.endpoint = endpoint; self.projectId = projectId
    }
}

public final class Playtics {
    public static let shared = Playtics()
    private init() {}

    private var opts: PlayticsOptions? = nil
    private var deviceId: String = ""
    private var userId: String? = nil
    private var sessionId: String? = nil
    private var lastActive: TimeInterval = Date().timeIntervalSince1970

    private var timer: Timer? = nil
    private var queue: [Event] = []
    private var queueBytes: Int = 0
    private let queueLock = NSLock()

    private var queuePath: URL {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return caches.appendingPathComponent("playtics_queue_\(opts?.projectId ?? "")_\(deviceId).ndjson")
    }

    public func initSDK(_ options: PlayticsOptions) {
        self.opts = options
        self.deviceId = options.deviceId ?? Self.loadOrCreateDeviceId(projectId: options.projectId)
        self.restoreQueue()
        self.lastActive = Date().timeIntervalSince1970
        self.ensureTimer()
        if options.debug { print("[Playtics] init deviceId=\(deviceId) queued=\(queue.count)") }
        NotificationCenter.default.addObserver(self, selector: #selector(appDidEnterBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(appWillEnterForeground), name: UIApplication.willEnterForegroundNotification, object: nil)
    }

    public func setUserId(_ id: String?) { self.userId = id }

    public func track(_ name: String, props: [String: Any]? = nil) -> String {
        guard let o = opts else { return "" }
        let now = Date().timeIntervalSince1970
        rollSession(now)
        let e = Event(
            event_id: Self.uuidv7(),
            event_name: name,
            project_id: o.projectId,
            user_id: userId,
            device_id: deviceId,
            session_id: sessionId,
            ts_client: Int64((now * 1000.0).rounded()),
            platform: "ios",
            app_version: Bundle.main.infoDictionary?[(kCFBundleVersionKey as String)] as? String,
            country: nil,
            props: props
        )
        let est = e.estimateSize()
        queueLock.lock(); defer { queueLock.unlock() }
        queue.append(e); queueBytes += est; lastActive = now
        if opts?.debug == true { print("[Playtics] queued \(e.event_id) \(name)") }
        if queueBytes >= o.maxQueueBytes { flush() }
        persistQueue()
        return e.event_id
    }

    public func expose(_ exp: String, variant: String) -> String { return track("experiment_exposure", props: ["exp":exp, "variant":variant]) }
    public func revenue(amount: Double, currency: String, props: [String: Any]? = nil) -> String {
        var p = props ?? [:]; p["amount"] = amount; p["currency"] = currency
        return track("revenue", props: p)
    }

    public func flush() { DispatchQueue.global().async { self.flushOnce() } }
    public func shutdown() { timer?.invalidate(); timer = nil }

    private func ensureTimer() {
        guard timer == nil, let o = opts else { return }
        timer = Timer.scheduledTimer(withTimeInterval: o.flushIntervalSec, repeats: true) { [weak self] _ in self?.flush() }
    }

    private func flushOnce() {
        guard let o = opts else { return }
        var slice: [Event] = []
        queueLock.lock()
        if queue.isEmpty { queueLock.unlock(); return }
        let n = min(o.maxBatch, queue.count)
        slice = Array(queue.prefix(n))
        queue.removeFirst(n)
        recalcQueueBytesNoLock()
        persistQueue()
        queueLock.unlock()

        let ndjson = slice.map { $0.toJsonLine() }.joined(separator: "\n")
        guard let url = URL(string: "/v1/batch", relativeTo: o.endpoint) else { return }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue(o.apiKey, forHTTPHeaderField: "x-api-key")
        req.setValue("application/x-ndjson", forHTTPHeaderField: "content-type")

        let raw = Data(ndjson.utf8)
        let body: Data
        if let gz = Self.gzip(raw) { body = gz; req.setValue("gzip", forHTTPHeaderField: "content-encoding") } else { body = raw }
        if let secret = o.hmacSecret {
            let t = String(Int(Date().timeIntervalSince1970))
            let sigData = Data((t + "." + ndjson).utf8)
            let mac = HMAC<SHA256>.authenticationCode(for: sigData, using: SymmetricKey(data: Data(secret.utf8)))
            let sig = mac.map { String(format: "%02x", $0) }.joined()
            req.setValue("t=\(t), s=\(sig)", forHTTPHeaderField: "x-signature")
        }
        req.httpBody = body

        let sem = DispatchSemaphore(value: 0)
        URLSession.shared.dataTask(with: req) { _, resp, _ in
            if self.opts?.debug == true {
                let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
                print("[Playtics] flush code=\(code) count=\(slice.count)")
            }
            sem.signal()
        }.resume()
        _ = sem.wait(timeout: .now() + 15)
    }

    private func rollSession(_ now: TimeInterval) {
        guard let o = opts else { return }
        if sessionId == nil || now - lastActive > o.sessionGapSec { sessionId = Self.uuidv7() }
    }

    @objc private func appDidEnterBackground() { flush() }
    @objc private func appWillEnterForeground() { lastActive = Date().timeIntervalSince1970 }

    private func persistQueue() {
        do {
            let s = queue.map { $0.toJsonLine() }.joined(separator: "\n")
            try s.write(to: queuePath, atomically: true, encoding: .utf8)
        } catch { }
    }
    private func restoreQueue() { do { _ = try String(contentsOf: queuePath) } catch { } }

    private func recalcQueueBytesNoLock() { queueBytes = queue.reduce(0) { $0 + $1.estimateSize() } }

    private static func loadOrCreateDeviceId(projectId: String) -> String {
        let key = "pt_device_id_\(projectId)"
        if let existing = UserDefaults.standard.string(forKey: key), !existing.isEmpty { return existing }
        let idfv = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
        let hashed = "d_" + SHA1hex(idfv).prefix(24)
        UserDefaults.standard.set(hashed, forKey: key)
        return hashed
    }

    private static func SHA1hex(_ s: String) -> String {
        let data = Data(s.utf8)
        var ctx = Insecure.SHA1()
        ctx.update(data: data)
        let digest = ctx.finalize()
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private static func uuidv7() -> String {
        let ms = UInt64(Date().timeIntervalSince1970 * 1000)
        let ts = String(ms, radix: 16).leftPadding(toLength: 12, withPad: "0")
        let rnd = UUID().uuidString.replacingOccurrences(of: "-", with: "")
        let hex = ts + rnd.prefix(20)
        return String(format: "%@-%@-7%@-%@-%@",
                      String(hex.prefix(8)),
                      String(hex.dropFirst(8).prefix(4)),
                      String(hex.dropFirst(13).prefix(3)),
                      String(hex.dropFirst(16).prefix(4)),
                      String(hex.dropFirst(20).prefix(12)))
    }

    private static func gzip(_ data: Data) -> Data? {
        var dst = Data()
        data.withUnsafeBytes { (srcPtr: UnsafeRawBufferPointer) in
            var stream = compression_stream()
            var status = compression_stream_init(&stream, COMPRESSION_STREAM_ENCODE, COMPRESSION_ZLIB)
            guard status != COMPRESSION_STATUS_ERROR else { return }
            defer { compression_stream_destroy(&stream) }
            let dstBufferSize: size_t = 64 * 1024
            let dstBuffer = UnsafeMutablePointer<UInt8>.allocate(capacity: dstBufferSize)
            defer { dstBuffer.deallocate() }

            stream.src_ptr = srcPtr.bindMemory(to: UInt8.self).baseAddress!
            stream.src_size = srcPtr.count
            stream.dst_ptr = dstBuffer
            stream.dst_size = dstBufferSize

            while status == COMPRESSION_STATUS_OK {
                status = compression_stream_process(&stream, Int32(0))
                switch status {
                case COMPRESSION_STATUS_OK, COMPRESSION_STATUS_END:
                    dst.append(dstBuffer, count: dstBufferSize - stream.dst_size)
                    stream.dst_ptr = dstBuffer
                    stream.dst_size = dstBufferSize
                default: return
                }
                if stream.src_size == 0 { status = compression_stream_process(&stream, Int32(COMPRESSION_STREAM_FINALIZE.rawValue)) }
            }
        }
        return dst.isEmpty ? nil : dst
    }

    private struct Event {
        let event_id: String
        let event_name: String
        let project_id: String
        let user_id: String?
        let device_id: String
        let session_id: String?
        let ts_client: Int64
        let platform: String?
        let app_version: String?
        let country: String?
        let props: [String: Any]?

        func toJsonLine() -> String { Self.toJson(self) }
        func estimateSize() -> Int { Self.toJson(self).utf8.count + 1 }

        private static func toJson(_ e: Event) -> String {
            var a: [String] = []
            func f(_ k: String, _ v: String) { a.append("\"\(k)\":\"\(escape(v))\"") }
            func n(_ k: String, _ v: Int64) { a.append("\"\(k)\":\(v)") }
            func o(_ k: String, _ m: [String: Any]) { a.append("\"\(k)\":\(mapToJson(m))") }
            f("event_id", e.event_id); f("event_name", e.event_name); f("project_id", e.project_id)
            if let u = e.user_id { f("user_id", u) }
            f("device_id", e.device_id)
            if let s = e.session_id { f("session_id", s) }
            n("ts_client", e.ts_client)
            if let p = e.platform { f("platform", p) }
            if let v = e.app_version { f("app_version", v) }
            if let c = e.country { f("country", c) }
            if let pr = e.props, !pr.isEmpty { o("props", pr) }
            return "{" + a.joined(separator: ",") + "}"
        }
        private static func escape(_ s: String) -> String { s.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\"", with: "\\\"") }
        private static func mapToJson(_ m: [String: Any], depth: Int = 0) -> String {
            if depth > 2 { return "{}" }
            var parts: [String] = []
            var count = 0
            for (k,v) in m {
                if count >= 50 { break }
                parts.append("\"\(escape(k))\":\(valueToJson(v, depth: depth+1))"); count += 1
            }
            return "{" + parts.joined(separator: ",") + "}"
        }
        private static func valueToJson(_ v: Any, depth: Int) -> String {
            if depth > 2 { return "null" }
            switch v {
            case let s as String: return "\"\(escape(s))\""
            case let b as Bool: return b ? "true" : "false"
            case let n as Int: return String(n)
            case let n as Int64: return String(n)
            case let n as Double: return String(n)
            case let n as Float: return String(n)
            case let d as [String: Any]: return mapToJson(d, depth: depth)
            case let arr as [Any]: return arrayToJson(arr, depth: depth)
            default: return "\"\(escape(String(describing: v)))\""
            }
        }
        private static func arrayToJson(_ arr: [Any], depth: Int) -> String {
            if depth > 2 { return "[]" }
            var out: [String] = []
            let lim = min(50, arr.count)
            for i in 0..<lim { out.append(valueToJson(arr[i], depth: depth+1)) }
            return "[" + out.joined(separator: ",") + "]"
        }
    }
}

fileprivate extension String {
    func leftPadding(toLength: Int, withPad: String) -> String {
        if count < toLength { return String(repeatElement(Character(withPad), count: toLength - count)) + self } else { return self }
    }
}

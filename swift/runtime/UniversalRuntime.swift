// everyui — the UIKit support runtime for the Swift backend (tvOS, and iOS to come).
//
// The render seam's primitives as plain Swift functions over `V`, one per `@native` facade in
// tvos/src/main/scala/everyui/tvos/facades.scala — the twin of roku/runtime/UniversalRuntime.brs. A node is a
// UIKit view: a `UILabel` for text, a `UIImageView` for an image, a plain `UIView` for a container. Layout is
// absolute: every view is placed by its own top-left `(x, y)` and size, kept in a side table so a rotation
// (a `transform`, which makes `frame` meaningless) never disturbs it.

import UIKit
import CoreText
import QuartzCore

// MARK: - app state

enum UI {
    /// The view every component hangs off: the root view controller's view, 1920x1080 points on an Apple TV.
    static var root: UIView!
    /// `--freeze=<seconds>` from the launch arguments, or -1.
    static var freeze: Double = -1.0
    /// `--viewport=WxH`: lay out at that size whatever the screen (a phone shows the top-left of a TV frame).
    static var viewport: CGSize? = nil
    /// The animation callback, a Scala closure, driven by a display link.
    static var tick: V = .vNull
    /// The key hook, a Scala closure (uiOnKey).
    static var key: V = .vNull
    static var link: CADisplayLink?
    static var frames = 0
    static var collections = 0
    static var allocs = 0
    static var allocsAt = 0
    static var collectMs = 0.0
    static var collectMaxMs = 0.0
    static var reported = CACurrentMediaTime()
    static var fonts: [String: UIFontDescriptor] = [:]   // "family|weight" → the bundled face
}

/// Every node's absolute geometry: its top-left, size and rotation in the parent's coordinates.
struct Geo { var x = 0.0, y = 0.0, w = 0.0, h = 0.0, rot = 0.0, scale = 1.0 }
var geoTable: [ObjectIdentifier: Geo] = [:]

func geo(_ v: UIView) -> Geo { geoTable[ObjectIdentifier(v)] ?? Geo() }

/// Apply the geometry: the view's bounds are its size, its centre sits at the middle of its box, and its
/// rotation is a transform about that centre — exactly the DOM's `rotate()` and SceneGraph's pivot.
func place(_ v: UIView, _ g: Geo) {
    geoTable[ObjectIdentifier(v)] = g
    // The root is the view controller's view: the screen owns its geometry (`ui.size` is set to the screen's
    // size, which must not resize the root to 1920x0 between the two writes).
    if v === UI.root { return }
    // Text that was never given a size takes its natural size, as a `div` or a SceneGraph `Label` does.
    var w = g.w, h = g.h
    if let l = v as? UILabel {
        let natural = l.intrinsicContentSize
        if w <= 0 { w = Double(natural.width) }
        if h <= 0 { h = Double(natural.height) }
    }
    v.bounds = CGRect(x: 0, y: 0, width: w, height: h)
    v.center = CGPoint(x: g.x + w / 2.0, y: g.y + h / 2.0)
    v.transform = CGAffineTransform(rotationAngle: g.rot).scaledBy(x: g.scale, y: g.scale)
}

func view(_ v: V) -> UIView? {
    if case .vRef(let o) = v { return o as? UIView }
    return nil
}

func dbl(_ v: V) -> Double { rtD(v) ?? 0.0 }

func uiColor(_ hex: String) -> UIColor {
    var s = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
    if s.count == 3 { s = s.map { "\($0)\($0)" }.joined() }
    // #rrggbb, or #rrggbbaa
    let alpha: CGFloat = s.count == 8 ? CGFloat(UInt32(s.suffix(2), radix: 16) ?? 255) / 255.0 : 1.0
    if s.count == 8 { s = String(s.prefix(6)) }
    let n = UInt32(s, radix: 16) ?? 0
    return UIColor(red: CGFloat((n >> 16) & 0xFF) / 255.0, green: CGFloat((n >> 8) & 0xFF) / 255.0,
                   blue: CGFloat(n & 0xFF) / 255.0, alpha: alpha)
}

/// A site-absolute asset path (`/images/icon.png`) inside the app bundle.
func bundlePath(_ uri: String) -> String {
    let rel = uri.hasPrefix("/") ? String(uri.dropFirst()) : uri
    return (Bundle.main.resourcePath ?? ".") + "/" + rel
}

/// Register the bundled faces (`fonts/*.ttf` in the app) and remember each by family and CSS weight.
func registerFonts() {
    let dir = (Bundle.main.resourcePath ?? ".") + "/fonts"
    for name in (try? FileManager.default.contentsOfDirectory(atPath: dir)) ?? [] where name.hasSuffix(".ttf") {
        let url = URL(fileURLWithPath: dir + "/" + name)
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        guard let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [UIFontDescriptor],
              let d = descriptors.first else { continue }
        // the same choice roku's uiFontUri makes: the file decides the family and weight
        let key: String
        if name.hasPrefix("MaterialIcons") { key = "Material Icons|400" }
        else if name.hasPrefix("RobotoMono") { key = "Roboto Mono|400" }
        else if name.contains("SemiBold") { key = "Open Sans|600" }
        else { key = "Open Sans|400" }
        UI.fonts[key] = d
        print("[ui] font \(name) → \(d.postscriptName)")
    }
}

func font(_ family: String, _ weight: Int, _ size: Double) -> UIFont {
    let key = family == "Material Icons" ? "Material Icons|400" : family == "Roboto Mono" ? "Roboto Mono|400" : (weight >= 600 ? "Open Sans|600" : "Open Sans|400")
    if let d = UI.fonts[key] { return UIFont(descriptor: d, size: size) }
    return UIFont.systemFont(ofSize: size, weight: weight >= 600 ? .semibold : .regular)
}

// MARK: - nodes

func uiRoot() -> V { .vRef(UI.root) }

func uiText() -> V {
    let l = UILabel()
    l.numberOfLines = 1
    l.lineBreakMode = .byClipping
    l.textColor = uiColor("#1c1b1f")
    l.font = UIFont.systemFont(ofSize: 32, weight: .semibold)
    l.layer.anchorPoint = CGPoint(x: 0.5, y: 0.5)
    place(l, Geo())
    return .vRef(l)
}

func uiGetText(_ node: V) -> V { .vStr((view(node) as? UILabel)?.text ?? "") }

func uiSetText(_ node: V, _ text: V) -> V {
    guard let l = view(node) as? UILabel else { return .vNull }
    l.text = rtS(text)
    place(l, geo(l))
    return .vNull
}

func uiGetX(_ node: V) -> V { guard let v = view(node) else { return .vDbl(0) }; return .vDbl(geo(v).x) }
func uiSetX(_ node: V, _ x: V) -> V {
    guard let v = view(node) else { return .vNull }
    var g = geo(v); g.x = dbl(x); place(v, g)
    return .vNull
}
func uiGetY(_ node: V) -> V { guard let v = view(node) else { return .vDbl(0) }; return .vDbl(geo(v).y) }
func uiSetY(_ node: V, _ y: V) -> V {
    guard let v = view(node) else { return .vNull }
    var g = geo(v); g.y = dbl(y); place(v, g)
    return .vNull
}
func uiSetTranslation(_ node: V, _ x: V, _ y: V) -> V {
    guard let v = view(node) else { return .vNull }
    var g = geo(v); g.x = dbl(x); g.y = dbl(y); place(v, g)
    return .vNull
}

/// Radians, clockwise, about the node's own centre (UIKit's y axis points down, so a positive angle is
/// clockwise, as CSS's is).
func uiGetRotation(_ node: V) -> V { guard let v = view(node) else { return .vDbl(0) }; return .vDbl(geo(v).rot) }
func uiSetRotation(_ node: V, _ radians: V) -> V {
    guard let v = view(node) else { return .vNull }
    var g = geo(v); g.rot = dbl(radians); place(v, g)
    return .vNull
}

/// Drawn larger or smaller about its own centre, which is where the transform is applied.
func uiSetScale(_ node: V, _ factor: V) -> V {
    guard let v = view(node) else { return .vNull }
    var g = geo(v); g.scale = dbl(factor); place(v, g)
    return .vNull
}

func uiSetFontSize(_ node: V, _ size: V) -> V {
    if let l = view(node) as? UILabel { l.font = l.font.withSize(dbl(size)); place(l, geo(l)) }
    return .vNull
}

/// `"#rrggbb"` onto a label's text colour.
func uiSetColor(_ node: V, _ hex: V) -> V {
    (view(node) as? UILabel)?.textColor = uiColor(rtS(hex))
    return .vNull
}

/// A bundled face by family and CSS weight; size in points.
func uiSetFont(_ node: V, _ family: V, _ weight: V, _ size: V) -> V {
    guard let l = view(node) as? UILabel else { return .vNull }
    l.font = font(rtS(family), Int(dbl(weight)), dbl(size))
    place(l, geo(l))
    return .vNull
}

/// A label's text width at exactly `size` points of its face: the advance width, as a browser measures it.
func uiTextWidth(_ node: V, _ uri: V, _ size: V) -> V {
    guard let l = view(node) as? UILabel else { return .vDbl(0) }
    let f = l.font.withSize(dbl(size))
    let w = ((l.text ?? "") as NSString).size(withAttributes: [.font: f]).width
    return .vDbl(Double(w))
}

/// The laid-out size: what was set, or for text its natural size.
func uiGetWidth(_ node: V) -> V {
    guard let v = view(node) else { return .vDbl(0) }
    let g = geo(v)
    if g.w > 0 { return .vDbl(g.w) }
    if let l = v as? UILabel { return .vDbl(Double(l.intrinsicContentSize.width)) }
    return .vDbl(0)
}
func uiGetHeight(_ node: V) -> V {
    guard let v = view(node) else { return .vDbl(0) }
    let g = geo(v)
    if g.h > 0 { return .vDbl(g.h) }
    if let l = v as? UILabel { return .vDbl(Double(l.intrinsicContentSize.height)) }
    return .vDbl(0)
}

/// A container: a plain view that does not clip, so children animate past its edges as in the DOM.
func uiContainer() -> V {
    let v = UIView()
    v.clipsToBounds = false
    place(v, Geo())
    return .vRef(v)
}

func uiSetWidth(_ node: V, _ width: V) -> V {
    guard let v = view(node) else { return .vNull }
    var g = geo(v); g.w = dbl(width); place(v, g)
    return .vNull
}
func uiSetHeight(_ node: V, _ height: V) -> V {
    guard let v = view(node) else { return .vNull }
    var g = geo(v); g.h = dbl(height); place(v, g)
    return .vNull
}

/// `"#rrggbb"`, or `""` for transparent.
func uiSetBackgroundColor(_ node: V, _ hex: V) -> V {
    let s = rtS(hex)
    view(node)?.backgroundColor = s.isEmpty ? .clear : uiColor(s)
    return .vNull
}

/// An image node, stretched to its box.
func uiImage(_ uri: V, _ width: V, _ height: V) -> V {
    let iv = UIImageView()
    iv.contentMode = .scaleToFill
    place(iv, Geo(w: dbl(width), h: dbl(height)))
    let v: V = .vRef(iv)
    _ = uiSetImageSource(v, uri)
    return v
}

func uiSetImageSource(_ node: V, _ uri: V) -> V {
    let s = rtS(uri)
    if let iv = view(node) as? UIImageView, !s.isEmpty { iv.image = UIImage(contentsOfFile: bundlePath(s)) }
    return .vNull
}

func uiAppend(_ parent: V, _ child: V) -> V {
    if let p = view(parent), let c = view(child) { p.addSubview(c) }
    return .vNull
}

func uiRemove(_ node: V) -> V {
    view(node)?.removeFromSuperview()
    return .vNull
}

/// Decode `uri` and report its natural size in pixels (bundle images are 1x, so points are pixels).
func uiLoadImage(_ uri: V, _ loaded: V) throws -> V {
    let path = bundlePath(rtS(uri))
    guard let img = UIImage(contentsOfFile: path) else {
        print("[ui] image not found: \(path)")
        _ = try RtInvoke(loaded, rtArr([.vDbl(0), .vDbl(0)]))
        return .vNull
    }
    _ = try RtInvoke(loaded, rtArr([.vDbl(Double(img.size.width * img.scale)), .vDbl(Double(img.size.height * img.scale))]))
    return .vNull
}

// MARK: - platform

func uiScreenWidth() -> V { .vDbl(Double((UI.viewport ?? UIScreen.main.bounds.size).width)) }
func uiScreenHeight() -> V { .vDbl(Double((UI.viewport ?? UIScreen.main.bounds.size).height)) }

func uiLog(_ message: V) -> V {
    print("[ui] " + rtS(message))
    return .vNull
}

/// Milliseconds on a monotonic clock — the animation clock.
func uiTicks() -> V { .vDbl(CACurrentMediaTime() * 1000.0) }

func uiFreezeSeconds() -> V { .vDbl(UI.freeze) }

final class FrameDriver {
    @objc func frame(_ link: CADisplayLink) {
        do {
            _ = try RtInvoke(UI.tick, rtArr([]))
        } catch let e as RtError {
            print("[ui] uncaught in frame: " + rtS(e.v))
        } catch {
            print("[ui] uncaught in frame: \(error)")
        }
        // Between frames, with no emitted code on the stack: collect the cycles the frames left behind — once
        // the allocations since the last pass are worth a walk of the live graph (a quarter of it, at least
        // 2,000), so a steady-state frame that allocates little costs nothing here.
        UI.frames += 1
        UI.allocs += Rt.allocatedTotal - UI.allocsAt
        UI.allocsAt = Rt.allocatedTotal
        if Rt.allocated >= max(2000, Rt.live / 4) {
            let t = CACurrentMediaTime()
            rtCollectCycles([ga, rtUnit, UI.tick])
            let ms = (CACurrentMediaTime() - t) * 1000.0
            UI.collectMs += ms
            UI.collectMaxMs = max(UI.collectMaxMs, ms)
            UI.collections += 1
        }
        let now = CACurrentMediaTime()
        if now - UI.reported >= 1.0 {
            print(String(format: "[ui] collect n=%d avg=%.2fms max=%.2fms per.frame=%.2fms allocs/frame=%d live=%d",
                         UI.collections, UI.collections > 0 ? UI.collectMs / Double(UI.collections) : 0, UI.collectMaxMs,
                         UI.collectMs / Double(UI.frames), UI.allocs / UI.frames, Rt.live))
            UI.frames = 0; UI.collections = 0; UI.allocs = 0; UI.collectMs = 0; UI.collectMaxMs = 0; UI.reported = now
        }
    }
}
let frameDriver = FrameDriver()

/// Run `tick` (a Scala closure) once per display refresh.
func uiAnimate(_ tick: V) -> V {
    UI.tick = tick
    let link = CADisplayLink(target: frameDriver, selector: #selector(FrameDriver.frame(_:)))
    link.add(to: .main, forMode: .common)
    UI.link = link
    return .vNull
}

func uiRandom() -> V { .vDbl(Double.random(in: 0..<1)) }
func uiSqrt(_ x: V) -> V { rtSqrt(x) }
func uiPow(_ x: V, _ y: V) -> V { rtPow(x, y) }
func uiSin(_ x: V) -> V { rtSin(x) }
func uiCos(_ x: V) -> V { rtCos(x) }

// MARK: - the network module (network/native): WebSocket and HTTP over URLSession, callbacks on the main queue

final class SocketBox {
    let task: URLSessionWebSocketTask
    let onOpen: V
    let onText: V
    let onClose: V
    var opened = false
    var finished = false
    init(_ task: URLSessionWebSocketTask, _ onOpen: V, _ onText: V, _ onClose: V) {
        self.task = task; self.onOpen = onOpen; self.onText = onText; self.onClose = onClose
    }
}

final class SocketDelegate: NSObject, URLSessionWebSocketDelegate {
    static let shared = SocketDelegate()
    var boxes: [ObjectIdentifier: SocketBox] = [:]
    static let session = URLSession(configuration: .default, delegate: SocketDelegate.shared, delegateQueue: OperationQueue.main)

    func box(_ task: URLSessionTask) -> SocketBox? { boxes[ObjectIdentifier(task)] }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        guard let b = box(webSocketTask) else { return }
        b.opened = true
        _ = try? RtInvoke(b.onOpen, rtArr([]))
        receive(b)
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        guard let b = box(webSocketTask) else { return }
        let text = reason.flatMap { String(data: $0, encoding: .utf8) } ?? ""
        finish(b, closeCode.rawValue, text)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let b = box(task) else { return }
        finish(b, 1006, error?.localizedDescription ?? "closed")
    }

    func receive(_ b: SocketBox) {
        b.task.receive { result in
            DispatchQueue.main.async {
                if b.finished { return }
                switch result {
                case .success(.string(let s)):
                    _ = try? RtInvoke(b.onText, rtArr([.vStr(s)]))
                    self.receive(b)
                case .success(.data(let d)):
                    _ = try? RtInvoke(b.onText, rtArr([.vStr(String(data: d, encoding: .utf8) ?? "")]))
                    self.receive(b)
                case .success: self.receive(b)
                case .failure(let e): self.finish(b, 1006, e.localizedDescription)
                }
            }
        }
    }

    func finish(_ b: SocketBox, _ code: Int, _ reason: String) {
        if b.finished { return }
        b.finished = true
        boxes.removeValue(forKey: ObjectIdentifier(b.task))
        _ = try? RtInvoke(b.onClose, rtArr([.vInt(code), .vStr(reason)]))
    }
}

func uiSocketOpen(_ url: V, _ onOpen: V, _ onText: V, _ onClose: V) -> V {
    guard let u = URL(string: rtS(url)) else {
        _ = try? RtInvoke(onClose, rtArr([.vInt(1006), .vStr("bad url: " + rtS(url))]))
        return .vNull
    }
    let task = SocketDelegate.session.webSocketTask(with: u)
    let box = SocketBox(task, onOpen, onText, onClose)
    SocketDelegate.shared.boxes[ObjectIdentifier(task)] = box
    task.resume()
    return .vRef(box)
}

func uiSocketSend(_ h: V, _ text: V) -> V {
    if case .vRef(let o) = h, let b = o as? SocketBox { b.task.send(.string(rtS(text))) { _ in } }
    return .vNull
}

func uiSocketClose(_ h: V) -> V {
    if case .vRef(let o) = h, let b = o as? SocketBox { b.task.cancel(with: .normalClosure, reason: nil) }
    return .vNull
}

func uiHttp(_ method: V, _ url: V, _ headersJson: V, _ body: V, _ hasBody: V, _ done: V) -> V {
    guard let u = URL(string: rtS(url)) else {
        _ = try? RtInvoke(done, rtArr([.vInt(-1), .vStr("{}"), .vStr("bad url: " + rtS(url))]))
        return .vNull
    }
    var request = URLRequest(url: u)
    request.httpMethod = rtS(method)
    if let data = rtS(headersJson).data(using: .utf8),
       let headers = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
        for (k, v) in headers { request.setValue("\(v)", forHTTPHeaderField: k) }
    }
    if hasBody.rtTrue { request.httpBody = rtS(body).data(using: .utf8) }
    URLSession.shared.dataTask(with: request) { data, response, error in
        DispatchQueue.main.async {
            if let error = error {
                _ = try? RtInvoke(done, rtArr([.vInt(-1), .vStr("{}"), .vStr(error.localizedDescription)]))
                return
            }
            let http = response as? HTTPURLResponse
            var headers: [String: String] = [:]
            http?.allHeaderFields.forEach { headers["\($0.key)"] = "\($0.value)" }
            let headersJson = (try? JSONSerialization.data(withJSONObject: headers)).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
            let text = data.flatMap { String(data: $0, encoding: .utf8) } ?? ""
            _ = try? RtInvoke(done, rtArr([.vInt(http?.statusCode ?? -1), .vStr(headersJson), .vStr(text)]))
        }
    }.resume()
    return .vNull
}

/// `--<name>=value` on the launch, or "".
func uiLaunchArg(_ name: V) -> V {
    let prefix = "--" + rtS(name) + "="
    for a in CommandLine.arguments where a.hasPrefix(prefix) { return .vStr(String(a.dropFirst(prefix.count))) }
    return .vStr("")
}

func uiQuit() -> V { exit(0) }

func uiSetRadius(_ node: V, _ radius: V) -> V {
    view(node)?.layer.cornerRadius = CGFloat(rtDouble(radius))
    view(node)?.layer.masksToBounds = rtDouble(radius) > 0
    return .vNull
}

/// Milliseconds since the epoch as local `yyyy-MM-dd HH:mm:ss`.
/// The character at a Unicode code point, as a string.
func uiChr(_ codePoint: V) -> V {
    guard let scalar = Unicode.Scalar(UInt32(rtDouble(codePoint))) else { return .vStr("") }
    return .vStr(String(Character(scalar)))
}

func uiLocalDateTime(_ millis: V) -> V {
    let f = DateFormatter()
    f.dateFormat = "yyyy-MM-dd HH:mm:ss"
    return .vStr(f.string(from: Date(timeIntervalSince1970: rtDouble(millis) / 1000.0)))
}

// MARK: - input: the remote's presses to one hook (everyui.focus.Focus); there is no pointer

/// A Siri Remote's touch surface only reaches views the focus engine owns, and the shared layer owns the focus.
func uiOnPointer(_ node: V, _ kind: V, _ f: V) -> V { .vNull }

func uiOnKey(_ f: V) -> V {
    UI.key = f
    return .vNull
}

/// RootViewController's presses, by the web's names. Menu is Back, and tvOS's (leaving the app) only when the app does
/// not take it.
func uiKeyEvent(_ press: UIPress, _ pressed: Bool) -> Bool {
    guard case .vFn = UI.key else { return false }
    let name: String
    switch press.type {
    case .select: name = "Enter"
    case .upArrow: name = "ArrowUp"
    case .downArrow: name = "ArrowDown"
    case .leftArrow: name = "ArrowLeft"
    case .rightArrow: name = "ArrowRight"
    case .playPause: name = "MediaPlayPause"
    case .menu: name = "Back"
    default: return false
    }
    if case .vBool(let taken)? = try? RtInvoke(UI.key, rtArr([.vStr(name), .vBool(pressed)])) { return taken }
    return false
}

/// Clip a view to width by height (a scroll viewport).
func uiSetClip(_ node: V, _ width: V, _ height: V) -> V {
    view(node)?.clipsToBounds = true
    return .vNull
}

/// Whether a view and everything under it is drawn: what a scroll has out of sight costs nothing.
func uiSetVisible(_ node: V, _ visible: V) -> V {
    if case .vBool(let shown) = visible { view(node)?.isHidden = !shown }
    return .vNull
}

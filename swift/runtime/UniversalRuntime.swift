// everyui — the UIKit support runtime for the Swift backend (tvOS and iOS).
//
// The render seam's primitives as plain Swift functions over `V`, one per `@native` facade in
// tvos/src/main/scala/everyui/tvos/facades.scala — the twin of roku/runtime/UniversalRuntime.brs. A node is a
// UIKit view: a `UILabel` for text, a `UIImageView` for an image, a plain `UIView` for a container. Layout is
// absolute: every view is placed by its own top-left `(x, y)` and size, kept in a side table so a rotation
// (a `transform`, which makes `frame` meaningless) never disturbs it.

import UIKit
import CoreText
import QuartzCore
import MediaAccessibility
#if os(iOS)
import WebKit   // WebView: there is no WebKit on tvOS, so a tvOS build neither references nor links it
#endif

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
    let l = OutlinedLabel()
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

/// The whole app drawn `factor` times its size (everyui.theme.UiScale): the root's sublayers scaled from its top left.
/// A sublayer transform applies about the layer's anchor (its centre), so the scale is moved back to the corner.
func uiSetRootScale(_ factor: V) -> V {
    guard let root = UI.root else { return .vNull }
    let f = CGFloat(dbl(factor))
    let cx = root.bounds.width * root.layer.anchorPoint.x, cy = root.bounds.height * root.layer.anchorPoint.y
    root.layer.sublayerTransform = CATransform3DConcat(CATransform3DMakeScale(f, f, 1),
        CATransform3DMakeTranslation((f - 1) * cx, (f - 1) * cy, 0))
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

/// An edge `width` points each side round a label's letters in `hex`, or none for "".
func uiSetTextOutline(_ node: V, _ width: V, _ hex: V) -> V {
    guard let l = view(node) as? OutlinedLabel else { return .vNull }
    let colour = rtS(hex)
    l.edgeWidth = colour.isEmpty ? 0 : CGFloat(dbl(width))
    l.edgeColor = colour.isEmpty ? .clear : uiColor(colour)
    l.setNeedsDisplay()
    return .vNull
}

/// A bundled face by family and CSS weight; size in points.
func uiSetFont(_ node: V, _ family: V, _ weight: V, _ size: V) -> V {
    guard let l = view(node) as? UILabel else { return .vNull }
    l.font = font(rtS(family), Int(dbl(weight)), dbl(size))
    place(l, geo(l))
    return .vNull
}

/// Extra space after each letter of a label, in points (0 for the face's own): UIKit's kern.
func uiSetLetterSpacing(_ node: V, _ px: V) -> V {
    guard let l = view(node) as? OutlinedLabel else { return .vNull }
    l.kern = CGFloat(dbl(px))
    place(l, geo(l))
    return .vNull
}

/// A label's face made italic: the face's own italic where UIKit finds one, else its upright letters slanted (a
/// shear, as a browser slants a face that has no italic). Upright again is the face set afresh (`uiSetFont`).
func uiSetItalic(_ node: V) -> V {
    guard let l = view(node) as? UILabel else { return .vNull }
    let d = l.font.fontDescriptor
    if let italic = d.withSymbolicTraits(d.symbolicTraits.union(.traitItalic)) {
        l.font = UIFont(descriptor: italic, size: l.font.pointSize)
    } else {
        l.font = UIFont(descriptor: d.withMatrix(CGAffineTransform(a: 1, b: 0, c: 0.2, d: 1, tx: 0, ty: 0)),
                        size: l.font.pointSize)
    }
    place(l, geo(l))
    return .vNull
}

/// A label's text width at exactly `size` points of its face: the advance width, as a browser measures it, its
/// letter spacing with it.
func uiTextWidth(_ node: V, _ uri: V, _ size: V) -> V {
    guard let l = view(node) as? UILabel else { return .vDbl(0) }
    let f = l.font.withSize(dbl(size))
    let w = ((l.text ?? "") as NSString).size(withAttributes: [.font: f, .kern: (l as? OutlinedLabel)?.kern ?? 0]).width
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

/// `f()` once `seconds` have passed, on the main queue (Platform.after).
func uiAfter(_ seconds: V, _ f: V) -> V {
    DispatchQueue.main.asyncAfter(deadline: .now() + max(0.0, dbl(seconds))) { _ = try? RtInvoke(f, rtArr([])) }
    return .vNull
}

func uiFreezeSeconds() -> V { .vDbl(UI.freeze) }

/// Whether Reduce Motion is on in Accessibility.
func uiReduceMotion() -> V { .vBool(UIAccessibility.isReduceMotionEnabled) }

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

/// The same with a body of bytes (`hasBody`), or an answer wanted as bytes (`binary`) as well as text:
/// `done(status, headersJson, text, bytes)`, a Scala `Array[Byte]`'s numbers from -128 to 127.
func uiHttpBytes(_ method: V, _ url: V, _ headersJson: V, _ body: V, _ hasBody: V, _ binary: V, _ done: V) -> V {
    guard let u = URL(string: rtS(url)) else {
        _ = try? RtInvoke(done, rtArr([.vInt(-1), .vStr("{}"), .vStr("bad url: " + rtS(url)), rtArr([])]))
        return .vNull
    }
    var request = URLRequest(url: u)
    request.httpMethod = rtS(method)
    if let data = rtS(headersJson).data(using: .utf8),
       let headers = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
        for (k, v) in headers { request.setValue("\(v)", forHTTPHeaderField: k) }
    }
    if hasBody.rtTrue { request.httpBody = Data(rtItems(body).map { UInt8(truncatingIfNeeded: Int(rtDouble($0))) }) }
    let wanted = binary.rtTrue
    URLSession.shared.dataTask(with: request) { data, response, error in
        DispatchQueue.main.async {
            if let error = error {
                _ = try? RtInvoke(done, rtArr([.vInt(-1), .vStr("{}"), .vStr(error.localizedDescription), rtArr([])]))
                return
            }
            let http = response as? HTTPURLResponse
            var headers: [String: String] = [:]
            http?.allHeaderFields.forEach { headers["\($0.key)"] = "\($0.value)" }
            let headersJson = (try? JSONSerialization.data(withJSONObject: headers)).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
            let text = data.map { String(decoding: $0, as: UTF8.self) } ?? ""
            let bytes: [V] = wanted ? (data.map { d in d.map { V.vInt(Int(Int8(bitPattern: $0))) } } ?? []) : []
            _ = try? RtInvoke(done, rtArr([.vInt(http?.statusCode ?? -1), .vStr(headersJson), .vStr(text), rtArr(bytes)]))
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

/// The time zone the device is in, as IANA names it ("America/Chicago").
func uiTimeZone() -> V {
    return .vStr(TimeZone.current.identifier)
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

/// What a view holds shown past it again: `uiSetClip` undone.
func uiClearClip(_ node: V) -> V {
    view(node)?.clipsToBounds = false
    return .vNull
}

/// Whether a view and everything under it is drawn: what a scroll has out of sight costs nothing.
func uiSetVisible(_ node: V, _ visible: V) -> V {
    if case .vBool(let shown) = visible { view(node)?.isHidden = !shown }
    return .vNull
}

func uiSetOpacity(_ node: V, _ opacity: V) -> V {
    view(node)?.alpha = CGFloat(dbl(opacity))
    return .vNull
}

/// A label that can draw an edge round its letters (TextView.textOutline): the words drawn twice, as a stroke in the
/// edge's colour and then filled in their own, so the edge is outside every letter rather than eating into it, as
/// Android's OutlinedText draws it. UIKit's text has a shadow and no outline; this is the outline.
final class OutlinedLabel: UILabel {
    var edgeWidth: CGFloat = 0
    var edgeColor: UIColor = .clear
    /// Extra space after each letter (`uiSetLetterSpacing`): while it is not 0 the words are attributed text, which a
    /// font or a colour set later is applied over, keeping the kern.
    var kern: CGFloat = 0 { didSet { spaced(text) } }
    override var text: String? {
        get { super.text }
        set { spaced(newValue) }
    }
    private func spaced(_ words: String?) {
        if kern == 0 { super.text = words }
        else {
            super.attributedText = NSAttributedString(string: words ?? "",
                attributes: [.kern: kern, .font: font as Any, .foregroundColor: textColor as Any])
        }
    }
    // the colour is swapped for the edge's while the stroke is drawn, and a label redraws itself whenever its colour
    // is set: that is held off for the one pass
    private var drawing = false

    override func setNeedsDisplay() { if !drawing { super.setNeedsDisplay() } }

    override func drawText(in rect: CGRect) {
        guard edgeWidth > 0, let context = UIGraphicsGetCurrentContext() else { super.drawText(in: rect); return }
        drawing = true
        let fill = textColor
        context.setLineWidth(edgeWidth * 2)
        context.setLineJoin(.round)
        context.setTextDrawingMode(.stroke)
        textColor = edgeColor
        super.drawText(in: rect)
        context.setTextDrawingMode(.fill)
        textColor = fill
        super.drawText(in: rect)
        drawing = false
    }
}

// MARK: - text entry

/// The `UITextContentType` for an `autocomplete` token (`Autofill.token`): what AutoFill offers for the field, and on
/// an Apple TV what a nearby iPhone is asked for. nil where UIKit names nothing.
func uiContentType(_ token: String) -> UITextContentType? {
    switch token {
    case "username": return .username
    case "current-password": return .password
    case "new-password": return .newPassword
    case "one-time-code", "one-time-code-text": return .oneTimeCode
    case "email": return .emailAddress
    case "tel": return .telephoneNumber
    case "url": return .URL
    case "name": return .name
    case "given-name": return .givenName
    case "additional-name": return .middleName
    case "family-name": return .familyName
    case "nickname": return .nickname
    case "organization": return .organizationName
    case "street-address": return .fullStreetAddress
    case "address-line1": return .streetAddressLine1
    case "address-line2": return .streetAddressLine2
    case "address-level2": return .addressCity
    case "address-level1": return .addressState
    case "postal-code": return .postalCode
    case "country": return .countryName
    case "cc-number": return .creditCardNumber
    default: return nil
    }
}

/// Platform.editText: new words from the platform's keyboard, in a dialog over the app with one text field. The field
/// says what it asks for (`textContentType`), so a sign-in offers AutoFill and the Continuity keyboard on a nearby
/// iPhone; Done hands its words to `done`, Cancel nothing.
func uiEditText(_ title: V, _ value: V, _ secure: V, _ autofill: V, _ done: V) -> V {
    var hidden = false
    if case .vBool(let s) = secure { hidden = s }
    let token = rtS(autofill)
    let alert = UIAlertController(title: rtS(title), message: nil, preferredStyle: .alert)
    alert.addTextField { field in
        field.text = rtS(value)
        field.isSecureTextEntry = hidden || token == "current-password" || token == "new-password"
        field.textContentType = uiContentType(token)
        switch token {
        case "email", "username": field.keyboardType = .emailAddress
        case "tel": field.keyboardType = .phonePad
        case "url": field.keyboardType = .URL
        case "one-time-code", "cc-number": field.keyboardType = .numberPad
        case "one-time-code-text": field.keyboardType = .asciiCapable
        default: break
        }
        if field.isSecureTextEntry || token == "email" || token == "username" || token == "url" ||
            token == "one-time-code-text" {
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
        }
    }
    alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
    let ok = UIAlertAction(title: "Done", style: .default) { _ in
        let words = alert.textFields?.first?.text ?? ""
        _ = try? RtInvoke(done, rtArr([.vStr(words)]))
    }
    alert.addAction(ok)
    alert.preferredAction = ok
    var top = UI.root.window?.rootViewController
    while let shown = top?.presentedViewController { top = shown }
    top?.present(alert, animated: true)
    return .vNull
}

// MARK: - store: documents kept between launches (everyui.store.Store)

/// A space's directory under the app's Caches: the one place a television app may keep files of its own, and one the
/// system empties when it wants the room back, which is what the store tells every reader to expect.
func uiStoreDir(_ space: V) -> URL {
    let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
    let dir = caches.appendingPathComponent("everyui-store").appendingPathComponent(uiStoreSafe(rtS(space)))
    try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    return dir
}

/// A name as a file name: letters, digits, `.` and `-` as they are, anything else `_` (the Roku and Android fold it
/// the same way).
func uiStoreSafe(_ name: String) -> String {
    String(name.map { (c: Character) -> Character in (c.isLetter || c.isNumber || c == "." || c == "-") ? c : "_" })
}

func uiStoreFile(_ space: V, _ key: V) -> URL {
    uiStoreDir(space).appendingPathComponent(uiStoreSafe(rtS(key)) + ".json")
}

/// The document under a key, or "" where there is none.
func uiStoreGet(_ space: V, _ key: V) -> V {
    .vStr((try? String(contentsOf: uiStoreFile(space, key), encoding: .utf8)) ?? "")
}

func uiStorePut(_ space: V, _ key: V, _ value: V) -> V {
    try? rtS(value).write(to: uiStoreFile(space, key), atomically: true, encoding: .utf8)
    return .vNull
}

func uiStoreDelete(_ space: V, _ key: V) -> V {
    try? FileManager.default.removeItem(at: uiStoreFile(space, key))
    return .vNull
}

/// The keys a space holds, as the file names say them: a key folded by uiStoreSafe comes back folded.
func uiStoreKeys(_ space: V) -> V {
    let names = (try? FileManager.default.contentsOfDirectory(atPath: uiStoreDir(space).path)) ?? []
    return rtArr(names.filter { $0.hasSuffix(".json") }.map { (n: String) -> V in V.vStr(String(n.dropLast(5))) })
}

func uiStoreClear(_ space: V) -> V {
    try? FileManager.default.removeItem(at: uiStoreDir(space))
    return .vNull
}

// MARK: - captions

/// The viewer's caption setting (Settings, Accessibility, Subtitles and Captioning) as MediaAccessibility has it:
/// "On" for Closed Captions and SDH (always on), "Automatic" (where the sound is not in the viewer's language), "Off"
/// (forced subtitles only).
func uiCaptionsMode() -> V {
    switch MACaptionAppearanceGetDisplayType(.user) {
    case .alwaysOn: return .vStr("On")
    case .automatic: return .vStr("Automatic")
    default: return .vStr("Off")
    }
}

/// The first of the languages the viewer prefers captions in, or "".
func uiCaptionsLanguage() -> V {
    let languages = MACaptionAppearanceCopySelectedLanguages(.user).takeRetainedValue() as NSArray
    return .vStr((languages.firstObject as? String) ?? "")
}

var uiCaptionObservers: [Int: NSObjectProtocol] = [:]
var uiCaptionNext = 0

/// `changed()` each time the viewer changes the setting, until uiCaptionsUnlisten(the id answered).
func uiCaptionsListen(_ changed: V) -> V {
    uiCaptionNext += 1
    let name = Notification.Name(kMACaptionAppearanceSettingsChangedNotification as String)
    uiCaptionObservers[uiCaptionNext] = NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { _ in
        _ = try? RtInvoke(changed, rtArr([]))
    }
    return .vInt(uiCaptionNext)
}

func uiCaptionsUnlisten(_ id: V) -> V {
    guard case .vInt(let key) = rtToInt(id), let observer = uiCaptionObservers.removeValue(forKey: key) else { return .vNull }
    NotificationCenter.default.removeObserver(observer)
    return .vNull
}

// MARK: - a page of the web (everyui.component.WebView): a WKWebView on iOS; tvOS has no WebKit, so an empty box

#if os(iOS)

/// What a page posts through its bridge, handed to the page it came from. A handler of its own, holding the page
/// weakly: WebKit keeps a message handler as long as its controller, and the page holds the controller.
final class WebSaid: NSObject, WKScriptMessageHandler {
    weak var page: WebPage?
    init(_ page: WebPage) { self.page = page }
    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        // only what the page's own frame says is heard, as on the web
        if message.frameInfo.isMainFrame { page?.said(message.body) }
    }
}

/// A page of the web: a WKWebView configured as Android's WebView is (`Nodes.web`): script and storage on, media
/// that plays inline and without a touch, black behind the page until the app makes it clear. Its page is lent
/// `window.<bridge>.postMessage(string)`, which reaches `heard`; a reply to `ask` (`{everyui: "reply", session, id,
/// reply}`, this showing's session) reaches `replied` instead. A sandbox comes as near to an iframe's as WebKit lets it
/// after the view is made (`sandbox`).
final class WebPage: WKWebView, WKNavigationDelegate, WKUIDelegate {
    /// The one made ready by WebView.prewarm, for the next page to take.
    static var spare: WebPage? = nil

    let content: WKUserContentController
    var bridge = ""
    var heard: V = .vNull
    var replied: V = .vNull
    /// This showing's: new as each source is set, so a reply from a page no longer showing is not this one's.
    var session = ""
    var source = ""
    /// The app's server, for a site-absolute source the bundle does not hold.
    var origin = ""
    /// What the app last asked to show, which a sandbox lets through whatever its scheme.
    var asked: URL? = nil
    /// The sandbox's flags, or nil for none; and whether a sandboxed page is heard all the same.
    var flags: Set<String>? = nil
    var hearing = false
    var released = false

    init() {
        let content = WKUserContentController()
        let c = WKWebViewConfiguration()
        c.userContentController = content
        // an embed that waits for a touch, or plays only full screen, is a YouTube trailer that never plays in its box
        c.allowsInlineMediaPlayback = true
        c.mediaTypesRequiringUserActionForPlayback = []
        c.allowsPictureInPictureMediaPlayback = true
        self.content = content
        super.init(frame: .zero, configuration: c)
        navigationDelegate = self
        uiDelegate = self
        scrollView.contentInsetAdjustmentBehavior = .never
        setClear(false)
    }

    required init?(coder: NSCoder) { nil }

    var sandboxed: Bool { flags != nil }
    /// Whether the page is lent the bridge and heard: unsandboxed, or sandboxed and heard all the same.
    var lent: Bool { !sandboxed || hearing }
    func allows(_ flag: String) -> Bool { flags.map { $0.contains(flag) } ?? true }

    /// The bridge on the page's window from the loads after this, or taken off it.
    func lend() {
        content.removeAllUserScripts()
        content.removeScriptMessageHandler(forName: bridge)
        if bridge.isEmpty || !lent || released { return }
        content.add(WebSaid(self), name: bridge)
        let name = webJson(bridge)
        let script = "window[\(name)] = { postMessage: function (m) { " +
            "window.webkit.messageHandlers[\(name)].postMessage(String(m)); } };"
        content.addUserScript(WKUserScript(source: script, injectionTime: .atDocumentStart, forMainFrameOnly: true))
    }

    func setClear(_ clear: Bool) {
        let ground: UIColor = clear ? .clear : .black
        isOpaque = false
        backgroundColor = ground
        scrollView.backgroundColor = ground
        underPageBackgroundColor = ground
    }

    /// Show `url`: a path the app's bundle holds is that file; a site-absolute one it does not, the app's server's
    /// (`origin`) where it has one; anything else as it is.
    func show(_ url: String, _ origin: String) {
        source = url.trimmingCharacters(in: .whitespaces)
        session = String(Int.random(in: 0..<Int(1.0e12)), radix: 36)
        navigate(origin)
    }

    func navigate(_ origin: String) {
        self.origin = origin
        if source.isEmpty || released { return }
        if URL(string: source)?.scheme == nil {
            let path = String(source.prefix { $0 != "?" && $0 != "#" })
            let file = bundlePath(path)
            if !path.isEmpty, FileManager.default.fileExists(atPath: file), let root = Bundle.main.resourceURL {
                let at = URL(fileURLWithPath: file)
                asked = at
                loadFileURL(at, allowingReadAccessTo: root)
                return
            }
        }
        var at = source
        if source.hasPrefix("//") { at = "https:" + source }
        else if source.hasPrefix("/") && !origin.isEmpty {
            at = (origin.hasSuffix("/") ? String(origin.dropLast()) : origin) + source
        }
        guard let u = URL(string: at) else { print("[ui] web: not an address: \(source)"); return }
        asked = u
        load(URLRequest(url: u))
    }

    /// Sandboxed with `allowed` (nil: not at all), and the page loaded again where it shows one.
    func sandbox(_ allowed: Set<String>?) {
        if allowed == flags { return }
        flags = allowed
        lend()
        navigate(origin)
    }

    func hear(_ on: Bool) {
        if on == hearing { return }
        hearing = on
        if sandboxed {
            lend()
            navigate(origin)
        }
    }

    func said(_ body: Any) {
        if released { return }
        let message = webText(body)
        if message.hasPrefix("{\"everyui\":\"reply\"") {
            if let data = message.data(using: .utf8),
               let reply = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
                if reply["session"] as? String == session, let id = reply["id"] as? Int {
                    _ = try? RtInvoke(replied, rtArr([.vInt(id), .vStr(reply["reply"].map(webText) ?? "")]))
                }
                return
            }
        }
        _ = try? RtInvoke(heard, rtArr([.vStr(message)]))
    }

    /// Heard no more, its page stopped and left on about:blank, and taken off the screen: the app is done with it.
    func release() {
        if released { return }
        released = true
        heard = .vNull
        replied = .vNull
        stopLoading()
        load(URLRequest(url: URL(string: "about:blank")!))
        content.removeAllUserScripts()
        content.removeScriptMessageHandler(forName: bridge)
        navigationDelegate = nil
        uiDelegate = nil
        removeFromSuperview()
    }

    // script by "allow-scripts"; sandboxed, a form posted is refused without "allow-forms", and a link anywhere but a
    // page goes nowhere, as Android's SandboxClient has it
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, preferences: WKWebpagePreferences,
                 decisionHandler: @escaping (WKNavigationActionPolicy, WKWebpagePreferences) -> Void) {
        preferences.allowsContentJavaScript = allows("allow-scripts")
        if sandboxed && action.targetFrame?.isMainFrame != false && action.request.url != asked {
            let scheme = action.request.url?.scheme?.lowercased() ?? ""
            let form = action.navigationType == .formSubmitted || action.navigationType == .formResubmitted
            if (form && !allows("allow-forms")) || !["http", "https", "about", "data", "blob"].contains(scheme) {
                decisionHandler(.cancel, preferences)
                return
            }
        }
        decisionHandler(.allow, preferences)
    }

    // a new window (window.open, target="_blank") replaces the page, as on Android; sandboxed, only with "allow-popups"
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if allows("allow-popups") { load(action.request) }
        return nil
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        failed(error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { failed(error) }

    func failed(_ error: Error) {
        let e = error as NSError
        if e.domain == NSURLErrorDomain && e.code == NSURLErrorCancelled { return }
        print("[ui] web: \(source) did not load: \(e.localizedDescription)")
    }

    // iOS ends a page's process to have its memory back, which leaves the view blank: the page is shown again
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        print("[ui] web: the page's process ended; showing \(source) again")
        navigate(origin)
    }
}

/// A string as a JSON string, for script.
func webJson(_ s: String) -> String {
    guard let data = try? JSONSerialization.data(withJSONObject: [s]), let a = String(data: data, encoding: .utf8)
    else { return "\"\"" }
    return String(a.dropFirst().dropLast())
}

/// What a page said as a string: a string as it is, anything else as its JSON.
func webText(_ body: Any) -> String {
    if let s = body as? String { return s }
    if JSONSerialization.isValidJSONObject(body), let data = try? JSONSerialization.data(withJSONObject: body),
       let s = String(data: data, encoding: .utf8) { return s }
    return "\(body)"
}

func webPage(_ node: V) -> WebPage? { view(node) as? WebPage }

func uiWebViews() -> V { .vBool(true) }

/// A view made ready, with its page process started, for the next WebView to take (WebView.prewarm).
func uiWebPrewarm() -> V {
    if WebPage.spare == nil {
        let began = CACurrentMediaTime()
        let page = WebPage()
        page.load(URLRequest(url: URL(string: "about:blank")!))
        WebPage.spare = page
        print("[ui] web: warmed, a view made in \(Int((CACurrentMediaTime() - began) * 1000.0)) ms")
    }
    return .vNull
}

func uiWeb(_ bridge: V) -> V {
    let page = WebPage.spare ?? WebPage()
    WebPage.spare = nil
    page.bridge = rtS(bridge)
    page.lend()
    place(page, Geo())
    return .vRef(page)
}

func uiWebLoad(_ node: V, _ url: V, _ origin: V) -> V { webPage(node)?.show(rtS(url), rtS(origin)); return .vNull }

func uiWebAgent(_ node: V, _ agent: V) -> V {
    let a = rtS(agent).trimmingCharacters(in: .whitespaces)
    webPage(node)?.customUserAgent = a.isEmpty ? nil : a
    return .vNull
}

/// Sandboxed (`on`) with `flags`, comma-separated, allowed.
func uiWebSandbox(_ node: V, _ on: V, _ flags: V) -> V {
    let allowed = rtB(on) ? Set(rtS(flags).split(separator: ",").map { String($0) }) : nil
    webPage(node)?.sandbox(allowed)
    return .vNull
}

func uiWebHearSandboxed(_ node: V, _ on: V) -> V { webPage(node)?.hear(rtB(on)); return .vNull }

func uiWebClear(_ node: V, _ clear: V) -> V { webPage(node)?.setClear(rtB(clear)); return .vNull }

/// Script run in the page, where it is not sandboxed.
func uiWebRun(_ node: V, _ script: V) -> V {
    if let page = webPage(node), !page.sandboxed, !page.released {
        page.evaluateJavaScript(rtS(script), completionHandler: nil)
    }
    return .vNull
}

/// `{everyui: "ask", session, id, request}` posted to the page; false where it is not heard or shows nothing.
func uiWebAsk(_ node: V, _ id: V, _ request: V) -> V {
    guard let page = webPage(node), page.lent, !page.released, !page.session.isEmpty else { return .vBool(false) }
    let message: [String: Any] = [
        "everyui": "ask", "session": page.session, "id": Int(dbl(id)), "request": rtS(request)]
    guard let data = try? JSONSerialization.data(withJSONObject: message),
          let json = String(data: data, encoding: .utf8) else { return .vBool(false) }
    page.evaluateJavaScript("window.postMessage(" + json + ", '*')", completionHandler: nil)
    return .vBool(true)
}

func uiWebOnMessage(_ node: V, _ f: V) -> V { if let p = webPage(node), !p.released { p.heard = f }; return .vNull }

func uiWebOnReply(_ node: V, _ f: V) -> V { if let p = webPage(node), !p.released { p.replied = f }; return .vNull }

func uiWebRelease(_ node: V) -> V { webPage(node)?.release(); return .vNull }

#else

// tvOS: WebKit is not public here, so nothing of it is referenced or linked, and a page is an empty box
func uiWebViews() -> V { .vBool(false) }
func uiWebPrewarm() -> V { .vNull }
func uiWeb(_ bridge: V) -> V { uiContainer() }
func uiWebLoad(_ node: V, _ url: V, _ origin: V) -> V { .vNull }
func uiWebAgent(_ node: V, _ agent: V) -> V { .vNull }
func uiWebSandbox(_ node: V, _ on: V, _ flags: V) -> V { .vNull }
func uiWebHearSandboxed(_ node: V, _ on: V) -> V { .vNull }
func uiWebClear(_ node: V, _ clear: V) -> V { .vNull }
func uiWebRun(_ node: V, _ script: V) -> V { .vNull }
func uiWebAsk(_ node: V, _ id: V, _ request: V) -> V { .vBool(false) }
func uiWebOnMessage(_ node: V, _ f: V) -> V { .vNull }
func uiWebOnReply(_ node: V, _ f: V) -> V { .vNull }
func uiWebRelease(_ node: V) -> V { .vNull }

#endif

// MARK: - lifecycle

var uiLifecycleObservers: [NSObjectProtocol] = []

/// `away()` when the application resigns active (another app opened over it, the TV's home screen or app switcher,
/// the device going to sleep) or goes into the background, and `resumed()` when it is active again, on the main
/// queue; the Scala side (Presence) tells each once. Heard from the first call; a later one changes nothing.
func uiOnLifecycle(_ resumed: V, _ away: V) -> V {
    if !uiLifecycleObservers.isEmpty { return .vNull }
    let centre = NotificationCenter.default
    for name in [UIApplication.willResignActiveNotification, UIApplication.didEnterBackgroundNotification] {
        uiLifecycleObservers.append(centre.addObserver(forName: name, object: nil, queue: .main) { _ in
            _ = try? RtInvoke(away, rtArr([]))
        })
    }
    uiLifecycleObservers.append(centre.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil,
                                                   queue: .main) { _ in
        _ = try? RtInvoke(resumed, rtArr([]))
    })
    return .vNull
}

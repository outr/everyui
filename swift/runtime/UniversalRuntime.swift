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
import ImageIO
import CoreImage
import MediaAccessibility
import GameController
import Security
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
    /// The size the app is drawn at (uiSetRootScale): a point of the root is this many of the app's units.
    static var scale: Double = 1.0
    /// What the screen's edges take on an iPhone or an iPad (the safe area), which the root is placed inside.
    static var safe: UIEdgeInsets = .zero
    /// Told as the root changes size (a rotation, a split view), and as the system's light or dark changes.
    static var resized: [V] = []
    static var schemes: [V] = []
    /// What hears the text size change (uiOnTextScale), kept as long as the app runs.
    static var textScaleObservers: [NSObjectProtocol] = []
    /// Whether the Keychain has refused a secret (uiSecretPut), said once.
    static var secretsUnkept = false
    /// The text dialog up (uiEditText), and what closes it, Done (true) or Cancel, for the drive's keys and typing.
    static var editing: (UIAlertController, (Bool) -> Void)? = nil
    /// The drive's hook into the program (uiOnDrive), where the app was built and launched to be driven.
    static var drive: V = .vNull
    /// The event being reported: its modifiers (ctrl, meta, alt, shift), whether a finger made it, its button.
    static var modifiers: (Bool, Bool, Bool, Bool) = (false, false, false, false)
    static var finger = true
    static var button = 0
    /// The app's reading direction (uiSetDirection), which a label's start follows.
    static var rtl = false
}

/// The program's closures the runtime holds itself, roots of the collector with what RtHeld holds.
func uiHooks() -> [V] {
    var all: [V] = [UI.tick, UI.key, UI.drive] + UI.resized + UI.schemes
    all.append(contentsOf: [Pictures.svg, Pictures.failed])
    all.append(contentsOf: uiPointerRoots())
    all.append(contentsOf: uiWebRoots())
    all.append(contentsOf: uiMediaRoots())
    for box in SocketDelegate.shared.boxes.values { all.append(contentsOf: [box.onOpen, box.onText, box.onClose]) }
    return all
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
    // Text that was never given a size takes its natural size, as a `div` or a SceneGraph `Label` does; text that wraps
    // at a width it was given is as tall as its lines there.
    var w = g.w, h = g.h
    if let l = v as? UILabel {
        // (a label of several lines that does not wrap is as wide as its longest, whatever it was given: UIKit wraps
        // any label of more than one line at its width, which code laid out as it is written must not be)
        if let o = l as? OutlinedLabel, !o.wraps, l.numberOfLines > 1 {
            w = max(w, Double(l.intrinsicContentSize.width))
        }
        if w > 0 && h <= 0 && l.numberOfLines != 1 { h = labelHeight(l, w) }
        else {
            let natural = l.intrinsicContentSize
            if w <= 0 { w = Double(natural.width) }
            if h <= 0 { h = Double(natural.height) }
        }
    }
    v.bounds = CGRect(x: 0, y: 0, width: w, height: h)
    v.center = CGPoint(x: g.x + w / 2.0, y: g.y + h / 2.0)
    v.transform = CGAffineTransform(rotationAngle: g.rot).scaledBy(x: g.scale, y: g.scale)
    if let p = v as? PictureView { pictureResized(p) }
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

/// A site-absolute asset path (`/images/icon.png`) inside the app bundle, by its path alone: a query (`?v=3`) or a
/// fragment is a server's to read, as Android's assets have it.
func bundlePath(_ uri: String) -> String {
    let bare = String(uri.prefix { $0 != "?" && $0 != "#" })
    let rel = bare.hasPrefix("/") ? String(bare.dropFirst()) : bare
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

/// How many lines `s` was written with: one, and one more for each line break.
func uiLineCount(_ s: String) -> Int { s.reduce(1) { $1 == "\n" || $1 == "\r\n" || $1 == "\r" ? $0 + 1 : $0 } }

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

/// The words, as many lines as they were written with (a line break in them is one, as Android's setText has it):
/// a label is otherwise one line, and showed the words up to the first break alone.
func uiSetText(_ node: V, _ text: V) -> V {
    guard let l = view(node) as? UILabel else { return .vNull }
    let s = rtS(text)
    l.numberOfLines = (l as? OutlinedLabel)?.wraps == true ? 0 : uiLineCount(s)
    l.text = s
    place(l, geo(l))
    return .vNull
}

/// How tall a label's words are laid out `width` wide, as it draws them: whole pixels, so its last line is never cut.
func labelHeight(_ l: UILabel, _ width: Double) -> Double {
    let room = CGRect(x: 0, y: 0, width: width, height: 100_000)
    let r = l.textRect(forBounds: room, limitedToNumberOfLines: l.numberOfLines)
    return Double(r.height.rounded(.up))
}

/// Whether a label wraps at its width, as many lines as it takes, or keeps the lines its words were written with.
func uiSetWraps(_ node: V, _ on: V) -> V {
    guard let l = view(node) as? OutlinedLabel else { return .vNull }
    let wraps = rtB(on)
    if wraps == l.wraps { return .vNull }
    l.wraps = wraps
    l.numberOfLines = wraps ? 0 : uiLineCount(l.text ?? "")
    place(l, geo(l))
    return .vNull
}

/// How tall a label's words are wrapped at `width`.
func uiTextHeight(_ node: V, _ width: V) -> V {
    guard let l = view(node) as? UILabel else { return .vDbl(0) }
    return .vDbl(labelHeight(l, dbl(width)))
}

/// Where a label's lines sit and which way its words run (OutlinedLabel.styled).
func uiSetTextAlign(_ node: V, _ align: V, _ direction: V) -> V {
    guard let l = view(node) as? OutlinedLabel else { return .vNull }
    l.align = rtS(align)
    l.direction = rtS(direction)
    return .vNull
}

/// The app's reading direction: every label's start on its side, those running their own way (`auto`, `ltr`, `rtl`)
/// as before.
func uiSetDirection(_ rtl: V) -> V {
    UI.rtl = rtB(rtl)
    for l in OutlinedLabel.all.allObjects { l.styled(l.text) }
    return .vNull
}

/// Each wrapped line `px` below the last; 0, the face's own spacing.
func uiSetLineHeight(_ node: V, _ px: V) -> V {
    guard let l = view(node) as? OutlinedLabel else { return .vNull }
    l.advance = CGFloat(max(0, dbl(px)))
    place(l, geo(l))
    return .vNull
}

/// A shadow behind a label's letters, `blur` its spread as CSS has it, or none for "": the layer's own, which follows
/// the glyphs it draws.
func uiSetTextShadow(_ node: V, _ x: V, _ y: V, _ blur: V, _ hex: V) -> V {
    guard let l = view(node) as? UILabel else { return .vNull }
    let s = rtS(hex)
    if s.isEmpty { l.layer.shadowOpacity = 0; return .vNull }
    let colour = uiColor(s)
    var alpha: CGFloat = 1
    colour.getRed(nil, green: nil, blue: nil, alpha: &alpha)
    l.layer.shadowColor = colour.withAlphaComponent(1).cgColor
    l.layer.shadowOpacity = Float(alpha)
    l.layer.shadowOffset = CGSize(width: dbl(x), height: dbl(y))
    l.layer.shadowRadius = CGFloat(dbl(blur) / 2.0)
    l.layer.masksToBounds = false
    return .vNull
}

/// Whether the app bundle holds a file at `path` (a query or a fragment left off).
func uiBundled(_ path: V) -> V { .vBool(FileManager.default.fileExists(atPath: bundlePath(rtS(path)))) }

/// Whether words shown with `direction` ("", "auto", "ltr", "rtl") run right to left, the app's own direction being
/// UI.rtl: "auto" by the first letter with a direction (uiFirstStrong), or the app's where there is none.
func uiRightToLeft(_ direction: String, _ words: String) -> Bool {
    switch direction {
    case "rtl": return true
    case "ltr": return false
    case "auto": return uiFirstStrong(words) ?? UI.rtl
    default: return UI.rtl
    }
}

/// Whether the first letter of `words` with a direction reads right to left; nil where none has one:
/// everyui.text.Bidi's rule, over the same UTF-16 units.
func uiFirstStrong(_ words: String) -> Bool? {
    for u in words.utf16 {
        let c = Int(u)
        if (c >= 0x41 && c <= 0x5A) || (c >= 0x61 && c <= 0x7A) { return false }
        if c < 0xAA { continue }
        if c < 0x590 {
            if c == 0xAA || c == 0xB5 || c == 0xBA || (c >= 0xC0 && c <= 0x2B8 && c != 0xD7 && c != 0xF7) ||
                (c >= 0x2BB && c <= 0x2C1) || c == 0x2D0 || c == 0x2D1 || (c >= 0x2E0 && c <= 0x2E4) || c == 0x2EE ||
                (c >= 0x370 && c != 0x374 && c != 0x375 && c != 0x37E && c != 0x384 && c != 0x385 && c != 0x387) {
                return false
            }
            continue
        }
        if c < 0x900 {
            // Hebrew's points, Arabic's marks and digits, have none
            if (c >= 0x591 && c <= 0x5C7) || (c >= 0x600 && c <= 0x61A && c != 0x608 && c != 0x60B && c != 0x60D) ||
                (c >= 0x64B && c <= 0x66C) || c == 0x670 || (c >= 0x6D6 && c <= 0x6ED && c != 0x6E5 && c != 0x6E6) ||
                (c >= 0x6F0 && c <= 0x6F9) || (c >= 0x816 && c <= 0x82D && c != 0x81A && c != 0x824 && c != 0x828) ||
                (c >= 0x8D3 && c <= 0x8FF) { continue }
            return true
        }
        if c < 0x2000 { return false }
        if c == 0x200E { return false }
        if c == 0x200F { return true }
        if c < 0x2C00 { continue }
        if c < 0xD800 {
            if (c >= 0x2E00 && c <= 0x2E7F) || (c >= 0x3000 && c <= 0x303F) { continue }
            return false
        }
        if c == 0xD802 || c == 0xD803 || c == 0xD83A || c == 0xD83B { return true }
        if c < 0xF900 { continue }
        if c < 0xFB1D { return false }
        if c < 0xFE00 {
            if (c >= 0xFD3E && c <= 0xFD4F) || c == 0xFDFD { continue }
            return true
        }
        if c >= 0xFE70 && c <= 0xFEFE { return true }
        if (c >= 0xFF21 && c <= 0xFF3A) || (c >= 0xFF41 && c <= 0xFF5A) || (c >= 0xFF66 && c <= 0xFFDC) { return false }
    }
    return nil
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
    UI.scale = Double(f)
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

/// `"#rrggbb"`, or `""` for transparent. The root's is the ground round it too, under the status bar and the home
/// indicator (outside the safe area), and the status bar's words are dark or light against it.
func uiSetBackgroundColor(_ node: V, _ hex: V) -> V {
    let s = rtS(hex)
    let colour: UIColor = s.isEmpty ? .clear : uiColor(s)
    view(node)?.backgroundColor = colour
    if let v = view(node), v === UI.root, UI.viewport == nil, let around = v.superview {
        around.backgroundColor = s.isEmpty ? .white : colour
        #if os(iOS)
        around.window?.rootViewController?.setNeedsStatusBarAppearanceUpdate()
        #endif
    }
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

/// An ARB file's messages as a map of Strings (Platform.parseMessages), parsed by JSONSerialization with its metadata
/// (`@key`, `@@locale`) and anything that is not a string left out, as a Roku's uiArbMessages parses them natively:
/// the Scala parser over the emitted String's characters took an app's table of thousands of words minutes, its first
/// screen with it. Text that is not a JSON object throws, as the parser does.
func uiArbMessages(_ text: V) throws -> V {
    if rtS(text).isEmpty { return _mapEmpty() }   // no file, no words, as on a Roku
    guard let any = try? JSONSerialization.jsonObject(with: Data(rtS(text).utf8)),
          let json = any as? [String: Any] else {
        return try RtThrow(rtException("RuntimeException", "not an ARB file: " + String(rtS(text).prefix(80))))
    }
    var pairs: [V] = []
    pairs.reserveCapacity(json.count)
    for (k, v) in json where !k.hasPrefix("@") { if let s = v as? String { pairs.append(Tuple2(.vStr(k), .vStr(s))) } }
    return _mapFromPairs(rtArr(pairs))
}

/// A text file as UTF-8, then `done(text)` on the main queue (Platform.readText): one in the bundle by its path (a
/// leading `/` is the bundle's root, as Android reads its assets and a Roku `pkg:/`), a `file:` URL, or one at an
/// http(s) URL (an SVG icon), read off the main thread either way. One that is not there, or will not read, is logged
/// and answered with "", as on Android and a Roku, where the shared API has nothing else to say it with.
func uiReadTextThen(_ path: V, _ done: V) -> V {
    let p = rtS(path)
    let held = RtHeld.hold(done)
    func answer(_ text: String?, _ why: String) {
        DispatchQueue.main.async {
            RtHeld.release(held)
            if text == nil { print("[ui] readText: " + why) }
            RtGuard("a file read", done, [.vStr(text ?? "")])
        }
    }
    if p.hasPrefix("http://") || p.hasPrefix("https://"), let u = URL(string: p) {
        var request = URLRequest(url: u)
        request.timeoutInterval = 30
        URLSession.shared.dataTask(with: request) { data, response, error in
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            if let error = error { answer(nil, p + ": " + error.localizedDescription) }
            else if status < 200 || status >= 300 { answer(nil, p + ": HTTP " + String(status)) }
            else { answer(String(decoding: data ?? Data(), as: UTF8.self), "") }
        }.resume()
        return .vNull
    }
    let file = p.hasPrefix("file:") ? (URL(string: p)?.path ?? "") : bundlePath(p)
    DispatchQueue.global(qos: .userInitiated).async {
        if let data = FileManager.default.contents(atPath: file) { answer(String(decoding: data, as: UTF8.self), "") }
        else {
            answer(nil, (FileManager.default.fileExists(atPath: file) ? "could not read " : "no such file ") + file)
        }
    }
    return .vNull
}

// MARK: - pictures: an image node, and what loads and decodes its pictures

/// An image node: a UIImageView that knows the picture it was last asked for, so one that comes after it was asked for
/// another is never shown, and what it is drawn with (a tint, a blur), over the picture as it was decoded.
final class PictureView: UIImageView {
    /// The address asked for last, and a ticket that changes with it: what comes for an older ticket is let go.
    var uri = ""
    var ticket = 0
    /// What it waits on (Pictures.want), to give up as it is asked for another or for none.
    var waiter = 0
    /// The picture as decoded, before its tint and blur.
    var decoded: Picture? = nil
    /// Asked again at a larger size, for this ticket, once: a picture decoded smaller than it came to be drawn.
    var askedAgain = false
    var tint: UIColor? = nil
    var blur = 0.0
    /// What the blur under way, or the blurred picture shown, was made of.
    var blurredFor = ""
    /// A look at its size is waiting for the turn to end (place, as it is resized).
    var looking = false
}

/// A picture decoded: its image (none where only its size was read), its natural size in pixels, the longer side in
/// pixels it was decoded at, whether that is the most it is ever decoded at (its whole size, or Pictures.largest), and
/// an SVG's text, to draw it again larger.
final class Picture {
    let image: UIImage?
    let width: Double
    let height: Double
    let side: Double
    let whole: Bool
    let svg: String?
    init(_ image: UIImage?, _ width: Double, _ height: Double, _ side: Double, _ whole: Bool, _ svg: String?) {
        self.image = image; self.width = width; self.height = height; self.side = side; self.whole = whole
        self.svg = svg
    }
}

/// A file as it came: its bytes and the type it came as.
final class Fetched {
    let data: Data
    let type: String
    init(_ data: Data, _ type: String) { self.data = data; self.type = type }
}

/// One address on its way: everyone waiting on it (an id, the longer side in pixels each wants, -1 for its size alone,
/// and what to tell), and its download, if it is one.
final class PictureLoad {
    var waiters: [(id: Int, side: Double, done: (Picture?) -> Void)] = []
    var task: URLSessionDataTask? = nil
}

/// Pictures by address: an app's own file (a path in the bundle), a `file:` URL, a `data:` address, or an http(s) one,
/// fetched with URLSession and kept on disk by its URLCache as the server allows; read and decoded off the main thread,
/// made only as large as they are drawn (ImageIO's thumbnail, so a 4K backdrop on a 1080p screen is decoded at 1080p),
/// and handed back on it. One load an address, however many ask; one nobody waits on any longer is cancelled. An SVG
/// (by its type, its name, or its first bytes) is drawn by the program (EveryUI's VectorArt) through `svg`.
enum Pictures {
    /// The most pixels a picture is decoded at on its longer side, as SvgImage.largest.
    static let largest = 4096.0
    /// Pictures decoded lately, by address, at the largest they were needed: a screen of posters shown again comes from
    /// memory. NSCache lets them go under memory pressure, or past its cost (their pixels' bytes).
    static let decoded: NSCache<NSString, Picture> = {
        let c = NSCache<NSString, Picture>()
        c.totalCostLimit = 192 << 20
        return c
    }()
    /// The files fetched lately, by address: a picture's size read (Image) and its view's picture then decoded take
    /// one download, and one decoded again larger takes none.
    static let fetched: NSCache<NSString, Fetched> = {
        let c = NSCache<NSString, Fetched>()
        c.totalCostLimit = 48 << 20
        return c
    }()
    static var loads: [String: PictureLoad] = [:]
    static var waiting = 0
    /// The User-Agent pictures are fetched with (Platform.identify); URLSession's own when empty.
    static var agent = ""
    /// The program's SVG painter, `(address, text, pixels)` (uiPictureHooks), and what it is drawing into meanwhile.
    static var svg: V = .vNull
    static var canvas: ArtCanvas? = nil
    /// The program's word on a picture that would not load, `(address, why)`; why is "" for one that came but was
    /// neither a picture nor an SVG.
    static var failed: V = .vNull
    static let session: URLSession = {
        let config = URLSessionConfiguration.default
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
        config.urlCache = URLCache(memoryCapacity: 8 << 20, diskCapacity: 256 << 20,
                                   directory: caches?.appendingPathComponent("everyui-pictures"))
        config.requestCachePolicy = .useProtocolCachePolicy
        // a network gone away leaves a picture waiting no longer than this
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 90
        config.httpMaximumConnectionsPerHost = 6
        return URLSession(configuration: config)
    }()
    static let work = DispatchQueue(label: "everyui-pictures", qos: .utility, attributes: .concurrent)
    static let blurring = DispatchQueue(label: "everyui-blur", qos: .utility)
    static let effects = CIContext(options: nil)
    /// Each picture's sizes logged as it is decoded, where the app was launched with `--picturelog=1` (a check reads it).
    static let logged = CommandLine.arguments.contains("--picturelog=1")

    /// Whether `p` will do for a view wanting `side` pixels on its longer side (-1 its size alone, 0 its whole size).
    static func enough(_ p: Picture, _ side: Double) -> Bool {
        side < 0 || (p.image != nil && (p.whole || (side > 0 && p.side >= side - 1.0)))
    }

    /// The picture at `uri` as `side` wants it, then `done` on the main queue (or at once, from memory); nil where it
    /// cannot be had. The answer is an id to `forget` it by (0 where it was answered at once).
    @discardableResult
    static func want(_ uri: String, _ side: Double, _ done: @escaping (Picture?) -> Void) -> Int {
        if let p = decoded.object(forKey: uri as NSString) {
            if enough(p, side) { done(p); return 0 }
            // an SVG is drawn again larger from its words, here and now
            if let text = p.svg {
                let again = drawSvg(uri, text, side)
                if let again = again { keep(uri, again) }
                done(again ?? p)
                return 0
            }
        }
        waiting += 1
        let id = waiting
        if let load = loads[uri] {
            load.waiters.append((id, side, done))
            return id
        }
        let load = PictureLoad()
        load.waiters.append((id, side, done))
        loads[uri] = load
        fetch(uri, load)
        return id
    }

    /// Nobody waits on `id` any longer: a load nobody waits on is given up, its download cancelled.
    static func forget(_ uri: String, _ id: Int) {
        guard id > 0, let load = loads[uri] else { return }
        load.waiters.removeAll { $0.id == id }
        if load.waiters.isEmpty {
            load.task?.cancel()
            loads.removeValue(forKey: uri)
        }
    }

    static func keep(_ uri: String, _ p: Picture) {
        guard let image = p.image else { return }
        if let held = decoded.object(forKey: uri as NSString), held.image != nil, held.side > p.side { return }
        let px = image.size.width * image.scale * image.size.height * image.scale
        decoded.setObject(p, forKey: uri as NSString, cost: Int(px) * 4)
    }

    /// The file's bytes, from memory, the network, the bundle, a file or the address itself; then `got` on the main
    /// queue.
    static func fetch(_ uri: String, _ load: PictureLoad) {
        if let f = fetched.object(forKey: uri as NSString) {
            DispatchQueue.main.async { got(uri, load, f) }
            return
        }
        if uri.hasPrefix("http://") || uri.hasPrefix("https://") {
            guard let u = URL(string: uri) else { failed(uri, load, "not an address"); return }
            var request = URLRequest(url: u)
            if !agent.isEmpty { request.setValue(agent, forHTTPHeaderField: "User-Agent") }
            let task = session.dataTask(with: request) { data, response, error in
                // given up on (`forget`): nobody waits for it
                if let e = error as NSError?, e.domain == NSURLErrorDomain, e.code == NSURLErrorCancelled { return }
                let status = (response as? HTTPURLResponse)?.statusCode ?? 200
                DispatchQueue.main.async {
                    if let error = error { failed(uri, load, error.localizedDescription) }
                    else if status < 200 || status >= 300 { failed(uri, load, "HTTP " + String(status)) }
                    else {
                        let f = Fetched(data ?? Data(), response?.mimeType ?? "")
                        fetched.setObject(f, forKey: uri as NSString, cost: f.data.count)
                        got(uri, load, f)
                    }
                }
            }
            load.task = task
            task.resume()
            return
        }
        work.async {
            var f: Fetched? = nil
            var why = ""
            if uri.hasPrefix("data:") {
                f = dataAddress(uri)
                if f == nil { why = "not a data: address that can be read" }
            } else {
                let path = uri.hasPrefix("file:") ? (URL(string: uri)?.path ?? "") : bundlePath(uri)
                if let data = FileManager.default.contents(atPath: path) { f = Fetched(data, "") }
                else {
                    why = (FileManager.default.fileExists(atPath: path) ? "could not read " : "no such file ") + path
                }
            }
            DispatchQueue.main.async {
                if let f = f {
                    fetched.setObject(f, forKey: uri as NSString, cost: f.data.count)
                    got(uri, load, f)
                } else { failed(uri, load, why) }
            }
        }
    }

    /// A `data:` address's bytes (base64, or percent-encoded) and its type.
    static func dataAddress(_ uri: String) -> Fetched? {
        guard let comma = uri.firstIndex(of: ",") else { return nil }
        let head = uri[uri.index(uri.startIndex, offsetBy: 5)..<comma].split(separator: ";").map {
            $0.trimmingCharacters(in: .whitespaces)
        }
        let body = String(uri[uri.index(after: comma)...])
        let type = head.first ?? ""
        if head.contains("base64") {
            guard let data = Data(base64Encoded: body, options: .ignoreUnknownCharacters) else { return nil }
            return Fetched(data, type)
        }
        guard let text = body.removingPercentEncoding else { return nil }
        return Fetched(Data(text.utf8), type)
    }

    /// Whether the file is an SVG: by its type, its name, or its first bytes (`<svg`, after a declaration, a comment or
    /// a DOCTYPE), as SvgImage tells one.
    static func isSvg(_ uri: String, _ f: Fetched) -> Bool {
        let lower = uri.lowercased()
        if f.type.lowercased().contains("image/svg+xml") || lower.hasPrefix("data:image/svg+xml") { return true }
        let bare = String(lower.prefix { $0 != "?" && $0 != "#" })
        if !lower.hasPrefix("data:") && bare.hasSuffix(".svg") { return true }
        let head = String(decoding: f.data.prefix(4096), as: UTF8.self)
        var at = head.startIndex
        while at < head.endIndex {
            let rest = head[at...]
            if let c = rest.first, c.isWhitespace || c == "\u{FEFF}" { at = head.index(after: at); continue }
            func past(_ mark: String) -> String.Index? {
                rest.range(of: mark).map { $0.upperBound }
            }
            if rest.hasPrefix("<?") { guard let p = past("?>") else { return false }; at = p }
            else if rest.hasPrefix("<!--") { guard let p = past("-->") else { return false }; at = p }
            else if rest.hasPrefix("<!") { guard let p = past(">") else { return false }; at = p }
            else {
                guard rest.hasPrefix("<") else { return false }
                let name = rest.dropFirst().prefix { !$0.isWhitespace && $0 != ">" && $0 != "/" }
                return name == "svg" || name.hasSuffix(":svg")
            }
        }
        return false
    }

    /// The file came: an SVG drawn by the program here, anything else decoded by ImageIO off the main thread, as large
    /// as the largest any waiter wants (only its size read where none wants pixels).
    static func got(_ uri: String, _ load: PictureLoad, _ f: Fetched) {
        guard loads[uri] === load else { return }
        let side = load.waiters.map { $0.side }.max() ?? -1
        if isSvg(uri, f) {
            let p = drawSvg(uri, String(decoding: f.data, as: UTF8.self), side)
            if p == nil { tell(uri, "") }
            finish(uri, load, p)
            return
        }
        work.async {
            let p = raster(f.data, side)
            DispatchQueue.main.async {
                if p == nil { tell(uri, "") }
                finish(uri, load, p)
            }
        }
    }

    /// `data` decoded with ImageIO: its natural size from its header (turned as its orientation says), and where
    /// `side` asks for pixels, a picture no larger than that on its longer side (0: its whole size), decoded here and
    /// now rather than as it is first drawn. Nil where ImageIO reads no picture in it. On any thread.
    static func raster(_ data: Data, _ side: Double) -> Picture? {
        let reading = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, reading),
              CGImageSourceGetCount(source) > 0,
              let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              var w = (props[kCGImagePropertyPixelWidth] as? NSNumber)?.doubleValue,
              var h = (props[kCGImagePropertyPixelHeight] as? NSNumber)?.doubleValue, w > 0, h > 0 else { return nil }
        if ((props[kCGImagePropertyOrientation] as? NSNumber)?.intValue ?? 1) >= 5 { swap(&w, &h) }
        if side < 0 { return Picture(nil, w, h, 0, false, nil) }
        let most = min(max(w, h), largest)
        let wanted = side > 0 ? min(side, most) : most
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: Int(wanted.rounded(.up)),
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        let made = Double(max(cg.width, cg.height))
        return Picture(UIImage(cgImage: cg), w, h, made, made >= most - 1.0, nil)
    }

    /// An SVG drawn by the program (AppleArtPen) at `side` pixels on its longer side, and at least its own size times
    /// the screen's scale; nil where it holds no SVG it can draw. On the main queue.
    static func drawSvg(_ uri: String, _ text: String, _ side: Double) -> Picture? {
        guard rtIsFn(svg) else { return nil }
        canvas = nil
        _ = RtGuard("an SVG", svg, [.vStr(uri), .vStr(text), .vDbl(max(0, side))])
        guard let c = canvas, let image = c.picture() else { canvas = nil; return nil }
        canvas = nil
        return Picture(image, c.naturalWidth, c.naturalHeight, Double(max(c.width, c.height)),
                       Double(max(c.width, c.height)) >= largest - 1.0, text)
    }

    /// Everyone waiting on `uri` told, and the picture kept.
    static func finish(_ uri: String, _ load: PictureLoad, _ p: Picture?) {
        guard loads[uri] === load else { return }
        loads.removeValue(forKey: uri)
        if let p = p { keep(uri, p) }
        if logged, let p = p {
            let shown = uri.hasPrefix("data:") ? "data:" : String(uri.prefix { $0 != "?" && $0 != "#" })
            print("[ui] picture " + shown + " natural=" + String(Int(p.width)) + "x" + String(Int(p.height)) +
                  " decoded=" + String(Int(p.side)) + (p.svg != nil ? " svg" : ""))
        }
        for w in load.waiters { w.done(p) }
    }

    static func failed(_ uri: String, _ load: PictureLoad, _ why: String) {
        guard loads[uri] === load else { return }
        tell(uri, why.isEmpty ? "it could not be had" : why)
        finish(uri, load, nil)
    }

    /// The program told (it logs each address once, as LoggedAddress shows it).
    static func tell(_ uri: String, _ why: String) {
        if rtIsFn(failed) { _ = RtGuard("a picture's failure", failed, [.vStr(uri), .vStr(why)]) }
        else { print("[ui] picture: " + why) }
    }
}

/// An image node, stretched to its box.
func uiImage(_ uri: V, _ width: V, _ height: V) -> V {
    let iv = PictureView()
    iv.contentMode = .scaleToFill
    place(iv, Geo(w: dbl(width), h: dbl(height)))
    let v: V = .vRef(iv)
    _ = uiSetImageSource(v, uri)
    return v
}

/// The picture the view draws, or none ("", which lets the one it drew go). What it showed is gone at once, so another
/// address's picture never stands in; one already decoded large enough shows at once (a poster shown again); else it
/// is asked for once the turn is over, when the view has the size it is drawn at.
func uiSetImageSource(_ node: V, _ uri: V) -> V {
    guard let iv = view(node) as? PictureView else { return .vNull }
    let s = rtS(uri)
    if s == iv.uri { return .vNull }
    Pictures.forget(iv.uri, iv.waiter)
    iv.uri = s
    iv.ticket += 1
    iv.waiter = 0
    iv.askedAgain = false
    iv.decoded = nil
    iv.blurredFor = ""
    iv.image = nil
    if s.isEmpty { return .vNull }
    // (the same address decoded smaller stands in until it comes larger)
    if let p = Pictures.decoded.object(forKey: s as NSString) {
        showPicture(iv, p)
        if Pictures.enough(p, pictureSide(iv)) { return .vNull }
    }
    let ticket = iv.ticket
    DispatchQueue.main.async { [weak iv] in
        if let iv = iv, iv.ticket == ticket { askPicture(iv) }
    }
    return .vNull
}

/// The longer side, in the screen's pixels, the view is drawn at; 0 where it has no size yet (the picture's whole
/// size).
func pictureSide(_ v: UIView) -> Double {
    let g = geo(v)
    let longest = max(g.w, g.h) * max(1.0, g.scale)
    return longest <= 0 ? 0 : (longest * Double(UIScreen.main.scale) * UI.scale).rounded(.up)
}

/// The view's picture asked for at the size it is drawn at.
func askPicture(_ iv: PictureView) {
    let side = pictureSide(iv)
    if let p = iv.decoded, Pictures.enough(p, side) { return }
    let ticket = iv.ticket
    Pictures.forget(iv.uri, iv.waiter)
    iv.waiter = Pictures.want(iv.uri, side) { [weak iv] p in
        guard let iv = iv, iv.ticket == ticket else { return }
        iv.waiter = 0
        guard let p = p, p.image != nil else { return }
        showPicture(iv, p)
        // decoded for a waiter that wanted less, or before the view grew: once more, at its size
        if !Pictures.enough(p, pictureSide(iv)) && !iv.askedAgain {
            iv.askedAgain = true
            DispatchQueue.main.async { if iv.ticket == ticket { askPicture(iv) } }
        }
    }
}

/// Resized (place): a picture decoded smaller than it is drawn now is asked for again, once the turn is over.
func pictureResized(_ iv: PictureView) {
    guard !iv.uri.isEmpty, !iv.looking, let p = iv.decoded, !Pictures.enough(p, pictureSide(iv)) else { return }
    iv.looking = true
    let ticket = iv.ticket
    DispatchQueue.main.async { [weak iv] in
        guard let iv = iv else { return }
        iv.looking = false
        if iv.ticket == ticket { askPicture(iv) }
    }
}

/// The decoded picture drawn with the view's tint and blur.
func showPicture(_ iv: PictureView, _ p: Picture) {
    iv.decoded = p
    guard let image = p.image else { iv.image = nil; return }
    if iv.blur <= 0 {
        iv.blurredFor = ""
        tinted(iv, image)
        return
    }
    // blurred off the main thread: until it is, what it showed stays (nothing, for a new address)
    let key = String(UInt(bitPattern: ObjectIdentifier(image).hashValue)) + "|" + String(iv.blur)
    if iv.blurredFor == key { return }
    iv.blurredFor = key
    let ticket = iv.ticket
    let g = geo(iv)
    // the blur is in the app's units: in the picture's pixels it is as much larger as the picture is to the view
    let perUnit = g.w > 0 ? Double(image.size.width * image.scale) / g.w : Double(UIScreen.main.scale) * UI.scale
    let sigma = iv.blur * perUnit
    Pictures.blurring.async {
        let made = blurred(image, sigma)
        DispatchQueue.main.async {
            if iv.ticket == ticket && iv.blurredFor == key, let made = made { tinted(iv, made) }
        }
    }
}

func tinted(_ iv: PictureView, _ image: UIImage) {
    if let tint = iv.tint {
        iv.tintColor = tint
        iv.image = image.withRenderingMode(.alwaysTemplate)
    } else {
        iv.image = image.withRenderingMode(.alwaysOriginal)
    }
}

/// `image` blurred by a gaussian of deviation `sigma` pixels, its edges held: made smaller first where the blur is wide
/// (a blur that wide leaves nothing the smaller picture lacks), so a backdrop blurs in a few milliseconds. On any
/// thread.
func blurred(_ image: UIImage, _ sigma: Double) -> UIImage? {
    guard let cg = image.cgImage else { return nil }
    var input = CIImage(cgImage: cg)
    let shrink = sigma > 8 ? 8 / sigma : 1.0
    if shrink < 1 { input = input.transformed(by: CGAffineTransform(scaleX: CGFloat(shrink), y: CGFloat(shrink))) }
    let extent = input.extent.integral
    guard let f = CIFilter(name: "CIGaussianBlur") else { return nil }
    f.setValue(input.clampedToExtent(), forKey: kCIInputImageKey)
    f.setValue(sigma * shrink, forKey: kCIInputRadiusKey)
    guard let out = f.outputImage?.cropped(to: extent),
          let made = Pictures.effects.createCGImage(out, from: extent) else { return nil }
    return UIImage(cgImage: made)
}

/// The picture in one colour (`"#rrggbb"`), its transparency kept, or as it is for "".
func uiSetTint(_ node: V, _ hex: V) -> V {
    guard let iv = view(node) as? PictureView else { return .vNull }
    let s = rtS(hex)
    iv.tint = s.isEmpty ? nil : uiColor(s)
    if let p = iv.decoded { iv.blurredFor = ""; showPicture(iv, p) }
    return .vNull
}

/// A gaussian blur of deviation `radius` (the app's units) over an image node's picture; anything else is drawn sharp.
func uiSetBlur(_ node: V, _ radius: V) -> V {
    guard let iv = view(node) as? PictureView else { return .vNull }
    let r = max(0, dbl(radius))
    if r == iv.blur { return .vNull }
    iv.blur = r
    if let p = iv.decoded { showPicture(iv, p) }
    return .vNull
}

/// Decode `uri` and report its natural size in pixels (an SVG's own size), 0 x 0 where it cannot be had or decoded
/// (said once, by the program's hook). Only the header is read for it; the view decodes the picture at its size.
func uiLoadImage(_ uri: V, _ loaded: V) throws -> V {
    let s = rtS(uri)
    if s.isEmpty { _ = try RtInvoke(loaded, rtArr([.vDbl(0), .vDbl(0)])); return .vNull }
    let held = RtHeld.hold(loaded)
    Pictures.want(s, -1) { p in
        RtHeld.release(held)
        RtGuard("an image's size", loaded, [.vDbl(p?.width ?? 0), .vDbl(p?.height ?? 0)])
    }
    return .vNull
}

/// The program's SVG painter and its word on a picture that would not load, held for as long as the app runs.
func uiPictureHooks(_ svg: V, _ failed: V) -> V {
    Pictures.svg = svg
    Pictures.failed = failed
    return .vNull
}

/// The User-Agent pictures are fetched with (Platform.identify).
func uiPictureAgent(_ agent: V) -> V {
    Pictures.agent = rtS(agent)
    return .vNull
}

/// Where an SVG is drawn (AppleArtPen): a bitmap context `width` by `height` pixels, clear, y down as the art has it,
/// with the path being made.
final class ArtCanvas {
    let context: CGContext?
    let width: Int
    let height: Int
    let naturalWidth: Double
    let naturalHeight: Double
    var path = CGMutablePath()
    init(_ width: Int, _ height: Int, _ naturalWidth: Double, _ naturalHeight: Double) {
        self.width = max(1, width); self.height = max(1, height)
        self.naturalWidth = naturalWidth; self.naturalHeight = naturalHeight
        context = CGContext(data: nil, width: self.width, height: self.height, bitsPerComponent: 8, bytesPerRow: 0,
                            space: CGColorSpaceCreateDeviceRGB(),
                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        context?.translateBy(x: 0, y: CGFloat(self.height))
        context?.scaleBy(x: 1, y: -1)
    }
    func picture() -> UIImage? { context?.makeImage().map { UIImage(cgImage: $0) } }
}

func artColor(_ r: V, _ g: V, _ b: V, _ a: V) -> CGColor {
    CGColor(srgbRed: CGFloat(dbl(r) / 255.0), green: CGFloat(dbl(g) / 255.0), blue: CGFloat(dbl(b) / 255.0),
            alpha: CGFloat(dbl(a)))
}

/// A canvas `width` by `height` pixels for the SVG being drawn, whose own size is `naturalWidth` by `naturalHeight`.
func uiArtBegin(_ width: V, _ height: V, _ naturalWidth: V, _ naturalHeight: V) -> V {
    Pictures.canvas = ArtCanvas(Int(dbl(width)), Int(dbl(height)), dbl(naturalWidth), dbl(naturalHeight))
    return .vNull
}
func uiArtMove(_ x: V, _ y: V) -> V {
    Pictures.canvas?.path.move(to: CGPoint(x: dbl(x), y: dbl(y)))
    return .vNull
}
func uiArtLine(_ x: V, _ y: V) -> V {
    Pictures.canvas?.path.addLine(to: CGPoint(x: dbl(x), y: dbl(y)))
    return .vNull
}
func uiArtCurve(_ x1: V, _ y1: V, _ x2: V, _ y2: V, _ x: V, _ y: V) -> V {
    Pictures.canvas?.path.addCurve(to: CGPoint(x: dbl(x), y: dbl(y)), control1: CGPoint(x: dbl(x1), y: dbl(y1)),
                                   control2: CGPoint(x: dbl(x2), y: dbl(y2)))
    return .vNull
}
func uiArtClose() -> V {
    Pictures.canvas?.path.closeSubpath()
    return .vNull
}
/// The path made so far filled in `r g b` (0 to 255) at `a` (0 to 1), by the even-odd rule or the non-zero one.
func uiArtFill(_ r: V, _ g: V, _ b: V, _ a: V, _ evenOdd: V) -> V {
    guard let c = Pictures.canvas, let cx = c.context else { return .vNull }
    cx.addPath(c.path)
    cx.setFillColor(artColor(r, g, b, a))
    cx.fillPath(using: rtB(evenOdd) ? .evenOdd : .winding)
    c.path = CGMutablePath()
    return .vNull
}
/// The path made so far outlined, `width` pixels wide, its ends `cap` and its corners `join` (SVG's names).
func uiArtStroke(_ r: V, _ g: V, _ b: V, _ a: V, _ width: V, _ cap: V, _ join: V) -> V {
    guard let c = Pictures.canvas, let cx = c.context else { return .vNull }
    cx.addPath(c.path)
    cx.setStrokeColor(artColor(r, g, b, a))
    cx.setLineWidth(CGFloat(dbl(width)))
    cx.setLineCap(rtS(cap) == "round" ? .round : rtS(cap) == "square" ? .square : .butt)
    cx.setLineJoin(rtS(join) == "round" ? .round : rtS(join) == "bevel" ? .bevel : .miter)
    cx.setMiterLimit(4)
    cx.strokePath()
    c.path = CGMutablePath()
    return .vNull
}

// MARK: - platform

/// The root's size: the screen's, less the safe area on an iPhone or an iPad (RootViewController places the root
/// inside it), or the `--viewport` asked for.
func uiScreenWidth() -> V { .vDbl(Double((UI.viewport ?? uiRootSize()).width)) }
func uiScreenHeight() -> V { .vDbl(Double((UI.viewport ?? uiRootSize()).height)) }
func uiRootSize() -> CGSize {
    if let root = UI.root, root.bounds.width > 0 { return root.bounds.size }
    return UIScreen.main.bounds.size
}

func uiLog(_ message: V) -> V {
    print("[ui] " + rtS(message))
    return .vNull
}

/// Milliseconds on a monotonic clock — the animation clock.
func uiTicks() -> V { .vDbl(CACurrentMediaTime() * 1000.0) }

/// `f()` once `seconds` have passed, on the main queue (Platform.after), held for the collector until then.
func uiAfter(_ seconds: V, _ f: V) -> V {
    let held = RtHeld.hold(f)
    DispatchQueue.main.asyncAfter(deadline: .now() + max(0.0, dbl(seconds))) {
        RtHeld.release(held)
        RtGuard("a timer", f, [])
    }
    return .vNull
}

func uiFreezeSeconds() -> V { .vDbl(UI.freeze) }

/// Whether Reduce Motion is on in Accessibility.
func uiReduceMotion() -> V { .vBool(UIAccessibility.isReduceMotionEnabled) }

final class FrameDriver {
    @objc func frame(_ link: CADisplayLink) {
        RtGuard("a frame", UI.tick, [])
        // Between frames, with no emitted code on the stack: collect the cycles the frames left behind — once
        // the allocations since the last pass are worth a walk of the live graph (a quarter of it, at least
        // 2,000), so a steady-state frame that allocates little costs nothing here.
        UI.frames += 1
        UI.allocs += Rt.allocatedTotal - UI.allocsAt
        UI.allocsAt = Rt.allocatedTotal
        if Rt.allocated >= max(2000, Rt.live / 4) {
            let t = CACurrentMediaTime()
            rtCollectCycles(RtHeld.roots(uiHooks()))
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
    /// Closed from this side (uiSocketClose): its end is a normal closure, whatever URLSession says of the cancel.
    var closing = false
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
        RtGuard("a socket", b.onOpen, [])
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
                    RtGuard("a socket", b.onText, [.vStr(s)])
                    self.receive(b)
                case .success(.data(let d)):
                    RtGuard("a socket", b.onText, [.vStr(String(data: d, encoding: .utf8) ?? "")])
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
        if b.closing { RtGuard("a socket", b.onClose, [.vInt(1000), .vStr("closed")]) }
        else { RtGuard("a socket", b.onClose, [.vInt(code), .vStr(reason)]) }
    }
}

func uiSocketOpen(_ url: V, _ onOpen: V, _ onText: V, _ onClose: V) -> V {
    guard let u = URL(string: rtS(url)) else {
        RtGuard("a socket", onClose, [.vInt(1006), .vStr("bad url: " + rtS(url))])
        return .vNull
    }
    let task = SocketDelegate.session.webSocketTask(with: u)
    // a message as large as a server sends, where URLSession would refuse one past a megabyte and drop the line
    task.maximumMessageSize = 64 * 1024 * 1024
    let box = SocketBox(task, onOpen, onText, onClose)
    SocketDelegate.shared.boxes[ObjectIdentifier(task)] = box
    task.resume()
    return .vRef(box)
}

func uiSocketSend(_ h: V, _ text: V) -> V {
    // a send that fails has lost the line: closed, as a read that fails closes it, so the durable client reconnects
    if case .vRef(let o) = h, let b = o as? SocketBox, !b.finished {
        b.task.send(.string(rtS(text))) { error in
            guard let error = error else { return }
            DispatchQueue.main.async { SocketDelegate.shared.finish(b, 1006, error.localizedDescription) }
        }
    }
    return .vNull
}

func uiSocketClose(_ h: V) -> V {
    if case .vRef(let o) = h, let b = o as? SocketBox, !b.finished {
        b.closing = true
        b.task.cancel(with: .normalClosure, reason: nil)
    }
    return .vNull
}

/// One request through URLSession (uiHttp, uiHttpBytes), then `answer(status, headersJson, data, why)` on the main
/// queue: the status the server gave with its headers (a JSON object of names to values) and its body, or -1 and why
/// where no answer came (no connection, no such host, a refused certificate, App Transport Security, the time up),
/// which the network module fails the task with. URLSession follows redirects and gives up after a minute without a
/// byte (`timeoutInterval`), as Spice's client keeps its minute elsewhere.
func uiRequest(_ method: V, _ url: V, _ headersJson: V, _ body: Data?,
               _ answer: @escaping (Int, String, Data?, String) -> Void) {
    guard let u = URL(string: rtS(url)), u.scheme != nil else {
        answer(-1, "{}", nil, "not an address: " + rtS(url))
        return
    }
    var request = URLRequest(url: u)
    request.httpMethod = rtS(method)
    request.timeoutInterval = 60
    if let data = rtS(headersJson).data(using: .utf8),
       let headers = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
        for (k, v) in headers { request.setValue("\(v)", forHTTPHeaderField: k) }
    }
    request.httpBody = body
    URLSession.shared.dataTask(with: request) { data, response, error in
        DispatchQueue.main.async {
            guard error == nil, let http = response as? HTTPURLResponse else {
                answer(-1, "{}", nil, error?.localizedDescription ?? "no answer")
                return
            }
            var headers: [String: String] = [:]
            http.allHeaderFields.forEach { headers["\($0.key)"] = "\($0.value)" }
            let json = (try? JSONSerialization.data(withJSONObject: headers)).map {
                String(decoding: $0, as: UTF8.self)
            }
            answer(http.statusCode, json ?? "{}", data, "")
        }
    }.resume()
}

/// `done(status, headersJson, body)`: the body as UTF-8 text, or why where no answer came (status -1). `done` is held
/// for the collector while the request is under way.
func uiHttp(_ method: V, _ url: V, _ headersJson: V, _ body: V, _ hasBody: V, _ done: V) -> V {
    let held = RtHeld.hold(done)
    uiRequest(method, url, headersJson, hasBody.rtTrue ? Data(rtS(body).utf8) : nil) { status, headers, data, why in
        RtHeld.release(held)
        let text = status < 0 ? why : String(decoding: data ?? Data(), as: UTF8.self)
        RtGuard("a request", done, [.vInt(status), .vStr(headers), .vStr(text)])
    }
    return .vNull
}

/// The same with a body of bytes (`hasBody`), or an answer wanted as bytes (`binary`) as well as text:
/// `done(status, headersJson, text, bytes)`, a Scala `Array[Byte]`'s numbers from -128 to 127.
func uiHttpBytes(_ method: V, _ url: V, _ headersJson: V, _ body: V, _ hasBody: V, _ binary: V, _ done: V) -> V {
    let sent = hasBody.rtTrue ? Data(rtItems(body).map { UInt8(truncatingIfNeeded: Int(rtDouble($0))) }) : nil
    let wanted = binary.rtTrue
    let held = RtHeld.hold(done)
    uiRequest(method, url, headersJson, sent) { status, headers, data, why in
        RtHeld.release(held)
        let text = status < 0 ? why : String(decoding: data ?? Data(), as: UTF8.self)
        let bytes: [V] = wanted ? (data.map { d in d.map { V.vInt(Int(Int8(bitPattern: $0))) } } ?? []) : []
        RtGuard("a request", done, [.vInt(status), .vStr(headers), .vStr(text), rtArr(bytes)])
    }
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

// MARK: - keys: the remote's presses, and a keyboard's, to one hook (everyui.focus.Focus)

func uiOnKey(_ f: V) -> V {
    UI.key = f
    return .vNull
}

/// RootViewController's presses, by the web's names. On an Apple TV the remote's: Menu is Back, and tvOS's (leaving the
/// app) only when the app does not take it. On an iPhone or an iPad a keyboard's (a hardware one, or the Mac's on the
/// simulator), by the key: Escape is Escape there, and Return is Enter. The emitted program's closures are objects
/// (rtIsFn), so the hook is asked for as one.
func uiKeyEvent(_ press: UIPress, _ pressed: Bool) -> Bool {
    guard rtIsFn(UI.key) else { return false }
    var name = ""
    if let key = press.key {
        let flags = key.modifierFlags
        UI.modifiers = (flags.contains(.control), flags.contains(.command), flags.contains(.alternate),
                        flags.contains(.shift))
    }
    #if os(tvOS)
    name = uiRemoteName(press)
    if name.isEmpty, let key = press.key { name = uiKeyName(key) }
    #else
    if let key = press.key { name = uiKeyName(key) }
    if name.isEmpty { name = uiRemoteName(press) }
    #endif
    if name.isEmpty { return false }
    return uiKey(name, pressed)
}

/// A remote's press by the web's name, or "".
func uiRemoteName(_ press: UIPress) -> String {
    switch press.type {
    case .select: return "Enter"
    case .upArrow: return "ArrowUp"
    case .downArrow: return "ArrowDown"
    case .leftArrow: return "ArrowLeft"
    case .rightArrow: return "ArrowRight"
    case .playPause: return "MediaPlayPause"
    case .menu: return "Back"
    default: return ""
    }
}

/// A keyboard's key by the web's name (KeyboardEvent.key): the named keys by their names, any other by what it types.
func uiKeyName(_ key: UIKey) -> String {
    switch key.keyCode {
    case .keyboardUpArrow: return "ArrowUp"
    case .keyboardDownArrow: return "ArrowDown"
    case .keyboardLeftArrow: return "ArrowLeft"
    case .keyboardRightArrow: return "ArrowRight"
    case .keyboardReturnOrEnter, .keypadEnter: return "Enter"
    case .keyboardEscape: return "Escape"
    case .keyboardTab: return "Tab"
    case .keyboardDeleteOrBackspace: return "Backspace"
    case .keyboardDeleteForward: return "Delete"
    case .keyboardSpacebar: return " "
    case .keyboardHome: return "Home"
    case .keyboardEnd: return "End"
    case .keyboardPageUp: return "PageUp"
    case .keyboardPageDown: return "PageDown"
    case .keyboardLeftShift, .keyboardRightShift: return "Shift"
    case .keyboardLeftControl, .keyboardRightControl: return "Control"
    case .keyboardLeftAlt, .keyboardRightAlt: return "Alt"
    case .keyboardLeftGUI, .keyboardRightGUI: return "Meta"
    default:
        let typed = key.characters.isEmpty ? key.charactersIgnoringModifiers : key.characters
        let control = typed.unicodeScalars.first.map { CharacterSet.controlCharacters.contains($0) } ?? true
        return typed.count == 1 && !control ? typed : ""
    }
}

/// A key pressed or released, by the web's name, told to the program: whether it took it.
@discardableResult
func uiKey(_ name: String, _ pressed: Bool) -> Bool {
    guard rtIsFn(UI.key) else { return false }
    if case .vBool(let taken) = RtGuard("a key", UI.key, [.vStr(name), .vBool(pressed)]) { return taken }
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
    /// Extra space after each letter (`uiSetLetterSpacing`), each wrapped line this far below the last (0: the face's
    /// own), where its lines sit ("start", "center", "end") and which way its words run ("" the app's, "auto", "ltr",
    /// "rtl"), and whether it wraps at its width. Where any is not as it starts the words are attributed text, which a
    /// font or a colour set later is applied over, keeping them.
    var kern: CGFloat = 0 { didSet { styled(text) } }
    var advance: CGFloat = 0 { didSet { styled(text) } }
    var align = "start" { didSet { styled(text) } }
    var direction = "" { didSet { styled(text) } }
    var wraps = false { didSet { styled(text) } }
    override var text: String? {
        get { super.text }
        set { styled(newValue) }
    }
    /// Every label made, held weakly: the app's direction changes the side each starts on (uiSetDirection).
    static let all = NSHashTable<OutlinedLabel>.weakObjects()
    override init(frame: CGRect) {
        super.init(frame: frame)
        OutlinedLabel.all.add(self)
    }
    required init?(coder: NSCoder) { super.init(coder: coder) }

    func styled(_ words: String?) {
        let rtl = uiRightToLeft(direction, words ?? "")
        let side: NSTextAlignment = align == "center" ? .center : ((align == "end") != rtl ? .right : .left)
        let breaking: NSLineBreakMode = wraps ? .byWordWrapping : .byClipping
        textAlignment = side
        lineBreakMode = breaking
        if kern == 0 && advance == 0 && !rtl && direction.isEmpty { super.text = words; return }
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = side
        paragraph.lineBreakMode = breaking
        paragraph.baseWritingDirection = rtl ? .rightToLeft : .leftToRight
        if advance > 0 { paragraph.minimumLineHeight = advance; paragraph.maximumLineHeight = advance }
        super.attributedText = NSAttributedString(string: words ?? "", attributes: [.kern: kern, .font: font as Any,
            .foregroundColor: textColor as Any, .paragraphStyle: paragraph])
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
    // `done` held for the collector while the dialog is up, and the dialog known to the drive (uiDriveRun) till then
    let held = RtHeld.hold(done)
    let submit: (Bool) -> Void = { taken in
        RtHeld.release(held)
        UI.editing = nil
        if taken { RtGuard("a text dialog", done, [.vStr(alert.textFields?.first?.text ?? "")]) }
    }
    alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { _ in submit(false) })
    let ok = UIAlertAction(title: "Done", style: .default) { _ in submit(true) }
    alert.addAction(ok)
    alert.preferredAction = ok
    UI.editing = (alert, submit)
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
var uiCaptionHeld: [Int: Int] = [:]   // each listener's closure, held for the collector (RtHeld)

/// `changed()` each time the viewer changes the setting, until uiCaptionsUnlisten(the id answered).
func uiCaptionsListen(_ changed: V) -> V {
    uiCaptionNext += 1
    let name = Notification.Name(kMACaptionAppearanceSettingsChangedNotification as String)
    uiCaptionObservers[uiCaptionNext] = NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { _ in
        RtGuard("the caption settings", changed, [])
    }
    uiCaptionHeld[uiCaptionNext] = RtHeld.hold(changed)
    return .vInt(uiCaptionNext)
}

func uiCaptionsUnlisten(_ id: V) -> V {
    guard case .vInt(let key) = rtToInt(id), let observer = uiCaptionObservers.removeValue(forKey: key) else { return .vNull }
    NotificationCenter.default.removeObserver(observer)
    if let held = uiCaptionHeld.removeValue(forKey: key) { RtHeld.release(held) }
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
    /// Every page alive, whose handlers are roots of the collector (uiHooks) while it is.
    static let live = NSHashTable<WebPage>.weakObjects()

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
        WebPage.live.add(self)
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
                    RtGuard("a web page", replied, [.vInt(id), .vStr(reply["reply"].map(webText) ?? "")])
                }
                return
            }
        }
        RtGuard("a web page", heard, [.vStr(message)])
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

/// Each live page's handlers, roots of the collector (uiHooks).
func uiWebRoots() -> [V] { WebPage.live.allObjects.flatMap { [$0.heard, $0.replied] } }

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
func uiWebRoots() -> [V] { [] }
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
    _ = RtHeld.hold(resumed)   // held for the collector for good, as the observers are
    _ = RtHeld.hold(away)
    let centre = NotificationCenter.default
    for name in [UIApplication.willResignActiveNotification, UIApplication.didEnterBackgroundNotification] {
        uiLifecycleObservers.append(centre.addObserver(forName: name, object: nil, queue: .main) { _ in
            RtGuard("the lifecycle", away, [])
        })
    }
    uiLifecycleObservers.append(centre.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil,
                                                   queue: .main) { _ in
        RtGuard("the lifecycle", resumed, [])
    })
    return .vNull
}

// MARK: - the device: which it is, what it has, and what the app's bundle says of itself

/// What the app is running on, as UIKit's idiom has it: "tv" (an Apple TV), "phone", "pad", "mac" (an iPad app on a
/// Mac), "vision", or "car". One emitted app serves them all, so this is asked as it runs.
func uiIdiom() -> V {
    #if os(tvOS)
    return .vStr("tv")
    #else
    switch UIDevice.current.userInterfaceIdiom {
    case .tv: return .vStr("tv")
    case .pad: return .vStr("pad")
    case .mac: return .vStr("mac")
    case .carPlay: return .vStr("car")
    case .vision: return .vStr("vision")
    default: return .vStr("phone")
    }
    #endif
}

/// What the device calls itself (UIDevice's name: "iPhone", "iPad", "Apple TV", the owner's own name for it where the
/// system lets an app read that), else its model.
func uiDeviceName() -> V {
    let name = UIDevice.current.name.trimmingCharacters(in: .whitespaces)
    return .vStr(name.isEmpty ? UIDevice.current.model : name)
}

/// The hardware's own name for itself ("iPhone18,1", "AppleTV14,1"): on the simulator the device it stands for.
func uiDeviceModel() -> V {
    if let simulated = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] { return .vStr(simulated) }
    var info = utsname()
    uname(&info)
    let machine = withUnsafeBytes(of: &info.machine) { raw in
        String(decoding: raw.prefix { $0 != 0 }, as: UTF8.self)
    }
    return .vStr(machine.isEmpty ? UIDevice.current.model : machine)
}

/// The build's number (CFBundleVersion, Android's versionCode: its first whole number, 0 where it has none) and its
/// release (CFBundleShortVersionString, Android's versionName), as AppleApp.version wrote them into the Info.plist.
func uiAppVersion() -> V {
    let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""
    return .vInt(Int(build.prefix { $0.isNumber }) ?? 0)
}
func uiAppVersionName() -> V {
    .vStr(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")
}

/// The language the person reads, first of those they prefer, as a BCP 47 tag ("en-US", "pt-BR").
func uiLanguage() -> V { .vStr(Locale.preferredLanguages.first ?? "en") }

/// The system's light or dark ("light", "dark"), the screen's (an app's own Info.plist style does not change it), and
/// `f` told each as it changes (RootViewController's traits).
func uiColorScheme() -> V {
    .vStr(UIScreen.main.traitCollection.userInterfaceStyle == .dark ? "dark" : "light")
}
func uiOnColorScheme(_ f: V) -> V {
    UI.schemes.append(f)
    return .vNull
}

/// Screen pixels to a point (2 or 3 on an iPhone, 1 on an Apple TV at 1080p).
func uiScreenScale() -> V { .vDbl(Double(UIScreen.main.scale)) }

/// The safe area the root is placed inside, in points, at one edge: 0 top, 1 right, 2 bottom, 3 left.
func uiSafeInset(_ edge: V) -> V {
    let s = UI.safe
    let at: [CGFloat] = [s.top, s.right, s.bottom, s.left]
    return .vDbl(Double(at[max(0, min(3, Int(dbl(edge))))]))
}

/// `f()` each time the root changes size: a rotation, a split view or Slide Over on an iPad, a window resized.
func uiOnResize(_ f: V) -> V {
    UI.resized.append(f)
    return .vNull
}

/// The root resized (RootViewController.viewDidLayoutSubviews): each listener told.
func uiResized() {
    for f in UI.resized { RtGuard("a resize", f, []) }
}

/// Whether a keyboard is attached (a hardware one, an iPad's Magic Keyboard), or a mouse or a trackpad.
func uiHasKeyboard() -> V {
    if #available(iOS 14.0, tvOS 14.0, *) { return .vBool(GCKeyboard.coalesced != nil) }
    return .vBool(false)
}
func uiHasPointer() -> V {
    if #available(iOS 14.0, tvOS 14.0, *) { return .vBool(GCMouse.current != nil) }
    return .vBool(false)
}

/// The event being reported: one of its modifiers (0 ctrl, 1 meta, 2 alt, 3 shift), whether a finger made it, its
/// button.
func uiModifier(_ which: V) -> V {
    let m = UI.modifiers
    return .vBool([m.0, m.1, m.2, m.3][max(0, min(3, Int(dbl(which))))])
}
func uiPointerFinger() -> V { .vBool(UI.finger) }
func uiPointerButton() -> V { .vInt(UI.button) }

// MARK: - the system's services: the clipboard, links, the text size, the screen kept on, a screen reader, settings

/// `text` on the general pasteboard, in place of what was there, with `html` beside it where there is any: an app that
/// takes styled words pastes the HTML, any other the text. An Apple TV has no pasteboard, so nothing.
func uiClipboardSet(_ text: V, _ html: V) -> V {
    #if os(iOS)
    var item: [String: Any] = ["public.utf8-plain-text": rtS(text)]
    let h = rtS(html)
    if !h.isEmpty { item["public.html"] = h }
    UIPasteboard.general.items = [item]
    #endif
    return .vNull
}
/// The pasteboard's text, "" where it holds none (iOS asks the person first where another app put it there). An
/// Apple TV has none.
func uiClipboardGet() -> V {
    #if os(iOS)
    return .vStr(UIPasteboard.general.string ?? "")
    #else
    return .vStr("")
    #endif
}

/// `url` opened by the system: the browser, or the app that owns the address. Where nothing opens it (an Apple TV has
/// no browser, so a web address opens nothing there) it is logged and nothing happens, as a link that cannot open
/// does nothing elsewhere.
func uiOpenUrl(_ url: V) -> V {
    let s = rtS(url)
    guard let u = URL(string: s), u.scheme != nil else {
        print("[ui] openUrl: not an address: " + s)
        return .vNull
    }
    UIApplication.shared.open(u, options: [:]) { opened in
        print("[ui] openUrl: " + (opened ? "opened " : "nothing opens ") + s)
    }
    return .vNull
}

/// The text size the person chose (Display & Text Size, Larger Text: Dynamic Type) as a scale of the one an iPhone
/// comes with: body text's size at their size over its size at the default, Large (17 points), so 19/17 at Extra Large
/// and up to 53/17 at the largest accessibility size, as Android's font scale is told. 1 on an Apple TV, whose text
/// size is not followed.
func uiTextScale() -> V {
    #if os(iOS)
    let at = UITraitCollection(preferredContentSizeCategory: UIApplication.shared.preferredContentSizeCategory)
    return .vDbl(Double(UIFontMetrics(forTextStyle: .body).scaledValue(for: 17.0, compatibleWith: at) / 17.0))
    #else
    return .vDbl(1.0)
    #endif
}
/// `f(scale)` each time the person changes the text size, on the main queue, held for the collector for good as the
/// observer is.
func uiOnTextScale(_ f: V) -> V {
    #if os(iOS)
    _ = RtHeld.hold(f)
    UI.textScaleObservers.append(NotificationCenter.default.addObserver(
        forName: UIContentSizeCategory.didChangeNotification, object: nil, queue: .main) { _ in
        RtGuard("the text size", f, [uiTextScale()])
    })
    #endif
    return .vNull
}

/// The screen kept on (something is being watched without a touch), or let dim and lock as the system's idle timer
/// says: the idle timer off and on again.
func uiKeepAwake(_ on: V) -> V {
    UIApplication.shared.isIdleTimerDisabled = on.rtTrue
    return .vNull
}

/// `words` read out by VoiceOver when it next can, where it is on (a new message in a chat); nothing where it is off.
func uiAnnounce(_ words: V) -> V {
    if UIAccessibility.isVoiceOverRunning {
        UIAccessibility.post(notification: .announcement, argument: rtS(words))
    }
    return .vNull
}

/// A value kept across launches (Platform.setting), in the app's UserDefaults under `everyui.<name>`, or "" when there
/// is none; "" kept forgets it.
func uiSettingGet(_ name: V) -> V {
    .vStr(UserDefaults.standard.string(forKey: "everyui." + rtS(name)) ?? "")
}
func uiSettingPut(_ name: V, _ value: V) -> V {
    let key = "everyui." + rtS(name), v = rtS(value)
    if v.isEmpty { UserDefaults.standard.removeObject(forKey: key) } else { UserDefaults.standard.set(v, forKey: key) }
    return .vNull
}

/// A value that must not be readable off the device (a session's token), kept across launches in the Keychain as a
/// generic password of the app's (its bundle id the service, `name` the account), readable once the device has been
/// unlocked after a restart and never moved to another device; "" where there is none, and "" kept forgets it. An app
/// the Keychain refuses (signed with no entitlements: errSecMissingEntitlement, -34018) keeps it in its UserDefaults
/// instead, as the settings are kept, and says so once.
func uiSecretGet(_ name: V) -> V {
    var query = uiSecretQuery(rtS(name))
    query[kSecReturnData as String] = true
    query[kSecMatchLimit as String] = kSecMatchLimitOne
    var found: CFTypeRef? = nil
    if SecItemCopyMatching(query as CFDictionary, &found) == errSecSuccess, let data = found as? Data {
        return .vStr(String(decoding: data, as: UTF8.self))
    }
    return .vStr(UserDefaults.standard.string(forKey: "everyui.secret." + rtS(name)) ?? "")
}
func uiSecretPut(_ name: V, _ value: V) -> V {
    let query = uiSecretQuery(rtS(name)), v = rtS(value), unkept = "everyui.secret." + rtS(name)
    SecItemDelete(query as CFDictionary)
    UserDefaults.standard.removeObject(forKey: unkept)
    if !v.isEmpty {
        var add = query
        add[kSecValueData as String] = Data(v.utf8)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(add as CFDictionary, nil)
        if status != errSecSuccess {
            UserDefaults.standard.set(v, forKey: unkept)
            if !UI.secretsUnkept {
                UI.secretsUnkept = true
                print("[ui] secret: the Keychain refused one (\(status)), so secrets are kept with the settings")
            }
        }
    }
    return .vNull
}
private func uiSecretQuery(_ name: String) -> [String: Any] {
    [kSecClass as String: kSecClassGenericPassword,
     kSecAttrService as String: Bundle.main.bundleIdentifier ?? "everyui",
     kSecAttrAccount as String: name]
}

// MARK: - what nothing caught: the program's (RtReportError), Objective-C's, and a crash, told to Platform.onError

/// The app's hook for what nothing caught (Platform.onError): `f(message, stack)`. A crash the last run ended on is
/// told to it too, once, as that run's (`uiCrashGuard` kept a note of it).
func uiOnError(_ f: V) -> V {
    RtErrors.hook = f
    if let last = Crash.last {
        Crash.last = nil
        DispatchQueue.main.async { RtReportProblem("the last run", last, "") }
    }
    return .vNull
}

/// How many errors nothing caught this run has seen.
func uiErrorsSeen() -> V { .vInt(RtErrors.seen) }

/// A crash's note, kept in a file under Caches by a signal's handler, which can do no more than write to one already
/// open, and read at the next launch.
enum Crash {
    static var fd: Int32 = -1
    static var told = false
    static var last: String? = nil
    static var prefix: UnsafeMutablePointer<CChar>? = nil
}

/// Every crash heard at launch: an Objective-C exception nothing caught (told to the hook at once, its reason and its
/// stack), and a signal that ends the app (a Swift trap, a bad access, an abort), noted for the next launch to tell,
/// the app then ending as it would have, with the system's crash report. What the last run's note said is read first.
func uiCrashGuard() {
    let file = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("everyui-crash.txt")
    if let note = try? String(contentsOf: file, encoding: .utf8) {
        let said = note.trimmingCharacters(in: .whitespacesAndNewlines)
        if !said.isEmpty { Crash.last = "the app ended on " + said }
    }
    Crash.fd = open(file.path, O_WRONLY | O_CREAT | O_TRUNC, 0o644)
    Crash.prefix = strdup("signal ")
    NSSetUncaughtExceptionHandler { e in
        Crash.told = true
        RtReportProblem("Objective-C", e.name.rawValue + ": " + (e.reason ?? ""),
                        e.callStackSymbols.joined(separator: "\n"))
    }
    for s in [SIGABRT, SIGSEGV, SIGBUS, SIGILL, SIGTRAP, SIGFPE] {
        signal(s) { n in
            // only what is safe in a signal's handler: a write to a file already open, then the signal again
            if Crash.fd >= 0 && !Crash.told, let p = Crash.prefix {
                _ = write(Crash.fd, p, 7)
                var digits: (UInt8, UInt8, UInt8) = (UInt8(48 + (n / 10) % 10), UInt8(48 + n % 10), 10)
                _ = withUnsafeBytes(of: &digits) { write(Crash.fd, $0.baseAddress, 3) }
            }
            signal(n, SIG_DFL)
            raise(n)
        }
    }
}

// MARK: - the pointer: a finger, or an iPad's trackpad or mouse, to the views that listen (Android's model)

/// What a view hears of the pointer (uiOnPointer), by kind ("down", "up", "move", "click", "menu", "enter", "leave",
/// "wheel"), and how it scrolls: kept with the view (an associated object), so it goes with it.
final class PointerHandlers {
    var kinds: [String: V] = [:]
    /// 1 scrolls down, 2 across (uiSetScrollable): a drag along it is taken from what it went down on.
    var along = 0
    /// What drags itself keeps a drag from the scroll it is in (uiSetTouchDrags).
    var keepsDrag = false
}
var pointerKey: UInt8 = 0
/// Every view that has handlers, for the collector's roots while it lives.
let pointerViews = NSHashTable<UIView>.weakObjects()

func pointerHandlers(_ v: UIView, _ make: Bool) -> PointerHandlers? {
    if let h = objc_getAssociatedObject(v, &pointerKey) as? PointerHandlers { return h }
    if !make { return nil }
    let h = PointerHandlers()
    objc_setAssociatedObject(v, &pointerKey, h, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
    pointerViews.add(v)
    return h
}

func uiPointerRoots() -> [V] {
    pointerViews.allObjects.flatMap { v in pointerHandlers(v, false).map { Array($0.kinds.values) } ?? [] }
}

/// `f(x, y)` for each pointer event of `kind` on the node, at the root's coordinates in the app's units. An Apple TV
/// has none (its remote's touch surface moves the focus), so nothing is ever told there.
func uiOnPointer(_ node: V, _ kind: V, _ f: V) -> V {
    if let v = view(node) { pointerHandlers(v, true)?.kinds[rtS(kind).lowercased()] = f }
    return .vNull
}

/// The node scrolls its content down (`vertical`) or across, or not at all: a finger's drag along it is its own.
func uiSetScrollable(_ node: V, _ vertical: V, _ horizontal: V) -> V {
    if let v = view(node) { pointerHandlers(v, true)?.along = rtB(vertical) ? 1 : rtB(horizontal) ? 2 : 0 }
    return .vNull
}
func uiSetTouchDrags(_ node: V, _ on: V) -> V {
    if let v = view(node) { pointerHandlers(v, true)?.keepsDrag = rtB(on) }
    return .vNull
}

/// The deepest view under `p` (in `v`'s coordinates) whose handlers `wanted` says yes to: drawn, and inside what clips
/// it; the last drawn first. A view that hears nothing lets the pointer through to what is under it, as on Android.
func pointerHit(_ v: UIView, _ p: CGPoint, _ wanted: (PointerHandlers) -> Bool) -> UIView? {
    if v.isHidden || v.alpha < 0.01 { return nil }
    let inside = v.bounds.contains(p)
    if v.clipsToBounds && !inside { return nil }
    for c in v.subviews.reversed() {
        if let found = pointerHit(c, v.convert(p, to: c), wanted) { return found }
    }
    if inside, let h = pointerHandlers(v, false), wanted(h) { return v }
    return nil
}

/// The event of `kind` to `v`, if it hears it, at `p` (the root's points) in the app's units.
func pointerFire(_ kind: String, _ v: UIView?, _ p: CGPoint) {
    guard let v = v, let f = pointerHandlers(v, false)?.kinds[kind] else { return }
    RtGuard("pointer " + kind, f, [.vDbl(Double(p.x) / UI.scale), .vDbl(Double(p.y) / UI.scale)])
}

/// One pointer at a time: the touch (or the trackpad's press) being followed, and what it went down on.
enum Touch {
    static var target: UIView? = nil
    static var downAt = CGPoint.zero
    static var strayed = false
    static var pressedLong = false
    static var taken = false
    static var held: DispatchWorkItem? = nil
    /// How far a finger may move and still tap, in points.
    static let slop: CGFloat = 10
}

/// A touch's phase ("down", "move", "up", "cancel") at `p`, the root's points, from RootViewController or the drive.
/// As Android has it: what it went down on hears it to the end (and the root its moves and its lift, for a drag
/// followed from there); a finger held still on what has a menu asks for it; a drag along a scroll further than a tap
/// may is taken by the scroll (what it went down on told it was lifted); and a lift where it went down, no further than
/// a tap may have strayed, is a click.
func uiTouch(_ phase: String, _ p: CGPoint, _ finger: Bool) {
    UI.finger = finger
    let root: UIView? = UI.root
    switch phase {
    case "down":
        Touch.target = root.flatMap { pointerHit($0, p) { !$0.kinds.isEmpty } }
        Touch.downAt = p
        Touch.strayed = false
        Touch.pressedLong = false
        Touch.taken = false
        Touch.held?.cancel()
        pointerFire("down", Touch.target, p)
        if let t = Touch.target, pointerHandlers(t, false)?.kinds["menu"] != nil {
            if UI.button == 2 {
                Touch.pressedLong = true
                pointerFire("menu", t, p)
            } else {
                let held = DispatchWorkItem {
                    Touch.held = nil
                    Touch.pressedLong = true
                    pointerFire("menu", t, Touch.downAt)
                }
                Touch.held = held
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5, execute: held)
            }
        }
    case "move":
        let dx = abs(p.x - Touch.downAt.x), dy = abs(p.y - Touch.downAt.y)
        if dx > Touch.slop || dy > Touch.slop {
            Touch.strayed = true
            Touch.held?.cancel()
            Touch.held = nil
            if !Touch.taken, let scroll = pointerScrollTaking(dx, dy) {
                Touch.taken = true
                pointerFire("up", Touch.target, p)
                Touch.target = scroll
                pointerFire("down", scroll, Touch.downAt)
            }
        }
        pointerFire("move", Touch.target, p)
        if Touch.target !== root { pointerFire("move", root, p) }
    default:
        Touch.held?.cancel()
        Touch.held = nil
        let target = Touch.target
        pointerFire("up", target, p)
        if target !== root { pointerFire("up", root, p) }
        if phase == "up", let t = target, !Touch.pressedLong, !(Touch.strayed && finger) {
            let at = root.map { $0.convert(p, to: t) } ?? p
            if !Touch.strayed || t.bounds.contains(at) { pointerFire("click", t, p) }
        }
        Touch.target = nil
    }
}

/// The scroll round what the touch went down on that takes a drag of `dx` across and `dy` down: one along that
/// direction, further along it than across it, and nothing between that keeps its own drag.
func pointerScrollTaking(_ dx: CGFloat, _ dy: CGFloat) -> UIView? {
    var at = Touch.target
    while let v = at {
        if let h = pointerHandlers(v, false) {
            if h.keepsDrag { return nil }
            if v !== Touch.target && ((h.along == 1 && dy > dx) || (h.along == 2 && dx > dy)) { return v }
        }
        at = v.superview
        if v === UI.root { break }
    }
    return nil
}

/// What a hovering pointer (an iPad's trackpad or mouse) is over, entered: each view from the deepest that listens to
/// the root, told as the pointer comes onto it and goes off it, and the deepest its moves.
var pointerOver: [UIView] = []
func uiHover(_ p: CGPoint?) {
    UI.finger = false
    var now: [UIView] = []
    if let p = p, let root = UI.root, let hit = pointerHit(root, p, { !$0.kinds.isEmpty }) {
        var at = hit
        while true {
            if pointerHandlers(at, false) != nil { now.append(at) }
            if at === root { break }
            guard let up = at.superview else { break }
            at = up
        }
    }
    for v in pointerOver where !now.contains(where: { $0 === v }) { pointerFire("leave", v, p ?? .zero) }
    for v in now where !pointerOver.contains(where: { $0 === v }) { pointerFire("enter", v, p ?? .zero) }
    pointerOver = now
    if let p = p { pointerFire("move", now.first, p) }
}

/// A trackpad's two fingers or a mouse's wheel, `dx` and `dy` points the content is to move, to the deepest view under
/// `p` that hears the wheel.
func uiWheel(_ p: CGPoint, _ dx: CGFloat, _ dy: CGFloat) {
    UI.finger = false
    guard let root = UI.root, let v = pointerHit(root, p, { $0.kinds["wheel"] != nil }) else { return }
    guard let f = pointerHandlers(v, false)?.kinds["wheel"] else { return }
    RtGuard("pointer wheel", f, [.vDbl(Double(dx)), .vDbl(Double(dy))])
}

// MARK: - the drive: input from a test, over localhost, in a build made for it (tools/apple-drive.sh)

/// The drive's hook into the program (Platform's, which answers each line), and whether the drive listens at all: only
/// in a build compiled with `-D EVERYUI_DRIVE` (tools/tvos-run.sh's) and launched with `--drive=<port>` or
/// EVERYUI_DRIVE=<port> in its environment. A release build has none of it.
func uiOnDrive(_ f: V) -> V {
    UI.drive = f
    return .vNull
}

#if EVERYUI_DRIVE

/// The port asked for at launch, or 0.
func uiDrivePort() -> Int {
    for a in CommandLine.arguments where a.hasPrefix("--drive=") { return Int(a.dropFirst(8)) ?? 0 }
    return Int(ProcessInfo.processInfo.environment["EVERYUI_DRIVE"] ?? "") ?? 0
}

func uiDriveOn() -> V { .vBool(uiDrivePort() > 0) }

/// Listen on 127.0.0.1 at the port asked for: each connection a line at a time, each line answered with one, its work
/// done on the main queue between frames.
func uiDriveStart() {
    let port = uiDrivePort()
    if port <= 0 { return }
    let server = socket(AF_INET, SOCK_STREAM, 0)
    var yes: Int32 = 1
    setsockopt(server, SOL_SOCKET, SO_REUSEADDR, &yes, socklen_t(MemoryLayout<Int32>.size))
    var address = sockaddr_in()
    address.sin_family = sa_family_t(AF_INET)
    address.sin_port = in_port_t(UInt16(port).bigEndian)
    address.sin_addr.s_addr = inet_addr("127.0.0.1")
    let bound = withUnsafePointer(to: &address) {
        $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
            bind(server, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
        }
    }
    if bound != 0 || listen(server, 4) != 0 {
        // another app (another simulator's, the simulators sharing the Mac's loopback) has the port: said plainly, as
        // a drive at that port reaches that app instead
        let why = String(cString: strerror(errno))
        print("[ui] drive: ERROR could not listen on 127.0.0.1:\(port) (" + why + "): another app holds the port, and " +
              "a drive sent there reaches it, not this one; launch with another --drive=<port>")
        close(server)
        return
    }
    print("[ui] drive: listening on 127.0.0.1:\(port)")
    Thread.detachNewThread {
        while true {
            let client = accept(server, nil, nil)
            if client < 0 { continue }
            Thread.detachNewThread { uiDriveServe(client) }
        }
    }
}

func uiDriveServe(_ client: Int32) {
    var pending = Data()
    var buffer = [UInt8](repeating: 0, count: 4096)
    while true {
        let n = read(client, &buffer, buffer.count)
        if n <= 0 { break }
        pending.append(contentsOf: buffer[0..<n])
        while let end = pending.firstIndex(of: 10) {
            let line = String(decoding: pending[pending.startIndex..<end], as: UTF8.self)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            pending.removeSubrange(pending.startIndex...end)
            if line.isEmpty { continue }
            let done = DispatchSemaphore(value: 0)
            var reply = ""
            DispatchQueue.main.async { uiDriveRun(line) { said in reply = said; done.signal() } }
            done.wait()
            // a reply is its lines and then an empty one, so a client reads to the empty line
            let out = Array((reply + "\n\n").utf8)
            _ = out.withUnsafeBytes { write(client, $0.baseAddress, out.count) }
        }
    }
    close(client)
}

/// One line: the program reads it (everyui.drive.Drive) and answers what to do, `<action>\t<reply>`, which is done
/// here and the reply given. The actions: `tap x y`, `hold x y ms`, `swipe x1 y1 x2 y2 ms` (the app's units), `key
/// name`, `type words`, and `say` (nothing to do).
func uiDriveRun(_ line: String, _ answer: @escaping (String) -> Void) {
    guard rtIsFn(UI.drive) else { answer("error the app has not started"); return }
    let said = rtS(RtGuard("the drive", UI.drive, [.vStr(line)]))
    let parts = said.components(separatedBy: "\t")
    let action = parts[0]
    var reply = parts.count > 1 ? parts[1...].joined(separator: "\t") : "ok"
    // which simulator answered, and on which port, so a drive meant for another one can tell (tools/apple-drive.sh)
    if line == "ping" {
        reply += " udid=" + (ProcessInfo.processInfo.environment["SIMULATOR_UDID"] ?? "none") + " port=" +
            String(uiDrivePort())
    }
    let words = action.split(separator: " ", maxSplits: 1).map(String.init)
    let numbers = action.split(separator: " ").dropFirst().compactMap { Double($0) }
    // the app's units to the root's points
    func point(_ i: Int) -> CGPoint { CGPoint(x: numbers[i] * UI.scale, y: numbers[i + 1] * UI.scale) }
    switch words.first ?? "" {
    case "tap" where numbers.count >= 2:
        uiDrivePress(point(0), point(0), 0.06) { answer(reply) }
    case "hold" where numbers.count >= 3:
        uiDrivePress(point(0), point(0), numbers[2] / 1000.0) { answer(reply) }
    case "swipe" where numbers.count >= 5:
        uiDrivePress(point(0), point(2), numbers[4] / 1000.0) { answer(reply) }
    case "key" where words.count > 1:
        if case let (alert, close)? = UI.editing, ["Enter", "Escape", "Back"].contains(words[1]) {
            alert.dismiss(animated: false)
            close(words[1] == "Enter")
        } else {
            uiKey(words[1], true)
            uiKey(words[1], false)
        }
        answer(reply)
    case "type" where words.count > 1:
        if case let (alert, _)? = UI.editing, let field = alert.textFields?.first {
            field.text = (field.text ?? "") + words[1]
        } else {
            for c in words[1] {
                uiKey(String(c), true)
                uiKey(String(c), false)
            }
        }
        answer(reply)
    default:
        answer(reply)
    }
}

/// A finger down at `from`, moved to `to` over `seconds` (a frame at a time) and lifted there, through uiTouch as a
/// real one is.
func uiDrivePress(_ from: CGPoint, _ to: CGPoint, _ seconds: Double, _ done: @escaping () -> Void) {
    UI.button = 0
    uiTouch("down", from, true)
    let steps = max(1, Int(seconds * 60.0))
    func step(_ i: Int) {
        let t = CGFloat(i) / CGFloat(steps)
        let p = CGPoint(x: from.x + (to.x - from.x) * t, y: from.y + (to.y - from.y) * t)
        if from != to { uiTouch("move", p, true) }
        if i >= steps {
            uiTouch("up", to, true)
            done()
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + seconds / Double(steps)) { step(i + 1) }
        }
    }
    DispatchQueue.main.asyncAfter(deadline: .now() + seconds / Double(steps)) { step(1) }
}

#else

func uiDriveOn() -> V { .vBool(false) }
func uiDriveStart() {}

#endif

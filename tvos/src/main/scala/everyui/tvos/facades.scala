package everyui.tvos

import everyui.compiler.api.native

/** A UIKit view — an opaque handle. */
@native
class SGNode

/** everyui's tvOS support runtime (`swift/runtime/UniversalRuntime.swift`,
  * built into the app by tools/tvos-run.sh).
  *
  * Every primitive the render seam needs, as a plain Swift function over `V`.
  * Each facade below is `@native`, so the compiler emits the call verbatim and emits
  * NO code for the declaration — `uiSetText(n, s)` in Scala is `uiSetText(n, s)`
  * in the channel. The web backend declares the same nine names against a DOM
  * element (see `web/.../everyui/dom/facades.scala`), which is why the shared
  * `RenderNode` implementations stay line-for-line parallel.
  */
@native object uiRoot {
  def apply(): SGNode = native.value
}
@native object uiText {
  def apply(): SGNode = native.value
}
@native object uiGetText {
  def apply(node: SGNode): String = native.value
}
@native object uiSetText {
  def apply(node: SGNode, text: String): Unit = native.value
}
@native object uiGetX {
  def apply(node: SGNode): Double = native.value
}
@native object uiSetX {
  def apply(node: SGNode, x: Double): Unit = native.value
}
@native object uiGetY {
  def apply(node: SGNode): Double = native.value
}
@native object uiSetY {
  def apply(node: SGNode, y: Double): Unit = native.value
}
@native object uiImage {
  def apply(uri: String, width: Double, height: Double): SGNode = native.value
}
@native object uiGetRotation {
  def apply(node: SGNode): Double = native.value
}
@native object uiSetRotation {
  def apply(node: SGNode, radians: Double): Unit = native.value
}
/** Drawn larger or smaller about the node's own centre. */
@native object uiSetScale {
  def apply(node: SGNode, factor: Double): Unit = native.value
}
/** The whole app drawn `factor` times its size, from the screen's top left (everyui.theme.UiScale). */
@native object uiSetRootScale {
  def apply(factor: Double): Unit = native.value
}
@native object uiSetFontSize {
  def apply(node: SGNode, size: Double): Unit = native.value
}
@native object uiScreenWidth {
  def apply(): Double = native.value
}
@native object uiScreenHeight {
  def apply(): Double = native.value
}
@native object uiAppend {
  def apply(parent: SGNode, child: SGNode): Unit = native.value
}
@native object uiLog {
  def apply(message: String): Unit = native.value
}
/** Milliseconds since channel start — the animation clock. */
@native object uiTicks {
  def apply(): Double = native.value
}
/** `f` once `seconds` have passed, on the main queue. */
@native object uiAfter {
  def apply(seconds: Double, f: () => Unit): Unit = native.value
}
/** Run `tick` once per render frame (a `CADisplayLink`). */
@native object uiAnimate {
  def apply(tick: () => Unit): Unit = native.value
}
/** The math intrinsics, through the runtime. */
/** A number from 0 up to 1, at random. */
@native object uiRandom {
  def apply(): Double = native.value
}
@native object uiSqrt {
  def apply(x: Double): Double = native.value
}
@native object uiPow {
  def apply(x: Double, y: Double): Double = native.value
}
@native object uiSin {
  def apply(x: Double): Double = native.value
}
@native object uiCos {
  def apply(x: Double): Double = native.value
}
/** `"#rrggbb"` onto a Label's color. */
@native object uiSetColor {
  def apply(node: SGNode, hex: String): Unit = native.value
}
/** An edge `width` each side round a label's letters in `hex`, or none for "". */
@native object uiSetTextOutline {
  def apply(node: SGNode, width: Double, hex: String): Unit = native.value
}
/** Extra space after each letter of a label, in points (0 for the face's own). */
@native object uiSetLetterSpacing {
  def apply(node: SGNode, px: Double): Unit = native.value
}
/** A label's face made italic: its own italic where it has one, else its letters slanted. */
@native object uiSetItalic {
  def apply(node: SGNode): Unit = native.value
}
/** A bundled face by family and CSS weight; size in points. */
@native object uiSetFont {
  def apply(node: SGNode, family: String, weight: Int, size: Double): Unit = native.value
}
/** The node's laid-out bounding box. */
@native object uiGetWidth {
  def apply(node: SGNode): Double = native.value
}
@native object uiGetHeight {
  def apply(node: SGNode): Double = native.value
}
/** A Label's text width at exactly `size` pixels of the face at `uri`. */
@native object uiTextWidth {
  def apply(node: SGNode, uri: String, size: Double): Double = native.value
}
/** A transparent `Rectangle`: a group that can also paint a background. */
@native object uiContainer {
  def apply(): SGNode = native.value
}
@native object uiSetWidth {
  def apply(node: SGNode, width: Double): Unit = native.value
}
@native object uiSetHeight {
  def apply(node: SGNode, height: Double): Unit = native.value
}
/** `"#rrggbb"`, or `""` for transparent. */
@native object uiSetBackgroundColor {
  def apply(node: SGNode, hex: String): Unit = native.value
}
@native object uiSetImageSource {
  def apply(node: SGNode, uri: String): Unit = native.value
}
@native object uiRemove {
  def apply(node: SGNode): Unit = native.value
}
/** Load `uri` and report its natural size. */
@native object uiLoadImage {
  def apply(uri: String, loaded: (Double, Double) => Unit): Unit = native.value
}
/** A text file as UTF-8 (a path in the bundle, a `file:` URL or an http(s) one), read off the main thread and given to
  * `done` on it; "" where it is not there or will not read, which is logged. */
@native object uiReadTextThen {
  def apply(path: String, done: String => Unit): Unit = native.value
}
/** An ARB file's messages, parsed natively, its metadata left out ("" is none). */
@native object uiArbMessages {
  def apply(text: String): Map[String, String] = native.value
}
/** Both coordinates at once. */
@native object uiSetTranslation {
  def apply(node: SGNode, x: Double, y: Double): Unit = native.value
}
/** Whether Reduce Motion is on in Accessibility. */
@native object uiReduceMotion {
  def apply(): Boolean = native.value
}
/** Seconds from `--freeze=` on the launch, or -1. */
@native object uiFreezeSeconds {
  def apply(): Double = native.value
}
/** `--<name>=value` on the launch, or "". */
@native object uiLaunchArg {
  def apply(name: String): String = native.value
}
/** Exit the process. */
@native object uiQuit {
  def apply(): Unit = native.value
}
/** Rounded corners; a SceneGraph Rectangle has none, so the Roku ignores it. */
@native object uiSetRadius {
  def apply(node: SGNode, radius: Double): Unit = native.value
}
/** Milliseconds since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
@native object uiLocalDateTime {
  def apply(millis: Double): String = native.value
}
/** The time zone the device is in, as IANA names it ("America/Chicago"). */
@native object uiTimeZone {
  def apply(): String = native.value
}
/** A pointer event of `kind` on the node, if the platform has a pointer. */
@native object uiOnPointer {
  def apply(node: SGNode, kind: String, f: (Double, Double) => Unit): Unit = native.value
}
/** Every key, by the web's name, pressed or released. */
@native object uiOnKey {
  def apply(f: (String, Boolean) => Boolean): Unit = native.value
}
/** Clip the node to `width` by `height`. */
@native object uiSetClip {
  def apply(node: SGNode, width: Double, height: Double): Unit = native.value
}
/** Let what the node holds show past it again (`uiSetClip` undone). */
@native object uiClearClip {
  def apply(node: SGNode): Unit = native.value
}
/** Whether the node and everything under it is drawn. */
@native object uiSetVisible {
  def apply(node: SGNode, visible: Boolean): Unit = native.value
}
/** How opaque the node and all it holds are drawn, 0 to 1. */
@native object uiSetOpacity {
  def apply(node: SGNode, opacity: Double): Unit = native.value
}
/** The character at a Unicode code point, as a string. */
@native object uiChr {
  def apply(codePoint: Int): String = native.value
}
/** New words from the platform's keyboard: a text field in a dialog, headed `title`, holding `value`, hiding what is
  * typed when `secure`, asking for `autofill` (an `autocomplete` token, as its `textContentType`); `done` hears the
  * words when it is closed with Done. */
@native object uiEditText {
  def apply(title: String, value: String, secure: Boolean, autofill: String, done: String => Unit): Unit = native.value
}
/** A document the device kept between launches (under Caches), or "" where there is none — which a cache the system
  * emptied and a document never written are both allowed to be. */
@native object uiStoreGet {
  def apply(space: String, key: String): String = native.value
}
/** Keep a document under a key within a space. */
@native object uiStorePut {
  def apply(space: String, key: String, value: String): Unit = native.value
}
/** Forget one document. */
@native object uiStoreDelete {
  def apply(space: String, key: String): Unit = native.value
}
/** The keys a space holds. */
@native object uiStoreKeys {
  def apply(space: String): List[String] = native.value
}
/** Forget every document in a space. */
@native object uiStoreClear {
  def apply(space: String): Unit = native.value
}
/** The viewer's caption setting as MediaAccessibility has it ("On", "Automatic", "Off") and the first language they
  * prefer captions in ("" for none); a change heard (`uiCaptionsListen`, an id to stop it with) and no longer. */
@native object uiCaptionsMode {
  def apply(): String = native.value
}
@native object uiCaptionsLanguage {
  def apply(): String = native.value
}
@native object uiCaptionsListen {
  def apply(changed: () => Unit): Int = native.value
}
@native object uiCaptionsUnlisten {
  def apply(id: Int): Unit = native.value
}
/** `resumed()` when the application is active again and `away()` when it resigns active or goes into the background
  * (UIApplication's notifications), from the first call on; a later call changes nothing. */
@native object uiOnLifecycle {
  def apply(resumed: () => Unit, away: () => Unit): Unit = native.value
}
/** A page of the web (everyui.component.WebView): a WKWebView on iOS, lent `window.<bridge>.postMessage(string)`; on
  * tvOS, which has no WebKit, an empty view, and every call after it does nothing (`uiWebViews` is false there). */
@native object uiWebViews {
  def apply(): Boolean = native.value
}
@native object uiWebPrewarm {
  def apply(): Unit = native.value
}
@native object uiWeb {
  def apply(bridge: String): SGNode = native.value
}
/** Show `url`: a path the app's bundle holds is that file, a site-absolute one it does not is `origin`'s. */
@native object uiWebLoad {
  def apply(node: SGNode, url: String, origin: String): Unit = native.value
}
@native object uiWebAgent {
  def apply(node: SGNode, agent: String): Unit = native.value
}
/** Sandboxed (`on`) with `flags` allowed, by commas, and the page loaded again. */
@native object uiWebSandbox {
  def apply(node: SGNode, on: Boolean, flags: String): Unit = native.value
}
@native object uiWebHearSandboxed {
  def apply(node: SGNode, on: Boolean): Unit = native.value
}
@native object uiWebClear {
  def apply(node: SGNode, clear: Boolean): Unit = native.value
}
@native object uiWebRun {
  def apply(node: SGNode, script: String): Unit = native.value
}
@native object uiWebAsk {
  def apply(node: SGNode, id: Int, request: String): Boolean = native.value
}
@native object uiWebOnMessage {
  def apply(node: SGNode, f: String => Unit): Unit = native.value
}
@native object uiWebOnReply {
  def apply(node: SGNode, f: (Int, String) => Unit): Unit = native.value
}
/** Heard no more, its page stopped and left on about:blank, and taken off the screen. */
@native object uiWebRelease {
  def apply(node: SGNode): Unit = native.value
}
/** What the app runs on, by UIKit's idiom: "tv", "phone", "pad", "mac", "vision" or "car". */
@native object uiIdiom {
  def apply(): String = native.value
}
/** What the device calls itself ("iPhone", "iPad", "Apple TV"), and the hardware's name for itself ("iPhone18,1"). */
@native object uiDeviceName {
  def apply(): String = native.value
}
@native object uiDeviceModel {
  def apply(): String = native.value
}
/** The bundle's CFBundleVersion as a number, and its CFBundleShortVersionString. */
@native object uiAppVersion {
  def apply(): Int = native.value
}
@native object uiAppVersionName {
  def apply(): String = native.value
}
/** The first language the person prefers, as a BCP 47 tag. */
@native object uiLanguage {
  def apply(): String = native.value
}
/** The system's "light" or "dark", and `f` told each as it changes. */
@native object uiColorScheme {
  def apply(): String = native.value
}
@native object uiOnColorScheme {
  def apply(f: String => Unit): Unit = native.value
}
/** Screen pixels to a point. */
@native object uiScreenScale {
  def apply(): Double = native.value
}
/** The safe area the root is placed inside, in points, at one edge: 0 top, 1 right, 2 bottom, 3 left. */
@native object uiSafeInset {
  def apply(edge: Int): Double = native.value
}
/** `f` each time the root changes size (a rotation, a split view). */
@native object uiOnResize {
  def apply(f: () => Unit): Unit = native.value
}
/** Whether a hardware keyboard is attached, and a mouse or a trackpad. */
@native object uiHasKeyboard {
  def apply(): Boolean = native.value
}
@native object uiHasPointer {
  def apply(): Boolean = native.value
}
/** The event being reported: one of its modifiers (0 ctrl, 1 meta, 2 alt, 3 shift), whether a finger made it, and
  * its button. */
@native object uiModifier {
  def apply(which: Int): Boolean = native.value
}
@native object uiPointerFinger {
  def apply(): Boolean = native.value
}
@native object uiPointerButton {
  def apply(): Int = native.value
}
/** A view that scrolls its content down or across: a finger's drag along it is its own. */
@native object uiSetScrollable {
  def apply(node: SGNode, vertical: Boolean, horizontal: Boolean): Unit = native.value
}
/** A view that drags itself keeps a drag from the scroll it is in. */
@native object uiSetTouchDrags {
  def apply(node: SGNode, on: Boolean): Unit = native.value
}
/** `f(message, stack)` for each error nothing caught (the program's, Objective-C's, and the crash the last run ended
  * on); how many this run has seen. */
@native object uiOnError {
  def apply(f: (String, String) => Unit): Unit = native.value
}
@native object uiErrorsSeen {
  def apply(): Int = native.value
}
/** Whether this build takes a test's input (compiled with `-D EVERYUI_DRIVE` and launched with `--drive=<port>`), and
  * the program's answer to each line it is sent. */
@native object uiDriveOn {
  def apply(): Boolean = native.value
}
@native object uiOnDrive {
  def apply(f: String => String): Unit = native.value
}
/** `text` on the pasteboard with `html` beside it where it is not "", and the pasteboard's text ("" for none): on an
  * iPhone or an iPad; an Apple TV has no pasteboard. */
@native object uiClipboardSet {
  def apply(text: String, html: String): Unit = native.value
}
@native object uiClipboardGet {
  def apply(): String = native.value
}
/** `url` opened by the system (the browser, or the app that owns the address), logged where nothing opens it. */
@native object uiOpenUrl {
  def apply(url: String): Unit = native.value
}
/** The text size the person chose (Dynamic Type) as a scale of the default, and `f` told it each time it changes: on
  * an iPhone or an iPad; 1 on an Apple TV. */
@native object uiTextScale {
  def apply(): Double = native.value
}
@native object uiOnTextScale {
  def apply(f: Double => Unit): Unit = native.value
}
/** The screen kept on, or let dim and lock again (the system's idle timer). */
@native object uiKeepAwake {
  def apply(on: Boolean): Unit = native.value
}
/** Words read out by VoiceOver, where it is on. */
@native object uiAnnounce {
  def apply(words: String): Unit = native.value
}
/** A value kept across launches (UserDefaults), "" for none; "" kept forgets it. */
@native object uiSettingGet {
  def apply(name: String): String = native.value
}
@native object uiSettingPut {
  def apply(name: String, value: String): Unit = native.value
}
/** A value kept across launches in the Keychain, "" for none; "" kept forgets it. */
@native object uiSecretGet {
  def apply(name: String): String = native.value
}
@native object uiSecretPut {
  def apply(name: String, value: String): Unit = native.value
}

// ---- text: wrapped at a width, aligned, spaced, shadowed ----
/** Whether a label wraps at its width (as many lines as it takes) or keeps the lines its words were written with. */
@native object uiSetWraps {
  def apply(node: SGNode, on: Boolean): Unit = native.value
}
/** How tall a label's words are wrapped at `width`, as UIKit draws them. */
@native object uiTextHeight {
  def apply(node: SGNode, width: Double): Double = native.value
}
/** Where a label's lines sit ("start", "center", "end") and which way its words run ("", "auto", "ltr", "rtl"). */
@native object uiSetTextAlign {
  def apply(node: SGNode, align: String, direction: String): Unit = native.value
}
/** The app's reading direction, which every label's start follows. */
@native object uiSetDirection {
  def apply(rtl: Boolean): Unit = native.value
}
/** Each wrapped line `px` below the last (0: the face's own spacing). */
@native object uiSetLineHeight {
  def apply(node: SGNode, px: Double): Unit = native.value
}
/** A shadow behind a label's letters, or none for "". */
@native object uiSetTextShadow {
  def apply(node: SGNode, x: Double, y: Double, blur: Double, hex: String): Unit = native.value
}

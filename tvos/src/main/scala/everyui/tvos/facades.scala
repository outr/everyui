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

package everyui.roku

import everyui.compiler.api.native

/** A Roku SceneGraph node (`roSGNode`) — an opaque handle. */
@native
class SGNode

/** everyui's Roku support runtime (`roku/runtime/UniversalRuntime.brs`,
  * copied into the emitted channel by `emitRoku`).
  *
  * Every primitive the render seam needs, as a plain BrightScript global.
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
/** Run `tick` once per render frame (a SceneGraph `Timer` at `duration=0`). */
@native object uiAnimate {
  def apply(tick: () => Unit): Unit = native.value
}
/** BrightScript's math intrinsics (`Sqr`, `^`, `Sin`, `Cos`). */
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
/** A bundled face (`pkg:/fonts/`) by family and CSS weight; size in pixels. */
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
/** A `Group`, which draws nothing of its own (see uiFill). */
@native object uiContainer {
  def apply(): SGNode = native.value
}
/** A transparent `Rectangle`, to be given a colour and a size: a square edge. */
@native object uiRect {
  def apply(): SGNode = native.value
}
/** A container's colour, `width` by `height` behind its children: `existing`, a new Rectangle, or none for `""`. */
@native object uiFill {
  def apply(node: SGNode, existing: Option[SGNode], hex: String, width: Double, height: Double): Option[SGNode] = native.value
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
/** Load `uri` and report its natural size once SceneGraph has it. */
@native object uiLoadImage {
  def apply(uri: String, loaded: (Double, Double) => Unit): Unit = native.value
}
/** Both coordinates at once. */
@native object uiSetTranslation {
  def apply(node: SGNode, x: Double, y: Double): Unit = native.value
}
/** Seconds from `pkg:/freeze.txt`, or -1. */
@native object uiFreezeSeconds {
  def apply(): Double = native.value
}
/** The text of `pkg:/<name>.txt`, or "". */
@native object uiLaunchArg {
  def apply(name: String): String = native.value
}
/** A packaged text file by its `pkg:/` path, or "". */
@native object uiReadText {
  def apply(path: String): String = native.value
}
/** Draw `text` in `label`, in runs of faces that have its glyphs when its own has not: the width drawn, or -1. */
@native object uiRichText {
  def apply(label: SGNode, text: String, baseUri: String, weight: Int, size: Double, color: String): Double = native.value
}
/** The language the app is in (a code such as `zh_Hant`): which CJK glyphs text takes. */
@native object uiSetLanguage {
  def apply(code: String): Unit = native.value
}
/** The panel's UI pixels per design pixel (2/3 on a 720p UI): a 9-patch's corners are drawn in panel pixels. */
@native object uiPanelScale {
  def apply(): Double = native.value
}
/** Layout units to pixels (2, or 1 for a sideload with `viewport.txt`). */
@native object uiScale {
  def apply(): Double = native.value
}
/** A tinted shape (a rounded fill or edge) behind `parent`'s children at `index`: `existing`, or a new one. */
@native object uiShape {
  def apply(parent: SGNode, existing: Option[SGNode], uri: String, hex: String, width: Double, height: Double, index: Int): SGNode = native.value
}
/** A font file (`pkg:/fonts/Roboto-Regular.ttf`) at `size` pixels. */
@native object uiSetFontFile {
  def apply(node: SGNode, uri: String, size: Double): Unit = native.value
}
/** Run `f` on a later turn of the event loop, on a fresh stack. */
@native object uiLater {
  def apply(f: () => Unit): Unit = native.value
}
/** An ARB file's messages as a string-keyed table (a Map to the runtime), parsed natively. */
@native object uiArbMessages {
  def apply(text: String): Map[String, String] = native.value
}
/** A saved setting (the registry), or "". */
@native object uiSetting {
  def apply(name: String): String = native.value
}
/** Keep a setting; "" forgets it. */
@native object uiSaveSetting {
  def apply(name: String, value: String): Unit = native.value
}
/** The device's language, as a BCP 47 tag. */
@native object uiLanguage {
  def apply(): String = native.value
}
/** What the person calls this device. */
@native object uiDeviceName {
  def apply(): String = native.value
}
/** Rounded corners; a SceneGraph Rectangle has none, so the Roku ignores it. */
@native object uiSetRadius {
  def apply(node: SGNode, radius: Double): Unit = native.value
}
/** Milliseconds since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
@native object uiLocalDateTime {
  def apply(millis: Long): String = native.value
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
/** A gradient (`angle` as CSS has it, `stops` in the seam's encoding) drawn behind `parent`'s children: `existing`, or a new
  * Poster. */
@native object uiGradient {
  def apply(parent: SGNode, existing: Option[SGNode], angle: Double, stops: String, width: Double, height: Double): SGNode = native.value
}
/** Cut `node`'s children to a rounded rectangle: `existing`, or a new MaskGroup they are moved into. */
@native object uiRoundClip {
  def apply(node: SGNode, existing: Option[SGNode], width: Double, height: Double, radius: Double): SGNode = native.value
}
/** The pixels between a Label's lines. */
@native object uiSetLineSpacing {
  def apply(node: SGNode, spacing: Double): Unit = native.value
}
/** Whether a Label wraps its words at its width. */
@native object uiSetWrap {
  def apply(node: SGNode, wrap: Boolean): Unit = native.value
}
/** Whether the node and everything under it is drawn. */
@native object uiSetVisible {
  def apply(node: SGNode, visible: Boolean): Unit = native.value
}
/** The character at a Unicode code point, as a string. */
@native object uiChr {
  def apply(codePoint: Int): String = native.value
}
/** A field of `node` moving through keyframes (`fractions`, with values `a`, and `b` for a translation or a scale) over
  * `seconds`, repeating, after `delay`: the Animation node that runs it. */
@native object uiMotion {
  def apply(node: SGNode, property: String, seconds: Double, delay: Double, fractions: List[Double], a: List[Double], b: List[Double]): SGNode = native.value
}
/** Stop an Animation from uiMotion and take it away. */
@native object uiStopMotion {
  def apply(animation: SGNode): Unit = native.value
}
/** Run `f` once `seconds` have passed, from a one-shot Timer. */
@native object uiAfter {
  def apply(seconds: Double, f: () => Unit): Unit = native.value
}

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
@native object uiRoot:
  def apply(): SGNode = native.value
@native object uiText:
  def apply(): SGNode = native.value
@native object uiGetText:
  def apply(node: SGNode): String = native.value
@native object uiSetText:
  def apply(node: SGNode, text: String): Unit = native.value
@native object uiGetX:
  def apply(node: SGNode): Double = native.value
@native object uiSetX:
  def apply(node: SGNode, x: Double): Unit = native.value
@native object uiGetY:
  def apply(node: SGNode): Double = native.value
@native object uiSetY:
  def apply(node: SGNode, y: Double): Unit = native.value
@native object uiImage:
  def apply(uri: String, width: Double, height: Double): SGNode = native.value
@native object uiGetRotation:
  def apply(node: SGNode): Double = native.value
@native object uiSetRotation:
  def apply(node: SGNode, radians: Double): Unit = native.value
@native object uiSetFontSize:
  def apply(node: SGNode, size: Double): Unit = native.value
@native object uiScreenWidth:
  def apply(): Double = native.value
@native object uiScreenHeight:
  def apply(): Double = native.value
@native object uiAppend:
  def apply(parent: SGNode, child: SGNode): Unit = native.value
@native object uiLog:
  def apply(message: String): Unit = native.value
/** Milliseconds since channel start — the animation clock. */
@native object uiTicks:
  def apply(): Double = native.value
/** Run `tick` once per render frame (a `CADisplayLink`). */
@native object uiAnimate:
  def apply(tick: () => Unit): Unit = native.value
/** The math intrinsics, through the runtime. */
@native object uiSqrt:
  def apply(x: Double): Double = native.value
@native object uiPow:
  def apply(x: Double, y: Double): Double = native.value
@native object uiSin:
  def apply(x: Double): Double = native.value
@native object uiCos:
  def apply(x: Double): Double = native.value
/** `"#rrggbb"` onto a Label's color. */
@native object uiSetColor:
  def apply(node: SGNode, hex: String): Unit = native.value
/** A bundled face by family and CSS weight; size in points. */
@native object uiSetFont:
  def apply(node: SGNode, family: String, weight: Int, size: Double): Unit = native.value
/** The node's laid-out bounding box. */
@native object uiGetWidth:
  def apply(node: SGNode): Double = native.value
@native object uiGetHeight:
  def apply(node: SGNode): Double = native.value
/** A Label's text width at exactly `size` pixels of the face at `uri`. */
@native object uiTextWidth:
  def apply(node: SGNode, uri: String, size: Double): Double = native.value
/** A transparent `Rectangle`: a group that can also paint a background. */
@native object uiContainer:
  def apply(): SGNode = native.value
@native object uiSetWidth:
  def apply(node: SGNode, width: Double): Unit = native.value
@native object uiSetHeight:
  def apply(node: SGNode, height: Double): Unit = native.value
/** `"#rrggbb"`, or `""` for transparent. */
@native object uiSetBackgroundColor:
  def apply(node: SGNode, hex: String): Unit = native.value
@native object uiSetImageSource:
  def apply(node: SGNode, uri: String): Unit = native.value
@native object uiRemove:
  def apply(node: SGNode): Unit = native.value
/** Load `uri` and report its natural size. */
@native object uiLoadImage:
  def apply(uri: String, loaded: (Double, Double) => Unit): Unit = native.value
/** Both coordinates at once. */
@native object uiSetTranslation:
  def apply(node: SGNode, x: Double, y: Double): Unit = native.value
/** Seconds from `--freeze=` on the launch, or -1. */
@native object uiFreezeSeconds:
  def apply(): Double = native.value
/** `--<name>=value` on the launch, or "". */
@native object uiLaunchArg:
  def apply(name: String): String = native.value
/** Exit the process. */
@native object uiQuit:
  def apply(): Unit = native.value
/** Rounded corners; a SceneGraph Rectangle has none, so the Roku ignores it. */
@native object uiSetRadius:
  def apply(node: SGNode, radius: Double): Unit = native.value
/** Milliseconds since the epoch as local `yyyy-MM-dd HH:mm:ss`. */
@native object uiLocalDateTime:
  def apply(millis: Double): String = native.value
/** A pointer event of `kind` on the node, if the platform has a pointer. */
@native object uiOnPointer:
  def apply(node: SGNode, kind: String, f: (Double, Double) => Unit): Unit = native.value
/** Every key, by the web's name, pressed or released. */
@native object uiOnKey:
  def apply(f: (String, Boolean) => Unit): Unit = native.value
/** Clip the node to `width` by `height`. */
@native object uiSetClip:
  def apply(node: SGNode, width: Double, height: Double): Unit = native.value

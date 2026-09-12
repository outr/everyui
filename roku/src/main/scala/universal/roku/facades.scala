package universal.roku

import universal.compiler.api.native

/** A Roku SceneGraph node (`roSGNode`) — an opaque handle. */
@native
class SGNode

/** universal-ui's Roku support runtime (`roku/runtime/UniversalRuntime.brs`,
  * copied into the emitted channel by `emitRoku`).
  *
  * Every primitive the render seam needs, as a plain BrightScript global.
  * Each facade below is `@native`, so the compiler emits the call verbatim and emits
  * NO code for the declaration — `uiSetText(n, s)` in Scala is `uiSetText(n, s)`
  * in the channel. The web backend declares the same nine names against a DOM
  * element (see `web/.../universal/dom/facades.scala`), which is why the shared
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
/** Run `tick` once per render frame (a SceneGraph `Timer` at `duration=0`). */
@native object uiAnimate:
  def apply(tick: () => Unit): Unit = native.value
/** BrightScript's math intrinsics (`Sqr`, `^`, `Sin`, `Cos`). */
@native object uiSqrt:
  def apply(x: Double): Double = native.value
@native object uiPow:
  def apply(x: Double, y: Double): Double = native.value
@native object uiSin:
  def apply(x: Double): Double = native.value
@native object uiCos:
  def apply(x: Double): Double = native.value

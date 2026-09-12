package universal.render

import universal.roku.*

/** **The one invention.** The abstract render node, Roku edition: a SceneGraph
  * node behind a get/set/append surface that `Prop` can be built against.
  *
  * The shared UI layer names only this type. Its DOM twin
  * (`web/.../universal/render/RenderNode.scala`) has the identical signature
  * over an `Element` — same names, same arities — so `Component` and `Label`
  * compile unchanged against either. Selection is by SOURCE ROOT at compile
  * time (the Scala.js / Scala Native pattern), not by subtyping, so neither
  * backend pays for dispatch.
  *
  * Every method takes explicit parens. A paren-less getter on another user
  * instance is ambiguous with a field read in the emitted BrightScript
  * (`node.getText` would yield the function ref, not call it).
  */
class RenderNode(val sg: SGNode):
  def getText(): String = uiGetText(sg)
  def setText(text: String): Unit = uiSetText(sg, text)

  def getX(): Double = uiGetX(sg)
  def setX(x: Double): Unit = uiSetX(sg, x)

  def getY(): Double = uiGetY(sg)
  def setY(y: Double): Unit = uiSetY(sg, y)

  /** Radians, clockwise, about the node's own centre — matching youi's
    * `ImageView.rotation`, which is in TURNS (0..1); the example converts. */
  def getRotation(): Double = uiGetRotation(sg)
  def setRotation(radians: Double): Unit = uiSetRotation(sg, radians)

  def setFontSize(size: Double): Unit = uiSetFontSize(sg, size)

  def append(child: RenderNode): Unit = uiAppend(sg, child.sg)

/** Backend node factories. */
object Nodes:
  /** The channel's root Group, created by the generated `AppScene`. */
  def root(): RenderNode = new RenderNode(uiRoot())
  /** A text node — a SceneGraph `Label`. */
  def text(): RenderNode = new RenderNode(uiText())
  /** An image node — a SceneGraph `Poster` loading from `pkg:/`. */
  def image(uri: String, width: Double, height: Double): RenderNode =
    new RenderNode(uiImage(uri, width, height))

/** The handful of non-render platform services the slice needs. */
object Platform:
  def log(message: String): Unit = uiLog(message)
  def ticks(): Double = uiTicks()
  def animate(tick: () => Unit): Unit = uiAnimate(tick)

  /** Channel-package URI — Roku loads assets from `pkg:/`. */
  def imagePath(name: String): String = "pkg:/images/" + name

  def screenWidth(): Double = uiScreenWidth()
  def screenHeight(): Double = uiScreenHeight()

  // BrightScript intrinsics, through universal-ui's runtime. The web backend
  // supplies the same four from the real Scala stdlib.
  def sqrt(x: Double): Double = uiSqrt(x)
  def pow(x: Double, y: Double): Double = uiPow(x, y)
  def sin(x: Double): Double = uiSin(x)
  def cos(x: Double): Double = uiCos(x)

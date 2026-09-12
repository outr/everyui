package universal.ui

import universal.render.RenderNode

/** The component model, with the DOM coupling removed.
  *
  * youi's `Component(val element: html.Element)` is the ONLY DOM-bound part of
  * its UI layer — its Props are built on a concrete `html.Element`. Here the
  * same Props are built on a [[universal.render.RenderNode]], which each
  * backend supplies: the DOM wraps an element, Roku wraps a SceneGraph node.
  *
  * `RenderNode` is resolved per platform at COMPILE time (one source root per
  * backend, the Scala.js / Scala Native pattern), not by subtype dispatch. One
  * app targets one backend, so nothing is lost, and nothing pays for dispatch.
  */
class Component(val node: RenderNode):
  /** Horizontal offset, in platform pixels. */
  val x: Prop[Double] = new Prop[Double](() => node.getX(), v => node.setX(v))

  /** Vertical offset, in platform pixels. */
  val y: Prop[Double] = new Prop[Double](() => node.getY(), v => node.setY(v))

  def append(child: Component): Unit = node.append(child.node)

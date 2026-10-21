package everyui.app.screen

import reactify.*
import everyui.async.Task
import everyui.component.{Component, Container}
import everyui.component.types.PositionType
import everyui.layout.LayoutPass

/** A screen with a container the size of its host, filled by `createUI` once, on its first showing. */
trait UIScreen extends Screen with PathActivation {
  /** The manager showing this screen; where its container goes. */
  def manager: ScreenManager
  /** This screen's own background image (a site-absolute path or a URL), in place of the theme's: a title's artwork, say. */
  val background: Var[Option[String]] = Var(None)
  /** Shown in place of `background` when that will not load: a picture addressed indirectly, say, which the server may
    * not have. */
  val backgroundFallback: Var[Option[String]] = Var(None)
  /** How the artwork behind it is treated while this screen is shown: where it sits, and the washes over it. Moving to
    * another screen blends one treatment into the next (`ScreenManager.washes`). */
  val treatment: Var[everyui.component.Treatment] = Var(everyui.component.Treatment.none)

  protected lazy val container: Container = {
    val c = new Container
    c.position.`type` @= PositionType.Relative
    c
  }

  /** What the screen shows, whole: where the focus stops looking for what took the place of something gone (Focus). */
  def screenRoot: Component = container

  override protected def init(): Task[Unit] = {
    if (!built) {
      built = true
      unbuilt = (container :: parts.all.filterNot(_ eq container)).collect { case c: Container => c -> c.children() }
    }
    super.init().flatMap { _ =>
      container.size.width := manager.host.size.width
      container.size.height := manager.host.size.height
      // the whole UI laid out in one pass once it is built, rather than a container at a time as it is put together
      LayoutPass.batched(createUI())
    }
  }

  // whether it has been built, and each container made with the screen (its own among them) with the children it had
  // before its first build: a rebuild puts them back so, for the build to add to again
  private var built: Boolean = false
  private var unbuilt: List[(Container, Vector[Component])] = Nil

  /** Built afresh (`ScreenManager.rebuild`): what the last build made is taken apart, a slice a turn, and what the
    * screen made as it was constructed (its fields) is kept, put back as it was before the first build, and `init`
    * (and so `createUI`) runs again over it. A field given a lasting reaction by `createUI` (a `bind`, an `attach`)
    * would have it twice: such a part is better made in `createUI` itself. */
  override protected def rebuild(): Task[Unit] = {
    // (the marks are read by a container placing its children: nothing changes until they are cleared)
    container.listMark = true
    parts.all.foreach(_.listMark = true)
    // what was made with the screen out of what the build put it in, and what the build added to it, which goes: only
    // on this screen (what else was made on the turn the screen was, an app bar say, is not the screen's to change)
    val detached = parts.all.flatMap(k => k.parent().toList.collect {
      case p: Container if !p.listMark && shows(p) => p -> k
    })
    val mine = unbuilt.filter((c, _) => (c eq container) || shows(c))
    val gone = mine.flatMap((c, was) => c.children().toList.filter(child => !child.listMark && !was.exists(_ eq child)))
    container.listMark = false
    parts.all.foreach(_.listMark = false)
    detached.foreach((p, k) => p.children -= k)
    mine.foreach((c, was) => c.children @= was.filterNot(_.takenApart))
    if (gone.nonEmpty) Component.disposeGradually(gone, () => everyui.render.Platform.collect(), getClass.getSimpleName)
    init()
  }

  /** Whether `c` is on this screen: in its container, however deep. */
  private def shows(c: Component): Boolean = {
    var at = c.parent()
    while (at.nonEmpty && !(at.get eq container)) at = at.get.parent()
    at.nonEmpty
  }

  /** Showing a screen swaps its container into the host, in front of the backdrop and its washes when those share the
    * host. */
  override protected def activate(): Task[Unit] = {
    manager.host.children @= manager.host.children().filter(c => (c eq manager.backdrop) || (c eq manager.washes)) :+
      container
    super.activate()
  }

  /** What has the focus when the screen activates. Every screen names one, so a remote always has somewhere to
    * start; returning to the screen with Back puts the focus back where it was instead. */
  // each component made while this screen is being made, for `dispose`: a field the screen holds but never shows
  // (an episode box on a film's page) is out of the container's reach, and bound to the theme it held the screen
  private val parts: ScreenParts = new ScreenParts
  Component.making @= Some(parts.adding)
  // its constructor's turn is over by the next
  everyui.render.Platform.later(() => Component.making @= None)

  /** Its container taken apart, and with it everything the screen built (Component.dispose), and what it made that
    * the container never held: a slice a turn (`Component.disposeGradually`), so a screen of thousands of components is
    * taken apart whole however long that takes, and then what it held is collected. */
  override protected def dispose(): Unit =
    Component.disposeGradually(container :: parts.take(), () => everyui.render.Platform.collect(), getClass.getSimpleName)

  def defaultFocus(): Component

  /** Whether the screen starts with the focus on `defaultFocus` where a pointer is used, though that is no field:
    * there a screen shown starts with nothing focused (nothing lit), unless the keys are moving the focus or it starts
    * at something typed into (`Focus.starts`). A remote's platform always starts there. */
  def startsFocused: Boolean = false

  def createUI(): Task[Unit]
}

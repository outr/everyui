package everyui.capture

import everyui.Color
import everyui.async.Task
import everyui.component.Component
import everyui.draw.{Drawing, Figure}
import everyui.render.SnapshotAccess

/** A picture of what the app draws, as a PNG (`SnapshotImage`): the whole window, or one component and everything in
  * it, `scale` times its size in pixels (2 for a sharper copy). A component is drawn as it is laid out, over the colour
  * behind it (the nearest solid background round it, else the window's), whatever is over it or scrolled out
  * of sight. The desktop takes it from JavaFX (`Node.snapshot`), Android from its views (the window through PixelCopy,
  * with video and hardware layers), and the web draws the page's own elements again, into a canvas. A Roku and an
  * Apple TV take none (`supported` false), and answer None.
  *
  * A `Drawing`'s figures are drawn straight into a PNG (`drawing`, `figures`), crisp at any scale: a graph exported as
  * a picture. The web, the desktop and Android draw them (`drawable`).
  *
  * {{{
  *   if (Snapshot.supported) Snapshot.window().map(_.foreach(shot => send(FormPart.file("shot", "shot.png", shot.png,
  *     shot.mime)))).start()
  *   Snapshot.drawing(chart, scale = 3.0, background = Some(Color.White)).map(_.foreach(_.save("chart.png"))).start()
  * }}}
  *
  * On the web a component is drawn from its elements as an SVG `foreignObject` draws HTML: the page's styles as they
  * stand, its fonts and its pictures carried in. What cannot be carried is left out, as nothing: a picture of another
  * origin that does not allow the page to read it (CORS), a video whose frames cannot be read, and what an embed or
  * web view shows (another page's). Scroll bars are not drawn. */
object Snapshot {
  /** The picture a snapshot answers with. */
  type Image = SnapshotImage

  /** Whether this platform can take a picture of the window or a component. */
  def supported: Boolean = SnapshotAccess.supported()

  /** Whether this platform can draw a drawing's figures into a PNG. */
  def drawable: Boolean = SnapshotAccess.draws()

  /** `c` and everything in it, `scale` times its size; None where it has no size, or it cannot be taken here. */
  def component(c: Component, scale: Double = 1.0): Task[Option[SnapshotImage]] =
    Task.async[Option[SnapshotImage]](done => SnapshotAccess.node(c.node, scale, done))

  /** The whole window as the person sees it, dialogs and menus with it, `scale` times its size. */
  def window(scale: Double = 1.0): Task[Option[SnapshotImage]] =
    Task.async[Option[SnapshotImage]](done => SnapshotAccess.window(scale, done))

  /** `d`'s figures at its size, over `background` (clear where None). */
  def drawing(d: Drawing, scale: Double = 1.0, background: Option[Color] = None): Task[Option[SnapshotImage]] =
    figures(d.figures(), d.size.width(), d.size.height(), scale, background)

  /** `figures` drawn in a box `width` by `height` (in their own pixels), `scale` times that size, over `background`
    * (clear where None): for a picture of a drawing that is not shown, or one drawn larger than it is shown. */
  def figures(figures: List[Figure], width: Double, height: Double, scale: Double = 1.0,
              background: Option[Color] = None): Task[Option[SnapshotImage]] =
    Task.async[Option[SnapshotImage]] { done =>
      if (width * scale < 0.5 || height * scale < 0.5) done(None)
      else SnapshotAccess.figures(figures, width, height, scale, background, done)
    }
}

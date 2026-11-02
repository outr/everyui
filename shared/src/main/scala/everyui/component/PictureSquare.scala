package everyui.component

import everyui.component.types.Semantics
import reactify.*

/** The picture at `source` (a site-absolute path or an address) fitted in a square `side` pixels across at its own
  * proportions, centred, and `said` read out for it and shown on a hover: an emoji of the app's own, an avatar beside a
  * completion. Nothing shows where it cannot be had. */
class PictureSquare(source: String, side: Double, said: String) extends Container {
  private val view = new ImageView

  size.width @= side
  size.height @= side
  view.alt @= said
  if (said.nonEmpty) {
    tooltip @= said
    semantics @= Semantics.image(said)
  }
  children += view
  everyui.image.Image(source).map { loaded =>
    if (!takenApart && loaded.width > 0.0 && loaded.height > 0.0) {
      val scale = math.min(side / loaded.width, side / loaded.height)
      view.image @= loaded
      // (sized once the picture is in: taking it sets the view to its natural size)
      view.size.width @= loaded.width * scale
      view.size.height @= loaded.height * scale
      view.position.left @= (side - loaded.width * scale) / 2.0
      view.position.top @= (side - loaded.height * scale) / 2.0
    }
  }.startUnit()

  override def kind: String = "PictureSquare(" + source + ")"
}

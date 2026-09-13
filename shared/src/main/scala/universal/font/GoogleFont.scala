package universal.font

import universal.async.Task
import universal.component.types.FontWeight
import universal.render.Platform

/** youi's `GoogleFont` catalogue, limited to the faces universal-ui bundles (`assets/fonts`). */
trait GoogleFont:
  def family: String
  def weights: List[GoogleFontWeight]

object GoogleFont:
  object `Open Sans` extends GoogleFont:
    override lazy val family: String = "Open Sans"
    lazy val `regular`: GoogleFontWeight = GoogleFontWeight(this, "regular")
    lazy val `600`: GoogleFontWeight = GoogleFontWeight(this, "600")

    override lazy val weights: List[GoogleFontWeight] = List(`regular`, `600`)

case class GoogleFontWeight(font: GoogleFont, name: String):
  /** youi fetches the face from Google Fonts; here it is bundled, so this waits for it to be usable. */
  def load(): Task[GoogleFontWeight] =
    Task.async[GoogleFontWeight](done => Platform.loadFont(font.family, FontWeight.fromName(name).value, () => done(this)))

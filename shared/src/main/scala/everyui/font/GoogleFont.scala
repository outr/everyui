package everyui.font

import everyui.async.Task
import everyui.component.types.FontWeight
import everyui.render.Platform

/** youi's `GoogleFont` catalogue, limited to the faces everyui bundles (`assets/fonts`). */
trait GoogleFont {
  def family: String
  def weights: List[GoogleFontWeight]
}

object GoogleFont {
  object `Open Sans` extends GoogleFont {
    override lazy val family: String = "Open Sans"
    lazy val `regular`: GoogleFontWeight = GoogleFontWeight(this, "regular")
    lazy val `600`: GoogleFontWeight = GoogleFontWeight(this, "600")

    override lazy val weights: List[GoogleFontWeight] = List(`regular`, `600`)
  }
}


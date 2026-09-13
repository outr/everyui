package everyui.component.types

import everyui.font.GoogleFontWeight

import scala.language.implicitConversions

/** A CSS font weight. One converted from a [[GoogleFontWeight]] also carries its family, which is how youi's
  * `font.weight @= googleFontWeight` sets both at once. */
class FontWeight(val value: Int, val family: String)

object FontWeight:
  lazy val Normal: FontWeight = new FontWeight(400, "")
  lazy val Bold: FontWeight = new FontWeight(700, "")

  /** A Google Fonts weight name: "regular", "300" ... "800". */
  def fromName(name: String): FontWeight =
    if name == "300" then new FontWeight(300, "")
    else if name == "500" then new FontWeight(500, "")
    else if name == "600" then new FontWeight(600, "")
    else if name == "700" then new FontWeight(700, "")
    else if name == "800" then new FontWeight(800, "")
    else Normal

  implicit def fromGoogleFontWeight(weight: GoogleFontWeight): FontWeight =
    new FontWeight(fromName(weight.name).value, weight.font.family)

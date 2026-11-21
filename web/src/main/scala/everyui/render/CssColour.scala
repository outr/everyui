package everyui.render

/** A colour as the page is given it: EveryUI writes one seen through as `#rrggbbaa`, which no engine before Chromium
  * 62 reads (webOS 3's 38, webOS 4's 53, Tizen 4's 56), and a style given a value it does not read keeps the one it had,
  * so every scrim, wash, edge and shadow seen through was not drawn at all; there it is written `rgba(...)`. Plain
  * Scala, so its rules are specified on the JVM. */
object CssColour {
  /** `colour` as an engine without `#rrggbbaa` reads it: that one as `rgba(r, g, b, a)`, its alpha to three places;
    * anything else as it is. */
  def written(colour: String): String =
    if (colour.length != 9 || colour.charAt(0) != '#' || !colour.drop(1).forall(c => Character.digit(c, 16) >= 0))
      colour
    else {
      def channel(i: Int): Int = Integer.parseInt(colour.substring(i, i + 2), 16)
      val alpha = math.round(channel(7) / 255.0 * 1000.0) / 1000.0
      "rgba(" + channel(1) + ", " + channel(3) + ", " + channel(5) + ", " + alpha.toString + ")"
    }
}


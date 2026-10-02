package everyui.sbt

/** A face an EveryUI web app draws with, as its build declares it (`everyuiWebFaces`), for WebFaces to ship: its
  * family, weight and slant as the app's `Face` names them (CSS's `@font-face`), and its file, by name, among
  * EveryUI's fonts and the app's (`everyuiAppFonts`).
  *
  * A face at the `start` is fetched with the page (`<link rel="preload">`) and waited for before the first frame
  * draws, as the bundled ones always were; one declared `later` is fetched only when words on the page are drawn in
  * it, and never waited for, so a face the app declares but does not draw costs nothing. */
final case class WebFace(family: String, weight: Int, file: String, italic: Boolean = false, start: Boolean = true,
                         glyphs: WebGlyphs = WebGlyphs.Scripts) {
  /** The same face, fetched only once words are drawn in it. */
  def later: WebFace = copy(start = false)
  /** CSS's way of naming it to `document.fonts`: `italic 400 Roboto`. */
  def css: String = (if (italic) "italic " else "normal ") + weight + " " + family
}

object WebFace {
  // EveryUI's own faces, in the everyui-web jar (everyuiWebFonts)
  val OpenSans: WebFace = WebFace("Open Sans", 400, "OpenSans-Regular.ttf")
  val OpenSansSemiBold: WebFace = WebFace("Open Sans", 600, "OpenSans-SemiBold.ttf")
  val MaterialIcons: WebFace = WebFace("Material Icons", 400, "MaterialIcons-Regular.ttf", glyphs = WebGlyphs.Reached)
  val RobotoMono: WebFace = WebFace("Roboto Mono", 400, "RobotoMono-Regular.ttf").later

  /** What an app has with no faces of its own, as CONSUMING.md's page had them: Open Sans and Material Icons at the
    * start, Roboto Mono (code) once it is drawn. */
  val bundled: Seq[WebFace] = Seq(OpenSans, OpenSansSemiBold, MaterialIcons, RobotoMono)
}

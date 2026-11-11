package everyui.render

import everyui.image.SvgImage
import everyui.log.LoggedAddress
import everyui.tvos.*

/** What the runtime's picture loader (swift/runtime/UniversalRuntime.swift's Pictures) asks of the program: an SVG
  * drawn with EveryUI's own reader (VectorArt) on Core Graphics (AppleArtPen), and a picture that would not load said
  * once for its address, as Android says it. Lent the runtime with the first picture. */
object ApplePictures {
  private var lent = false
  // the addresses already said to have failed
  private var told: Map[String, Boolean] = Map.empty

  def lend(): Unit = if (!lent) {
    lent = true
    uiPictureHooks((uri: String, text: String, pixels: Double) => draw(uri, text, pixels),
      (uri: String, why: String) => failed(uri, why))
  }

  /** The SVG `text` at `uri` drawn `pixels` on its longer side, and no smaller than its own size times the screen's
    * scale; nothing where it holds no SVG of any size (the runtime finds no canvas, and the picture is undecoded). */
  def draw(uri: String, text: String, pixels: Double): Unit = SvgImage.art(text).foreach { art =>
    val (nw, nh) = art.natural
    val longest = math.max(nw, nh)
    val own = Platform.pixelRatio()
    val factor = if (longest <= 0.0) own else math.max(own, pixels / longest)
    val (w, h) = SvgImage.pixels(art, nw * factor, nh * factor)
    uiArtBegin(w.toDouble, h.toDouble, nw, nh)
    art.paint(new AppleArtPen, w.toDouble, h.toDouble)
    SvgImage.drawn(uri, art)
  }

  /** A picture that would not load, said once for its address: "" is one that came but was neither a picture nor an
    * SVG. Its view stays empty, and an Avatar keeps its initial. */
  def failed(uri: String, why: String): Unit =
    if (why.isEmpty) SvgImage.undecoded(uri)
    else if (!told.contains(uri)) {
      told = told + (uri -> true)
      Platform.log("Image: " + LoggedAddress(uri) + " could not be loaded (" + why.replace(uri, LoggedAddress(uri)) +
        ")")
    }
}

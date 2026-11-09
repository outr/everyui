package everyui.image

import everyui.draw.VectorArt
import everyui.render.Platform

/** An SVG shown as a picture (`Image`, `ImageView`, `Avatar`) where the platform's own decoder reads none: Android and
  * the desktop read the file as [[VectorArt]] (`VectorArt.parse` says what of one is drawn) and paint it with their own
  * vector drawing, at the size it is drawn at times the screen's density, so it stays sharp. The web's `<img>` reads
  * SVG itself; a Roku and an Apple TV draw none (`undrawn`). What is said of a picture is logged once for each address
  * (`logged`). */
object SvgImage {
  /** The most pixels an SVG is drawn at on its longer side, however large it is shown. */
  val largest: Double = 4096.0

  /** Whether `address`, with the `contentType` it came with where it came with one, names an SVG: image/svg+xml, a
    * `data:image/svg+xml` address, or a path ending in .svg (its query and fragment aside). */
  def named(address: String, contentType: String = ""): Boolean = {
    val lower = address.toLowerCase
    contentType.toLowerCase.contains("image/svg+xml") || lower.startsWith("data:image/svg+xml") ||
      (!lower.startsWith("data:") && lower.takeWhile(c => c != '?' && c != '#').endsWith(".svg"))
  }

  /** Whether a file's first bytes, as text, are an SVG's: `<svg`, or an XML declaration, near the start. */
  def sniffed(head: String): Boolean = {
    val start = head.dropWhile(c => c <= ' ' || c == '﻿')
    start.startsWith("<?xml") || start.startsWith("<svg") || head.take(1024).contains("<svg")
  }

  /** `text` as art: None where it holds no SVG, or one of no size. Safe on any thread. */
  def art(text: String): Option[VectorArt] = VectorArt.parse(text).filter(a => a.width > 0.0 && a.height > 0.0)

  /** What of the SVG at `url` was not drawn (a gradient, text, a filter), logged once for it, where there is any. */
  def drawn(url: String, art: VectorArt): Unit =
    if (art.skipped.nonEmpty) once("Image: " + shown(url) + " is drawn without its " + art.skipped.mkString(", "))

  /** The picture at `url` came, but neither the platform's decoder nor the SVG reader made anything of it: logged once
    * for it. */
  def undecoded(url: String): Unit = once("Image: " + shown(url) + " could not be decoded (not a raster image or SVG?)")

  /** The SVG at `url` cannot be drawn on `platform` (a Roku, an Apple TV), whose image views read none: logged once
    * for it. */
  def undrawn(url: String, platform: String): Unit =
    once("Image: " + shown(url) + " is an SVG, which " + platform + " cannot draw (an Avatar keeps its initial)")

  /** The pixels to draw `art` at for a box `wide` by `high` pixels: in its own proportion, covering the box, and no
    * more than `largest` on its longer side. */
  def pixels(art: VectorArt, wide: Double, high: Double): (Int, Int) = {
    val (w, h) = art.natural
    if (w <= 0.0 || h <= 0.0) (0, 0)
    else {
      val factor = math.min(math.max(wide / w, high / h), largest / math.max(w, h))
      (math.max(1, math.ceil(w * factor - 1e-6).toInt), math.max(1, math.ceil(h * factor - 1e-6).toInt))
    }
  }

  /** What has been logged, oldest first: what a spec reads. */
  private[everyui] def logged: List[String] = told.reverse

  private var told: List[String] = Nil

  private def once(message: String): Unit = if (!told.contains(message)) {
    told = message :: told
    Platform.log(message)
  }

  // an address as it is logged: a data: address by its start alone
  private def shown(url: String): String = if (url.startsWith("data:") && url.length > 48) url.take(48) + "..." else url
}

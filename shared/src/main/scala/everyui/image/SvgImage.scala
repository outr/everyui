package everyui.image

import everyui.draw.VectorArt
import everyui.log.LoggedAddress
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

  /** How many of a file's first bytes `sniffed` is to be given, at least: room for a long comment (a licence) before
    * the root. */
  val sniffedBytes: Int = 4096

  /** Whether a file's first bytes, as text, are an SVG's: its first element is `<svg>` (`<svg:svg>` too, under any
    * prefix), after a byte-order mark, white space, XML declarations and processing instructions (`<?xml …?>`),
    * comments and a DOCTYPE (with its internal subset), any of them. Other XML (`<rss>`, an Android `<vector>`, an
    * HTML page) is not, nor are first bytes that end before an element starts. */
  def sniffed(head: String): Boolean = {
    var at = 0
    var root = false
    var reading = head != null
    while (reading) {
      while (at < head.length && (head.charAt(at) <= ' ' || head.charAt(at) == '﻿')) at += 1
      if (head.startsWith("<?", at)) at = past(head, head.indexOf("?>", at + 2), 2)
      else if (head.startsWith("<!--", at)) at = past(head, head.indexOf("-->", at + 4), 3)
      else if (head.startsWith("<!", at)) at = past(head, declarationEnd(head, at), 1)
      else {
        reading = false
        if (head.startsWith("<", at)) {
          var end = at + 1
          while (end < head.length && head.charAt(end) > ' ' && head.charAt(end) != '/' && head.charAt(end) != '>')
            end += 1
          // the name must have ended within the bytes: "<svgfoo" cut at "<svg" is not "<svg"
          root = end < head.length && VectorArt.localName(head.substring(at + 1, end)).contains("svg")
        }
      }
    }
    root
  }

  /** `text` as art: None where it holds no SVG, or one of no size, never an exception, whatever it holds
    * (`VectorArt.parse`). Safe on any thread. */
  def art(text: String): Option[VectorArt] =
    try VectorArt.parse(text).filter(a => a.width > 0.0 && a.height > 0.0)
    catch {
      case e: Exception =>
        Platform.log("SvgImage: an SVG could not be read (" + e.toString + ")")
        None
    }

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

  // an address as it is logged: without its query, or a data: address's contents (LoggedAddress)
  private def shown(url: String): String = LoggedAddress(url)

  // the place after a mark found at `found` `length` long; the end of `head` where it was not found
  private def past(head: String, found: Int, length: Int): Int = if (found < 0) head.length else found + length

  // the `>` that ends a declaration (`<!DOCTYPE svg …>`) starting at `from`, past an internal subset in [ ]; -1 if none
  private def declarationEnd(head: String, from: Int): Int = {
    val close = head.indexOf('>', from)
    val subset = head.indexOf('[', from)
    if (subset >= 0 && (close < 0 || subset < close)) {
      val ended = head.indexOf(']', subset)
      if (ended < 0) -1 else head.indexOf('>', ended)
    } else close
  }
}

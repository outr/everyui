package everyui.render

import everyui.Color
import everyui.text.{CaptionStyle, TextRun}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** A video's subtitles drawn by EveryUI where the browser's own would not be seen ([[CueDrawing]]: a TV, whose player
  * draws the picture on a plane under the page): the chosen text track's active cues (`activeCues`, the track
  * `hidden`), each as `CueText` reads its words and where `CuePlace` puts it, in a box laid right after the `<video>`
  * in the box that holds it, as large as it and where it is. So it is over the picture and under what comes after the
  * video (a player's controls), and before Chromium 62 it is a layer of its own as they are
  * (`RenderNode.overVideoStyle`, `video ~ *`). It follows the video: its size and place as they are set (a
  * MutationObserver on its style), and the box holding it as it moves (`WebMedia.moved`: a mini player's). Drawn again
  * as the cues change (`cuechange`), and as [[WebMedia]] hears the element play, seek and move on; nothing is made until
  * a track is followed.
  *
  * The look is `WebMedia.cueStyle` where the app gives one (a [[CaptionStyle]], as the device's caption settings are
  * on Android), else white words edged in black on a dark box, 4.5% of the video's height, at its foot, 5% in. ES 5.1
  * throughout (webOS 3's Chromium 38), and a cue's settings read where the engine has them, its words from `text`. */
final class WebCues(video: html.Element) {
  private var box: html.Element = null
  private var track: js.Dynamic = null
  // the cues drawn now, to be drawn again only once they change
  private var drawn: List[js.Dynamic] = Nil
  private var drawnStyle: Option[CaptionStyle] = None
  private var tall = -1
  private var watcher: js.Dynamic = null
  private val heard: js.Function1[dom.Event, Unit] = _ => draw()

  /** The text track whose cues are drawn from now (null for none), drawn now. */
  def follow(chosen: js.Dynamic): Unit = {
    if (!(chosen eq track)) {
      listen(track, on = false)
      track = chosen
      listen(track, on = true)
      if (box != null) box.setAttribute(WebCues.attribute, label)
    }
    draw()
  }

  /** The cues active now drawn, where they changed (or the look did). */
  def draw(): Unit = {
    val now = active
    val style = WebMedia.cueStyle()
    val fresh = box == null && (track != null || now.nonEmpty)
    if (fresh) made()
    if (box != null) {
      val restyled = style != drawnStyle
      if (restyled) {
        drawnStyle = style
        tall = -1
      }
      val changed = restyled || now.size != drawn.size || now.zip(drawn).exists(p => !(p._1 eq p._2))
      // laid again as it is made, as the cues change and where it is no longer beside the video; between those its size
      // and place follow the video's style as it is set (the observer), not read at every frame the player says
      if (fresh || changed || watcher == null || !(video.nextSibling eq box)) place()
      if (changed) {
        drawn = now
        while (box.firstChild != null) box.removeChild(box.firstChild)
        val look = style.getOrElse(WebCues.look)
        val (ordinary, placed) = now.map(c => (c, WebCues.placeOf(c))).partition(_._2.ordinary)
        if (ordinary.nonEmpty) {
          val column = div("position:absolute;left:0;right:0;bottom:" + CuePlace.margin + "%")
          ordinary.foreach { case (c, p) => column.appendChild(cue(c, p, look)) }
          box.appendChild(column)
        }
        placed.foreach { case (c, p) =>
          val one = cue(c, p, look)
          one.style.position = "absolute"
          one.style.marginLeft = "0"
          one.style.left = p.left + "%"
          if (p.percent >= 0.0) {
            one.style.top = p.percent + "%"
            if (p.shift > 0.0) one.style.setProperty("transform", "translateY(-" + p.shift + "%)")
          }
          else if (p.edge == CuePlace.Top) {
            one.style.top = CuePlace.margin + "%"
            one.style.marginTop = (p.lines * WebCues.lineHeight) + "em"
          }
          else {
            one.style.bottom = CuePlace.margin + "%"
            one.style.marginBottom = (p.lines * WebCues.lineHeight) + "em"
          }
          box.appendChild(one)
        }
      }
    }
  }

  /** The box laid right after the video again, in the box holding it now, as large as it and where it is. */
  def place(): Unit = if (box != null) {
    val parent = video.parentNode
    if (parent == null) { if (box.parentNode != null) box.parentNode.removeChild(box) }
    else if (!(box.parentNode eq parent) || !(video.nextSibling eq box)) parent.insertBefore(box, video.nextSibling)
    WebCues.copied.foreach(p => box.style.setProperty(p, styled(p)))
    val turned = styled("transform")
    if (turned.nonEmpty && WebFeatures.videoSquashes) box.style.setProperty("transform", turned + " translateZ(0px)")
    val now = video.offsetHeight.toInt
    if (now != tall) {
      tall = now
      val scale = drawnStyle.map(_.scale).filter(_ > 0.0).getOrElse(1.0)
      box.style.fontSize = (math.max(8.0, now * WebCues.size * scale)).toString + "px"
    }
  }

  /** Nothing drawn and nothing held: the box taken off the page, the track and the video no longer heard. */
  def clear(): Unit = {
    listen(track, on = false)
    track = null
    drawn = Nil
    drawnStyle = None
    tall = -1
    if (watcher != null) {
      watcher.disconnect()
      watcher = null
    }
    if (box != null) {
      if (box.parentNode != null) box.parentNode.removeChild(box)
      box = null
      WebCues.live -= 1
    }
  }

  // the cues active now on the track followed, in the order they start
  private def active: List[js.Dynamic] =
    if (track == null || js.isUndefined(track)) Nil
    else {
      val list = track.activeCues
      if (js.isUndefined(list) || list == null) Nil
      else (0 until list.length.asInstanceOf[Int]).toList.map(i => list.selectDynamic(i.toString))
        .filter(c => !js.isUndefined(c) && c != null).sortBy(c => number(c.startTime).getOrElse(0.0))
    }

  private def made(): Unit = {
    box = div("position:absolute;pointer-events:none;overflow:visible;margin:0;padding:0;line-height:" +
      WebCues.lineHeight + ";white-space:normal")
    box.className = WebCues.name
    box.setAttribute("aria-hidden", "true")
    box.setAttribute(WebCues.attribute, label)
    WebCues.live += 1
    WebFeatures.global("MutationObserver").foreach { observer =>
      val seen: js.Function0[Unit] = () => place()
      watcher = js.Dynamic.newInstance(observer)(seen)
      watcher.observe(video, js.Dynamic.literal(attributes = true, attributeFilter = js.Array("style")))
    }
  }

  // one cue: a block across its place, its lines each on the dark box, its runs styled
  private def cue(c: js.Dynamic, p: CuePlace, look: CaptionStyle): html.Element = {
    val text = if (js.typeOf(c.text) == "string") c.text.asInstanceOf[String] else ""
    val block = div("margin-left:" + p.left + "%;width:" + p.width + "%;text-align:" + p.align +
      (if (look.window.alpha > 0.0) ";background-color:" + look.window.toRGBA else ""))
    block.setAttribute("dir", "auto")
    val lines = dom.document.createElement("span").asInstanceOf[html.Element]
    lines.style.cssText = "color:" + look.text.toRGBA + ";background-color:" + look.background.toRGBA +
      ";padding:0 0.25em;-webkit-box-decoration-break:clone;box-decoration-break:clone" +
      WebCues.edge(look.edge, look.edgeColor).map(";text-shadow:" + _).getOrElse("")
    CueText.runs(text).foreach { r =>
      val parts = r.text.split("\n", -1)
      parts.zipWithIndex.foreach { case (part, i) =>
        if (i > 0) lines.appendChild(dom.document.createElement("br"))
        if (part.nonEmpty) lines.appendChild(run(r, part))
      }
    }
    block.appendChild(lines)
    block
  }

  private def run(r: TextRun, words: String): dom.Node =
    if (!r.italic && r.weight == 0 && !r.underline && r.color.isEmpty) dom.document.createTextNode(words)
    else {
      val span = dom.document.createElement("span").asInstanceOf[html.Element]
      if (r.italic) span.style.fontStyle = "italic"
      if (r.weight > 0) span.style.fontWeight = r.weight.toString
      if (r.underline) span.style.textDecoration = "underline"
      if (r.color.nonEmpty) span.style.color = r.color
      span.textContent = words
      span
    }

  // the video's own style for `property`, "" where it has none (Chromium 38 answers null)
  private def styled(property: String): String = Option(video.style.getPropertyValue(property)).getOrElse("")

  private def div(css: String): html.Element = {
    val d = dom.document.createElement("div").asInstanceOf[html.Element]
    d.style.cssText = css
    d
  }

  private def listen(t: js.Dynamic, on: Boolean): Unit =
    if (t != null && !js.isUndefined(t) && js.typeOf(t.addEventListener) == "function") {
      if (on) t.addEventListener("cuechange", heard) else t.removeEventListener("cuechange", heard)
    }

  private def label: String =
    if (track == null || js.isUndefined(track) || js.typeOf(track.label) != "string") ""
    else track.label.asInstanceOf[String]

  private def number(v: js.Dynamic): Option[Double] =
    if (js.typeOf(v) == "number") Some(v.asInstanceOf[Double]).filterNot(_.isNaN) else None
}

object WebCues {
  /** The class of each box of subtitles, which `RenderNode.insert` counts no child of its box. */
  val name: String = "everyui-cues"

  /** The attribute on each box saying the label of the track drawn in it, for a look from a check or a TV's
    * inspector. */
  val attribute: String = "data-everyui-cues"

  /** How many boxes of subtitles are on the page or held: 0 where none is, so `RenderNode.insert` counts as before. */
  private[render] var live: Int = 0

  /** How tall the letters are, as a share of the video's height, and the line, as a multiple of them. */
  val size: Double = 0.045
  val lineHeight: Double = 1.25

  /** The look where the app gives none: white words edged in black on a dark box, no window. */
  val look: CaptionStyle =
    CaptionStyle(Color.White, Color.Black.withAlpha(0.6), Color.Clear, "outline", Color.Black, 1.0)

  // what of the video's own style the box takes, so it lies over it
  private val copied: List[String] =
    List("left", "top", "right", "bottom", "width", "height", "visibility", "display", "opacity", "transform")

  /** The CSS `text-shadow` for a caption edge, None for none. */
  def edge(kind: String, colour: Color): Option[String] = {
    val c = colour.toRGBA
    kind match {
      case "outline" => Some(List("-1", "1").flatMap(x => List("-1", "1").map(y =>
        x + "px " + y + "px 0 " + c)).mkString(", ") + ", 0 0 0.12em " + c)
      case "shadow" => Some("0.06em 0.06em 0.12em " + c)
      case "raised" => Some("0.05em 0.05em 0 " + c)
      case "depressed" => Some("-0.05em -0.05em 0 " + c)
      case _ => None
    }
  }

  /** Where a cue goes, from the settings it has (an older engine's cue may lack any of them, or `align` as
    * `middle`), and the keypad place SubRip's override gives it. */
  def placeOf(c: js.Dynamic): CuePlace = {
    def text(v: js.Dynamic): String = if (js.typeOf(v) == "string") v.asInstanceOf[String] else ""
    def number(v: js.Dynamic): Option[Double] =
      if (js.typeOf(v) == "number") Some(v.asInstanceOf[Double]).filterNot(_.isNaN) else None
    val words = text(c.text)
    CuePlace.of(
      line = number(c.line),
      snapToLines = js.typeOf(c.snapToLines) != "boolean" || c.snapToLines.asInstanceOf[Boolean],
      lineAlign = text(c.lineAlign),
      position = number(c.position),
      positionAlign = text(c.positionAlign),
      size = number(c.size).getOrElse(100.0),
      align = text(c.align),
      keypad = CueText.anchor(words))
  }
}

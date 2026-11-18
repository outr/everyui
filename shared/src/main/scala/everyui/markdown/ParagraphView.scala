package everyui.markdown

import everyui.Color
import everyui.component.{Component, Container, Link, RichText, TextProbes}
import everyui.component.types.WhiteSpace
import everyui.event.Key
import everyui.layout.VerticalLayout
import everyui.menu.{Menu, MenuItem}
import everyui.text.TextRun
import everyui.theme.Theme
import everyui.tooltip.Tooltip
import reactify.*

/** A paragraph or a heading of a `MarkdownView`: its lines wrapped to `width`, a picture on a line of its own. New
  * words for it (`inlines`, as an answer streams in) are drawn in place while its lines and pictures stay as they
  * were. Where it holds links it takes the focus, and Enter (a remote's OK) follows its link, or offers its links in a
  * menu where it has several; a click or a tap on a link follows it where the platform draws links. What its links say
  * (a title, or the app's words: `Link.describe`) shows as the pointer rests on one and, in a `Tooltip`, as the keys
  * bring the focus to it where the focus shows tips, and beside each in its menu. Its words run the
  * way the view says (`MarkdownContext.direction`), and a word too long for a line (a URL, a path) is broken where it
  * must be. Passages are marked in its lines (`Markable`) unless it is not `markable` (raw HTML, shown as code). */
class ParagraphView(content: List[MdInline], ctx: MarkdownContext, width: => Double, points: Double,
                    weight: Int = 400, markable: Boolean = true) extends Container with Markable {
  /** What it shows. */
  val inlines: Var[List[MdInline]] = own(Var(content))
  // its links, and the mentions a press on answers (`MarkdownContext.pressesMentions`), each once
  private val links: Val[List[(String, String)]] = own(Val(MarkdownRuns.links(inlines()) ++
    (if (!ctx.pressesMentions()) Nil
      else MarkdownRuns.parts(inlines(), points, weight, ctx.breaks, ctx.mentions(), pressable = true).flatMap(_.runs)
        .filter(r => everyui.component.RichText.acts(r.href)).map(r => (r.text, r.href))
        .foldLeft(List.empty[(String, String)])((out, l) => if (out.exists(_._2 == l._2)) out else out :+ l))))
  private var shape = ""
  // where the view measures (`MarkdownContext.measures`): each line again on one line, out of sight, its width kept and
  // what measured it let go
  private lazy val probes: TextProbes = new TextProbes(this)
  private val pictures: Var[Boolean] = own(Var(false))

  /** How wide it would be drawn on its own: its widest line, or none where it holds a picture (which takes the
    * width) or the view does not measure, nor until each line's width has been read (`TextProbes`), where a line not
    * yet read is 0 wide for want of the read: a message's bubble sized by it was laid out at no width, its words read
    * there, and all of it again once they were. As its words change it is as wide as they were until they are read
    * again, or none where they are other lines. */
  val natural: Val[Option[Double]] = own(Val(
    if (!ctx.measures || pictures()) None else probes.widths().map(_.foldLeft(0.0)((w, l) => math.max(w, l)))))
  // the runs of each line of words, measured where the view measures
  private val measured: Val[Vector[List[TextRun]]] = own(Val(if (!ctx.measures) Vector.empty
    else MarkdownRuns.parts(inlines(), points, weight, ctx.breaks, ctx.mentions(), ctx.pressesMentions()).toVector
      .filter(_.image.isEmpty).map(_.runs)))
  private var lineCount = -1
  // the passages marked in each line, once there have been some (`mark`): until then nothing is kept for them
  private var marks: Option[Var[Vector[Vector[MarkSpan]]]] = None

  size.width := width
  layout @= Some(VerticalLayout(0.0))
  focusable := links().nonEmpty
  radius @= 4.0
  backgroundColor := (if (focusShown()) Theme.controlLit() else Color.Clear)
  inlines.attach(_ => build())
  build()
  if (ctx.measures) {
    measured.attach(measure)
    measure(measured())
  }

  override def keepsKey(key: Key): Boolean = key == Key.Enter && links().nonEmpty
  event.key.down.attach(k => if (k.key == Key.Enter) openLinks())

  // what its links say (`tip`) shown as the keys bring the focus to it, where the focus shows tips: a
  // pointer resting on a link has the run's own
  private lazy val pointerOver: Val[Boolean] = event.pointer.over
  // followed from the first link, before the focus asks, and not by a paragraph with none
  if (links().nonEmpty) pointerOver
  links.attach(l => if (l.nonEmpty) pointerOver)
  focusShown.attach { on =>
    if (on && Tooltip.onFocus() && !pointerOver()) Tooltip.askFor(this, () => tip)
    else if (!on) Tooltip.hideFor(this)
  }
  onDispose(() => Tooltip.hideFor(this))

  /** What its links say as the pointer rests on them (a Markdown link's title, or the app's words for it:
    * `Link.describe`), by `href`, for those that say something. */
  def titles: Map[String, String] = MarkdownRuns.parts(inlines(), points, weight, ctx.breaks, ctx.mentions(),
      ctx.pressesMentions()).flatMap(_.runs).filter(_.href.nonEmpty).map(Link.titled)
    .foldLeft(Map.empty[String, String])((out, r) =>
      if (r.title.isEmpty || out.contains(r.href)) out else out.updated(r.href, r.title))

  /** What the focus on it shows in a tip: its link's words where it has one, each link's words after its own where it
    * has several (those with none left out), and nothing where none say anything. */
  def tip: Option[String] = {
    val said = titles
    val all = links().filter(l => said.contains(l._2))
    if (all.isEmpty) None
    else if (links().size == 1) Some(said(all.head._2))
    else Some(all.map(l => (if (l._1.trim.isEmpty) "" else l._1.trim + ": ") + said(l._2)).mkString("; "))
  }

  /** Its link followed, or its links offered to choose from, each with what it says (`titles`) or its address. */
  def openLinks(): Unit = {
    val all = links()
    if (all.size == 1) ctx.press(all.head._2, this)
    else if (all.size > 1) {
      val said = titles
      Menu.below(this, all.map(l => MenuItem(if (l._1.trim.isEmpty) l._2 else l._1, () => ctx.press(l._2, this),
        detail = said.getOrElse(l._2, if (Link.appOwn(l._2)) "" else l._2))))
    }
  }

  /** Its lines made again where they are no longer the same lines and pictures; otherwise their words follow
    * `inlines` where they stand. */
  private def build(): Unit = {
    val parts = MarkdownRuns.parts(inlines(), points, weight, ctx.breaks, ctx.mentions(), ctx.pressesMentions())
    val now = parts.map(p => p.image.map(i => "i" + i.src).getOrElse("t")).mkString("|")
    if (now != shape) {
      shape = now
      pictures @= parts.exists(_.image.nonEmpty)
      val old = children().filterNot(c => ctx.measures && probes.held.exists(_ eq c))
      val lines = parts.indices.toVector.map(i => parts(i).image match {
        case Some(image) => new MarkdownImage(image, ctx, size.width())
        case None => line(i)
      })
      children @= (if (ctx.measures) lines ++ probes.held else lines)
      old.foreach(_.dispose())
    }
  }

  // each line's words measured on one line, as they would be drawn on their own: afresh where they are other lines
  private def measure(lines: Vector[List[TextRun]]): Unit = {
    val fresh = lines.size != lineCount
    lineCount = lines.size
    probes.measure(lines.map(shown => new RichText {
      whiteSpace @= WhiteSpace.NoWrap
      font.size @= points
      direction @= ctx.direction
      wrapAnywhere @= true
      runs @= shown
    }), fresh)
  }

  def markWords: Vector[String] = if (!markable) Vector.empty
    else MarkdownRuns.parts(inlines(), points, weight, ctx.breaks, ctx.mentions(), ctx.pressesMentions()).toVector
      .map(Passages.words)

  def mark(spans: Vector[Vector[MarkSpan]]): Unit = marks match {
    case Some(v) => if (v() != spans) v @= spans
    case None => if (spans.exists(_.nonEmpty)) {
      marks = Some(own(Var(spans)))
      // its lines bound again, to follow them
      children().zipWithIndex.foreach {
        case (r: RichText, i) => r.runs := runsOf(i)
        case _ => ()
      }
    }
  }

  def markUnit(i: Int): Component = children().lift(i).getOrElse(this)

  // line `i`'s runs, with the passages in it marked
  private def runsOf(i: Int): List[TextRun] = {
    val parts = MarkdownRuns.parts(inlines(), points, weight, ctx.breaks, ctx.mentions(), ctx.pressesMentions())
    val runs = if (i < parts.size) parts(i).runs else Nil
    marks match {
      case Some(v) => Passages.marked(runs, v().lift(i).getOrElse(Vector.empty))
      case None => runs
    }
  }

  // line `i`, wrapped to the paragraph's width
  private def line(i: Int): RichText = new RichText {
    size.width := ParagraphView.this.size.width()
    font.size @= points
    color := (if (ctx.quiet) Theme.muted() else Theme.text())
    direction @= ctx.direction
    wrapAnywhere @= true
    follow = href => ctx.press(href, this)
    runs := runsOf(i)
  }

  override def kind: String = "ParagraphView"
}

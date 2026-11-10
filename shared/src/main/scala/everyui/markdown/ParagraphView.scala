package everyui.markdown

import everyui.Color
import everyui.component.{Container, Link, RichText}
import everyui.component.types.WhiteSpace
import everyui.event.Key
import everyui.layout.VerticalLayout
import everyui.menu.{Menu, MenuItem}
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
  * must be. */
class ParagraphView(content: List[MdInline], ctx: MarkdownContext, width: => Double, points: Double,
                    weight: Int = 400) extends Container {
  /** What it shows. */
  val inlines: Var[List[MdInline]] = own(Var(content))
  // its links, and the mentions a press on answers (`MarkdownContext.pressesMentions`), each once
  private val links: Val[List[(String, String)]] = own(Val(MarkdownRuns.links(inlines()) ++
    (if (!ctx.pressesMentions()) Nil
      else MarkdownRuns.parts(inlines(), points, weight, ctx.breaks, ctx.mentions(), pressable = true).flatMap(_.runs)
        .filter(r => everyui.component.RichText.acts(r.href)).map(r => (r.text, r.href))
        .foldLeft(List.empty[(String, String)])((out, l) => if (out.exists(_._2 == l._2)) out else out :+ l))))
  private var shape = ""
  // where the view measures (`MarkdownContext.measures`): each line again on one line, out of sight and taking no room
  private val twins: Container = new Container {
    size.width @= 0.0
    size.height @= 0.0
    visible @= false
  }
  private val pictures: Var[Boolean] = own(Var(false))

  /** How wide it would be drawn on its own: its widest line, or none where it holds a picture (which takes the
    * width) or the view does not measure, nor until each line's width has been read (`MeasuredFeature.known`), where
    * a line not yet read is 0 wide for want of the read: a message's bubble sized by it was laid out at no width, its
    * words read there, and all of it again once they were. */
  val natural: Val[Option[Double]] = own(Val(
    if (!ctx.measures || pictures() || !twins.children().forall(_.measured.known())) None
    else Some(twins.children().foldLeft(0.0)((w, t) => math.max(w, t.size.width())))))

  size.width := width
  layout @= Some(VerticalLayout(0.0))
  focusable := links().nonEmpty
  radius @= 4.0
  backgroundColor := (if (focusShown()) Theme.controlLit() else Color.Clear)
  inlines.attach(_ => build())
  build()

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
      val old = children().filterNot(_ eq twins)
      val lines = parts.indices.toVector.map(i => parts(i).image match {
        case Some(image) => new MarkdownImage(image, ctx, size.width())
        case None => line(i, wrapped = true)
      })
      if (ctx.measures) {
        val oldTwins = twins.children()
        twins.children @= parts.indices.toVector.filter(i => parts(i).image.isEmpty).map(i => line(i, wrapped = false))
        oldTwins.foreach(_.dispose())
        children @= lines :+ twins
      }
      else children @= lines
      old.foreach(_.dispose())
    }
  }

  // line `i`, wrapped to the paragraph's width, or on one line as it would be drawn on its own
  private def line(i: Int, wrapped: Boolean): RichText = new RichText {
    if (wrapped) size.width := ParagraphView.this.size.width()
    else whiteSpace @= WhiteSpace.NoWrap
    font.size @= points
    color := (if (ctx.quiet) Theme.muted() else Theme.text())
    direction @= ctx.direction
    wrapAnywhere @= true
    follow = href => ctx.press(href, this)
    runs := {
      val parts = MarkdownRuns.parts(inlines(), points, weight, ctx.breaks, ctx.mentions(), ctx.pressesMentions())
      if (i < parts.size) parts(i).runs else Nil
    }
  }

  override def kind: String = "ParagraphView"
}

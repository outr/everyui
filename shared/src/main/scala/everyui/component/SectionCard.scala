package everyui.component

import everyui.Color
import everyui.component.types.{FontWeight, Semantics}
import everyui.event.Key
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A titled group of an app's own rows drawn as one card, so its header reads as the heading of the rows under it
  * and not as one more of them: one ground, one edge and one set of rounded corners round the header and every row,
  * a hairline across it under the header in the edge's colour and a fainter one between rows, set in from its
  * sides, and no gap anywhere in it. Its `title` heads it (a chevron before it, ▾ open and ▸ closed, ◂ right to
  * left), and the header is a button (a disclosure to a screen reader, focusable on every platform, so a remote
  * reaches it) that opens and closes it on a click, a tap, Enter or Space; closed, the header alone is the card, its
  * corners all round. An empty `title` gives it no header, its rows always shown; `collapsible` off makes the header a
  * heading only, not a button, its rows always shown. Open with no rows, the header alone is the card too.
  *
  * The `rows` are the app's, any component (a checkbox and words, a person, a file), drawn top to bottom as wide as the
  * card's inside, each as tall as it is; the card shows and hides them as it opens and closes, and takes apart a row
  * dropped from `rows`, and every row with itself. A row draws no ground or edge of its own, so a translucent theme's
  * picture shows through one ground, not two: the card lights a row itself while the pointer is over it or the focus
  * is drawn in it (its tick, its words), and the header while either is on it, each in a rounded light set in from
  * the card's edge, so the light keeps inside its corners on every platform (the desktop cuts a box square). The
  * card is the box: bind its `size.width`, and give it a `backgroundColor` and a `borderColor` of the app's (a
  * calendar's tint) in place of the theme's surface (as see-through as `Theme.surfaceOpacity` says) and border; its
  * hairlines follow the edge's colour. `open` is the app's to share (one choice for every day's group).
  * {{{
  *   val tasks = new SectionCard(tasksOpen)
  *   tasks.title @= "Tasks · 3 due"
  *   tasks.size.width @= 360.0
  *   tasks.rows @= due.map(taskRow)
  * }}} */
class SectionCard(val open: Var[Boolean] = Var(true)) extends Container {
  /** The header's words; empty, there is no header. */
  val title: Var[String] = own(Var(""))
  /** Whether the header opens and closes the card; off, it is a heading only. */
  val collapsible: Var[Boolean] = own(Var(true))
  /** The rows, top to bottom. */
  val rows: Var[Vector[Component]] = own(Var(Vector.empty))

  private val inner: Val[Double] = own(Val(math.max(0.0, size.width() - 2.0 * SectionCard.edge)))
  private val headed: Val[Boolean] = own(Val(title().nonEmpty))
  /** Whether the header is a button that opens and closes it. */
  private[component] val toggles: Val[Boolean] = own(Val(headed() && collapsible()))
  /** Whether its rows show: open, or with nothing to close them. */
  val shows: Val[Boolean] = own(Val(!toggles() || open()))

  /** The header: its chevron and its title, lit inside its own rounded light, inset from the card's edge. */
  val header: Container = new Container {
    size.width := inner()
    focusable := toggles()
    tapSlop @= true
    cursor := (if (toggles()) "pointer" else "")
    semantics := (if (toggles()) Semantics.disclosure(title(), open())
      else Semantics.heading(3).copy(label = title()))
  }
  /** The light behind the header while the pointer or the focus is on it: rounded, inside the card's corners. */
  private[component] val light: Container = new Container {
    position.left @= SectionCard.lightInset
    position.top @= SectionCard.lightInset
    size.width := math.max(0.0, inner() - 2.0 * SectionCard.lightInset)
    size.height := math.max(0.0, header.size.height() - 2.0 * SectionCard.lightInset)
    radius @= SectionCard.radius - SectionCard.edge - SectionCard.lightInset
    backgroundColor := (if (!toggles()) Color.Clear else if (header.focusShown()) Theme.controlLit()
      else if (header.event.pointer.over()) Theme.control().withAlpha(0.6) else Color.Clear)
    borderWidth := (if (toggles() && header.focusShown()) 2.0 else 0.0)
    borderColor := Theme.accent()
  }
  private[component] val chevron: MaterialIconView = new MaterialIconView(MaterialIcon.ExpandMore,
    SectionCard.chevronSize) {
    icon := (if (open()) MaterialIcon.ExpandMore else if (Direction.rtl()) MaterialIcon.ChevronLeft
      else MaterialIcon.ChevronRight)
    color := Theme.muted()
    visible := toggles()
    position.left := along(SectionCard.pad, SectionCard.chevronSize)
  }
  // where the title starts, past the chevron where there is one
  private val wordsStart: Val[Double] = own(Val(SectionCard.pad +
    (if (toggles()) SectionCard.chevronSize + SectionCard.chevronGap else 0.0)))
  private[component] val words: TextView = new TextView {
    content := title()
    font.size @= SectionCard.titlePoints
    font.weight @= FontWeight.SemiBold
    color := Theme.text()
    maxWidth := math.max(20.0, inner() - wordsStart() - SectionCard.pad)
    position.left := along(wordsStart(), size.width())
  }
  header.size.height := math.max(SectionCard.chevronSize, words.size.height()) + 2.0 * SectionCard.pad
  words.position.top := (header.size.height() - words.size.height()) / 2.0
  chevron.position.top := (header.size.height() - SectionCard.chevronSize) / 2.0
  header.children ++= List(light, chevron, words)
  header.event.click.attach { e =>
    if (toggles()) {
      e.consume()
      open @= !open()
    }
  }
  header.event.key.down.attach(k => if (toggles() && k.key == Key.Space) open @= !open())

  // the hairlines: under the header across the card in the edge's colour, between rows fainter and set in from
  // either side by the header's padding; each a line in a holder as wide as the inside, kept and handed out in order
  private var lines: Vector[Container] = Vector.empty
  private def hairline(i: Int, strong: Boolean): Container = {
    while (lines.size <= i) lines = lines :+ new Container {
      size.width := inner()
      size.height @= SectionCard.hairline
      collapses @= true
      children += new Container {
        size.height @= SectionCard.hairline
      }
    }
    val l = lines(i)
    SectionCard.lineOf(l).foreach { line =>
      line.position.left @= (if (strong) 0.0 else SectionCard.pad)
      line.size.width := math.max(0.0, inner() - (if (strong) 0.0 else 2.0 * SectionCard.pad))
      line.backgroundColor := (if (strong) borderColor()
        else borderColor().withAlpha(borderColor().alpha * SectionCard.faint))
    }
    l
  }

  // each row in a holder of its own, with a light behind it: kept for as long as the row is in `rows`
  private var holders: Vector[(Component, Container)] = Vector.empty
  private def holderOf(r: Component): Container = holders.find(_._1 eq r).map(_._2).getOrElse {
    val h = new Container
    val lit = new Container {
      position.left @= SectionCard.lightInset
      position.top @= SectionCard.lightInset / 2.0
      size.width := math.max(0.0, inner() - 2.0 * SectionCard.lightInset)
      size.height := math.max(0.0, h.size.height() - SectionCard.lightInset)
      radius @= SectionCard.radius - SectionCard.edge - SectionCard.lightInset
      // (as a list's row is lit: the focus in it as its cursor, the pointer over it fainter)
      backgroundColor := (if (SectionCard.focusIn(r)) Theme.control()
        else if (h.event.pointer.over()) Theme.control().withAlpha(0.6) else Color.Clear)
    }
    h.size.width := inner()
    h.size.height := r.size.height()
    h.collapses @= true
    r.size.width := inner()
    h.children ++= List(lit, r)
    holders = holders :+ (r -> h)
    h
  }

  padding @= SectionCard.edge
  layout @= Some(VerticalLayout(0.0))
  radius @= SectionCard.radius
  borderWidth @= SectionCard.edge
  borderColor := Theme.border()
  backgroundColor := { val s = Theme.surface(); s.withAlpha(s.alpha * Theme.surfaceOpacity()) }
  clipsToBox @= Some(true)

  /** The parts in order: the header (hidden, taking no room, where there is none), then each row's holder with a
    * hairline before it where something is above it. Every part stays in the card; a closed card's rows and hairlines
    * are hidden and take no room. A row dropped from `rows` is taken apart with its holder. */
  private def draw(): Unit = {
    val rs = rows()
    var parts = Vector[Component](header)
    var n = 0
    rs.zipWithIndex.foreach { (r, i) =>
      if (headed() || i > 0) {
        val l = hairline(n, strong = headed() && i == 0)
        l.visible := shows()
        parts = parts :+ l
        n += 1
      }
      val h = holderOf(r)
      h.visible := shows()
      parts = parts :+ h
    }
    lines = lines.take(n)
    holders = holders.filter(p => rs.exists(_ eq p._1))
    header.visible := headed()
    header.collapses @= true
    children.replaceMaking(parts)
  }
  rows.attach(_ => draw())
  headed.attach(_ => draw())
  draw()

  // where a piece `length` long that starts `start` from the header's start goes, mirrored right to left
  private def along(start: Double, length: Double): Double =
    if (Direction.rtl()) inner() - start - length else start
}

object SectionCard {
  /** How wide the card's edge is, and how round its corners. */
  val edge: Double = 1.0
  val radius: Double = 10.0
  /** The room round the header's chevron and words, and how far its light is set in from the card's edge. */
  val pad: Double = 10.0
  val lightInset: Double = 3.0
  /** How big the chevron is, the gap after it, and the title's size. */
  val chevronSize: Double = 20.0
  val chevronGap: Double = 8.0
  val titlePoints: Double = 14.0
  /** How thick a hairline is, and how much of the edge's colour a line between rows takes. */
  val hairline: Double = 1.0
  val faint: Double = 0.45
  /** The line a hairline's holder draws. */
  private[component] def lineOf(l: Container): Option[Container] = l.children().headOption.flatMap(_.asContainer)

  /** Whether the focus is drawn on `c` or on something in it, as `Component.focusShown` draws it: always where a
    * remote moves it, elsewhere while the keys move it. */
  def focusIn(c: Component): Boolean = (Platform.remoteNavigation() || Focus.visible()) &&
    Focus.focused().exists(f => within(f, c))
  private def within(f: Component, c: Component): Boolean = (f eq c) || f.parent().exists(p => within(p, c))
}

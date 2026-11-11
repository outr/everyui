package everyui.component

import everyui.Color
import everyui.component.types.{FontWeight, Semantics}
import everyui.event.Key
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.layout.{PlacedLayout, VerticalLayout}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A titled group of an app's own rows drawn as one card, so its header reads as the heading of the rows under it
  * and not as one more of them: one ground, one edge and one set of rounded corners round the header and every row,
  * a hairline across it under the header in the edge's colour and a fainter one between rows, set in from its
  * sides, and no gap anywhere in it. Its `title` heads it (a chevron before it, ▾ open and ▸ closed, ◂ right to
  * left), and the header's `toggle` is a button (a disclosure to a screen reader, focusable on every platform, so a
  * remote reaches it) that opens and closes it on a click, a tap, Enter or Space; closed, the header alone is the card,
  * its corners all round. An empty `title` gives it no header, its rows always shown; `collapsible` off makes the
  * toggle a heading only, not a button, its rows always shown. Open with no rows, the header alone is the card too.
  *
  * The header can hold more than its title. `leading` stands between the chevron and the title (a spinner while work
  * runs), part of the toggle: something to see, not a control. `trailing` are controls at the header's end (an icon
  * button that clears what is finished), beside the toggle and not in it: a screen reader hears the disclosure and
  * each control as controls of their own, a press, a tap or Enter on one does what it does and never opens or closes
  * the card, and Tab, and Right (Left, right to left) from the toggle, reach them in order. Either is the app's to set
  * and clear at any time, and one dropped is taken apart, as a row is.
  *
  * The `rows` are the app's, any component (a checkbox and words, a person, a file), drawn top to bottom as wide as the
  * card's inside, each as tall as it is; the card shows and hides them as it opens and closes, and takes apart a row
  * dropped from `rows`, and every row with itself. A row draws no ground or edge of its own, so a translucent theme's
  * picture shows through one ground, not two: the card lights a row itself while a hovering pointer is over it or the
  * focus is drawn in it (its tick, its words), and the toggle while either is on it, each in a rounded light set in
  * from the card's edge, so the light keeps inside its corners on every platform (the desktop cuts a box square). A
  * finger's tap leaves no light behind (`PointerEvents.hover`). The card is the box: bind its `size.width`, and give
  * it a `backgroundColor` and a `borderColor` of the app's (a calendar's tint) in place of the theme's surface (as
  * see-through as `Theme.surfaceOpacity` says) and border; its hairlines follow the edge's colour. `open` is the app's
  * to share (one choice for every day's group).
  *
  * `compact` on, closed, the card is only as wide as its header needs (its chevron, leading, title and trailing), no
  * wider than `openWidth`, and open it is `openWidth` wide: give `openWidth` in place of `size.width`, which the card
  * then sets. It changes width at once as it opens and closes, as its rows show and hide at once.
  *
  * A sub-section (`SectionCard.sub`, `sub` true) is a card drawn as a part of the card it is a row of: no edge, no
  * corners and no ground of its own, its header's words dimmer and smaller on a dim rounded ground, with a toggle,
  * leading, trailing and rows of its own and the outer card's hairlines (fainter ones under its header and between
  * its rows), so the whole reads as one card. It folds on its own, can hold sub-sections of its own, and lights its
  * own header and rows; the outer card does not light it as a row.
  * {{{
  *   val tasks = new SectionCard(tasksOpen)
  *   tasks.title @= "Tasks · 3 due"
  *   tasks.size.width @= 360.0
  *   tasks.rows @= due.map(taskRow)
  * }}} */
class SectionCard(val open: Var[Boolean] = Var(true), val sub: Boolean = false) extends Container {
  /** The header's words; empty, there is no header. */
  val title: Var[String] = own(Var(""))
  /** Whether the header opens and closes the card; off, it is a heading only. */
  val collapsible: Var[Boolean] = own(Var(true))
  /** The rows, top to bottom. */
  val rows: Var[Vector[Component]] = own(Var(Vector.empty))
  /** What stands before the title, after the chevron (a spinner), part of the toggle; None, nothing. */
  val leading: Var[Option[Component]] = own(Var(None))
  /** The controls at the header's end, in reading order, beside the toggle and not in it. */
  val trailing: Var[Vector[Component]] = own(Var(Vector.empty))
  /** Whether, closed, the card is only as wide as its header needs, and `openWidth` wide open. */
  val compact: Var[Boolean] = own(Var(false))
  /** How wide the card is open, and the most it is closed, while `compact`. */
  val openWidth: Var[Double] = own(Var(0.0))

  // how wide its edge is: none for a sub-section, a part of the card it is in
  private val rim: Double = if (sub) 0.0 else SectionCard.edge
  private val headed: Val[Boolean] = own(Val(title().nonEmpty))
  /** Whether the toggle is a button that opens and closes it. */
  private[component] val toggles: Val[Boolean] = own(Val(headed() && collapsible()))
  /** Whether its rows show: open, or with nothing to close them. */
  val shows: Val[Boolean] = own(Val(!toggles() || open()))
  // how wide the inside is open: the rows', the hairlines' and the title's room (while compact, never the card's own
  // width, which follows the title)
  private val inner: Val[Double] = own(Val(math.max(0.0, (if (compact() && !sub) openWidth() else size.width()) -
    2.0 * rim)))
  // how wide the header is: the inside as the card is now
  private val across: Val[Double] = own(Val(math.max(0.0, size.width() - 2.0 * rim)))
  // the room the trailing controls take at the header's end, with the gap before them and the inset after
  private val endsRoom: Val[Double] = own(Val {
    val ts = trailing()
    var room = 0.0
    ts.foreach(t => room += t.size.width() + SectionCard.trailingGap)
    if (ts.isEmpty) 0.0 else room + SectionCard.trailingInset
  })
  // the room above and below the header's words
  private val headPad: Double = if (sub) SectionCard.subPad else SectionCard.pad

  /** The header: a part of the card holding its ground, its light, its toggle and its trailing controls, in that
    * order for the keys (Left and Right along it, as it reads). */
  val header: Container = new Container {
    size.width := across()
    layout @= Some(PlacedLayout())
  }
  /** The disclosure: its chevron, its leading and its title, from the header's start to its trailing controls. */
  val toggle: Container = new Container {
    size.width := math.max(0.0, across() - endsRoom())
    size.height := header.size.height()
    position.left := (if (Direction.rtl()) across() - size.width() else 0.0)
    focusable := toggles()
    tapSlop @= true
    cursor := (if (toggles()) "pointer" else "")
    semantics := (if (toggles()) Semantics.disclosure(title(), open())
      else Semantics.heading(if (sub) 4 else 3).copy(label = title()))
  }
  /** A sub-section's header's ground, dim, rounded and set in as the light is, across the whole header; none for a
    * card's own header. */
  private[component] val ground: Container = new Container {
    position.left @= SectionCard.lightInset
    position.top @= SectionCard.lightInset
    size.width := math.max(0.0, across() - 2.0 * SectionCard.lightInset)
    size.height := math.max(0.0, header.size.height() - 2.0 * SectionCard.lightInset)
    radius @= SectionCard.radius - SectionCard.edge - SectionCard.lightInset
    backgroundColor := (if (sub) Theme.control().withAlpha(Theme.control().alpha * SectionCard.subGround)
      else Color.Clear)
  }
  /** The light behind the toggle while a hovering pointer or the focus is on it: rounded, inside the card's corners. */
  private[component] val light: Container = new Container {
    position.left := toggle.position.left() + SectionCard.lightInset
    position.top @= SectionCard.lightInset
    size.width := math.max(0.0, toggle.size.width() - 2.0 * SectionCard.lightInset)
    size.height := math.max(0.0, header.size.height() - 2.0 * SectionCard.lightInset)
    radius @= SectionCard.radius - SectionCard.edge - SectionCard.lightInset
    backgroundColor := (if (toggles() && toggle.focusShown()) Theme.controlLit()
      else if (toggles() && toggle.event.pointer.hover()) Theme.control().withAlpha(if (sub) 1.0 else 0.6)
      else Color.Clear)
    borderWidth := (if (toggles() && toggle.focusShown()) 2.0 else 0.0)
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
  // where the leading starts, past the chevron where there is one, and where the title starts, past both
  private val leadStart: Val[Double] = own(Val(SectionCard.pad +
    (if (toggles()) SectionCard.chevronSize + SectionCard.chevronGap else 0.0)))
  private val wordsStart: Val[Double] = own(Val(leadStart() + (leading() match {
    case Some(l) => l.size.width() + SectionCard.chevronGap
    case None => 0.0
  })))
  private[component] val words: TextView = new TextView {
    content := title()
    font.size @= (if (sub) SectionCard.subPoints else SectionCard.titlePoints)
    font.weight @= FontWeight.SemiBold
    color := (if (sub) Theme.muted() else Theme.text())
    maxWidth := math.max(20.0, inner() - endsRoom() - wordsStart() - SectionCard.pad)
    position.left := along(wordsStart(), size.width())
  }
  // what the header's words, chevron and leading need, and its trailing controls
  private val headHeight: Val[Double] = own(Val {
    var tallest = math.max(SectionCard.chevronSize, words.size.height())
    leading().foreach(l => tallest = math.max(tallest, l.size.height()))
    var ends = 0.0
    trailing().foreach(t => ends = math.max(ends, t.size.height()))
    math.max(tallest + 2.0 * headPad, if (ends > 0.0) ends + 2.0 * SectionCard.trailingPad else 0.0)
  })
  header.size.height := headHeight()
  words.position.top := (header.size.height() - words.size.height()) / 2.0
  chevron.position.top := (header.size.height() - SectionCard.chevronSize) / 2.0
  toggle.event.click.attach { e =>
    if (toggles()) {
      e.consume()
      open @= !open()
    }
  }
  toggle.event.key.down.attach(k => if (toggles() && k.key == Key.Space) open @= !open())

  // the toggle's parts: the chevron, the leading where there is one, the title; one dropped is taken apart
  private def drawToggle(): Unit = {
    val lead = leading()
    lead.foreach { l =>
      l.position.left := along(leadStart(), l.size.width())
      l.position.top := (header.size.height() - l.size.height()) / 2.0
    }
    toggle.children.replaceMaking(lead match {
      case Some(l) => Vector(chevron, l, words)
      case None => Vector(chevron, words)
    })
  }
  // the header's parts: the ground, the light, the toggle, then each trailing control from the end back, in
  // reading order
  private def drawHeader(): Unit = {
    val ts = trailing()
    ts.zipWithIndex.foreach { (t, i) =>
      t.position.left := {
        var after = SectionCard.trailingInset
        trailing().drop(i + 1).foreach(n => after += n.size.width() + SectionCard.trailingGap)
        val start = across() - after - t.size.width()
        if (Direction.rtl()) across() - start - t.size.width() else start
      }
      t.position.top := (header.size.height() - t.size.height()) / 2.0
    }
    header.children.replaceMaking(Vector[Component](ground, light, toggle) ++ ts)
  }

  // the hairlines: under the header across the card in the edge's colour, between rows fainter and set in from
  // either side by the header's padding (a sub-section's all fainter); each a line in a holder as wide as the inside,
  // kept and handed out in order
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

  // each row in a holder of its own, with a light behind it: kept for as long as the row is in `rows`; a
  // sub-section is lit by itself, part by part, and drawn in this card's edge colour
  private var holders: Vector[(Component, Container)] = Vector.empty
  private def holderOf(r: Component): Container = holders.find(_._1 eq r).map(_._2).getOrElse {
    val h = new Container
    val inside = r match {
      case s: SectionCard =>
        if (s.sub) s.borderColor := borderColor()
        true
      case _ => false
    }
    val lit = new Container {
      position.left @= SectionCard.lightInset
      position.top @= SectionCard.lightInset / 2.0
      size.width := math.max(0.0, inner() - 2.0 * SectionCard.lightInset)
      size.height := math.max(0.0, h.size.height() - SectionCard.lightInset)
      radius @= SectionCard.radius - SectionCard.edge - SectionCard.lightInset
      // (as a list's row is lit: the focus in it as its cursor, a hovering pointer over it fainter)
      backgroundColor := (if (inside) Color.Clear else if (SectionCard.focusIn(r)) Theme.control()
        else if (h.event.pointer.hover()) Theme.control().withAlpha(0.6) else Color.Clear)
    }
    h.size.width := inner()
    h.size.height := r.size.height()
    h.collapses @= true
    r.size.width := inner()
    h.children ++= List(lit, r)
    holders = holders :+ (r -> h)
    h
  }

  padding @= rim
  layout @= Some(VerticalLayout(0.0))
  radius @= (if (sub) 0.0 else SectionCard.radius)
  borderWidth @= rim
  if (!sub) {
    borderColor := Theme.border()
    backgroundColor := { val s = Theme.surface(); s.withAlpha(s.alpha * Theme.surfaceOpacity()) }
    clipsToBox @= Some(true)
  }
  // compact, the card's own width: its header's need closed (no more than open), `openWidth` open
  compact.attach(on => if (on && !sub) size.width := {
    val need = 2.0 * rim + wordsStart() + words.size.width() + SectionCard.pad + endsRoom()
    if (toggles() && !open()) math.min(math.ceil(need), openWidth()) else openWidth()
  })

  /** The parts in order: the header (hidden, taking no room, where there is none), then each row's holder with a
    * hairline before it where something is above it. Every part stays in the card; a closed card's rows and hairlines
    * are hidden and take no room. A row dropped from `rows` is taken apart with its holder. */
  private def draw(): Unit = {
    val rs = rows()
    var parts = Vector[Component](header)
    var n = 0
    rs.zipWithIndex.foreach { (r, i) =>
      if (headed() || i > 0) {
        val l = hairline(n, strong = headed() && i == 0 && !sub)
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
  leading.attach(_ => drawToggle())
  trailing.attach(_ => drawHeader())
  drawToggle()
  drawHeader()
  draw()

  // where a piece `length` long that starts `start` from the toggle's start goes, mirrored right to left
  private def along(start: Double, length: Double): Double =
    if (Direction.rtl()) toggle.size.width() - start - length else start
}

object SectionCard {
  /** How wide the card's edge is, and how round its corners. */
  val edge: Double = 1.0
  val radius: Double = 10.0
  /** The room round the header's chevron and words (a sub-section's above and below them less), and how far its
    * light is set in from the card's edge. */
  val pad: Double = 10.0
  val subPad: Double = 7.0
  val lightInset: Double = 3.0
  /** How big the chevron is, the gap after it (and after the leading), and the title's size (a sub-section's less). */
  val chevronSize: Double = 20.0
  val chevronGap: Double = 8.0
  val titlePoints: Double = 14.0
  val subPoints: Double = 13.0
  /** The gap between the title and the trailing controls and between each of them, the room after the last, and the
    * least room above and below them. */
  val trailingGap: Double = 4.0
  val trailingInset: Double = 6.0
  val trailingPad: Double = 4.0
  /** How thick a hairline is, and how much of the edge's colour a line between rows takes. */
  val hairline: Double = 1.0
  val faint: Double = 0.45
  /** How much of the theme's control colour a sub-section's header takes for its ground. */
  val subGround: Double = 0.5

  /** A sub-section: a card drawn as a part of the card it is a row of, its header dimmer (`SectionCard.sub`). */
  def sub(open: Var[Boolean] = Var(true)): SectionCard = new SectionCard(open, sub = true)

  /** The line a hairline's holder draws. */
  private[component] def lineOf(l: Container): Option[Container] = l.children().headOption.flatMap(_.asContainer)

  /** Whether the focus is drawn on `c` or on something in it, as `Component.focusShown` draws it: always where a
    * remote moves it, elsewhere while the keys move it. */
  def focusIn(c: Component): Boolean = (Platform.remoteNavigation() || Focus.visible()) &&
    Focus.focused().exists(f => within(f, c))
  private def within(f: Component, c: Component): Boolean = (f eq c) || f.parent().exists(p => within(p, c))
}

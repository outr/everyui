package everyui.list

import everyui.Color
import everyui.chat.{Avatar, Participant}
import everyui.component.{Component, Container, IconButton, MaterialIconView, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.font.MaterialIcon
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A row of a `RichList`, handed line after line as it scrolls: a section's header (a chevron, its title, its count),
  * or an item as its `RichLook` says, with its avatar or icon, its title and detail, and at its end the time, the count
  * and the mark. A ⋮ at its end opens the item's menu, shown while the pointer is over the row, the cursor is on it in
  * a list with the focus, or (on a touch screen) it is the item chosen; it takes the time's place while it shows. */
class RichRow[T](owner: RichList[T]) extends ListRow[RichLine[T]] {
  private val header: Val[Boolean] = own(Val(item().exists(_.item.isEmpty)))
  private val look: Val[Option[RichLook]] = own(Val(item().flatMap(_.item).map(owner.lookOf)))
  private val tall: Val[Boolean] = own(Val(look().exists(_.detail.nonEmpty)))
  private val unread: Val[Boolean] = own(Val(look().exists(_.unread)))
  /** Whether it shows the item the app has chosen (`RichList.selected`). */
  val chosen: Val[Boolean] = own(Val(item().flatMap(_.item).exists(t => owner.selected().contains(owner.keyOf(t)))))
  private val hovered: Val[Boolean] = if (Platform.pointerSupport()) event.pointer.over else own(Val(false))
  private val menuShows: Val[Boolean] = own(Val(!header() && item().flatMap(_.item).exists(t => owner.menu()(t).nonEmpty) &&
    (hovered() || (current() && active()) || (chosen() && Platform.touchSupport() && !Platform.pointerSupport()))))
  private val lead: Val[Double] = own(Val(if (tall()) RichRow.tallLead else RichRow.shortLead))
  private val hasLead: Val[Boolean] = own(Val(look().exists(l => l.who.nonEmpty || l.icon.nonEmpty)))
  /** Where the title starts, from the start edge. */
  private val textStart: Val[Double] = own(Val(RichRow.inset + (if (hasLead()) lead() + 10.0 else 0.0)))
  // the end edge the trailing pieces line up against, clear of the list's scroll thumb
  private val end: Val[Double] = own(Val(size.width() - RichRow.inset))

  // ---- a header ----
  private val chevron = new MaterialIconView(MaterialIcon.ExpandMore, 18.0) {
    icon := (if (item().exists(_.open)) MaterialIcon.ExpandMore
      else if (everyui.i18n.Direction.rtl()) MaterialIcon.ChevronLeft else MaterialIcon.ChevronRight)
    color := Theme.muted()
    visible := header()
    position.left := ListRow.along(ListRow.inset, 18.0, RichRow.this.size.width())
    position.top := (RichRow.this.size.height() - 18.0) / 2.0
  }
  private val heading: RowLabel = new RowLabel(item().filter(_.item.isEmpty).map(_.section.title).getOrElse(""),
    Decoration(weight = FontWeight.SemiBold, color = Some(Theme.muted()),
      badge = item().filter(_.item.isEmpty).map(_.count.toString).getOrElse("")),
    RichRow.this.size.width() - ListRow.inset - 22.0 - RichRow.inset + 8.0, RichRow.this.size.height(), 12.5)
  heading.position.left := ListRow.along(ListRow.inset + 22.0, heading.size.width(), size.width())
  heading.visible := header()

  // ---- an item ----
  private val nobody = Participant("", "")
  private def face(diameter: Double, wanted: => Boolean): Avatar = new Avatar(look().flatMap(_.who).getOrElse(nobody), diameter) {
    visible := wanted && look().exists(_.who.nonEmpty)
    position.left := ListRow.along(RichRow.inset, diameter, RichRow.this.size.width())
    position.top := (RichRow.this.size.height() - diameter) / 2.0
  }
  val tallFace: Avatar = face(RichRow.tallLead, tall())
  private val shortFace: Avatar = face(RichRow.shortLead, !tall())
  private val glyph = new MaterialIconView(MaterialIcon.Empty, 22.0) {
    icon := look().flatMap(l => if (l.who.isEmpty) l.icon else None).getOrElse(MaterialIcon.Empty)
    color := look().flatMap(_.iconColor).getOrElse(Theme.muted())
    visible := look().exists(l => l.who.isEmpty && l.icon.nonEmpty)
    position.left := ListRow.along(RichRow.inset + (lead() - 22.0) / 2.0, 22.0, RichRow.this.size.width())
    position.top := (RichRow.this.size.height() - 22.0) / 2.0
  }

  val time: TextView = new TextView {
    content := look().flatMap(_.time).map(t => owner.timeText(t, owner.now())).getOrElse("")
    font.size @= 11.5
    font.weight := (if (unread()) FontWeight.SemiBold else FontWeight.Normal)
    color := (if (unread()) Theme.accent() else Theme.muted())
    visible := look().exists(_.time.nonEmpty) && !menuShows()
  }
  val mark: MaterialIconView = new MaterialIconView(MaterialIcon.Empty, 14.0) {
    icon := look().flatMap(_.mark).getOrElse(MaterialIcon.Empty)
    color := Theme.muted()
    visible := look().exists(_.mark.nonEmpty) && !menuShows()
  }
  val countText: TextView = new TextView {
    content := RichList.countText(look().map(_.count).getOrElse(0))
    font.size @= 11.0
    font.weight @= FontWeight.SemiBold
    color := Theme.onAccent()
  }
  private val badge: Container = new Container {
    size.height @= 18.0
    size.width := math.max(18.0, countText.size.width() + 10.0)
    radius @= 9.0
    backgroundColor := (if (unread()) Theme.accent() else Theme.muted())
    visible := look().exists(_.count > 0) && !menuShows()
    children += countText
  }
  countText.position.center := badge.size.width() / 2.0
  countText.position.middle := 9.0
  val more: IconButton = new IconButton(MaterialIcon.MoreVert, RichList.moreLabel, () => openMore()) {
    visible := menuShows()
  }
  more.focusable @= false

  // how wide the trailing pieces are: on the title's line, and (in a tall row) on the detail's
  private def span(c: Component, gap: Double): Double = if (c.visible()) c.size.width() + gap else 0.0
  private val firstTrail: Val[Double] = own(Val(
    if (menuShows()) more.size.width()
    else span(time, 0.0) + span(mark, 4.0) + (if (tall()) 0.0 else span(badge, 6.0))))
  private val secondTrail: Val[Double] = own(Val(if (menuShows()) more.size.width() else span(badge, 0.0)))

  val title: TextView = new TextView {
    content := look().map(_.title).getOrElse("")
    font.size @= 14.0
    font.weight := (if (unread()) FontWeight.Bold else FontWeight.Normal)
    color := Theme.text()
    maxWidth := math.max(20.0, end() - textStart() - firstTrail() - 8.0)
    visible := !header() && !editing()
  }
  val detail: TextView = new TextView {
    content := look().flatMap(_.detail).getOrElse("")
    font.size @= 12.5
    color := (if (unread()) Theme.text() else Theme.muted())
    maxWidth := math.max(20.0, end() - textStart() - secondTrail() - 8.0)
    visible := tall()
  }

  // the title's line in the middle of a short row, the two lines together in the middle of a tall one
  private val titleTop: Val[Double] = own(Val(
    if (tall()) (size.height() - title.size.height() - detail.size.height() - 2.0) / 2.0
    else (size.height() - title.size.height()) / 2.0))
  private def from(start: => Double, c: Component): Unit =
    c.position.left := ListRow.along(start, c.size.width(), RichRow.this.size.width())
  from(textStart(), title)
  title.position.top := titleTop()
  from(textStart(), detail)
  detail.position.top := titleTop() + title.size.height() + 2.0
  private val lineMiddle: Val[Double] = own(Val(titleTop() + title.size.height() / 2.0))
  private val detailMiddle: Val[Double] = own(Val(detail.position.top() + detail.size.height() / 2.0))
  // at the end: in a tall row the time (and the mark before it) on the title's line and the badge under them; in a
  // short one the mark, the time and the badge in a line
  from(end() - badge.size.width(), badge)
  badge.position.top := (if (tall()) detailMiddle() else lineMiddle()) - 9.0
  from(end() - (if (tall()) 0.0 else span(badge, 6.0)) - time.size.width(), time)
  time.position.top := lineMiddle() - time.size.height() / 2.0
  from(end() - (if (tall()) 0.0 else span(badge, 6.0)) - span(time, 4.0) - 14.0, mark)
  mark.position.top := lineMiddle() - 7.0
  from(end() - more.size.width() + 6.0, more)
  more.position.top := (size.height() - more.size.height()) / 2.0

  size.height := (if (header()) RichRow.headerHeight else if (tall()) RichRow.tallHeight else RichRow.shortHeight)
  backgroundColor := (if (drop().contains(DropPosition.Into)) Theme.accent().withAlpha(0.45)
    else if (chosen()) Theme.accent().withAlpha(if (active()) 0.26 else 0.18)
    else if (current() && active()) Theme.control()
    else if (hovered()) Theme.control().withAlpha(0.6)
    else Color.Clear)
  semantics := item().map(l => Semantics("listitem", label = l.item match {
    case Some(t) => RichList.said(owner.lookOf(t), owner.now(), owner.timeText)
    case None => l.section.title + ", " + l.count.toString
  })).getOrElse(Semantics.Plain)
  children ++= List(chevron, heading, tallFace, shortFace, glyph, title, detail, mark, time, badge, more)

  override def editStart: Double = textStart() - 6.0

  private def openMore(): Unit = item().foreach { l =>
    l.item.foreach { t =>
      owner.list.current @= Some(l.key)
      owner.openMenu(t, Some(more), 0.0, 0.0)
    }
  }

  override def kind: String = "RichRow"
}

object RichRow {
  val inset: Double = 12.0
  val headerHeight: Double = 30.0
  val shortHeight: Double = 44.0
  val tallHeight: Double = 60.0
  /** How big the avatar is in a row with a second line, and in one without. */
  val tallLead: Double = 38.0
  val shortLead: Double = 28.0
}

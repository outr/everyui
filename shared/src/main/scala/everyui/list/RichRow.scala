package everyui.list

import everyui.Color
import everyui.chat.{Avatar, Participant}
import everyui.component.{Component, Container, IconButton, MaterialIconView, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.font.MaterialIcon
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A row of a `RichList`, handed line after line as it scrolls: a section's header (a chevron, its icon, its title, its
  * count, and a ⋮ for its menu where it has one, shown as an item's is, and always where there is no pointer), set in
  * under its group's where it is in one; a group's header, the same in bold and the text's colour;
  * or an item as its `RichLook` says, with its avatar or icon, its title and detail, and at its end the time, the count
  * and the mark. A ⋮ at its end opens the item's menu, shown while the pointer is over the row, the cursor is on it in
  * a list with the focus, or (on a touch screen) it is the item chosen; it takes the time's place while it shows.
  *
  * In `RichStyle.Blocks` a group's header is a caption, and a section's lines are drawn on its block: each row draws its
  * own part of the panel, square, with its corners rounded where it is the block's first or last line (a rounded panel
  * reaching past the row, cut to it), so the block costs every row the same few nodes as it scrolls; the last also
  * leaves the gap under the block. The cursor, the pointer and the item chosen are lit inside the block. */
class RichRow[T](owner: RichList[T]) extends ListRow[RichLine[T]] {
  private val header: Val[Boolean] = own(Val(item().exists(_.item.isEmpty)))
  private val look: Val[Option[RichLook]] = own(Val(item().flatMap(_.item).map(owner.lookOf)))
  private val tall: Val[Boolean] = own(Val(look().exists(_.detail.nonEmpty)))
  private val unread: Val[Boolean] = own(Val(look().exists(_.unread)))
  /** Whether it shows the item the app has chosen (`RichList.selected`). */
  val chosen: Val[Boolean] = own(Val(item().flatMap(_.item).exists(t => owner.selected().contains(owner.keyOf(t)))))
  private val hovered: Val[Boolean] = if (Platform.pointerSupport()) event.pointer.over else own(Val(false))
  /** The group this row heads, where it is a group's header. */
  private val group: Val[Option[RichGroup]] = own(Val(item().filter(_.item.isEmpty).flatMap(_.groupHead)))
  /** The header's section, where this row is a section's header. */
  private val section: Val[Option[RichSection[T]]] = own(Val(item().filter(l => l.item.isEmpty && l.groupHead.isEmpty)
    .map(_.section)))
  /** Whether the list draws its sections as blocks (`RichStyle.Blocks`). */
  private val blocks: Val[Boolean] = own(Val(owner.style() == RichStyle.Blocks))
  // how far a section's header in a group is set in under the group's (never in a block)
  private val nest: Val[Double] = own(Val(if (section().nonEmpty && item().exists(_.nested) && !blocks()) RichRow.nest
    else 0.0))
  // where the pieces start from either edge: inside the block's padding in a block
  private val side: Val[Double] = own(Val(if (blocks()) RichRow.blockMargin + RichRow.blockPad else RichRow.inset))
  // how tall the row's own line is, and the room a block leaves above and below it (its padding, and the gap after it)
  private val base: Val[Double] = own(Val(
    if (header()) {
      if (!blocks()) RichRow.headerHeight else if (group().nonEmpty) RichRow.captionHeight else RichRow.blockHeader
    }
    else if (tall()) RichRow.tallHeight else RichRow.shortHeight))
  private val above: Val[Double] = own(Val(if (blocks() && !header() && item().exists(_.blockStart)) RichRow.blockPad
    else 0.0))
  private val below: Val[Double] = own(Val(if (blocks() && item().exists(_.blockEnd))
    (if (header()) 0.0 else RichRow.blockPad) + RichRow.blockGap else 0.0))
  // where a piece `h` tall sits, in the middle of the row's line
  private def middle(h: Double): Double = above() + (base() - h) / 2.0
  private val headerHasMenu: Val[Boolean] = own(Val(group() match {
    case Some(g) => owner.groupMenu()(g).nonEmpty
    case None => section().exists(s => owner.sectionMenu()(s).nonEmpty)
  }))
  private val sectionMenuShows: Val[Boolean] = own(Val(headerHasMenu() &&
    (hovered() || (current() && active()) || !Platform.pointerSupport())))
  private val menuShows: Val[Boolean] = own(Val(!header() && item().flatMap(_.item).exists(t => owner.menu()(t).nonEmpty) &&
    (hovered() || (current() && active()) || (chosen() && Platform.touchSupport() && !Platform.pointerSupport()))))
  private val lead: Val[Double] = own(Val(if (tall()) RichRow.tallLead else RichRow.shortLead))
  private val hasLead: Val[Boolean] = own(Val(look().exists(l => l.who.nonEmpty || l.icon.nonEmpty)))
  /** Where the title starts, from the start edge. */
  private val textStart: Val[Double] = own(Val(side() + (if (hasLead()) lead() + 10.0 else 0.0)))
  // the end edge the trailing pieces line up against, clear of the list's scroll thumb
  private val end: Val[Double] = own(Val(size.width() - side()))
  // a block's ▾ or ▸, at a header's end: a section's always, a group's caption only while it is closed
  private val toggles: Val[Boolean] = own(Val(blocks() && header() && (section().nonEmpty ||
    item().exists(l => l.groupHead.nonEmpty && !l.open))))
  private val toggleRoom: Val[Double] = own(Val(if (toggles()) RichRow.toggleSize + 6.0 else 0.0))

  // ---- a header ----
  private val chevron = new MaterialIconView(MaterialIcon.ExpandMore, 18.0) {
    icon := (if (item().exists(_.open)) MaterialIcon.ExpandMore
      else if (everyui.i18n.Direction.rtl()) MaterialIcon.ChevronLeft else MaterialIcon.ChevronRight)
    color := (if (group().nonEmpty) Theme.text() else Theme.muted())
    visible := header() && !blocks()
    position.left := ListRow.along(ListRow.inset + nest(), 18.0, RichRow.this.size.width())
    position.top := middle(18.0)
  }
  // a group's header a level above a section's: bold, in the text's colour, where a section's is muted (in a block, a
  // section's title is in the text's colour, the block itself setting it apart)
  private val heading: RowLabel = new RowLabel(group().map(_.title).orElse(section().map(_.title)).getOrElse(""),
    Decoration(icon = if (group().nonEmpty) group().flatMap(_.icon) else section().flatMap(_.icon),
      weight = if (group().nonEmpty) FontWeight.Bold else FontWeight.SemiBold,
      color = Some(if (group().nonEmpty || blocks()) Theme.text() else Theme.muted()),
      badge = if (blocks()) "" else item().filter(_.item.isEmpty).map(_.count.toString).getOrElse("")),
    // short of the ⋮ where any header has a menu, so the counts keep one line as the ⋮ comes and goes (in a block, short
    // of its count, which a long title never hides)
    if (blocks()) countStart() - side()
    else RichRow.this.size.width() - ListRow.inset - 22.0 - RichRow.inset + 8.0 - nest() -
      (if (owner.headersHaveMenus()) RichRow.headerMore - 2.0 else 0.0), base(), 12.5)
  heading.position.left := ListRow.along(if (blocks()) side() else ListRow.inset + 22.0 + nest(), heading.size.width(),
    size.width())
  heading.position.top := above()
  heading.visible := header() && !(blocks() && group().nonEmpty)
  /** The header's ⋮, opening its section's (or group's) menu: a little smaller than an item's, to fit the header. */
  val sectionMore: IconButton = new IconButton(MaterialIcon.MoreVert, RichList.moreLabel, () => openSectionMore()) {
    size.width @= RichRow.headerMore
    size.height @= RichRow.headerMore
    radius @= RichRow.headerMore / 2.0
    icon.position.left @= (RichRow.headerMore - 22.0) / 2.0
    icon.position.top @= (RichRow.headerMore - 22.0) / 2.0
    visible := sectionMenuShows()
  }
  sectionMore.focusable @= false

  // ---- an item ----
  private val nobody = Participant("", "")
  private def face(diameter: Double, wanted: => Boolean): Avatar = new Avatar(look().flatMap(_.who).getOrElse(nobody), diameter) {
    visible := wanted && look().exists(_.who.nonEmpty)
    busy := wanted && look().exists(_.busy)
    busyDetail := look().map(_.busyDetail).getOrElse("")
    position.left := ListRow.along(side(), diameter, RichRow.this.size.width())
    position.top := middle(diameter)
  }
  val tallFace: Avatar = face(RichRow.tallLead, tall())
  private val shortFace: Avatar = face(RichRow.shortLead, !tall())
  private val glyph = new MaterialIconView(MaterialIcon.Empty, 22.0) {
    icon := look().flatMap(l => if (l.who.isEmpty) l.icon else None).getOrElse(MaterialIcon.Empty)
    color := look().flatMap(_.iconColor).getOrElse(Theme.muted())
    visible := look().exists(l => l.who.isEmpty && l.icon.nonEmpty)
    position.left := ListRow.along(side() + (lead() - 22.0) / 2.0, 22.0, RichRow.this.size.width())
    position.top := middle(22.0)
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
    if (tall()) above() + (base() - title.size.height() - detail.size.height() - 2.0) / 2.0
    else middle(title.size.height())))
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
  more.position.top := middle(more.size.height())
  // (in a block, before the ▾ or ▸ at the header's end)
  from(end() - toggleRoom() - sectionMore.size.width() + 6.0, sectionMore)
  sectionMore.position.top := (if (blocks() && group().nonEmpty) captionMiddle() - sectionMore.size.height() / 2.0
    else middle(sectionMore.size.height()))

  size.height := base() + above() + below()
  // (in a block the row draws none of it: its block's panel and the light in it do)
  backgroundColor := (if (blocks()) Color.Clear else lit())
  borderWidth := (if (!blocks() && current() && active()) 1.0 else 0.0)
  semantics := item().map(l => Semantics("listitem", label = l.item match {
    case Some(t) => RichList.said(owner.lookOf(t), owner.now(), owner.timeText)
    case None => l.groupHead.map(_.title).getOrElse(l.section.title) + ", " + l.count.toString
  })).getOrElse(Semantics.Plain)
  children ++= List(chevron, heading, sectionMore, tallFace, shortFace, glyph, title, detail, mark, time, badge, more)

  // what the row is lit with: a drop into it, the item chosen, the cursor, the pointer over it
  private def lit(): Color =
    if (drop().contains(DropPosition.Into)) Theme.accent().withAlpha(0.45)
    else if (chosen()) Theme.accent().withAlpha(if (active()) 0.26 else 0.18)
    else if (current() && active()) Theme.control()
    else if (hovered()) Theme.control().withAlpha(0.6)
    else Color.Clear

  // ---- a block (RichStyle.Blocks): made the first time the list draws them, so a tree's rows have none of it ----
  private lazy val inBlock: Val[Boolean] = own(Val(blocks() && item().exists(_.groupHead.isEmpty)))
  // the block's part behind this row: down to the gap under the block
  private lazy val part: Val[Double] = own(Val(size.height() - (if (item().exists(_.blockEnd)) RichRow.blockGap else 0.0)))
  private[list] lazy val panel: Container = new Container {
    // past the part's top and bottom by a corner where the block goes on, so only the block's own corners are round
    size.width := math.max(0.0, RichRow.this.size.width() - 2.0 * RichRow.blockMargin)
    position.top := (if (item().exists(_.blockStart)) 0.0 else -RichRow.blockRadius)
    size.height := part() + (if (item().exists(_.blockStart)) 0.0 else RichRow.blockRadius) +
      (if (item().exists(_.blockEnd)) 0.0 else RichRow.blockRadius)
    radius @= RichRow.blockRadius
    backgroundColor := RichRow.blockTint()
  }
  private[list] lazy val holder: Container = new Container {
    position.left @= RichRow.blockMargin
    size.width := panel.size.width()
    size.height := part()
    visible := inBlock()
    children += panel
  }
  // the cursor, the pointer and the item chosen, inside the block's padding
  private[list] lazy val light: Container = new Container {
    position.left @= RichRow.blockMargin + RichRow.blockPad / 2.0
    position.top := above() + 2.0
    size.width := math.max(0.0, RichRow.this.size.width() - 2.0 * RichRow.blockMargin - RichRow.blockPad)
    size.height := math.max(0.0, base() - 4.0)
    radius @= RichRow.blockRadius - 3.0
    backgroundColor := (if (blocks()) lit() else Color.Clear)
    borderWidth := (if (blocks() && current() && active()) 1.0 else 0.0)
    borderColor := Theme.accent()
    visible := blocks()
  }
  // a group's title over its blocks: small, muted, in capitals, a little spaced
  private[list] lazy val caption: TextView = new TextView {
    content := group().map(_.title.toUpperCase).getOrElse("")
    font.size @= 11.0
    font.weight @= FontWeight.SemiBold
    font.letterSpacing @= 0.8
    color := Theme.muted()
    maxWidth := math.max(20.0, RichRow.this.size.width() - side() - side() - toggleRoom() -
      (if (headerHasMenu()) RichRow.headerMore else 0.0))
    visible := blocks() && group().nonEmpty
    position.left := ListRow.along(side(), size.width(), RichRow.this.size.width())
    position.top := above() + base() - RichRow.captionBelow - size.height()
  }
  private lazy val captionMiddle: Val[Double] = own(Val(caption.position.top() + caption.size.height() / 2.0))
  private[list] lazy val toggle: MaterialIconView = new MaterialIconView(MaterialIcon.ExpandMore, RichRow.toggleSize) {
    icon := (if (item().exists(_.open)) MaterialIcon.ExpandMore
      else if (everyui.i18n.Direction.rtl()) MaterialIcon.ChevronLeft else MaterialIcon.ChevronRight)
    color := Theme.muted()
    visible := toggles()
    position.left := ListRow.along(RichRow.this.size.width() - side() - RichRow.toggleSize, RichRow.toggleSize,
      RichRow.this.size.width())
    position.top := (if (group().nonEmpty) captionMiddle() - RichRow.toggleSize / 2.0 else middle(RichRow.toggleSize))
  }
  // a section's count at its bar's end, before its ⋮ (in one line down the list where any header has one) and its ▾
  private[list] lazy val count: TextView = new TextView {
    content := item().filter(_.item.isEmpty).map(_.count.toString).getOrElse("")
    font.size @= 10.5
    color := Theme.muted()
    visible := blocks() && section().nonEmpty
    position.top := middle(size.height())
  }
  private lazy val countStart: Val[Double] = {
    val start = own(Val(end() - toggleRoom() - (if (owner.headersHaveMenus()) RichRow.headerMore - 2.0 else 0.0) -
      count.size.width()))
    count.position.left := ListRow.along(start(), count.size.width(), RichRow.this.size.width())
    start
  }
  private var blockMade = false
  private def makeBlock(): Unit = if (!blockMade) {
    blockMade = true
    holder.size.width.attach(_ => holder.node.setClip(holder.size.width(), holder.size.height()))
    holder.size.height.attach(_ => holder.node.setClip(holder.size.width(), holder.size.height()))
    holder.node.setClip(holder.size.width(), holder.size.height())
    children @= Vector[Component](holder, light) ++ children() ++ Vector[Component](caption, count, toggle)
  }
  blocks.attach(b => if (b) makeBlock())
  if (blocks()) makeBlock()

  override def editStart: Double = textStart() - 6.0

  private def openSectionMore(): Unit = item().filter(_.item.isEmpty).foreach { l =>
    owner.list.current @= Some(l.key)
    l.groupHead match {
      case Some(g) => owner.openGroupMenu(g, Some(sectionMore), 0.0, 0.0)
      case None => owner.openSectionMenu(l.section, Some(sectionMore), 0.0, 0.0)
    }
  }

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
  /** How far a section's header in a group is set in under the group's: its chevron under the group's title. */
  val nest: Double = 22.0
  /** How big a header's ⋮ is. */
  val headerMore: Double = 28.0
  val shortHeight: Double = 44.0
  val tallHeight: Double = 60.0
  /** How big the avatar is in a row with a second line, and in one without. */
  val tallLead: Double = 38.0
  val shortLead: Double = 28.0

  /** In `RichStyle.Blocks`: how far a block is from the list's sides, and its padding inside. */
  val blockMargin: Double = 8.0
  val blockPad: Double = 6.0
  /** How round a block's corners are, and the gap between one block and the next. */
  val blockRadius: Double = 10.0
  val blockGap: Double = 8.0
  /** How tall a block's header bar is, and a group's caption, whose words sit `captionBelow` over the block after it. */
  val blockHeader: Double = 36.0
  val captionHeight: Double = 30.0
  val captionBelow: Double = 6.0
  /** How big the ▾ or ▸ at a block's header's end is. */
  val toggleSize: Double = 16.0

  /** A block's tint: the theme's `list.block`, else the text's colour, faintly, so it shows on any ground. */
  def blockTint(): Color = Theme.extra("list.block", Theme.text().withAlpha(0.05))
}

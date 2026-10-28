package everyui.list

import everyui.Color
import everyui.chat.{Avatar, Participant}
import everyui.component.{Button, ButtonStyle, Component, Container, IconButton, MaterialIconView, TextView}
import everyui.component.types.{FontWeight, Semantics, WhiteSpace}
import everyui.easing.Easing
import everyui.font.MaterialIcon
import everyui.markdown.{MarkdownStyle, MarkdownView}
import everyui.render.Platform
import everyui.task.{PartialAnimate, TaskInstance, sequential, synchronous}
import everyui.theme.Theme
import reactify.*
import scala.concurrent.duration.*

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
  * leaves the gap under the block. The cursor, the pointer and the item chosen are lit inside the block.
  *
  * A section's action (`RichSection.action`) is a small button in its header, and an open section that holds nothing
  * shows its `empty` words on a line of their own, muted, which is never lit.
  *
  * An item expanded (`RichList.expansion`) shows its whole text under its title's line, from where the title starts to
  * the row's end, wrapped (as Markdown unless the expansion says not), and its actions as buttons under it, the words of
  * the first in line with the text's, wrapping onto another line where they do not fit; the row is as tall as all of
  * it, lit and ringed whole, and its text fades in as it opens (at once where Quality says glides jump).
  *
  * A live item (`RichLook.live`) is tinted faintly in the accent where nothing else lights it, and has a thin accent bar
  * at the row's start edge whatever lights it, made the first time a row shows one (in a block, in the margin beside it).
  *
  * While the list's items can be dragged (`RichList.dragsItems`) an item's row has a grip at its start, in room of its
  * own before its avatar or icon, so nothing shifts as it shows: with the pointer over the row or the cursor on it, and
  * on every row on a touch screen. */
class RichRow[T](owner: RichList[T]) extends ListRow[RichLine[T]] {
  private val header: Val[Boolean] = own(Val(item().exists(_.header)))
  /** Whether it is the line an empty section shows in place of its items. */
  private val emptyLine: Val[Boolean] = own(Val(item().exists(_.empty)))
  private val look: Val[Option[RichLook]] = own(Val(item().flatMap(_.item).map(owner.lookOf)))
  private val tall: Val[Boolean] = own(Val(look().exists(_.detail.nonEmpty)))
  private val unread: Val[Boolean] = own(Val(look().exists(_.unread)))
  /** Whether its item is live somewhere else in the app (`RichLook.live`): open in a tab, playing, connected. */
  private[list] val live: Val[Boolean] = own(Val(look().exists(_.live)))
  /** Whether it shows the item the app has chosen (`RichList.selected`). */
  val chosen: Val[Boolean] = own(Val(item().flatMap(_.item).exists(t => owner.selected().contains(owner.keyOf(t)))))
  private val hovered: Val[Boolean] = if (Platform.pointerSupport()) event.pointer.over else own(Val(false))
  /** The group this row heads, where it is a group's header. */
  private val group: Val[Option[RichGroup]] = own(Val(item().filter(_.item.isEmpty).flatMap(_.groupHead)))
  /** The header's section, where this row is a section's header. */
  private val section: Val[Option[RichSection[T]]] = own(Val(item().filter(l => l.header && l.groupHead.isEmpty)
    .map(_.section)))
  /** Whether the list draws its sections as blocks (`RichStyle.Blocks`). */
  private val blocks: Val[Boolean] = own(Val(owner.style() == RichStyle.Blocks))
  // how far a section's header in a group is set in under the group's (never in a block)
  private val nest: Val[Double] = own(Val(if (section().nonEmpty && item().exists(_.nested) && !blocks()) RichRow.nest
    else 0.0))
  // where the pieces start from either edge: inside the block's padding in a block
  private val side: Val[Double] = own(Val(if (blocks()) RichRow.blockMargin + RichRow.blockPad else RichRow.inset))
  // how tall the row's own line is, and the room a block leaves above and below it (its padding, and the gap after it)
  // how tall an item's words are, with the room round them: a title or a detail that wraps (words wrap by default) makes
  // its row taller, the list measuring each row
  private val wordsTall: Var[Double] = own(Var(0.0))
  private val base: Val[Double] = own(Val(
    if (header()) {
      if (!blocks()) RichRow.headerHeight else if (group().nonEmpty) RichRow.captionHeight else RichRow.blockHeader
    }
    else if (emptyLine()) RichRow.emptyHeight
    else math.max(if (tall()) RichRow.tallHeight else RichRow.shortHeight, wordsTall())))
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
    (hovered() || (current() && cursorShown()) || !Platform.pointerSupport())))
  private val menuShows: Val[Boolean] = own(Val(!header() && item().flatMap(_.item).exists(t => owner.menu()(t).nonEmpty) &&
    (hovered() || (current() && cursorShown()) || (chosen() && Platform.touchSupport() && !Platform.pointerSupport()))))
  // the room a grip takes at an item's start while its list drags them (`RichList.dragsItems`), shown or not, so the
  // row never shifts as it comes and goes
  private val gripRoom: Val[Double] = own(Val(if (item().exists(_.item.nonEmpty) && owner.dragsItems()) RichRow.gripRoom
    else 0.0))
  // where an item's avatar or icon starts, from the start edge: past its grip's room
  private val itemStart: Val[Double] = own(Val(side() + gripRoom()))
  private val lead: Val[Double] = own(Val(if (tall()) RichRow.tallLead else RichRow.shortLead))
  private val hasLead: Val[Boolean] = own(Val(look().exists(l => l.who.nonEmpty || l.icon.nonEmpty)))
  /** Where the title starts, from the start edge. */
  private val textStart: Val[Double] = own(Val(itemStart() + (if (hasLead()) lead() + 10.0 else 0.0)))
  // the end edge the trailing pieces line up against, clear of the list's scroll thumb
  private val end: Val[Double] = own(Val(size.width() - side()))
  // a block's ▾ or ▸, at a header's end: a section's always, a group's caption only while it is closed
  private val toggles: Val[Boolean] = own(Val(blocks() && header() && (section().nonEmpty ||
    item().exists(l => l.groupHead.nonEmpty && !l.open))))
  private val toggleRoom: Val[Double] = own(Val(if (toggles()) RichRow.toggleSize + 6.0 else 0.0))
  // the section's action (or the item's), where it has one, and whether the cursor is on it rather than on the row
  private val actionOf: Val[Option[RichAction]] = own(Val(section().flatMap(_.action).orElse(look().flatMap(_.action))))
  private val onAction: Val[Boolean] = own(Val(current() && active() && owner.atAction() && actionOf().nonEmpty))
  /** Whether the item expands (`RichList.expansion`), whether it shows expanded or not. */
  private val expands: Val[Boolean] = own(Val(item().flatMap(_.item).exists(t => owner.expansion()(t).nonEmpty)))
  /** What the item shows expanded, where it is. */
  private[list] val expansionOf: Val[Option[RichExpansion]] = own(Val(item().filter(_.expanded).flatMap(_.item)
    .flatMap(t => owner.expansion()(t))))
  // how much taller the row is for its expansion (0 until a row first shows one, and folded)
  private val extra: Var[Double] = own(Var(0.0))
  // whether the cursor is on one of the expansion's buttons rather than the row
  private val onButtons: Val[Boolean] = own(Val(current() && active() && owner.atButton() >= 0 && expansionOf().nonEmpty))

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
      badge = if (blocks()) "" else item().filter(_.header).map(_.count.toString).getOrElse("")),
    // short of the ⋮ where any header has a menu, and of the action where any section has one, so the counts keep one
    // line as the ⋮ comes and goes (in a block, short of its count, which a long title never hides, and its action)
    if (blocks()) countStart() - (if (section().exists(_.action.nonEmpty)) RichRow.headerMore else 0.0) - side()
    else RichRow.this.size.width() - ListRow.inset - 22.0 - RichRow.inset + 8.0 - nest() -
      (if (owner.headersHaveMenus()) RichRow.headerMore - 2.0 else 0.0) -
      (if (owner.headersHaveActions()) RichRow.headerMore else 0.0), base(), 12.5)
  heading.position.left := ListRow.along(if (blocks()) side() else ListRow.inset + 22.0 + nest(), heading.size.width(),
    size.width())
  heading.position.top := above()
  heading.visible := header() && !(blocks() && group().nonEmpty)
  // a header is a line tall: its title cut short is read whole in its tip
  tip := (if (header()) heading.cut() else None)
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
    position.left := ListRow.along(itemStart(), diameter, RichRow.this.size.width())
    position.top := middle(diameter)
  }
  val tallFace: Avatar = face(RichRow.tallLead, tall())
  private val shortFace: Avatar = face(RichRow.shortLead, !tall())
  private val glyph = new MaterialIconView(MaterialIcon.Empty, 22.0) {
    icon := look().flatMap(l => if (l.who.isEmpty) l.icon else None).getOrElse(MaterialIcon.Empty)
    color := look().flatMap(_.iconColor).getOrElse(Theme.muted())
    visible := look().exists(l => l.who.isEmpty && l.icon.nonEmpty)
    position.left := ListRow.along(itemStart() + (lead() - 22.0) / 2.0, 22.0, RichRow.this.size.width())
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
  // in a row with an action, the room its trailing pieces take whichever show (the ⋮ or the time, the mark and the badge),
  // so the action and the title keep their places as the ⋮ comes and goes
  private val itemAction: Val[Boolean] = own(Val(!header() && look().exists(_.action.nonEmpty)))
  private val restTrail: Val[Double] = own(Val(if (!itemAction()) 0.0 else math.max(
    (if (look().exists(_.time.nonEmpty)) time.size.width() else 0.0) +
      (if (look().exists(_.mark.nonEmpty)) mark.size.width() + 4.0 else 0.0) +
      (if (!tall() && look().exists(_.count > 0)) badge.size.width() + 6.0 else 0.0),
    // (the ⋮'s glyph, its box reaching 6 past the end and 5 round the glyph)
    if (item().flatMap(_.item).exists(t => owner.menu()(t).nonEmpty)) more.size.width() - 11.0 else 0.0)))

  val title: TextView = new TextView {
    content := look().map(_.title).getOrElse("")
    font.size @= 14.0
    font.weight := (if (unread()) FontWeight.Bold else FontWeight.Normal)
    color := Theme.text()
    // (short of the action, where the row has one, whether it shows or not)
    maxWidth := math.max(20.0, end() - textStart() - (if (itemAction()) restTrail() + RichRow.headerMore else firstTrail()) -
      8.0)
    visible := !header() && !editing()
  }
  val detail: TextView = new TextView {
    content := look().flatMap(_.detail).getOrElse("")
    font.size @= 12.5
    color := (if (unread()) Theme.text() else Theme.muted())
    maxWidth := math.max(20.0, end() - textStart() - secondTrail() - 8.0)
    visible := tall()
  }

  wordsTall := (if (header() || emptyLine()) 0.0
    else if (tall()) title.size.height() + detail.size.height() + 2.0 + RichRow.wordsPad
    else title.size.height() + RichRow.wordsPad)

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

  size.height := base() + above() + below() + extra()
  // (in a block the row draws none of it: its block's panel and the light in it do)
  backgroundColor := (if (blocks()) Color.Clear else lit())
  borderWidth := (if (!blocks() && current() && cursorShown() && !onAction() && !onButtons()) 1.0 else 0.0)
  // (an item that expands says whether it is)
  semantics := item().map(l => Semantics("listitem", label = l.item match {
    case Some(t) => RichList.said(owner.lookOf(t), owner.now(), owner.timeText)
    case None if l.empty => l.section.empty.getOrElse("")
    case None => l.groupHead.map(_.title).getOrElse(l.section.title) + ", " + l.count.toString
  }, expanded = if (expands()) Some(l.expanded) else None)).getOrElse(Semantics.Plain)
  children ++= List(chevron, heading, sectionMore, tallFace, shortFace, glyph, title, detail, mark, time, badge, more)

  // what the row is lit with: a drop into it, the item chosen, the cursor, the pointer over it, its item live elsewhere
  // (a live item's bar stays whatever lights it, so the item chosen and live shows both)
  private def lit(): Color =
    if (emptyLine()) Color.Clear
    else if (drop().contains(DropPosition.Into)) Theme.accent().withAlpha(0.45)
    else if (chosen()) Theme.accent().withAlpha(if (active()) 0.26 else 0.18)
    else if (current() && cursorShown()) Theme.control()
    else if (hovered()) Theme.control().withAlpha(0.6)
    else if (live()) RichRow.liveTint()
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
    size.height := math.max(0.0, base() + extra() - 4.0)
    radius @= RichRow.blockRadius - 3.0
    backgroundColor := (if (blocks()) lit() else Color.Clear)
    borderWidth := (if (blocks() && current() && cursorShown() && !onAction() && !onButtons()) 1.0 else 0.0)
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
    content := item().filter(_.header).map(_.count.toString).getOrElse("")
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

  // ---- a section's action and an empty section's line: made the first time a row shows one, so others have none ----
  /** The section's action (`RichSection.action`) in its header: before its count in a block, at the header's end in a
    * tree (short of the ⋮ where any header has one); or the item's (`RichLook.action`) before its time, its mark and its
    * badge, or its ⋮. Lit and ringed as the ⋮ is, and while the cursor is on it. */
  private[list] lazy val actionButton: IconButton = new IconButton(MaterialIcon.Empty, "", () => actionOf().foreach(_.run())) {
    size.width @= RichRow.headerMore
    size.height @= RichRow.headerMore
    radius @= RichRow.headerMore / 2.0
    icon.position.left @= (RichRow.headerMore - 22.0) / 2.0
    icon.position.top @= (RichRow.headerMore - 22.0) / 2.0
    icon.icon := actionOf().map(_.icon).getOrElse(MaterialIcon.Empty)
    tooltip := actionOf().map(_.label).getOrElse("")
    semantics := Semantics.button(actionOf().map(_.label).getOrElse(""))
    visible := actionShows()
    backgroundColor := (if (focusShown() || event.pointer.over() || onAction()) Theme.controlLit() else Color.Clear)
    icon.color := (if (focusShown() || event.pointer.over() || onAction()) Theme.text() else tint())
    borderWidth := (if (onAction()) 1.0 else 0.0)
    borderColor := Theme.accent()
  }
  /** The words an open section that holds nothing shows in place of its items (`RichSection.empty`), muted. */
  private[list] lazy val emptyText: TextView = new TextView {
    content := item().filter(_.empty).flatMap(_.section.empty).getOrElse("")
    font.size @= 13.0
    color := Theme.muted()
    maxWidth := math.max(20.0, end() - side())
    visible := emptyLine()
    position.left := ListRow.along(side(), size.width(), RichRow.this.size.width())
    position.top := middle(size.height())
  }
  // a section's always; an item's while the pointer or the cursor is on its row, it is the one chosen on a touch screen, or
  // always where its look or the action says
  private val actionShows: Val[Boolean] = own(Val(actionOf().nonEmpty && (header() || look().exists(_.actionShown) ||
    actionOf().exists(_.shown) ||
    hovered() || (current() && cursorShown()) || (chosen() && Platform.touchSupport() && !Platform.pointerSupport()))))
  private var actionMade = false
  private def makeAction(): Unit = if (!actionMade) {
    actionMade = true
    actionButton.focusable @= false
    // (a header's glyph 6 short of the count in a block; in a tree, its glyph's end at the end, or the ⋮'s gap before
    // it; an item's glyph 6 short of its trailing pieces on the title's line, or at the end without any)
    from(if (!header()) end() - restTrail() - (if (restTrail() > 0.0) 6.0 else 0.0) - RichRow.headerMore + 3.0
      else if (blocks()) countStart() - RichRow.headerMore - 3.0
      else end() - (if (owner.headersHaveMenus()) RichRow.headerMore - 2.0 else 0.0) - RichRow.headerMore + 3.0,
      actionButton)
    actionButton.position.top := (if (header()) middle(RichRow.headerMore) else lineMiddle() - RichRow.headerMore / 2.0)
    children += actionButton
  }
  actionOf.attach(a => if (a.nonEmpty) makeAction())
  if (actionOf().nonEmpty) makeAction()
  /** Whether `along` (from the row's start edge) is on its action (its section's, or its item's), which a click there is for alone. */
  def onActionAt(along: Double): Boolean = actionMade && actionButton.visible() && {
    val b = actionButton
    val start = if (everyui.i18n.Direction.rtl()) size.width() - b.position.left() - b.size.width() else b.position.left()
    along >= start && along <= start + b.size.width()
  }
  private var emptyMade = false
  private def makeEmpty(): Unit = if (!emptyMade) {
    emptyMade = true
    children += emptyText
  }
  emptyLine.attach(e => if (e) makeEmpty())
  if (emptyLine()) makeEmpty()

  // ---- a live item (RichLook.live): its bar made the first time a row shows one, so others have none ----
  /** The thin accent bar at a live item's start edge, as tall as its line less `liveInset` at each end, `liveFrom` in from
    * the row's edge: clear of the cursor's ring in a tree, and in a block in the margin beside it, clear of an avatar's
    * busy ring, which reaches into the block's padding. */
  private[list] lazy val liveBar: Container = new Container {
    size.width @= RichRow.liveWidth
    size.height := math.max(0.0, base() + extra() - 2.0 * RichRow.liveInset)
    radius @= RichRow.liveWidth / 2.0
    backgroundColor := RichRow.liveEdge()
    visible := live()
    position.left := ListRow.along(RichRow.liveFrom, RichRow.liveWidth, RichRow.this.size.width())
    position.top := above() + RichRow.liveInset
  }
  private var liveMade = false
  private def makeLive(): Unit = if (!liveMade) {
    liveMade = true
    children += liveBar
  }
  live.attach(l => if (l) makeLive())
  if (live()) makeLive()

  // ---- an item that can be dragged (RichList.dragsItems): its grip made the first time a row shows one ----
  /** The grip at the start of an item's row while its list drags them: muted, in its own room before the avatar, shown
    * while the pointer is over the row or the cursor is on it, and on every row where there is no pointer. The whole
    * row drags, the grip only says so. */
  private[list] lazy val grip: MaterialIconView = new MaterialIconView(MaterialIcon.DragIndicator, RichRow.gripSize) {
    color := Theme.muted()
    visible := gripRoom() > 0.0 && (hovered() || (current() && cursorShown()) ||
      (Platform.touchSupport() && !Platform.pointerSupport()))
    position.left := ListRow.along(side() - RichRow.gripBefore, RichRow.gripSize, RichRow.this.size.width())
    position.top := middle(RichRow.gripSize)
  }
  private var gripMade = false
  private def makeGrip(): Unit = if (!gripMade) {
    gripMade = true
    children += grip
  }
  gripRoom.attach(g => if (g > 0.0) makeGrip())
  if (gripRoom() > 0.0) makeGrip()

  // ---- an expanded item (RichList.expansion): made the first time a row shows one, so others have none ----
  /** How tall the row's own line is, with the room above it in a block: the expansion goes under it. */
  private[list] def headHeight: Double = above() + base()
  // the text's width, from where the title starts to the row's end
  private lazy val bodyWidth: Val[Double] = own(Val(math.max(40.0, end() - textStart())))
  /** The expansion's text as Markdown. */
  private[list] lazy val markdown: MarkdownView = new MarkdownView(style = RichRow.textStyle) {
    content := expansionOf().filter(_.markdown).map(_.text).getOrElse("")
    size.width := bodyWidth()
    visible := expansionOf().exists(_.markdown)
  }
  /** The expansion's text as it is, where it is not Markdown. */
  private[list] lazy val plain: TextView = new TextView {
    content := expansionOf().filter(!_.markdown).map(_.text).getOrElse("")
    font.size @= RichRow.textStyle.points
    color := Theme.text()
    whiteSpace @= WhiteSpace.Normal
    wrapAnywhere @= true
    size.width := bodyWidth()
    visible := expansionOf().exists(!_.markdown)
  }
  private lazy val textHeight: Val[Double] = own(Val(
    if (expansionOf().exists(_.markdown)) markdown.size.height() else if (expansionOf().nonEmpty) plain.size.height() else 0.0))
  /** The expansion's actions as buttons, in order. */
  private[list] val buttons: Var[Vector[Button]] = own(Var(Vector.empty))
  // what the buttons were made for: each action's glyph, words and danger (they run whatever the expansion has now)
  private var shapesMade: List[(MaterialIcon, String, Boolean)] = Nil
  // the buttons' places from the start of their line, and how far down: as many in a line as fit, the next under them
  private lazy val flow: Val[Vector[(Double, Double)]] = own(Val {
    val room = bodyWidth() + RichRow.buttonLead
    var x = 0.0
    var y = 0.0
    buttons().map { b =>
      if (x > 0.0 && x + b.size.width() > room) {
        x = 0.0
        y += b.size.height() + 2.0
      }
      val at = (x, y)
      x += b.size.width() + 4.0
      at
    }
  })
  // (the places read with the buttons they are for: one changes before the other has followed it)
  private lazy val barHeight: Val[Double] = own(Val {
    val bs = buttons()
    val at = flow()
    if (bs.isEmpty || at.size != bs.size) 0.0 else at(at.size - 1)._2 + bs(bs.size - 1).size.height()
  })
  private lazy val bodyTop: Val[Double] = own(Val(above() + base() - 2.0))
  private lazy val barTop: Val[Double] = own(Val(bodyTop() + textHeight() + (if (textHeight() > 0.0) 6.0 else 0.0)))
  private lazy val body: Container = new Container {
    visible := expansionOf().nonEmpty
    children ++= List(markdown, plain)
  }
  private var bodyMade = false
  private var fading: Option[TaskInstance] = None
  private def makeBody(): Unit = if (!bodyMade) {
    bodyMade = true
    from(textStart(), markdown)
    markdown.position.top := bodyTop()
    from(textStart(), plain)
    plain.position.top := bodyTop()
    extra := (if (expansionOf().isEmpty) 0.0 else barTop() + barHeight() + RichRow.bodyBelow - above() - base())
    children += body
    expansionOf.attach(e => showBody(e))
    showBody(expansionOf())
  }
  private def showBody(e: Option[RichExpansion]): Unit = {
    val actions = e.map(_.actions).getOrElse(Nil)
    val shapes = actions.map(a => (a.icon, a.label, a.danger))
    if (shapes != shapesMade) {
      shapesMade = shapes
      val old = buttons()
      body.children @= Vector[Component](markdown, plain)
      old.foreach(_.dispose())
      buttons @= actions.indices.toVector.map(i => button(i, actions(i)))
      body.children @= Vector[Component](markdown, plain) ++ buttons()
    }
    fading.foreach(_.cancel())
    fading = None
    body.opacity @= 1.0
    // the item just expanded fades in; one scrolled into view expanded is simply there
    val key = item().flatMap(_.item).map(owner.keyOf)
    if (e.nonEmpty && key.nonEmpty && owner.opening == key) {
      owner.opening = None
      if (everyui.perf.Quality.glides) {
        body.opacity @= 0.0
        val in = PartialAnimate(() => 0.0, (t: Double) => body.opacity @= t, () => 1.0).in(RichRow.fade.millis)
          .easing(Easing.cubicOut)
        val run = sequential(in, synchronous(body.opacity @= 1.0)).start()
        // (where the app runs no frame loop nothing would step it: shown at once)
        if (!everyui.task.Frames.running) {
          run.cancel()
          body.opacity @= 1.0
        }
        else fading = Some(run)
        // shown whole by then whatever happens, the loop held up or not
        if (fading.nonEmpty) Platform.after(RichRow.fade.toDouble / 1000.0 + 0.4, () => if (fading.exists(_ eq run)) {
          run.cancel()
          fading = None
          body.opacity @= 1.0
        })
      }
    }
  }
  // the expansion's action `i`: its words after its glyph, in the danger colour where it is dangerous, lit and ringed
  // while the cursor is on it as the row's action is
  private def button(i: Int, a: RichAction): Button = {
    val b = new Button(a.label, () => runButton(i), ButtonStyle.Text, Some(a.icon), RichRow.buttonPoints)
    b.danger @= a.danger
    b.focusable @= false
    val on: Val[Boolean] = b.own(Val(current() && active() && owner.atButton() == i))
    b.backgroundColor := (if (on() || b.event.pointer.over()) Theme.controlLit() else Color.Clear)
    b.borderWidth := (if (on()) 1.5 else 0.0)
    b.borderColor := Theme.accent()
    b.position.left := ListRow.along(textStart() - RichRow.buttonLead + flow().drop(i).headOption.map(_._1).getOrElse(0.0),
      b.size.width(), RichRow.this.size.width())
    b.position.top := barTop() + flow().drop(i).headOption.map(_._2).getOrElse(0.0)
    b
  }
  /** The expansion's action `i` run, where it has one. */
  private[list] def runButton(i: Int): Unit = {
    val a = expansionOf().map(_.actions).getOrElse(Nil).drop(i).headOption
    a.foreach(_.run())
  }
  expansionOf.attach(e => if (e.nonEmpty) makeBody())
  if (expansionOf().nonEmpty) makeBody()

  override def editStart: Double = textStart() - 6.0

  private def openSectionMore(): Unit = item().filter(_.header).foreach { l =>
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
  /** The room above and below an item's words, which a row that grows for them keeps. */
  val wordsPad: Double = 22.0
  val headerHeight: Double = 30.0
  /** How far a section's header in a group is set in under the group's: its chevron under the group's title. */
  val nest: Double = 22.0
  /** How big a header's ⋮ is. */
  val headerMore: Double = 28.0
  val shortHeight: Double = 44.0
  val tallHeight: Double = 60.0
  /** How tall the line an empty section shows (`RichSection.empty`) is. */
  val emptyHeight: Double = 36.0
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

  /** How an expanded item's text is set (`RichExpansion`): a little smaller than the title, as a sidebar's are. */
  lazy val textStyle: MarkdownStyle = MarkdownStyle(points = 13.0, codePoints = 12.0, headings = List(1.3, 1.18, 1.08, 1.0,
    1.0, 0.92), spacing = 8.0, indent = 18.0, imageHeight = 240.0)
  /** How big an expanded item's buttons' words are, and how far a button's edge is before its words. */
  val buttonPoints: Double = 12.5
  val buttonLead: Double = buttonPoints * 1.1
  /** The room under an expanded item's buttons (or its text, without any). */
  val bodyBelow: Double = 8.0
  /** How long an item's text takes to fade in as it expands, in milliseconds. */
  val fade: Long = 160L

  /** A block's tint: the theme's `list.block`, else the text's colour, faintly, so it shows on any ground. */
  def blockTint(): Color = Theme.extra("list.block", Theme.text().withAlpha(0.05))

  /** How far a live item's bar is from the row's start edge, in a tree and in blocks alike. */
  val liveFrom: Double = 2.0
  /** How wide a live item's bar is (`RichLook.live`), and how far short of its line's top and bottom it stops. */
  val liveWidth: Double = 3.0
  val liveInset: Double = 9.0
  /** A live item's tint: the theme's `list.live`, else the accent, faintly, well under the item chosen's. */
  def liveTint(): Color = Theme.extra("list.live", Theme.accent().withAlpha(0.08))
  /** A live item's bar: the theme's `list.liveBar`, else the accent. */
  def liveEdge(): Color = Theme.extra("list.liveBar", Theme.accent())

  /** How big an item's grip is (`RichList.dragsItems`), how far before where the row's pieces start it begins (clear of
    * a live item's bar), and the room it takes before them. */
  val gripSize: Double = 16.0
  val gripBefore: Double = 4.0
  val gripRoom: Double = 14.0
}

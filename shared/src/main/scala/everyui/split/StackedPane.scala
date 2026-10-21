package everyui.split

import everyui.Color
import everyui.component.*
import everyui.component.types.{FontWeight, Semantics}
import everyui.event.{Key, Pointer}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.kit.Mirror
import everyui.list.RichAction
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** One pane of a `StackedPanes`: a header (a chevron, its `icon`, its `title` and its `actions`) over its `content`.
  * `collapsed` folds it to its header and `weight` is its share of the height while it is open: pass in Vars the app
  * keeps (and saves) to remember them. Open, its content is never made shorter than `minHeight` while there is room.
  *
  * A click or a tap on the header folds it or unfolds it, as Enter and Space do with the header focused. Its actions
  * sit at the header's end, each shown while the pointer is over the header or the focus is in it, or always where it
  * says (`RichAction.shown`; always on a touch screen, which has no pointer to show them); a click on one runs it and
  * folds nothing. The header reads as a button that opens and closes the pane, and the content as a region named by
  * the title. */
class StackedPane(heading: String, val content: Component, val collapsed: Var[Boolean] = Var(false),
                  val weight: Var[Double] = Var(1.0)) extends Container {
  val title: Var[String] = own(Var(heading))
  val icon: Var[Option[MaterialIcon]] = own(Var(None))
  val actions: Var[List[RichAction]] = own(Var(Nil))
  /** The least height its content is given while it is open, room allowing. */
  val minHeight: Var[Double] = own(Var(StackedPanes.minimum))
  /** How tall its header is: taller on a touch screen, for a finger. */
  val headerHeight: Var[Double] = own(Var(StackedPane.headerHeight()))
  /** The stack it is in, for the arrows out of it. */
  private[split] var stack: Option[StackedPanes] = None
  /** Whether a rule is drawn over its header: every pane's but the first. */
  private[split] val ruled: Var[Boolean] = own(Var(false))

  /** Folded, or opened. */
  def toggle(): Unit = collapsed @= !collapsed()

  // ---- the header ----
  val header: Container = new Container {
    focusable @= true
    override def keepsKey(key: Key): Boolean = key == Key.Space
    // the arrows from it, and from its actions, as the stack says
    override def focusAction(direction: Key): Option[() => Unit] =
      stack.flatMap(_.fromHeader(StackedPane.this, direction))
    override def kind: String = "PaneHeader"
  }
  private val hovered: Val[Boolean] = if (Platform.pointerSupport()) header.event.pointer.over else own(Val(false))
  // a touch screen's actions always show: there is no pointer to bring them
  private val touchOnly: Boolean = Platform.touchSupport() && !Platform.pointerSupport()
  /** Its actions' buttons, in order. */
  val buttons: Var[Vector[IconButton]] = own(Var(Vector.empty))
  /** Whether the focus is on the header or one of its actions. */
  val focusIn: Val[Boolean] = own(Val(header.focused() || buttons().exists(_.focused())))
  header.size.width := size.width()
  header.size.height := headerHeight()
  header.backgroundColor := (if (header.focusShown()) Theme.control() else if (hovered()) Theme.control().withAlpha(0.6)
    else Color.Clear)
  header.borderWidth := (if (header.focusShown()) 1.0 else 0.0)
  header.borderColor := Theme.accent()
  header.semantics := Semantics.disclosure(title(), !collapsed())

  private val rule: Container = new Container {
    size.width := header.size.width()
    size.height @= 1.0
    backgroundColor := Theme.border()
    visible := ruled()
  }
  private val chevron: MaterialIconView = new MaterialIconView(MaterialIcon.ExpandMore, 18.0) {
    this.icon := (if (collapsed()) Mirror.onward else MaterialIcon.ExpandMore)
    color := Theme.muted()
    position.top := (headerHeight() - 18.0) / 2.0
  }
  private val glyph: MaterialIconView = new MaterialIconView(MaterialIcon.Empty, 18.0) {
    this.icon := StackedPane.this.icon().getOrElse(MaterialIcon.Empty)
    color := Theme.muted()
    visible := StackedPane.this.icon().nonEmpty
    position.top := (headerHeight() - 18.0) / 2.0
  }
  // the room the actions take at the header's end, kept whether they show or not, so the title is cut once
  private val actionsRoom: Val[Double] = own(Val(if (actions().isEmpty) 0.0
    else actions().size.toDouble * StackedPane.actionSize + StackedPane.end))
  private val textStart: Val[Double] = own(Val(StackedPane.inset + 24.0 + (if (icon().nonEmpty) 24.0 else 0.0)))
  val titleText: TextView = new TextView {
    this.content := title()
    font.size @= 13.0
    font.weight @= FontWeight.SemiBold
    color := Theme.text()
    maxWidth := math.max(20.0, header.size.width() - textStart() - actionsRoom() - 8.0)
    position.top := (headerHeight() - size.height()) / 2.0
  }
  Mirror.place(chevron, () => header.size.width(), () => StackedPane.inset)
  Mirror.place(glyph, () => header.size.width(), () => StackedPane.inset + 24.0)
  Mirror.place(titleText, () => header.size.width(), () => textStart())
  header.children ++= List(rule, chevron, glyph, titleText)

  // a click or a tap on the header, or Enter on it: folded or opened, unless it was an action's (heard here too where
  // running the action outlasted the click's climb)
  header.event.click.attach { e =>
    val onAction = Pointer.consumed.contains(e) && buttons().exists(b => b.visible() && e.x >= b.absoluteLeft &&
      e.x < b.absoluteLeft + b.size.width() && e.y >= b.absoluteTop && e.y < b.absoluteTop + b.size.height())
    if (!onAction) toggle()
  }
  header.event.key.down.attach(k => if (k.key == Key.Space) toggle())

  private def makeButtons(list: List[RichAction]): Unit = {
    val old = buttons()
    val made = list.toVector.map { a =>
      val b = new IconButton(a.icon, a.label, a.run) {
        size.width @= StackedPane.actionSize
        size.height @= StackedPane.actionSize
        radius @= StackedPane.actionSize / 2.0
        this.icon.position.left @= (StackedPane.actionSize - 22.0) / 2.0
        this.icon.position.top @= (StackedPane.actionSize - 22.0) / 2.0
        position.top := (headerHeight() - StackedPane.actionSize) / 2.0
      }
      b.visible := a.shown || touchOnly || hovered() || focusIn()
      b
    }
    made.indices.foreach { i =>
      Mirror.place(made(i), () => header.size.width(),
        () => header.size.width() - StackedPane.end - (made.size - i).toDouble * StackedPane.actionSize)
    }
    buttons @= made
    header.children @= header.children().filterNot(c => old.exists(_ eq c)) ++ made
    old.foreach(_.dispose())
  }
  makeButtons(actions())
  actions.attach(makeButtons)

  // ---- the content ----
  /** What holds the content under the header, cut to its room: a region named by the title. */
  val body: Container = new Container {
    // out of the content: Up to the header, Down to the next pane's
    override def focusAction(direction: Key): Option[() => Unit] =
      if (direction == Key.ArrowUp) Some(() => header.focus())
      else if (direction == Key.ArrowDown) stack.flatMap(_.below(StackedPane.this)).map(p => () => p.header.focus())
      else None
  }
  private val clip: Container = SplitPane.pane(content)
  clip.size.width := body.size.width()
  clip.size.height := body.size.height()
  body.children += clip
  body.position.top := headerHeight()
  body.size.width := size.width()
  body.size.height := math.max(0.0, size.height() - headerHeight())
  body.visible := body.size.height() > 0.0
  body.semantics := Semantics.region(title())
  children ++= List(body, header)

  // folded with the focus in it, the focus goes to the header, so it is never left on nothing
  follow(collapsed, (on: Boolean) => if (on && Focus.focused().exists(c => Focus.within(c, body))) header.focus())

  override def kind: String = "StackedPane"
}

object StackedPane {
  /** Where the chevron starts, from the header's start. */
  val inset: Double = 6.0
  /** How big an action's button is, and the gap after the last at the header's end. */
  val actionSize: Double = 28.0
  val end: Double = 4.0

  /** A header's height: 32, or 44 on a touch screen, for a finger. */
  def headerHeight(): Double = if (Platform.touchSupport() && !Platform.pointerSupport()) 44.0 else 32.0
}

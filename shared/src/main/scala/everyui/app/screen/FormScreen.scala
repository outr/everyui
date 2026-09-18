package everyui.app.screen

import everyui.async.Task
import everyui.component.*
import everyui.component.types.{FontWeight, TextAlign}
import everyui.font.MaterialIcon
import everyui.layout.VerticalLayout
import everyui.theme.Theme
import reactify.*

/** A form on a card in the middle of the screen: a `header` (its `heading`, unless a screen adds more), then what the
  * screen puts in `fields`, its error or notice, its `actions` and its `links`, scrolling when the screen is too
  * short. Sign-in screens, a settings page, a first-run choice.
  *
  * A screen fills the three in `build` (with `field`, `primary`, `secondary`, `link` and `note`, whose words follow
  * what they read, so they change with the language), names its `defaultFocus`, and sets `busy`,
  * `error` and `notice` as it works. Arriving at a screen clears its error and shows the notice the screen before it
  * left in `arriving`. */
trait FormScreen extends UIScreen {
  def heading: Val[String]
  /** How wide the card's content would be with room for it. */
  protected def wanted: Double = 364.0
  /** How wide the card's content is: what the screen asks for, or what fits where that is wider than the screen (a
    * phone), the card keeping a margin either side. */
  protected def width: Double =
    math.min(wanted, everyui.ui.size.width() - 32.0 - (if (framed) 56.0 else 0.0))
  /** A card; a screen that is its own layout goes without. */
  protected def framed: Boolean = true
  /** What the primary action says while `busy`. */
  protected def waiting: String = "…"

  val busy: Var[Boolean] = Var(false)
  val error: Var[Option[String]] = Var(None)
  val notice: Var[Option[String]] = Var(None)
  /** A notice carried from the screen that sent a person here. */
  var arriving: Option[String] = None

  protected val fields: Container = Column(12.0)()
  protected val actions: Container = Column(12.0)()
  protected val links: Container = Column(2.0)()

  protected def build(): Unit

  /** The top of the card: the heading, centred. A screen adds a logo above it, say. */
  protected def header(): Component = Centered(width)(new TextView {
    content := heading()
    font.size @= (if (framed) 20.0 else 26.0)
    font.weight @= FontWeight.Medium
    color := Theme.text()
  })

  override def createUI(): Task[Unit] = Task {
    val top = header()
    val errorBox = new Notice(error().getOrElse(""), Theme.danger(), width)
    val noticeBox = new Notice(notice().getOrElse(""), Theme.accent(), width)
    val card = new Container {
      layout @= Some(VerticalLayout(18.0))
      padding @= (if (framed) 28.0 else 0.0)
      size.width @= width + padding() * 2.0
      radius @= 16.0
      if (framed) backgroundColor := Theme.surface()
    }
    card.children.bind(
      List(top) ++ present(fields) ++ (if (error().nonEmpty) List(errorBox) else Nil) ++
        (if (notice().nonEmpty) List(noticeBox) else Nil) ++ present(actions) ++ present(links))(c => c)
    val scroll = new ScrollContainer
    scroll.size.width := container.size.width
    scroll.size.height := container.size.height
    card.position.left := math.max(16.0, (scroll.size.width() - card.size.width()) / 2.0)
    card.position.top := math.max(24.0, (scroll.size.height() - card.size.height()) / 2.0)
    scroll.content.size.height := card.position.top() + card.size.height() + 24.0
    scroll.content.children += card
    container.children += scroll
    build()
  }

  private def present(c: Container): List[Component] = if (c.children().nonEmpty) List(c) else Nil

  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    error @= None
    notice @= arriving
    arriving = None
  }

  /** A check before asking anything failed: say so. */
  protected def refuse(problem: String): Unit = error @= Some(problem)

  /** A field as wide as the form, bound both ways to `value`; Enter in it runs `enter`. */
  protected def field(label: => String, value: Var[String], secure: Boolean = false)(enter: => Unit): TextInput =
    TextInput.bound(label, value, secure, width)(enter)

  /** The one thing to do, as wide as the form; it says `waiting` and does nothing while `busy`. */
  protected def primary(words: => String)(action: => Unit): Button = {
    val button = Button.bound(if (busy()) waiting else words, () => action, ButtonStyle.Filled, points = 15.0)
    button.enabled := !busy()
    button.minWidth @= width
    button
  }

  /** A secondary action, outlined, with its icon. */
  protected def secondary(words: => String, glyph: MaterialIcon)(action: => Unit): Button = {
    val button = Button.bound(words, () => action, ButtonStyle.Outlined, Some(glyph), points = 14.0)
    button.enabled := !busy()
    button.minWidth @= width
    button
  }

  /** A link to somewhere else, centred. */
  protected def link(words: => String)(action: => Unit): Component =
    Centered(width)(Button.bound(words, () => action, ButtonStyle.Text, points = 14.0))

  /** Muted words, centred and wrapped. */
  protected def note(words: => String): Component = TextView.paragraph(words, width, 13.0, Theme.muted(), TextAlign.Center)
}

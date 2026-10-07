package everyui.app

import everyui.app.screen.{FormScreen, ScreenManager}
import everyui.async.Task
import everyui.component.*
import everyui.font.MaterialIcon
import everyui.layout.VerticalLayout
import everyui.net.*
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** The unwind demo's form (UnwindDemo): `build` adds an empty container, and `show` sets its children afterwards, as
  * NaboTV's DoneStep does, logging before and after the statement and once the screen is up. */
class UnwindScreen extends FormScreen {
  override def manager: ScreenManager = UnwindDemo
  override def heading: Val[String] = Val("All set")
  override def path: URLPath = path"/unwind"
  override protected def wanted: Double = 560.0

  private val throws: Boolean = Platform.launchArg("throw") == "deep"
  private val middle = new Container {
    layout @= Some(VerticalLayout(0.0))
  }
  private val foot = new Container
  private val home = Button.bound("Go Home", () => Platform.log("unwind: home"), ButtonStyle.Filled)

  override def defaultFocus(): Component = home

  override protected def build(): Unit = {
    middle.size.width @= width
    middle.size.height := middle.contentHeight()
    foot.size.width @= width
    foot.size.height := foot.contentHeight()
    fields.children += middle
    actions.children += foot
    middle.exitFocus(everyui.event.Key.ArrowDown, foot)
    middle.stopFocus(everyui.event.Key.ArrowUp, everyui.event.Key.ArrowLeft, everyui.event.Key.ArrowRight)
    show()
  }

  private def show(): Unit = {
    val summary = Column(14.0)(
      line(MaterialIcon.LiveTv, "Streaming services", ImageView("/images/logo.png", 26.0)),
      line(MaterialIcon.VideoLibrary, "Your library", TextView("Plex · Home", 18.0)),
      line(MaterialIcon.BookmarkAdded, "Your picks", TextView("3 to watch, 5 liked", 18.0)))
    val around: () => Component = () => new Container {
      size.width @= 1.0
      size.height := math.max(0.0, (186.0 - summary.size.height()) / 2.0)
    }
    // the reach of what is set, heard as the children are attached, before the statement returns
    if (throws) middle.contentHeight.attach(_ => throw new RuntimeException("unwind: thrown at depth"))
    Platform.log("unwind: before the children")
    middle.children @= Vector(around(), Centered(width)(summary), around())
    Platform.log("unwind: after the children")
    foot.children @= Vector(Centered(width)(home))
  }

  /** A thing set up: a tick, its icon and name, and what came of it in a box as wide as every line's. */
  private def line(icon: MaterialIcon, name: String, value: Component): Component = {
    val held = new Container {
      size.width @= 300.0
      size.height := value.size.height()
      children += value
    }
    Row(14.0, middle = true)(
      new MaterialIconView(MaterialIcon.CheckCircle, 24.0) {
        color := Theme.accent()
      },
      Row(8.0, middle = true)(new MaterialIconView(icon, 20.0) {
        color := Theme.muted()
      }, TextView.paragraph(name, 176.5, 18.0, Theme.muted())),
      held)
  }

  override protected def activate(): Task[Unit] = super.activate().map(_ => Platform.log("unwind: shown"))
}

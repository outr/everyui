package everyui.widget

import everyui.render.WidgetAccess
import everyui.theme.Palette

/** Home-screen widgets: the kinds the app offers, and redrawing those placed. Android draws them from the tree each
  * kind's `content` answers (RemoteViews); the other platforms have none yet, where `placed` is empty and the rest
  * does nothing.
  *
  * The app names a `WidgetApp` for the platform to load where the app is not running, and registers the same kinds
  * as it starts, so `refresh` from the app redraws them with what it has just learned:
  *
  * {{{
  *   MailWidgets.kinds.foreach(Widgets.register)
  *   mail.unread.attach(_ => Widgets.refresh("mail"))
  *   signedOut.attach(_ => Widgets.refreshAll())
  * }}} */
object Widgets {
  /** The colours a widget's `WidgetColour`s are drawn in, in the device's light mode and its dark one. */
  @volatile var light: Palette = Palette.light
  @volatile var dark: Palette = Palette.dark

  @volatile private var registered: Vector[WidgetKind] = Vector.empty
  @volatile private var started: Option[WidgetApp] = None

  /** Offer `kind`, in place of one registered with its key. */
  def register(kind: WidgetKind): Unit = synchronized {
    registered = registered.filterNot(_.key == kind.key) :+ kind
  }

  def kinds: Vector[WidgetKind] = registered

  def kind(key: String): Option[WidgetKind] = registered.find(_.key == key)

  /** Every placed widget of the kind drawn again (the app open, its data changed). */
  def refresh(kind: String): Unit = WidgetAccess.refresh(kind)

  /** Every placed widget of every kind drawn again: the person signed out, and nothing they had may show. */
  def refreshAll(): Unit = WidgetAccess.refreshAll()

  /** The widgets of the kind on the home screen now, as each would be asked for. */
  def placed(kind: String): Vector[WidgetRequest] = WidgetAccess.placed(kind)

  /** A placed widget's configure screen opened (from the app's settings). */
  def configure(id: Int): Unit = WidgetAccess.configure(id)

  /** `app` made ready and its kinds registered, once a process: the platform's, where the app's `main` may not run. */
  private[everyui] def start(app: WidgetApp): Unit = synchronized {
    if (!started.exists(_ eq app)) {
      started = Some(app)
      app.prepare()
      app.kinds.foreach(register)
    }
  }
}

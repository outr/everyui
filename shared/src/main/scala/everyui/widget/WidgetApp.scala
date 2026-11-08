package everyui.widget

/** The app's widgets, as an object the platform loads by name where the app is not running: a launcher asks for a
  * widget's content with the app closed, so its kinds are found from here rather than from the app's `main`
  * (Android's manifest names the object under the meta-data "everyui.widgets").
  *
  * {{{
  *   object MailWidgets extends WidgetApp {
  *     def kinds: List[WidgetKind] = List(Inbox.widget)
  *   }
  * }}} */
trait WidgetApp {
  def kinds: List[WidgetKind]

  /** Made ready before a kind is asked for, where the app's own `main` has not run: the origin its server is reached
    * at (`Network.origin`), its theme's colours for widgets (`Widgets.light`, `Widgets.dark`). Nothing by default. */
  def prepare(): Unit = ()
}

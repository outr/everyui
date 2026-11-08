package everyui.app

import everyui.app.screen.ScreenManager
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import everyui.widget.Widgets

/** Where the "inbox" home-screen widget's taps open the app (InboxWidgets): `/inbox` (InboxScreen), or
  * `/inbox/<id>` for one message (MessageScreen), each logged as it shows ("[ui] inbox: /inbox/m2"). Its screens run
  * half a second after it starts, as an app's that reads its session first, so a tap heard before them (the activity
  * made again for a task whose process had gone) is seen to land where it was aimed. `demo=inbox` opens it too. */
object InboxDemo extends ScreenManager {
  lazy val inbox: InboxScreen = new InboxScreen
  lazy val message: MessageScreen = new MessageScreen

  def run(): Unit = {
    InboxWidgets.kinds.foreach(Widgets.register)
    ui.backgroundColor := Theme.background()
    Platform.after(0.5, () => run(inbox, message))
  }
}

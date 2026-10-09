package everyui.render

import everyui.app.LeaveGuards
import javafx.stage.{Stage, WindowEvent}

/** A window's close asked of the app's guards (`Platform.leaveGuard`): while one answers true, a close (its ×, the
  * system's keys, the taskbar) is held and `LeaveGuards.ask` put up. Stay keeps the window; Leave closes it as the
  * close would have, the request made again for the window's own handlers to hear (an app's `setOnCloseRequest`, which
  * may still keep it, asking its tabs with `TabGroups.closeAllTabs`), so they hear it once and only after the answer.
  * A window closed by the app itself (`close`, `hide`, `Platform.quit`) is not asked. */
object WindowLeave {
  /** `stage`'s close guarded: the launcher's window (Desktop.launch), or any other the app opens. */
  def watch(stage: Stage): Unit = {
    var leaving = false
    // a filter, so a close held here reaches no handler of the window's
    stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, (e: WindowEvent) =>
      if (!leaving && LeaveGuards.guarded()) {
        e.consume()
        LeaveGuards.ask().map { leave =>
          if (leave) {
            leaving = true
            try stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST))
            finally leaving = false
          }
        }.start()
      })
  }
}

package everyui.activity

import android.app.Activity
import everyui.app.LeaveGuards

/** Back from the first screen, which nothing in the app takes, while the app is guarded (`Platform.leaveGuard`): it
  * asks Stay or Leave rather than leave at once, and Leave sends the app to the background, as Back there does from
  * Android 12 (before it, Back finished the activity). An app with no guard answering true leaves as it always has. */
object LeaveBack {
  /** Whether a Back nothing took is held to ask first. */
  def holds(): Boolean = LeaveGuards.guarded()

  /** The question put up, and on Leave the app sent to the background. */
  def ask(activity: Activity): Unit =
    LeaveGuards.ask().map(leave => if (leave) activity.moveTaskToBack(true)).startUnit()
}

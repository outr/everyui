package everyui.render

import android.view.{View, ViewGroup}
import android.widget.EditText

/** Whether a touch let go on a view came up on something else in it that answers a click (`mark`ed) or takes typing:
  * a press belongs to what it went down on, so a finger or a mouse that went down on a dialog's sheet, around the
  * dialog, and came up on the dialog is not a click on the sheet, outside the dialog, and does not close it. (One that
  * went down in the dialog is the dialog's from the start, and never reaches the sheet.) */
private[render] object PressOwner {
  private val clickers = java.util.Collections.newSetFromMap(new java.util.WeakHashMap[View, java.lang.Boolean]())

  /** `view` answers clicks. */
  def mark(view: View): Unit = clickers.add(view)

  /** Whether the touch on `view` came up, at `rawX`, `rawY` on the screen, on something in it that answers a click. */
  def elsewhere(view: View, rawX: Float, rawY: Float): Boolean = view match {
    case group: ViewGroup => (0 until group.getChildCount).exists { i =>
      val child = group.getChildAt(i)
      child.getVisibility == View.VISIBLE && holds(child, rawX, rawY) &&
        (clickers.contains(child) || child.isInstanceOf[EditText] || elsewhere(child, rawX, rawY))
    }
    case _ => false
  }

  // where `view` shows on the screen, with every scale on the way to it (the root's) and cut by what holds it: its own
  // size times its own scale alone was the root's units read as the screen's pixels, a part 2.6 times too small
  private def holds(view: View, x: Float, y: Float): Boolean = {
    val shown = new android.graphics.Rect
    val window = Array(0, 0)
    view.getRootView.getLocationOnScreen(window)
    view.getGlobalVisibleRect(shown) && shown.contains(math.floor(x).toInt - window(0), math.floor(y).toInt - window(1))
  }
}

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

  private def holds(view: View, x: Float, y: Float): Boolean = {
    val at = Array(0, 0)
    view.getLocationOnScreen(at)
    x >= at(0) && y >= at(1) && x < at(0) + view.getWidth * view.getScaleX && y < at(1) + view.getHeight * view.getScaleY
  }
}

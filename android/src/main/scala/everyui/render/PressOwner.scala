package everyui.render

import android.view.{View, ViewGroup}
import android.widget.EditText
import everyui.event.PointerKind

/** Whether a touch let go on a view came up on something else in it that answers a click (`mark`ed) or takes typing:
  * a press belongs to what it went down on, so a finger or a mouse that went down on a dialog's sheet, around the
  * dialog, and came up on the dialog is not a click on the sheet, outside the dialog, and does not close it. (One that
  * went down in the dialog is the dialog's from the start, and never reaches the sheet.) */
private[render] object PressOwner {
  private val clickers = java.util.Collections.newSetFromMap(new java.util.WeakHashMap[View, java.lang.Boolean]())

  /** Whether a node that hears the pointer kinds `heard` takes a touch that lands on it as its own: it answers a click
    * or a long press, follows the pointer as it moves or to its lift, or `drags` (a scroll, a slider, a pinch). One that
    * only watches a press go down (a tip hiding itself), or a long press (`holdWatched`: a tip shown for it), leaves the
    * touch to what holds it that takes one, as the web hands a press on to what is round it. */
  def owns(heard: collection.Set[PointerKind], drags: Boolean, holdWatched: Boolean = false): Boolean = drags ||
    heard.exists(k => k == PointerKind.Click || (k == PointerKind.Menu && !holdWatched) || k == PointerKind.Up ||
      k == PointerKind.Move)

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

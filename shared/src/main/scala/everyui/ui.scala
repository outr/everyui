package everyui

import everyui.component.Container
import everyui.render.{Nodes, Platform}
import everyui.task.TaskSupport
import everyui.theme.UiScale

/** youi's `ui`: the root container, the size of the screen — re-measured whenever that changes. Its start is the focus
  * layer's too (`Focus.listen`). The screen is measured in the app's own units, over the size it is drawn at
  * (`UiScale`): drawn larger, the app has a smaller screen to lay out in. */
object ui extends Container(Nodes.root()) with TaskSupport {
  private def measureScreen(): Unit = {
    // the screen's width as the platform has it, which the device's text size is held to (`UiScale.narrowest`): the
    // window's own, never the width read back over the size it decides, which chased its own last bit for ever
    settle(UiScale.room, Platform.screenRoom())
    settle(size.width, Platform.screenWidth())
    settle(size.height, Platform.screenHeight())
  }

  // `v` set to `to` only where it is more than rounding away: the whole app is laid out again for each change
  private def settle(v: reactify.Var[Double], to: Double): Unit = if (!UiScale.near(v(), to)) v @= to

  // the size it is drawn at: the size chosen, with the device's text size where it is followed
  private val drawn: reactify.Val[Double] = reactify.Val(UiScale())
  Platform.setUiScale(drawn())
  measureScreen()
  Platform.onResize(() => measureScreen())
  // a size chosen outside the range is taken into it (set again, which comes back here)
  UiScale.factor.attach(f => if (UiScale.chosen() != f) UiScale.factor @= UiScale.chosen())
  drawn.attach { d =>
    Platform.setUiScale(d)
    measureScreen()
  }
  everyui.focus.Focus.listen()
}

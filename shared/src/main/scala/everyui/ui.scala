package everyui

import everyui.component.Container
import everyui.render.{Nodes, Platform}
import everyui.task.TaskSupport
import everyui.theme.{ScreenFit, UiScale}

/** youi's `ui`: the root container, the size of the screen — re-measured whenever that changes. Its start is the focus
  * layer's too (`Focus.listen`). The screen is measured in the app's own units, over the size it is drawn at
  * (`UiScale`): drawn larger, the app has a smaller screen to lay out in. */
object ui extends Container(Nodes.root()) with TaskSupport {
  // the size it is drawn at (the size chosen, with the device's text size where it is followed, held to the room the
  // window leaves: `Platform.screenRoom`, never the width read back over the size it decides), and the screen over it
  private val screen = new ScreenFit(UiScale.room, () => UiScale(), () => Platform.screenRoom(),
    () => Platform.screenWidth(), () => Platform.screenHeight(), d => Platform.setUiScale(d), size.width, size.height)
  Platform.onResize(() => screen.measure())
  // a size chosen outside the range is taken into it (set again, which comes back here)
  UiScale.factor.attach(f => if (UiScale.chosen() != f) UiScale.factor @= UiScale.chosen())
  everyui.focus.Focus.listen()
}

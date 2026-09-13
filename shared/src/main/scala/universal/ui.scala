package universal

import universal.component.Container
import universal.render.{Nodes, Platform}
import universal.task.TaskSupport

/** youi's `ui`: the root container, the size of the screen — re-measured whenever that changes. */
object ui extends Container(Nodes.root()) with TaskSupport:
  private def measureScreen(): Unit =
    size.width @= Platform.screenWidth()
    size.height @= Platform.screenHeight()

  measureScreen()
  Platform.onResize(() => measureScreen())

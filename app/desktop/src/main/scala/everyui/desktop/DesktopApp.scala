package everyui.desktop

/** The example app on the desktop: `everyui.app.main` in a window (see [[Desktop]] for the options). */
object DesktopApp {
  def main(args: Array[String]): Unit =
    Desktop.launch(args, "EveryUI", "images/everyui-mark.png")(() => everyui.app.main())
}

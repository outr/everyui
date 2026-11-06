package everyui.desktop

/** The example app on the desktop: `everyui.app.main` in a window (see [[Desktop]] for the options); with
  * `--demo=tray`, the tray (`TrayDemo`). */
object DesktopApp {
  def main(args: Array[String]): Unit =
    Desktop.launch(args, "EveryUI", "images/everyui-mark.png")(() =>
      if (args.contains("--demo=tray")) TrayDemo.run() else everyui.app.main())
}

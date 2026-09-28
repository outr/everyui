package everyui.monaco.gallery

import everyui.desktop.Desktop
import everyui.render.{Host, Platform}

/** The gallery in a desktop window, Monaco in JavaFX's web view: `sbt monacoDesktop/run`. `--shot=out.png` saves the
  * window `--after` seconds (8 by default) after it opens, on the demo `--demo` names by its place in the list, and
  * exits. */
object GalleryDesktop {
  def main(args: Array[String]): Unit =
    Desktop.launch(args :+ "--size=1440x900", "everyui-monaco gallery", "images/everyui-mark.png") { () =>
      Gallery.main()
      Host.args.get("demo").flatMap(_.toIntOption).foreach(GalleryScreen.choose)
      if (Host.args.contains("probe")) Platform.after(10.0, () => ClipboardProbe.run())
      Host.args.get("shot").foreach { file =>
        Platform.after(Host.args.get("after").flatMap(_.toDoubleOption).getOrElse(8.0), () => {
          Host.screenshot = file
          Host.frozen()
        })
      }
    }
}

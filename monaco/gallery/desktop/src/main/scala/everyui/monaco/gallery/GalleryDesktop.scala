package everyui.monaco.gallery

import everyui.desktop.Desktop

/** The gallery in a desktop window, Monaco in JavaFX's web view: `sbt monacoDesktop/run`. */
object GalleryDesktop {
  def main(args: Array[String]): Unit =
    Desktop.launch(args :+ "--size=1440x900", "everyui-monaco gallery", "images/everyui-mark.png")(() => Gallery.main())
}

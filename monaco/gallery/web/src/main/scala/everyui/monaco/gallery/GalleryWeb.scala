package everyui.monaco.gallery

import everyui.render.Platform

/** The gallery in a web page (monaco/gallery/web/index.html); `?demo=<n>` opens the demo at that place in the list. */
object GalleryWeb {
  def main(args: Array[String]): Unit = {
    Gallery.main()
    Platform.launchArg("demo").toIntOption.foreach(GalleryScreen.choose)
  }
}

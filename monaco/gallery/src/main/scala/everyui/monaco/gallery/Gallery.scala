package everyui.monaco.gallery

import everyui.app.screen.ScreenManager
import everyui.render.Platform

/** everyui-monaco's example gallery: flutter_monaco_editor's nine demos, and one of what a code browser needs (files,
  * navigation, saving), each a page of its own beside a list of them. */
object Gallery extends ScreenManager {
  def main(): Unit = {
    Platform.setTitle("everyui-monaco gallery")
    run(GalleryScreen)
  }
}

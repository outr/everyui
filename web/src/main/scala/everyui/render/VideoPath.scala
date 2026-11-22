package everyui.render

import org.scalajs.dom
import org.scalajs.dom.html

/** The boxes holding a video, up to the app's root, marked `everyui-video-path` before Chromium 62
  * (`RenderNode.overVideoStyle`), so what comes after any of them is a layer of its own: marked as it loads, as it
  * plays and as it is moved on the page with a source ([[WebMedia]]), the boxes it was in before a move unmarked; and
  * unmarked as its source is let go (`emptied`). A box holding another video with a source stays marked. */
final class VideoPath(el: html.Element) {
  private var marked: List[html.Element] = Nil

  /** Every box holding it now marked, and those it has left unmarked. */
  def mark(): Unit = {
    val now = boxes
    marked.filterNot(b => now.exists(_ eq b)).foreach(unmark)
    now.foreach(_.classList.add(VideoPath.name))
    marked = now
  }

  /** Its source let go: the boxes it marked unmarked. */
  def clear(): Unit = {
    marked.foreach(unmark)
    marked = Nil
  }

  private def unmark(box: html.Element): Unit =
    if (box.querySelector("video[src]") == null) box.classList.remove(VideoPath.name)

  private def boxes: List[html.Element] = {
    var all = List.empty[html.Element]
    var at = el.parentNode
    while (at != null && at.nodeType == dom.Node.ELEMENT_NODE && at.asInstanceOf[html.Element].id != "app") {
      all = at.asInstanceOf[html.Element] :: all
      at = at.parentNode
    }
    all
  }
}

object VideoPath {
  /** The class each box on the way is marked with. */
  val name: String = "everyui-video-path"
}

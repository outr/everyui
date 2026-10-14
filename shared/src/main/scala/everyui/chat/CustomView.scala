package everyui.chat

import everyui.component.{Component, Container}
import everyui.event.Key
import everyui.list.ListRow
import reactify.*

/** The app's own component (`ChatEntry.Custom`) in the transcript, `width` wide: at the start edge in line with the
  * messages, as it made it. The transcript keeps the component, so what was typed or chosen in it stays while it is
  * scrolled away; `release` hands it back before this is taken apart. An arrow up or down that nothing in it answers
  * goes back to the transcript (`list`). */
class CustomView(val component: Component, width: => Double, list: Component) extends Container {
  component.parent().foreach {
    case p: Container => p.children -= component
    case _ => ()
  }
  component.position.left := ListRow.along(Transcript.indent(width), component.size.width(), width)
  component.position.top @= Transcript.groupGap
  size.width := width
  size.height := component.size.height() + Transcript.groupGap * 2.0
  exitFocus(Key.ArrowUp, list)
  exitFocus(Key.ArrowDown, list)
  children += component

  /** The component put back where the transcript keeps it, out of this, so taking this apart leaves it be. */
  def release(): Unit = if (children().exists(_ eq component)) {
    Component.unbind(component.position.left)
    children -= component
  }

  override def kind: String = "CustomView"
}

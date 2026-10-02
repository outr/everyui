package everyui.kit

import everyui.component.Container

/** What a `TabGroups` group shows for its tabs that share one view (`sharesGroupContent`): one editor for all of a
  * group's files, say. Made the first time the group shows such a tab, kept for as long as the group lives, and
  * disposed of with it. `show(k)` is told each time the group's shown tab becomes another such key (moved in from
  * another group, or opened in a second group where the key may show twice, it is told the same), and `hide()` when
  * the group shows a tab with content of its own, or none. It is sized to the group's body.
  *
  * {{{
  *   class EditorView extends GroupContent[File] {
  *     private val editor = new CodeEditor
  *     children += editor
  *     override def show(f: File): Unit = editor.model @= modelOf(f)
  *   }
  * }}} */
abstract class GroupContent[K] extends Container {
  /** `k` is the tab shown now. */
  def show(k: K): Unit

  /** No tab sharing this view is shown now. */
  def hide(): Unit = ()
}

object GroupContent {
  /** A view that shows nothing: what a `TabGroups` with no `groupContent` gives its groups. */
  def empty[K]: GroupContent[K] = new GroupContent[K] {
    override def show(k: K): Unit = ()
  }
}

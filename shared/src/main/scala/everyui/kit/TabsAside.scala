package everyui.kit

import everyui.component.Component

/** A `TabGroups`' groups set aside whole (`TabGroups.setAside`): every group with its bar, its tabs and their contents,
  * the splits between them as they were drawn, the group shown alone and the group the focus was in, off the screen and
  * alive, for `TabGroups.putBack` to put back as they were. Put back, it is spent. One the app will not put back is
  * disposed of (`dispose`), its contents with it, as closing its tabs would; until then it holds all it held, but for
  * the contents `TabGroups.evictAfter` lets go, which `putBack` makes again as each shows. `asideSince` says when it
  * was set aside (ms since the epoch, by `TabGroups`' clock), for an app keeping a few to let the oldest go. */
final class TabsAside[K] private[kit] (private[kit] val owner: TabGroups[K], private[kit] val tree: TabTree[K],
                                       private[kit] val alone: Option[TabGroup[K]],
                                       private[kit] val focused: TabGroup[K], private[kit] val root: Component,
                                       val asideSince: Long) {
  // put back, or disposed of
  private[kit] var spent: Boolean = false

  /** Whether it has been put back or disposed of: there is nothing left in it to put back. */
  def isSpent: Boolean = spent

  /** Its groups, their contents, their bars and the splits they lay in disposed of, never to be put back, and its
    * contents no longer timed by `TabGroups.evictAfter`. Nothing, once it has been put back. */
  def dispose(): Unit = if (!spent) {
    spent = true
    owner.forgetAside(this)
    TabTrees.groups(tree).foreach { g =>
      g.disposeContents()
      g.dispose()
    }
    // and the splits they lay in
    root.dispose()
  }
}

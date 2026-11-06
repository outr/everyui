package everyui.component

import everyui.component.feature.ContainerFeature
import everyui.layout.{FlowLayout, HorizontalLayout, Layout, LayoutPass, VerticalLayout}
import everyui.component.types.Prop
import everyui.render.{Nodes, Platform, RenderNode}
import reactify.*

class Container(node: RenderNode = Nodes.container()) extends Component(node) {
  lazy val children: ContainerFeature = new ContainerFeature(this)

  override def dispose(): Unit = if (!takenApart) {
    watchedBy = Nil
    val held = children()
    children.clear()
    // (not one another container has put in itself, which a list bound to what it reads went on naming)
    held.foreach(c => if (ContainerFeature.unplaced(c, this)) c.dispose())
    super.dispose()
  }

  /** youi's `LayoutSupport`: the layout, if any, places the children again whenever they, the container's size or
    * their sizes change, through the layout pass (`LayoutPass`: the deepest container first, in a loop). A container
    * given a layout sizes itself to its content, unless a size was set: its height always, its width when the layout
    * fits it (a column, a row; not a flow, which wraps to the width it is given). */
  lazy val layout: Var[Option[Layout]] = own(Var(None))
  /** Space between the edges and the children, on every side. */
  lazy val padding: Var[Double] = own(Var(0.0))
  /** Whether the width follows the children (a row or a column given no width): a mirrored row then spans its own run,
    * not the width its last placement made. */
  var widthFollowsContent: Boolean = false
  /** Whether the height was bound to the children's reach by the layout (a row or a column given no height). */
  private var heightFollowsContent: Boolean = false

  /** Whether its height is its own: set, or bound to something other than what it holds (the screen, what it sits
    * in). A column shares it out among its children with a `flex`; one sized to its children has none to share. */
  private[everyui] def ownsHeight: Boolean = size.height.explicit && {
    val read = size.height.references
    read.isEmpty || !((heightFollowsContent && read.contains(contentHeight)) || children().exists(c => read.contains(c.size.height)))
  }

  /** Whether its width is its own, as `ownsHeight` is for its height: what a row shares out among its children with a
    * `flex`. */
  private[everyui] def ownsWidth: Boolean = size.width.explicit && !followsChildren
  /** Whether what it holds runs on past its width on purpose (a rail's content, scrolled along): words in it are not
    * kept to it, nor to anything it is in (`TextView.textOverflow`). */
  private[everyui] var textRunsOn: Boolean = false

  /** Whether what it holds is cut to its box, its corners rounded with it. By default (`None`) a container that draws
    * a box clips: a background, a gradient or an edge, so words or a picture too big for a card stay in it, and once it
    * has drawn one it goes on clipping, as a highlight comes and goes. `Some(false)` lets what it holds hang out of it
    * (a badge on its corner, a thumb past its track, a child's shadow at its edge), `Some(true)` clips one that draws
    * none. A clip a component makes of its own (a scroll's, a list's) is not undone by it. */
  lazy val clipsToBox: Prop[Option[Boolean]] = own(new Prop[Option[Boolean]](None, c => {
    clipAsked = c
    clipBox()
  }))
  // what the app asked (`clipsToBox`, read without making it), whether it has drawn a box, and whether it is cut to it
  private var clipAsked: Option[Boolean] = None
  private var boxed = false
  private var boxClipped = false
  private var boxSized = false
  override private[everyui] def boxDrawn(): Unit = if (!boxed && drawsBox) {
    boxed = true
    clipBox()
  }
  private def clipBox(): Unit = {
    val on = clipAsked.getOrElse(boxed)
    if (on != boxClipped) {
      boxClipped = on
      clips = on
      // the desktop and the Roku clip to a size: followed once it clips at all
      if (on && !boxSized) {
        boxSized = true
        size.width.attach(_ => if (boxClipped) node.setBoxClip(true, size.width(), size.height()))
        size.height.attach(_ => if (boxClipped) node.setBoxClip(true, size.width(), size.height()))
      }
      node.setBoxClip(on, size.width(), size.height())
    }
  }

  /** Whether its width follows what it holds: a row or a column given no width, or a width bound to a child's (a
    * button's, a chip's, `size.width := label.size.width() + 20.0`). Words in it are kept to the room of what it is in. */
  private[everyui] def followsChildren: Boolean = size.width.explicit && {
    val read = size.width.references
    read.nonEmpty && (sizedToRun(read) || children().exists(c => read.contains(c.size.width)))
  }
  // whether the width is still the one the layout gave it, its children's reach (`contentWidth`): one given another
  // after its layout (`Column(...)` then `size.width := 760.0`) keeps `widthFollowsContent` but has a width of its own
  private def sizedToRun(read: Set[Val[?]]): Boolean = widthFollowsContent && read.contains(contentWidth)

  // Where words in this lie in their box (`TextView.textOverflow`), as of the look at boxes under way
  // (TextView.looks), worked out once for all the words in it: where it starts in the box, from the box's start edge
  // (`spaceFrom`), what lies after it inside what it is in on the way up to the box (`spaceTail`), the box's width
  // (`spaceWidth`, 0 for no box), and what the words in it watch to be looked at again (`spaceWatch`). The box is the
  // nearest container with a width of its own; one sized to what it holds leaves it to the one it is in. There is none
  // where the way up meets something hidden, content that runs on past its width on purpose (`textRunsOn`), or the top
  // of something not yet on the screen: what stops it is watched, to look again as it is shown or put on the screen.
  private var spaceLook: Int = -1
  private[component] var spaceFrom: Double = 0.0
  private[component] var spaceTail: Double = 0.0
  private[component] var spaceWidth: Double = 0.0
  private[component] var spaceWatch: List[Container] = Nil
  // and what words directly in this watch: that, and this where it lays its children along a row
  private var wordsWatch: List[Container] = Nil
  private var spaceLays: Boolean = false

  private[component] def space(): Unit = if (spaceLook != TextView.looks(0)) {
    spaceLook = TextView.looks(0)
    spaceLays = lays
    spaceWidth = 0.0
    spaceWatch = List(this)
    if (!textRunsOn && notHidden) {
      if (size.width.explicit && !followsChildren) {
        spaceFrom = 0.0
        spaceTail = 0.0
        spaceWidth = size.width()
      }
      else parent().flatMap(_.asContainer) match {
        case Some(p) =>
          p.space()
          spaceWatch = p.spaceWatch
          if (p.spaceWidth > 0.0) {
            spaceFrom = p.spaceFrom + p.startOf(this)
            spaceTail = p.spaceTail + p.endOf(this)
            spaceWidth = p.spaceWidth
            if (p.spaceLays) spaceWatch = p :: spaceWatch
          }
        case None => ()
      }
    }
    wordsWatch = if (spaceLays && spaceWidth > 0.0) this :: spaceWatch else spaceWatch
  }

  // whether this lays its children along a row: what comes after one leaves it less room as it grows
  private def lays: Boolean = layout() match {
    case Some(_: HorizontalLayout) => true
    case _ => false
  }

  /** Where `child` starts inside this, from its start edge (its left, or its right in a right-to-left language), as
    * the layout puts it: in a row, past the children before it with their gaps and the padding; in a column, which puts
    * a child too wide for it at the start, the padding; elsewhere where it is placed. */
  private[component] def startOf(child: Component): Double = layout() match {
    case Some(h: HorizontalLayout) =>
      var start = padding()
      var seen = false
      children().foreach { c =>
        if (c eq child) seen = true
        else if (!seen && c.size.width() > 0.0) start = start + c.size.width() + h.spacing
      }
      start
    case Some(_: VerticalLayout) => padding()
    case _ =>
      if (TextView.lookRtl(0)) size.width() - child.position.left() - child.size.width() else child.position.left()
  }

  /** What lies after `child` inside this, as the layout puts it: in a row, the children after it with their gaps (at
    * their whole width, so words before them are cut first) and the padding; in a column or a flow, which put a child
    * too wide for them at the start on its own, the padding; placed by hand, what is left of this past the child where
    * this is sized to it, else nothing. */
  private[component] def endOf(child: Component): Double = layout() match {
    case Some(_: HorizontalLayout) if TextView.boxedCount(0) == 0 && sizedToRun(size.width.references) =>
      // sized to its run, where nothing is cut to its box: what is left of the run past the child
      if (TextView.lookRtl(0)) child.position.left() else size.width() - child.position.left() - child.size.width()
    case Some(h: HorizontalLayout) =>
      var end = padding()
      var seen = false
      children().foreach { c =>
        if (c eq child) seen = true
        else if (seen && c.size.width() > 0.0) end = end + h.spacing + c.size.width() + c.boxShrunk
      }
      end
    case Some(_: VerticalLayout) => padding()
    case Some(_: FlowLayout) => padding()
    case _ =>
      if (!followsChildren) 0.0
      else if (TextView.lookRtl(0)) child.position.left()
      else size.width() - child.position.left() - child.size.width()
  }

  override private[everyui] def asContainer: Option[Container] = Some(this)

  /** The room `words`, a child of this, have in their box: from where they start (their left, or their right in a
    * right-to-left language) to its far edge, less what lies after them; 0 where there is no box, or where they start
    * outside it altogether (made out of sight, or placed past its edge). The words are told what to watch. */
  override private[everyui] def roomOf(words: TextView): Double = {
    space()
    words.watchesRoom(wordsWatch)
    if (spaceWidth <= 0.0) 0.0
    else {
      val from = spaceFrom + startOf(words)
      val after = spaceTail + endOf(words)
      if (from < -0.5 || from >= spaceWidth - 0.5) 0.0 else math.max(1.0, spaceWidth - from - after)
    }
  }

  // the words whose room this changes (TextView.watchesRoom): each is looked at again as its width changes, as it is put
  // in a container or taken out of one, and as it is shown, its width followed once the first is watched
  private var watchedBy: List[TextView] = Nil
  private var watchingText: Boolean = false
  private var watchingShown: Boolean = false
  private var watchedCount: Int = 0
  private var compactAt: Int = 64
  private[component] def watchRoomOf(words: TextView): Unit = {
    watchedBy = words :: watchedBy
    // what has been taken apart since is let go of as the list doubles
    watchedCount += 1
    if (watchedCount >= compactAt) {
      watchedBy = watchedBy.filterNot(_.takenApart)
      watchedCount = watchedBy.size
      compactAt = math.max(64, watchedCount * 2)
    }
    if (!watchingText) {
      watchingText = true
      size.width.attach(_ => watchedBy.foreach(TextView.check))
    }
    // and, hidden, as it is shown
    if (!notHidden && !watchingShown) {
      watchingShown = true
      visible.attach(_ => watchedBy.foreach(TextView.check))
    }
  }
  private[component] def unwatchRoomOf(words: TextView): Unit = {
    watchedBy = watchedBy.filterNot(_ eq words)
    watchedCount = watchedCount - 1
  }
  override private[everyui] def containerChanged(): Unit = watchedBy.foreach(TextView.check)
  // words it watched for taken apart: let go of on a turn of its own, with every other taken apart meanwhile
  private var lettingGo: Boolean = false
  private[component] def letGoLater(): Unit = if (!lettingGo) {
    lettingGo = true
    Platform.later(() => {
      lettingGo = false
      watchedBy = watchedBy.filterNot(_.takenApart)
      watchedCount = watchedBy.size
    })
  }

  // as much as the widest child, or every child in a row, is cut short, where this is sized to them
  override private[everyui] def boxShrunk: Double = {
    val kids = children()
    if (!followsChildren) 0.0
    else layout() match {
      case Some(_: HorizontalLayout) => kids.foldLeft(0.0)((sum, c) => sum + c.boxShrunk)
      case _ =>
        val whole = kids.foldLeft(0.0)((most, c) => math.max(most, c.position.left() + c.size.width() + c.boxShrunk))
        math.max(0.0, whole - (kids.foldLeft(0.0)((most, c) => math.max(most, c.position.left() + c.size.width()))))
    }
  }

  /** How far the children reach, plus the padding beyond them. */
  lazy val contentWidth: Val[Double] = {
    followReach()
    reachedWidth
  }
  lazy val contentHeight: Val[Double] = {
    followReach()
    reachedHeight
  }

  // How far the children reach, kept up as each one moves or changes size (ChildWatch, Extent): a Val over every child
  // walked them all again for each child laid out, which for a table of a few hundred rows kept a browser busy for
  // seconds.
  private lazy val reachedWidth: Var[Double] = own(Var(0.0))
  private lazy val reachedHeight: Var[Double] = own(Var(0.0))
  private val reachAcross: Extent = new Extent
  private val reachDown: Extent = new Extent
  private var reachFollowed: Boolean = false

  private def followReach(): Unit = if (!reachFollowed) {
    reachFollowed = true
    watchChildren()
    padding.attach(_ => setReach())
    recountReach()
  }

  private var reachRecounts: Int = 0

  /** Every child counted again (each watch once, though a child be listed twice). */
  private def recountReach(): Unit = {
    reachAcross.reset()
    reachDown.reset()
    reachRecounts += 1
    children().foreach { c =>
      c.containerWatches.foreach { w =>
        if ((w.container eq this) && w.round != reachRecounts) {
          w.round = reachRecounts
          countReach(w)
        }
      }
    }
    setReach()
  }

  private def countReach(w: ChildWatch): Unit = {
    w.x = w.reachX
    w.y = w.reachY
    reachAcross.add(w.x)
    reachDown.add(w.y)
  }

  private def setReach(): Unit = {
    reachedWidth @= reachAcross.value + padding()
    reachedHeight @= reachDown.value + padding()
  }

  /** A child's size (`sized`) or place changed: it is laid out again, and its reach counted where it now is. */
  private[component] def childWatched(w: ChildWatch, sized: Boolean): Unit = {
    if (sized && following) place()
    if (reachFollowed) {
      val x = w.reachX
      val y = w.reachY
      if (x != w.x || y != w.y) {
        val wasX = w.x
        val wasY = w.y
        w.x = x
        w.y = y
        if (reachAcross.moved(wasX, x) && reachDown.moved(wasY, y)) setReach() else recountReach()
      }
    }
  }

  // Whether the children are watched (ChildWatch): once there is a layout to follow their sizes, or a content size to
  // follow their reach.
  private var childrenWatched: Boolean = false

  private def watchChildren(): Unit = if (!childrenWatched) {
    childrenWatched = true
    children().foreach(watchChild)
  }

  private def watchChild(c: Component): Unit = c.containerWatches = new ChildWatch(this, c) :: c.containerWatches

  private def unwatchChild(c: Component): Unit = {
    c.containerWatches.foreach(w => if (w.container eq this) w.stop())
    c.containerWatches = c.containerWatches.filterNot(_.container eq this)
  }

  /** The children changed (ContainerFeature): what was added is watched, what left is not, and the reach follows. */
  override private[everyui] def childListChanged(added: Vector[Component], removed: Vector[Component]): Unit =
    if (childrenWatched) {
    removed.foreach(unwatchChild)
    added.foreach { c =>
      watchChild(c)
      if (reachFollowed) countReach(c.containerWatches.head)
    }
    if (reachFollowed) {
      if (removed.nonEmpty) recountReach() else setReach()
    }
  }

  private var exits: Map[everyui.event.Key, Option[Component]] = Map.empty

  /** An arrow `direction`'s way that finds nothing inside this container goes to `target` (onto its focusable part
    * nearest the focus): a header leading into a list, a panel handing back to a sidebar. */
  def exitFocus(direction: everyui.event.Key, target: Component): Unit = exits = exits + (direction -> Some(target))

  /** Arrows these ways stop at this container's edge instead of leaving it: a dialog, a keyboard, a menu. */
  def stopFocus(directions: everyui.event.Key*): Unit = directions.foreach(d => exits = exits + (d -> None))

  private var exitActions: Map[everyui.event.Key, () => Unit] = Map.empty

  /** An arrow `direction`'s way that finds nothing inside this container runs `action` instead of moving the focus:
    * a row of buttons that raises a panel when pressed on past, rather than handing off to whatever lies that way.
    * The arrow goes no further once it has run. */
  def exitAction(direction: everyui.event.Key, action: () => Unit): Unit =
    exitActions = exitActions + (direction -> action)

  /** Whatever this container said about an arrow leaving it `direction`'s way (`exitFocus`, `stopFocus`, `exitAction`)
    * taken back: its parent decides again. */
  def dropExit(direction: everyui.event.Key): Unit = {
    exits = exits - direction
    exitActions = exitActions - direction
  }

  /** What an arrow leaving this container `direction`'s way does instead of moving, when the container says. */
  def focusAction(direction: everyui.event.Key): Option[() => Unit] = exitActions.get(direction)

  /** Where an arrow leaving this container `direction`'s way goes: a component, `Some(None)` to stop, `None` when the
    * container does not say (its parent decides). */
  def focusExit(direction: everyui.event.Key): Option[Option[Component]] = exits.get(direction)

  /** The part of this container that had the focus last: where an arrow coming back into it lands, when it states no
    * entry. Kept by [[everyui.focus.Focus]]. */
  private[everyui] var remembered: Option[Component] = None

  private var arrival: Option[Component] = None

  /** An arrow arriving at this container from outside lands on `target` (onto its focusable part), wherever the focus
    * came from: a row of buttons that is entered at its first. */
  def entryFocus(target: Component): Unit = arrival = Some(target)

  /** Where an arrow arriving at this container lands, when the container says. */
  def focusEntry: Option[Component] = arrival

  private var typedInto: Option[everyui.focus.TextEntry] = None

  /** What is typed while the focus is on this container or in it goes to `to` (a character, a space, Backspace, a
    * phone app's Enter), ahead of the focused part's own key handling: the field an app's own keyboard edits, typed into
    * as well from a Roku's phone app or a keyboard while the focus is on the keys. The nearest container that says
    * takes it. A field of EveryUI's where the platform has none of its own (TextInput, SearchField on a Roku) says it
    * of itself. */
  def typeInto(to: everyui.focus.TextEntry): Unit = typedInto = Some(to)

  /** Where what is typed here goes, when this container says (`typeInto`). */
  def textEntry: Option[everyui.focus.TextEntry] = typedInto

  /** Place the children again, here and all the way down, in one pass: the reading direction changed. */
  def relayout(): Unit = LayoutPass.relayout(this)

  /** Whether the layout pass has this container waiting to be laid out. */
  private[everyui] var layoutPending: Boolean = false
  /** Whether the layout is placing the children now. */
  private[everyui] var placing: Boolean = false

  /** Run the layout (the layout pass calls it): where the children go, now. */
  private[everyui] def placeNow(): Unit = if (!placing) {
    placing = true
    if (everyui.perf.PerfCounters.on) placedIn = everyui.perf.PerfCounters.placedOne(kind, placedIn)
    try layout().foreach(_.layout(this, children()))
    finally placing = false
  }

  // the frame it was last placed in, counted (`PerfCounters`)
  private var placedIn = 0
  private def place(): Unit = LayoutPass.request(this)
  override def requestLayout(): Unit = place()
  layout.attach { l =>
    l.foreach { lay =>
      follow()
      if (!size.height.explicit) {
        heightFollowsContent = true
        val content = contentHeight
        size.height := content()
      }
      if (lay.fitsWidth() && !size.width.explicit) {
        widthFollowsContent = true
        val content = contentWidth
        size.width := content()
      }
    }
    place()
  }
  // What moves the children is followed once there is a layout to move them: most containers never have one (a card, a
  // badge), and following every child's size for each of them is time a screen of them waits for on a Roku. (A change
  // of the children asks for the layout itself: ContainerFeature.)
  private var following: Boolean = false
  private def follow(): Unit = if (!following) {
    following = true
    padding.attach(_ => place())
    size.width.attach(_ => place())
    size.height.attach(_ => place())
    watchChildren()
  }

  override def kind: String = "Container"
  override def childComponents: Vector[Component] = children()
}


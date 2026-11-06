package everyui.split

import everyui.component.*
import everyui.easing.Easing
import everyui.event.{Key, PointerEvent}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.perf.Quality
import everyui.render.Platform
import everyui.task.*
import reactify.*

import scala.concurrent.duration.*

/** Panes one over another, each under a header that folds it away (`StackedPane`), as an editor's sidebar stacks its
  * explorer's sections: the open panes share the height left by the headers by their `weight`, none shorter than its
  * `minHeight` while there is room; one open alone has it all; with all folded the headers stack at the top. Between an
  * open pane and the next open one below it a handle drags the room from one to the other, kept to both minimums, and
  * what it leaves is written back to the panes' weights (each open pane's set to its share, their total kept, so a
  * folded one's own weight, which it opens again with, still weighs the same against them). Folding and opening glide
  * (`glide`), at once at Quality's Minimal.
  *
  * The headers take the focus. Down from one goes into its pane's content where it is open and has anything to focus,
  * else to the next header; Up to the content of the pane above where it is open (where the focus last was there),
  * else to its header. Out of a pane's content, Up goes to its header and Down to the next. Right (Left, right to
  * left) from a header goes to its actions, one by one, and Left back. Past the first or the last header the arrows
  * are left to what the stack is in.
  *
  * Give the stack its size; each pane is given its own.
  *
  * {{{
  *   val folders = new StackedPane("Folders", tree, collapsed = settings.foldersClosed, weight = settings.foldersShare)
  *   folders.icon @= Some(MaterialIcon.Folder)
  *   folders.actions @= List(RichAction(MaterialIcon.CreateNewFolder, "New folder", () => newFolder()))
  *   val chats = new StackedPane("Conversations", list, collapsed = settings.chatsClosed, weight = settings.chatsShare)
  *   chats.actions @= List(RichAction(MaterialIcon.Add, "New conversation", () => startChat(), shown = true))
  *   val sidebar = new StackedPanes(Vector(folders, chats))
  *   sidebar.size.width @= 280.0
  *   sidebar.size.height := ui.size.height()
  * }}} */
class StackedPanes(val panes: Vector[StackedPane]) extends Container {
  /** How long folding and opening glide, in milliseconds: 0 at once. */
  val glide: Var[Double] = own(Var(StackedPanes.glide))
  /** How far a handle reaches into the header under it, which a pointer or a finger takes hold of. */
  val thickness: Var[Double] = own(Var(StackedPanes.thickness()))

  private val room: Val[Double] = own(Val(size.height() - panes.map(_.headerHeight()).sum))
  private val opened: Val[Vector[Boolean]] = own(Val(panes.map(p => !p.collapsed())))
  /** Each pane's content height as it settles: nothing for a folded pane, a share by weight for the open ones. */
  val target: Val[Vector[Double]] = own(Val(StackedPanes.heights(room(), opened(), panes.map(_.weight()),
    panes.map(_.minHeight()))))
  /** Each pane's content height now: the target, or on the way to it while a pane folds or opens. */
  val heights: Var[Vector[Double]] = own(Var(target()))
  // where each pane starts, to the whole pixel, and where the last ends
  private val edges: Val[Vector[Double]] = own(Val {
    var at = 0.0
    var out = Vector(0.0)
    panes.indices.foreach { i =>
      at += panes(i).headerHeight() + heights()(i)
      out = out :+ math.round(at).toDouble
    }
    out
  })

  private var was: Vector[Boolean] = opened()
  private var run: Option[TaskInstance] = None
  private var generation: Int = 0
  target.attach { to =>
    val folding = opened() != was
    was = opened()
    run.foreach(_.cancel())
    run = None
    generation += 1
    if (folding && glide() > 0.0 && Quality.glides && heights().size == to.size) {
      val from = heights()
      val mine = generation
      val move = PartialAnimate(() => 0.0, (t: Double) => if (mine == generation) {
        heights @= from.indices.map(i => from(i) + (to(i) - from(i)) * t).toVector
      }, () => 1.0).in(glide().toLong.millis).easing(Easing.cubicOut)
      run = Some(sequential(move, synchronous {
        if (mine == generation) {
          run = None
          heights @= target()
        }
      }).start())
    }
    else heights @= to
  }

  panes.indices.foreach { i =>
    val p = panes(i)
    p.stack = Some(this)
    p.ruled @= i > 0
    p.size.width := size.width()
    p.position.top := edges()(i)
    p.size.height := edges()(i + 1) - edges()(i)
  }

  /** The handles, one under each pane but the last: shown under an open pane with an open one somewhere below it. */
  val handles: Vector[SplitHandle] = panes.indices.dropRight(1).toVector.map { i =>
    val h = new SplitHandle(vertical = true)
    h.ruled @= false
    // out of Tab: its reach lies under the header below it, which it is drawn over, so it comes after every pane
    h.focusable @= false
    h.size.width := size.width()
    h.size.height := thickness()
    // its rule on the line between the panes, its reach mostly into the header under it
    h.ruleAt @= StackedPanes.above
    h.position.top := edges()(i + 1) - StackedPanes.above
    h.visible := opened()(i) && below(i).nonEmpty
    h
  }
  private var dragFrom: Vector[Double] = Vector.empty
  handles.indices.foreach { i =>
    val h = handles(i)
    h.pressed.attach(_ => dragFrom = heights())
    h.dragged.attach(delta => below(i).foreach(b => drag(i, b, delta)))
    // a click on it that drags nothing is the header's under it
    h.clicked.attach(e => if (!h.moved) headerAt(e).foreach(_.toggle()))
  }

  children ++= panes ++ handles
  size.width.attach(_ => node.setClip(size.width(), size.height()))
  size.height.attach(_ => node.setClip(size.width(), size.height()))
  onDispose(() => {
    run.foreach(_.cancel())
    run = None
  })

  /** The handle under pane `above` dragged `delta` from where it was pressed: the room moved between it and pane `below`,
    * and the weights written back. */
  private def drag(above: Int, below: Int, delta: Double): Unit = if (dragFrom.size == panes.size) {
    val moved = StackedPanes.dragged(dragFrom, panes.map(_.minHeight()), above, below, delta)
    val weights = StackedPanes.weightsFor(moved, opened(), panes.map(_.weight()))
    panes.indices.foreach(j => if (panes(j).weight() != weights(j)) panes(j).weight @= weights(j))
  }

  /** The first open pane under pane `i`. */
  private def below(i: Int): Option[Int] = {
    val o = opened()
    (i + 1 until panes.size).find(j => o(j))
  }

  /** The pane under `p`, whose header Down out of `p` goes to. */
  private[split] def below(p: StackedPane): Option[StackedPane] = {
    val i = panes.indexWhere(_ eq p)
    if (i >= 0 && i + 1 < panes.size) Some(panes(i + 1)) else None
  }

  private def headerAt(e: PointerEvent): Option[StackedPane] =
    panes.find(p => e.y >= p.header.absoluteTop && e.y < p.header.absoluteTop + p.header.size.height())

  /** Where an arrow from `p`'s header, or from one of its actions, goes (see above); None leaves it to the stack's
    * container. */
  private[split] def fromHeader(p: StackedPane, direction: Key): Option[() => Unit] = {
    val i = panes.indexWhere(_ eq p)
    val shown = p.buttons().filter(_.visible())
    val at = shown.indexWhere(_.focused())
    val on = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
    val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
    if (i < 0) None
    else if (direction == Key.ArrowDown) {
      if (!p.collapsed() && Focus.candidates(p.body).nonEmpty) Some(() => enter(p))
      else below(p).map(n => () => n.header.focus())
    }
    else if (direction == Key.ArrowUp) {
      if (i == 0) None
      else {
        val up = panes(i - 1)
        if (!up.collapsed() && Focus.candidates(up.body).nonEmpty) Some(() => enter(up)) else Some(() => up.header.focus())
      }
    }
    else if (direction == on) {
      if (at + 1 < shown.size) Some(() => shown(at + 1).focus()) else None
    }
    else if (direction == back && at >= 0) Some(() => if (at == 0) p.header.focus() else shown(at - 1).focus())
    else None
  }

  /** The focus into `p`'s content: where it last was there, else its first. */
  private def enter(p: StackedPane): Unit = {
    val all = Focus.candidates(p.body)
    p.body.remembered.filter(r => all.exists(_ eq r)).orElse(all.headOption).foreach(_.focus())
  }

  override def kind: String = "StackedPanes"
}

object StackedPanes {
  /** A pane's least content height, unless it says. */
  val minimum: Double = 60.0
  /** How long folding and opening glide, in milliseconds, unless the stack says. */
  val glide: Double = 160.0
  /** How far a handle reaches over the pane above it, its rule on the line between them. */
  val above: Double = 3.0

  /** How far a handle reaches, from 3 over the pane above to the header under it: 6 for a pointer, 20 for a finger. */
  def thickness(): Double = if (Platform.touchSupport() && !Platform.pointerSupport()) 20.0 else 6.0

  /** How tall each pane's content is in `room`, the height the headers leave: nothing for a folded pane (not `open`);
    * one open alone has it all; more share it by their `weights`, none below its minimum while there is room for all
    * of them, and by their minimums where there is not. */
  def heights(room: Double, open: Vector[Boolean], weights: Vector[Double], minimums: Vector[Double]): Vector[Double] = {
    val n = open.size
    val out = new Array[Double](n)
    val space = math.max(0.0, room)
    val shown = (0 until n).filter(i => open(i)).toVector
    if (shown.size == 1) out(shown.head) = space
    else if (shown.size > 1) {
      val least = shown.map(i => math.max(0.0, minimums(i))).sum
      if (least >= space) shown.foreach(i => out(i) = if (least > 0.0) space * math.max(0.0, minimums(i)) / least
        else space / shown.size.toDouble)
      else {
        // by weight, those whose share falls short of their minimum held at it and the rest shared again by the others
        val held = new Array[Boolean](n)
        var left = space
        var again = true
        while (again) {
          again = false
          val free = shown.filter(i => !held(i))
          val total = free.map(i => StackedPanes.weighs(weights(i))).sum
          free.foreach { i =>
            if (!again && left * StackedPanes.weighs(weights(i)) / total < minimums(i)) {
              held(i) = true
              out(i) = minimums(i)
              left -= minimums(i)
              again = true
            }
          }
          if (!again) free.foreach(i => out(i) = left * StackedPanes.weighs(weights(i)) / total)
        }
      }
    }
    out.toVector
  }

  /** The heights after the handle between open panes `above` and `below` is dragged `delta` from `from`: the two
    * share what they had between them, neither below its minimum; where they cannot both have it, nothing moves. */
  def dragged(from: Vector[Double], minimums: Vector[Double], above: Int, below: Int, delta: Double): Vector[Double] = {
    val both = from(above) + from(below)
    val most = both - minimums(below)
    if (most < minimums(above)) from
    else {
      val a = math.max(minimums(above), math.min(most, from(above) + delta))
      from.updated(above, a).updated(below, both - a)
    }
  }

  /** The weights that share the room out as `heights` do: each open pane's its share of the room the open panes have,
    * of the weight they had between them; a folded pane's kept. */
  def weightsFor(heights: Vector[Double], open: Vector[Boolean], weights: Vector[Double]): Vector[Double] = {
    val shown = heights.indices.filter(i => open(i))
    val room = shown.map(i => heights(i)).sum
    val total = shown.map(i => weighs(weights(i))).sum
    if (room <= 0.0) weights else weights.indices.map(i => if (open(i)) heights(i) / room * total else weights(i)).toVector
  }

  // a weight of nothing or less still weighs a little, so a share is never divided by nothing
  private def weighs(w: Double): Double = math.max(0.0001, w)
}

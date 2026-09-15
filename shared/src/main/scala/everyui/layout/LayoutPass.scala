package everyui.layout

import everyui.async.Task
import everyui.component.{Component, Container}

/** When containers lay out. A change that moves a container's children (its children, its size, its padding, a child's
  * size) asks for the container to be laid out rather than laying it out there and then; the pass lays out what was
  * asked for, the deepest container first, in a loop.
  *
  * So a cascade is iterative instead of nested: a child's layout that changes its size asks for its parent, and the
  * parent is laid out once the children under it are done, rather than inside the child's layout, while the parent's
  * own layout is still placing. On a JVM the nesting only costs time; on a Roku, whose stack is about 120 frames and
  * whose callbacks have a time limit, a form's nested layouts ran out of both.
  *
  * Outside a batch a request is laid out at once, so a size read right after a change is the laid-out one. Inside
  * `batch` (a screen building its UI, the reading direction changing) the requests wait for the end of it: one pass
  * over everything the batch touched. */
object LayoutPass {
  private var pending: List[Container] = Nil
  private var batches: Int = 0
  private var flushing: Boolean = false

  /** Lay `container` out: now, or at the end of the batch or pass under way. A container asking while it is being
    * laid out is already being placed. */
  def request(container: Container): Unit = if (!container.placing && container.layout().nonEmpty) {
    if (!container.layoutPending) {
      container.layoutPending = true
      pending = container :: pending
    }
    if (batches == 0) flush()
  }

  /** Run `f` with layout held until it is done, then lay out everything it asked for in one pass. */
  def batch[T](f: => T): T = {
    batches += 1
    try f
    finally close()
  }

  /** `task`, with layout held from its start until it completes (or, if it completes later, until its start returns). */
  def batched[T](task: Task[T]): Task[T] = Task.async[T] { done =>
    var open = true
    batches += 1
    task.map { value =>
      if (open) {
        open = false
        close()
      }
      done(value)
    }.start()
    if (open) {
      open = false
      close()
    }
  }

  /** Lay out every container asked for, the deepest first, until none is left. */
  def flush(): Unit = if (!flushing) {
    flushing = true
    try {
      while (pending.nonEmpty) {
        val next = deepest()
        pending = pending.filterNot(_ eq next)
        next.layoutPending = false
        next.placeNow()
      }
    } finally flushing = false
  }

  /** Ask for `root` and every container under it, in one pass: the reading direction changed. */
  def relayout(root: Container): Unit = batch {
    var left: List[Component] = List(root)
    while (left.nonEmpty) {
      val c = left.head
      left = c.childComponents.toList ++ left.tail
      c match {
        case container: Container => request(container)
        case _ => ()
      }
    }
  }

  private def close(): Unit = {
    batches -= 1
    if (batches == 0) flush()
  }

  private def deepest(): Container = {
    var best = pending.head
    var bestDepth = depthOf(best)
    pending.tail.foreach { c =>
      val d = depthOf(c)
      if (d > bestDepth) {
        best = c
        bestDepth = d
      }
    }
    best
  }

  private def depthOf(c: Component): Int = {
    var d = 0
    var at = c.parent()
    while (at.nonEmpty) {
      d += 1
      at = at.get.parent()
    }
    d
  }
}

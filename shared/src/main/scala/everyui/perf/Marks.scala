package everyui.perf

import everyui.render.Platform

/** Moments an app names as it runs (a workspace switched to, a conversation opened), each at `Platform.ticks()`, for a
  * check outside the app to read and time the gaps between: the last `kept` of them, here (`recent`) and wherever the
  * platform lets a harness read them (`Platform.marked`: `window.everyuiMarks` on the web, a log line elsewhere where
  * the app was launched with `marks=1`).
  *
  * {{{
  *   Marks.mark("switch:start")
  *   ...
  *   Marks.mark("switch:drawn")
  * }}} */
object Marks {
  /** How many are kept, the oldest let go first. */
  val kept: Int = 200

  private var marks: Vector[(String, Double)] = Vector.empty

  /** `name` marked now. */
  def mark(name: String): Unit = {
    val at = Platform.ticks()
    marks = (if (marks.length >= kept) marks.drop(1) else marks) :+ (name -> at)
    Platform.marked(name, at)
  }

  /** The marks kept, oldest first: each name with its ticks. */
  def recent: Vector[(String, Double)] = marks

  /** The ticks `name` was last marked at, if it was. */
  def last(name: String): Option[Double] = marks.reverseIterator.find(_._1 == name).map(_._2)

  /** Every mark let go. */
  def clear(): Unit = marks = Vector.empty
}

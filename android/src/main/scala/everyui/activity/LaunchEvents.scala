package everyui.activity

import android.content.Intent
import scala.collection.mutable

case class AppLaunch(id: Long, action: String, uri: Option[String], extras: Map[String, String])

/** Normalized launch requests, consumed once on the main thread. Only app-allowlisted string extras survive. */
object LaunchEvents {
  private val waiting = mutable.Queue.empty[AppLaunch]
  private var next = 0L
  def offer(intent: Intent, allowedExtras: Set[String] = Set.empty): Unit = if (intent != null) synchronized {
    next += 1
    val extras = allowedExtras.flatMap(k => Option(intent.getStringExtra(k)).filter(_.length <= 2048).map(k -> _)).toMap
    waiting.enqueue(AppLaunch(next, Option(intent.getAction).getOrElse(""), Option(intent.getDataString).filter(_.length <= 4096), extras))
    while (waiting.size > 16) waiting.dequeue()
  }
  def consume(): List[AppLaunch] = synchronized {
    val events = waiting.toList
    waiting.clear()
    events
  }
}

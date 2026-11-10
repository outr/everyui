package everyui.sbt

import java.io.File

/** A second process opening archives into an AarCache room, for AarCacheSpec: the room, the moment to start at
  * (milliseconds since the epoch, so every process starts together), how many threads, then the archives. Prints how
  * many of the opens it made were its own. */
object AarCacheProbe {
  def main(args: Array[String]): Unit = {
    val room = new File(args(0))
    val start = args(1).toLong
    val threads = args(2).toInt
    val archives = args.drop(3).map(new File(_)).toSeq
    archives.foreach(AarCache.digest)
    while (System.currentTimeMillis() < start) Thread.sleep(1)
    val opened = new java.util.concurrent.atomic.AtomicInteger
    val workers = (1 to threads).map { _ =>
      val t = new Thread(() => archives.foreach(a => if (AarCache.open(a, room)) opened.incrementAndGet()))
      t.start()
      t
    }
    workers.foreach(_.join())
    println(s"opened=${opened.get}")
  }
}

package everyui.webcheck

import rapid.Task

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** The Chrome profiles RoboBrowser leaves behind, taken away. The release the checks run on makes a profile of its own
  * for each launch (~/.config/robobrowser/instance-<port>), whatever profile it is given, and never removes it: 751
  * of them had grown to 26 GB. `around` runs a check and then removes each such profile made since it began that no
  * running process names, so another session's browser keeps its own. (RoboBrowser's `freshProfile`, which deletes its
  * profile as the browser goes, is not yet in a release.) */
object Profiles {
  private val root: Path = Paths.get(System.getProperty("user.home"), ".config", "robobrowser")

  /** `check`, and afterwards, however it ended, the profiles it left removed. */
  def around[T](check: => Task[T]): Task[T] = {
    val started = System.currentTimeMillis() - 1000L
    Task.defer(check).guarantee(Task(sweep(started)))
  }

  // (Chrome's helpers go a moment after the browser: a profile still named is waited on, up to ten seconds)
  private def sweep(since: Long): Unit = Try {
    val made = Files.list(root).iterator().asScala.toList.filter { p =>
      p.getFileName.toString.startsWith("instance-") && Files.isDirectory(p) &&
        Files.getLastModifiedTime(p).toMillis >= since
    }
    def named(p: Path, inUse: List[String]): Boolean = inUse.exists(_.contains(p.getFileName.toString))
    var tries = 0
    while (tries < 20 && { val inUse = running(); made.exists(named(_, inUse)) }) {
      Thread.sleep(500L)
      tries += 1
    }
    val inUse = running()
    val (open, gone) = made.partition(named(_, inUse))
    gone.foreach(remove)
    if (made.nonEmpty) println("[profiles] removed " + gone.size + (if (open.isEmpty) "" else ", " + open.size + " still open"))
  }

  // every running process's command line, to tell a profile still open
  private def running(): List[String] = Try(Files.list(Paths.get("/proc")).iterator().asScala.toList.flatMap { p =>
    Try(new String(Files.readAllBytes(p.resolve("cmdline")), "UTF-8")).toOption
  }).getOrElse(Nil)

  private def remove(dir: Path): Unit = Try {
    Files.walk(dir).iterator().asScala.toList.reverse.foreach(p => Try(Files.delete(p)))
  }
}

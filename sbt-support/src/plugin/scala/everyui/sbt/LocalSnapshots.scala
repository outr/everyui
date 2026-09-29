package everyui.sbt

import sbt.*
import sbt.librarymanagement.{UpdateReport, UpdateStats}

import java.security.MessageDigest

/** Keeps a build's resolution in step with the snapshots published to the local Ivy repository (`publishLocal`), as
  * EveryUI's are, again and again under one version. sbt resolves again only when what a project declares changes, and
  * a snapshot republished with other dependencies of its own (a newer Spice) changes nothing a project declares: it
  * kept the old resolution, and an app ran with the old jars beside the new ones and failed with NoSuchMethodError.
  *
  * The descriptors (ivy.xml) of a project's snapshot dependencies found there, and of the snapshots those depend on,
  * are hashed into a stamp that sbt's update weighs as it weighs a dependency project's (as one more of
  * `transitiveUpdate`'s reports): the project resolves again once after a republish changed any of them, and not
  * otherwise. Coursier also keeps each resolution in memory for as long as the sbt server runs, so when a project's
  * stamp changes that memory is let go too, or the new resolution would be the old one again. The plugin does this for
  * every project; a build without it recovers with `clean` and a new server. */
object LocalSnapshots {
  /** The local Ivy repository, where `publishLocal` puts what it publishes, for a build whose Ivy home is `ivyHome`. */
  def repository(ivyHome: Option[String]): File =
    ivyHome.map(file).orElse(sys.props.get("ivy.home").map(file)).getOrElse(Path.userHome / ".ivy2") / "local"

  /** The descriptor of each snapshot among `modules` (named as published, with their Scala suffix) found in
    * `repository`, and of each snapshot those depend on, in turn. */
  def descriptors(repository: File, modules: Seq[ModuleID]): Seq[File] = {
    def snapshot(revision: String): Boolean = revision.contains("SNAPSHOT")
    def descriptor(organization: String, name: String, revision: String): File =
      repository / organization / name / revision / "ivys" / "ivy.xml"

    @annotation.tailrec
    def walk(pending: List[File], found: Set[File]): Set[File] = pending match {
      case Nil => found
      case f :: rest if found.contains(f) || !f.isFile => walk(rest, found)
      case f :: rest =>
        val next = scala.util.Try(scala.xml.XML.loadFile(f) \ "dependencies" \ "dependency").toOption.toList.flatten
          .map(d => (d \@ "org", d \@ "name", d \@ "rev")).collect {
            case (o, n, r) if snapshot(r) => descriptor(o, n, r)
          }
        walk(next ::: rest, found + f)
    }
    walk(modules.filter(m => snapshot(m.revision)).map(m => descriptor(m.organization, m.name, m.revision)).toList,
      Set.empty).toSeq.sortBy(_.getPath)
  }

  /** A stamp of the `descriptors`' contents, or None where there are none. */
  def stamp(descriptors: Seq[File]): Option[String] =
    if (descriptors.isEmpty) None
    else {
      val digest = MessageDigest.getInstance("SHA-1")
      descriptors.foreach { f =>
        digest.update(f.getPath.getBytes("UTF-8"))
        digest.update(0.toByte)
        digest.update(IO.readBytes(f))
      }
      Some("sbt-everyui local snapshots " + digest.digest().map(b => f"$b%02x").mkString)
    }

  /** An empty report carrying `stamp`, to add to `transitiveUpdate`: sbt's update resolves again when the stamps of
    * those reports change. */
  def report(repository: File, stamp: String): UpdateReport =
    UpdateReport(repository, Vector.empty, UpdateStats(0L, 0L, 0L, false, Some(stamp)), Map.empty)

  /** Records `stamp` in `record` (a file of the project's target) and, where it is not the one recorded, lets go of
    * the resolutions coursier holds in memory. A new record (the project is new, or was cleaned) lets go of them too,
    * since the server may have resolved the project before. */
  def recorded(record: File, stamp: Option[String]): Unit = {
    val text = stamp.getOrElse("")
    if (!record.isFile || IO.read(record) != text) {
      forget()
      IO.write(record, text)
    }
  }

  /** Clears lm-coursier's in-memory resolutions, which are kept by what was asked and not by what the repository now
    * holds. It is lm-coursier's internal cache: where a later sbt has none, there is nothing to clear. */
  private def forget(): Unit =
    try lmcoursier.internal.SbtCoursierCache.default.clear()
    catch { case _: LinkageError => () }
}

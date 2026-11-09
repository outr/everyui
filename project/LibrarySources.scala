import sbt.*
import sbt.Keys.*
import sbt.io.IO
import sbt.librarymanagement.DependencyResolution

/** A Scala library compiled from its own source, unmodified, in place of its jar: for the compiler, which emits a
  * library for the Roku and tvOS from its TASTy (kept whole by -Yretain-trees, which a published jar was not compiled
  * with), and for Android, whose builds compile it with -Ylegacy-lazy-vals.
  *
  * The source is the library's published sources jar at the version the build names, resolved through sbt (the
  * build's resolvers and cache, as any dependency is) and unpacked into `<out>/<name>-sources/<version>`, so a checkout
  * builds on any machine with nothing beside it. An environment variable (or system property) of the caller's naming
  * puts a source tree of one's own in its place, for working on the library and EveryUI together; nothing is copied
  * from it, it is compiled where it is.
  *
  * What a library must be to be brought in this way: its sources jar must compile as Scala 3 as it is (a jar of
  * sources a Scala 2 build published may not); it may use only what the Roku emitter (and the Swift one) supports,
  * since its classes are emitted with the app's; and its sources jar must hold the one tree every platform compiles,
  * as a cross-built library's JVM sources jar holds its shared sources and its JVM ones, merged, and nothing here
  * picks among platform-split directories. reactify's holds its shared sources alone (it has no others), and they are
  * identical to its release tag's.
  *
  * Moving this into sbt-everyui would make it a task an app's build could use (an app's own Roku library, say): it
  * needs nothing the plugin does not already have (sbt's dependency resolution, as `everyuiCompiler` fetches the
  * compiler with it), only a setting key or two in its autoImport and CONSUMING.md's word on it. */
object LibrarySources {
  /** The directory of `module`'s sources: the one `overrideVariable` names where it is set (as an environment variable
    * or a system property, the property first), or else its sources jar unpacked under `out`, fetched and unpacked
    * once per version. `module` is cross-versioned for the Scala of the project it is asked in, whose resolution
    * fetches it: that project must not exclude it (excludeDependencies), or nothing is fetched. */
  def directory(module: ModuleID, overrideVariable: Option[String], out: File): Def.Initialize[Task[File]] =
    Def.task(Def.uncached {
      val local = overrideVariable.flatMap(v => sys.props.get(v).orElse(sys.env.get(v))).filter(_.nonEmpty).map(file)
      val log = streams.value.log
      local match {
        case Some(dir) =>
          if (!dir.isDirectory) {
            sys.error(s"LibrarySources: ${overrideVariable.get} names $dir, which is not a directory")
          }
          log.debug(s"LibrarySources: ${module.name} from $dir")
          dir
        case None =>
          val cross = CrossVersion(scalaVersion.value, scalaBinaryVersion.value)(module)
            .withCrossVersion(CrossVersion.disabled)
          unpacked(dependencyResolution.value, module.name, cross, out, log)
      }
    })

  /** The directory's Scala sources, for a project's `sourceGenerators`. */
  def sources(directory: Def.Initialize[Task[File]]): Def.Initialize[Task[Seq[File]]] =
    Def.task((directory.value ** "*.scala").get().sorted)

  private def unpacked(resolution: DependencyResolution, name: String, module: ModuleID, out: File,
                       log: Logger): File = {
    val dir = out / s"$name-sources" / module.revision
    // beside the tree rather than in it, so it is not taken for a source
    val stamp = out / s"$name-sources" / s"${module.revision}.unpacked"
    if (!stamp.exists || !dir.isDirectory) {
      val fetched = out / s"$name-sources" / s"${module.revision}.fetched"
      val wanted = resolution.wrapDependencyInModule(module.classifier("sources").intransitive())
      val jars = resolution.retrieve(wanted, fetched, log) match {
        case Right(files) => files.filter(_.getName.endsWith("-sources.jar")).distinct
        case Left(problem) => sys.error(s"LibrarySources: could not fetch $module's sources: " +
          problem.resolveException.getMessage)
      }
      if (jars.size != 1) {
        sys.error(s"LibrarySources: ${jars.size} sources jars fetched for $module, not one (is it excluded?)")
      }
      IO.delete(dir)
      IO.unzip(jars.head, dir, (entry: String) => !entry.startsWith("META-INF/"))
      IO.delete(fetched)
      IO.write(stamp, jars.head.getName)
      log.info(s"LibrarySources: ${module.organization}:${module.name}:${module.revision} sources unpacked in $dir")
    }
    dir
  }
}

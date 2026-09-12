package universal.compiler

import scala.tasty.inspector.TastyInspector
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

/** CLI entry for universal-ui's Roku compiler (driven by the build's `emitRoku`).
  *
  *   universal.compiler.Main [--strict] [--library=<classes-dir-or-jar>]...
  *     <classes-dir> <classpath> <out-dir> [<source-root> [<channel-title>]]
  *
  *  - `<classes-dir>`: the TASTy root of the program to emit.
  *  - `<classpath>`: path-separator-joined classpath for the TASTy inspector.
  *  - `<out-dir>`: the channel directory to write (`manifest`, `source/`, `components/`).
  *  - `<source-root>`: base directory for source-location comments.
  *  - `<channel-title>`: the manifest title.
  *  - `--library`: also emit a dependency's TASTy (a sibling project's classes
  *    directory, or a jar), compiled through with the program — how reactify
  *    reaches the Roku.
  *  - `--strict`: exit non-zero if anything could not be translated, listing
  *    each gap at its Scala source.
  */
object Main:
  def main(args: Array[String]): Unit =
    val (flags, positional) = args.toList.partition(_.startsWith("--"))
    val strict = flags.contains("--strict")
    val libraries = flags.collect { case f if f.startsWith("--library=") => f.stripPrefix("--library=") }
    val unknown = flags.filterNot(f => f == "--strict" || f.startsWith("--library="))

    if unknown.nonEmpty || positional.length < 3 || positional.length > 5 then
      unknown.foreach(f => System.err.println(s"unknown flag: $f"))
      System.err.println(
        "usage: universal.compiler.Main [--strict] [--library=<classes-dir-or-jar>]... " +
          "<classes-dir> <classpath> <out-dir> [<source-root> [<channel-title>]]"
      )
      System.exit(2)

    val classesDir = Paths.get(positional(0))
    val classpath  = positional(1).split(java.io.File.pathSeparator).toList.filter(_.nonEmpty)
    val outDir     = Paths.get(positional(2))
    val sourceRoot = if positional.length >= 4 then Some(Paths.get(positional(3)).toAbsolutePath) else None
    val title      = if positional.length >= 5 then positional(4) else "app"

    val ownTasty = findTastyFiles(classesDir)
    if ownTasty.isEmpty then
      System.err.println(s"No .tasty files found under $classesDir")
      System.exit(1)
    // Compile-through: classes directories contribute their .tasty files
    // alongside the program's; jars go to the inspector as jars.
    val (libDirs, libJars) = libraries.map(Paths.get(_)).partition(Files.isDirectory(_))
    val tastyFiles = (ownTasty ++ libDirs.flatMap(findTastyFiles)).distinct
    if libraries.nonEmpty then
      println(s"Compiling through ${libJars.size} library jar(s) and ${libDirs.size} project dir(s)")

    val emitter = new RokuEmitter(outDir, sourceRoot, title)
    val ok = TastyInspector.inspectAllTastyFiles(tastyFiles, libJars.map(_.toString), classpath)(emitter)
    if !ok then
      System.err.println("TASTy inspection failed")
      System.exit(1)

    emitter.writeOutput()
    println(s"Wrote Roku channel to ${outDir.toAbsolutePath}")

    if strict && emitter.unsupported.nonEmpty then
      System.err.println(s"--strict: ${emitter.unsupported.size} unsupported construct(s):")
      emitter.unsupported.foreach(g => System.err.println(s"  $g"))
      System.exit(3)

  private def findTastyFiles(root: Path): List[String] =
    if !Files.exists(root) then Nil
    else
      val stream = Files.walk(root)
      try
        stream.iterator().asScala
          .filter(p => p.toString.endsWith(".tasty"))
          .map(_.toString)
          // sorted: deterministic emission order across runs and machines
          .toList.sorted
      finally stream.close()

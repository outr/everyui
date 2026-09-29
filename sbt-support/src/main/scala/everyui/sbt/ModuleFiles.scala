package everyui.sbt

import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import java.util.zip.ZipFile

/** The files an EveryUI module carries for the web to serve, taken off the app's classpath: EveryUI's fonts
  * (`everyui/fonts/`, in everyui-web), a web guest's (`everyui-monaco/<version>/`, `everyui-terminal/<version>/`). Each
  * entry of the classpath is a jar, as an app that depends on the published artifacts has, or a classes directory, as a
  * module of the same build has; what is found under `prefix` is copied to `out` under the same path.
  *
  * {{{
  *   ModuleFiles.copy(classpath, "everyui-terminal/", out)     // out/everyui-terminal/6.0.0/xterm.js, ...
  *   ModuleFiles.guests(classpath, out)                        // every web guest the classpath carries
  *   ModuleFiles.appFonts(Seq(brandFont), fonts)               // the app's own fonts beside EveryUI's
  * }}}
  *
  * Plain Scala, for an sbt build to compile in (CONSUMING.md); the sbt-everyui plugin's `everyuiWebGuests` and
  * `everyuiWebFonts` are this, and `everyuiFonts` adds `everyuiAppFonts` through `appFonts` as `everyuiWebFonts` does. */
object ModuleFiles {
  /** Every file under `prefix` (a directory, ending in `/`) in `classpath`, copied to `out` at its own path: the files
    * written. */
  def copy(classpath: Seq[File], prefix: String, out: File): Seq[File] =
    classpath.flatMap(entry => from(entry, path => path.startsWith(prefix), out))

  /** Every web guest's files in `classpath`: the directories named `everyui-<module>/` at the root of an EveryUI
    * module (everyui-monaco, everyui-terminal and any to come), copied to `out` to serve next to the page. */
  def guests(classpath: Seq[File], out: File): Seq[File] =
    classpath.filter(e => e.isDirectory || e.getName.startsWith("everyui-")).flatMap(entry => from(entry, guest, out))

  /** The app's own font files (a face it registers with `Faces.register`), copied into `fonts`, the directory of
    * EveryUI's: the files written. A name one of EveryUI's fonts already has there, a name two of them share, or a file
    * that is not there, is an error naming each, rather than one font silently taking another's place. */
  def appFonts(files: Seq[File], fonts: File): Seq[File] = {
    val missing = files.filterNot(_.isFile)
    val taken = files.filter(f => new File(fonts, f.getName).exists)
    val twice = files.groupBy(_.getName).collect { case (name, fs) if fs.size > 1 => name }.toSeq.sorted
    val problems = missing.map(f => s"$f is not a file") ++ taken.map(f => s"${f.getName} is one of EveryUI's fonts ($f)") ++
      twice.map(n => s"$n is the name of more than one of them")
    if (problems.nonEmpty) throw new IllegalArgumentException(s"everyuiAppFonts: ${problems.mkString("; ")}; rename the app's")
    fonts.mkdirs()
    files.map { f =>
      val target = new File(fonts, f.getName)
      Files.copy(f.toPath, target.toPath, StandardCopyOption.REPLACE_EXISTING)
      target
    }
  }

  // a guest's file: under a top-level directory named everyui-<something>/
  private def guest(path: String): Boolean = {
    val slash = path.indexOf('/')
    slash > "everyui-".length && path.startsWith("everyui-") && slash < path.length - 1
  }

  private def from(entry: File, wanted: String => Boolean, out: File): Seq[File] =
    if (entry.isDirectory) {
      val root = entry.toPath
      if (!Files.exists(root)) Nil
      else {
        val walk = Files.walk(root)
        try {
          val files = walk.iterator()
          val copied = Seq.newBuilder[File]
          while (files.hasNext) {
            val file = files.next()
            val path = root.relativize(file).toString.replace(File.separatorChar, '/')
            if (Files.isRegularFile(file) && wanted(path)) {
              val target = new File(out, path)
              target.getParentFile.mkdirs()
              Files.copy(file, target.toPath, StandardCopyOption.REPLACE_EXISTING)
              copied += target
            }
          }
          copied.result()
        }
        finally walk.close()
      }
    }
    else if (entry.isFile && entry.getName.endsWith(".jar")) {
      val zip = new ZipFile(entry)
      try {
        val entries = zip.entries()
        val copied = Seq.newBuilder[File]
        while (entries.hasMoreElements) {
          val e = entries.nextElement()
          if (!e.isDirectory && !e.getName.contains("..") && wanted(e.getName)) {
            val target = new File(out, e.getName)
            target.getParentFile.mkdirs()
            val in = zip.getInputStream(e)
            try Files.copy(in, target.toPath, StandardCopyOption.REPLACE_EXISTING)
            finally in.close()
            copied += target
          }
        }
        copied.result()
      }
      finally zip.close()
    }
    else Nil
}

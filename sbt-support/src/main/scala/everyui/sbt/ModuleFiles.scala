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
  * }}}
  *
  * Plain Scala, for an sbt build to compile in (CONSUMING.md); the sbt-everyui plugin's `everyuiWebGuests` and
  * `everyuiWebFonts` are this. */
object ModuleFiles {
  /** Every file under `prefix` (a directory, ending in `/`) in `classpath`, copied to `out` at its own path: the files
    * written. */
  def copy(classpath: Seq[File], prefix: String, out: File): Seq[File] =
    classpath.flatMap(entry => from(entry, path => path.startsWith(prefix), out))

  /** Every web guest's files in `classpath`: the directories named `everyui-<module>/` at the root of an EveryUI
    * module (everyui-monaco, everyui-terminal and any to come), copied to `out` to serve next to the page. */
  def guests(classpath: Seq[File], out: File): Seq[File] =
    classpath.filter(e => e.isDirectory || e.getName.startsWith("everyui-")).flatMap(entry => from(entry, guest, out))

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

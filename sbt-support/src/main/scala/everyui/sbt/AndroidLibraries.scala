package everyui.sbt

import java.io.{ByteArrayOutputStream, File, InputStream}
import java.util.zip.{ZipFile, ZipInputStream}
import scala.jdk.CollectionConverters.*

/** What an Android archive (.aar) needs of an app's build beyond its classes: its resources, where its classes read
  * them through its own R class (`androidx.work.R$bool`, which an archive leaves to the app's build to write, since
  * the ids are the app's). An archive whose classes read no R class of its own needs nothing linked, so its resources
  * (a library's strings in eighty languages, say) stay out of the APK. Gradle links every archive's; here only those
  * read are, and the build fails at the shrinker on an R class nobody wrote rather than at run time. */
object AndroidLibraries {
  /** The package an archive's manifest names (`package="androidx.work"`), if it names one. */
  def packageOf(manifest: String): Option[String] =
    "<manifest[^>]*\\spackage\\s*=\\s*\"([^\"]+)\"".r.findFirstMatchIn(manifest).map(_.group(1))

  /** Whether any class among `classes` (their bytes) names a nested class of `pkg`'s R (`androidx/work/R$bool`). */
  def readsOwnR(pkg: String, classes: Iterator[Array[Byte]]): Boolean = {
    val wanted = (pkg.replace('.', '/') + "/R$").getBytes("ISO-8859-1")
    classes.exists(bytes => indexOf(bytes, wanted) >= 0)
  }

  private def indexOf(bytes: Array[Byte], wanted: Array[Byte]): Int = {
    var i = 0
    var found = -1
    while (found < 0 && i <= bytes.length - wanted.length) {
      var j = 0
      while (j < wanted.length && bytes(i + j) == wanted(j)) j += 1
      if (j == wanted.length) found = i
      i += 1
    }
    found
  }

  /** The package of the archive `aar` where it has resources and its classes read them through their own R class;
    * None otherwise. */
  def needsResources(aar: File): Option[String] = {
    val zip = new ZipFile(aar)
    try {
      val entries = zip.entries().asScala.toList
      val hasRes = entries.exists(e => !e.isDirectory && e.getName.startsWith("res/"))
      val pkg = Option(zip.getEntry("AndroidManifest.xml")).flatMap(m => packageOf(read(zip.getInputStream(m))))
      (hasRes, pkg) match {
        case (true, Some(p)) =>
          Option(zip.getEntry("classes.jar")).flatMap { jar =>
            val in = new ZipInputStream(zip.getInputStream(jar))
            try {
              val classes = Iterator.continually(in.getNextEntry).takeWhile(_ != null)
                .filter(_.getName.endsWith(".class")).map(_ => readAll(in))
              if (readsOwnR(p, classes)) Some(p) else None
            }
            finally in.close()
          }
        case _ => None
      }
    }
    finally zip.close()
  }

  private def read(in: InputStream): String = try new String(readAll(in), "UTF-8") finally in.close()

  private def readAll(in: InputStream): Array[Byte] = {
    val out = new ByteArrayOutputStream
    val buffer = new Array[Byte](65536)
    var n = in.read(buffer)
    while (n > 0) {
      out.write(buffer, 0, n)
      n = in.read(buffer)
    }
    out.toByteArray
  }
}

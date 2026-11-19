package everyui.sbt

import java.io.{ByteArrayOutputStream, File}
import java.nio.charset.StandardCharsets.{US_ASCII, UTF_8}
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

/** An app's web bundle as an LG webOS package (an `.ipk`), made here rather than by LG's `ares-package`, so a build
  * needs no Node tool. Laid out as `ares-package` lays it out: an `ar` archive of `debian-binary` ("2.0"),
  * `control.tar.gz` (the package's `control`) and `data.tar.gz`, which holds the app under
  * `usr/palm/applications/<id>/` (the bundle, its appinfo.json and its pictures) and the package's
  * `usr/palm/packages/<id>/packageinfo.json`. Every entry is owned by 0 and readable by all; a directory is 755.
  *
  * `stage` puts the bundle and what the package adds beside it; `ipk` writes the package of a staged app. */
object WebOSPackage {
  /** The bundle in `bundle` (its index.html and what it loads) staged afresh in `out` as the app's directory: each
    * file but a precompressed copy of another (`main.js.br` beside `main.js`: a CDN's, nothing on a TV serves it),
    * its page made to run from its files (`PackagedPage`), with `app`'s appinfo.json and its icons and splash picture
    * written at the sizes LG asks for. */
  def stage(bundle: File, app: WebOSApp, out: File): File = {
    val wrong = app.problems
    if (wrong.nonEmpty) sys.error("WebOSPackage: " + wrong.mkString("; "))
    if (!(bundle / "index.html").isFile) sys.error(s"WebOSPackage: $bundle has no index.html")
    if ((bundle / "appinfo.json").exists) sys.error(s"WebOSPackage: $bundle has an appinfo.json: give it as everyuiWebOS")
    delete(out)
    files(bundle).filterNot { case (path, _) => precompressed(path, bundle) }.foreach { case (path, f) =>
      val to = new File(out, path)
      to.getParentFile.mkdirs()
      Files.copy(f.toPath, to.toPath)
    }
    // the page as it runs from its files (PackagedPage: no base at the disk's root, no font preloads)
    val page = (out / "index.html").toPath
    Files.writeString(page, PackagedPage.local(Files.readString(page, UTF_8)), UTF_8)
    Files.writeString((out / "appinfo.json").toPath, app.appinfo, UTF_8)
    scaled(app.icon, 80, 80, out / "icon.png")
    scaled(app.largeIcon.getOrElse(app.icon), 130, 130, out / "largeIcon.png")
    app.splash.foreach(s => scaled(s, app.size._1, app.size._2, out / "splash.png"))
    out
  }

  /** The package of the app staged in `staged` (`stage`), as `<dir>/<id>_<version>_all.ipk`. `time` is each entry's,
    * in seconds since the epoch. */
  def ipk(staged: File, app: WebOSApp, dir: File, time: Long = System.currentTimeMillis / 1000): File = {
    val id = app.id
    val app0 = s"usr/palm/applications/$id/"
    val pkg = s"usr/palm/packages/$id/"
    val packageinfo = s"""{\n  "id": ${WebOSApp.quote(id)},\n  "version": ${WebOSApp.quote(app.version)},\n""" +
      s"""  "app": ${WebOSApp.quote(id)}\n}\n"""
    val content = files(staged).map { case (path, f) => (app0 + path, Files.readAllBytes(f.toPath)) } :+
      (pkg + "packageinfo.json", packageinfo.getBytes(UTF_8))
    val directories = (List("usr/", "usr/palm/", "usr/palm/applications/", "usr/palm/packages/", app0, pkg) ++
      content.flatMap { case (path, _) => parents(path) }).distinct
    val data = tarGz(directories.sorted.map(d => (d, None)) ++ content.sortBy(_._1).map(c => (c._1, Some(c._2))), time)
    val control =
      s"""Package: $id
         |Version: ${app.version}
         |Section: misc
         |Priority: optional
         |Architecture: all
         |Installed-Size: ${content.map(_._2.length.toLong).sum}
         |Maintainer: N/A <nobody@example.com>
         |Description: This is a webOS application.
         |webOS-Package-Format-Version: 2
         |webOS-Packager-Version: x.y.x
         |""".stripMargin
    val controlTar = tarGz(List(("control", Some(control.getBytes(UTF_8)))), time)
    dir.mkdirs()
    val out = dir / s"${id}_${app.version}_all.ipk"
    val ar = new ByteArrayOutputStream
    ar.write("!<arch>\n".getBytes(US_ASCII))
    List("debian-binary" -> "2.0\n".getBytes(US_ASCII), "control.tar.gz" -> controlTar, "data.tar.gz" -> data)
      .foreach { case (name, bytes) =>
        ar.write((pad(name, 16) + pad(time.toString, 12) + pad("0", 6) + pad("0", 6) + pad("100644", 8) +
          pad(bytes.length.toString, 10) + "`\n").getBytes(US_ASCII))
        ar.write(bytes)
        if (bytes.length % 2 == 1) ar.write('\n')
      }
    Files.write(out.toPath, ar.toByteArray)
    out
  }

  // the files under `root`, by their path in it ("fonts/OpenSans.woff2"), in order
  private def files(root: File): List[(String, File)] = {
    val base = root.toPath
    val walk = Files.walk(base)
    try {
      val all = walk.iterator()
      var found = List.empty[(String, File)]
      while (all.hasNext) {
        val p = all.next()
        if (Files.isRegularFile(p)) found = (base.relativize(p).toString.replace(File.separatorChar, '/'), p.toFile) :: found
      }
      found.sortBy(_._1)
    }
    finally walk.close()
  }

  private def precompressed(path: String, root: File): Boolean =
    (path.endsWith(".br") || path.endsWith(".gz")) && new File(root, path.dropRight(3)).isFile

  // each directory `path` is in, as "a/", "a/b/"
  private def parents(path: String): List[String] = {
    val parts = path.split('/').dropRight(1)
    parts.indices.map(i => parts.take(i + 1).mkString("", "/", "/")).toList
  }

  /** A gzipped tar (POSIX ustar) of `entries`, each a directory (a path ending "/", no content) or a file. A path too
    * long for the format's name and prefix, or not ASCII (which webOS's installer does not take), fails. */
  def tarGz(entries: List[(String, Option[Array[Byte]])], time: Long): Array[Byte] = {
    val bytes = new ByteArrayOutputStream
    val gz = new GZIPOutputStream(bytes)
    entries.foreach { case (path, content) =>
      gz.write(header(path, content.map(_.length.toLong).getOrElse(0L), content.isEmpty, time))
      content.foreach { c =>
        gz.write(c)
        val over = c.length % 512
        if (over != 0) gz.write(new Array[Byte](512 - over))
      }
    }
    gz.write(new Array[Byte](1024))
    gz.close()
    bytes.toByteArray
  }

  private def header(path: String, size: Long, directory: Boolean, time: Long): Array[Byte] = {
    if (!path.forall(c => c >= ' ' && c < 127)) sys.error(s"WebOSPackage: $path: webOS takes only ASCII file names")
    val (prefix, name) =
      if (path.length <= 100) ("", path)
      else {
        // split at a "/" into a prefix of up to 155 and a name of up to 100
        val at = path.indices.find(i => path.charAt(i) == '/' && i <= 155 && i < path.length - 1 &&
          path.length - i - 1 <= 100).getOrElse(sys.error(s"WebOSPackage: $path is too long for the package"))
        (path.substring(0, at), path.substring(at + 1))
      }
    val h = new Array[Byte](512)
    def put(at: Int, length: Int, s: String): Unit = {
      val b = s.getBytes(US_ASCII)
      System.arraycopy(b, 0, h, at, math.min(b.length, length))
    }
    def octal(at: Int, length: Int, n: Long): Unit = put(at, length, ("%0" + (length - 1) + "o").format(n) + "\u0000")
    put(0, 100, name)
    octal(100, 8, if (directory) 493 else 420)
    octal(108, 8, 0)
    octal(116, 8, 0)
    octal(124, 12, size)
    octal(136, 12, time)
    put(148, 8, "        ")
    h(156) = (if (directory) '5' else '0').toByte
    put(257, 6, "ustar\u0000")
    put(263, 2, "00")
    put(265, 32, "root")
    put(297, 32, "root")
    put(345, 155, prefix)
    val sum = h.map(_ & 0xff).sum
    put(148, 8, "%06o".format(sum) + "\u0000 ")
    h
  }

  private def pad(s: String, n: Int): String = s.padTo(n, ' ')

  private def scaled(from: File, width: Int, height: Int, to: File): Unit = {
    import java.awt.RenderingHints
    import java.awt.image.BufferedImage
    val source = javax.imageio.ImageIO.read(from)
    if (source == null) sys.error(s"WebOSPackage: $from is not a picture")
    val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.drawImage(source, 0, 0, width, height, null)
    g.dispose()
    javax.imageio.ImageIO.write(image, "png", to)
  }

  private def delete(f: File): Unit = {
    if (f.isDirectory) Option(f.listFiles).foreach(_.foreach(delete))
    f.delete()
  }

  extension (dir: File) private def /(name: String): File = new File(dir, name)
}

package everyui.sbt

import java.io.{File, OutputStream}
import java.nio.file.{Files, StandardCopyOption}
import java.security.MessageDigest
import java.util.zip.{ZipEntry, ZipOutputStream}

/** A Roku channel split into what its package ships and a remote bundle it fetches the rest from (`RokuRemote` says
  * which), and the package held to its budget. Plain Scala, for an sbt build to compile in; the sbt-everyui plugin's
  * `RokuChannel.remote` is this over an assembled channel.
  *
  * The bundle is a directory of the remote files named by the SHA-256 of their bytes (`<hash>.<ext>`), with
  * `everyui-assets.json`, the table the channel carries too (`pkg:/everyui-assets.json`): each remote path's hash,
  * extension and size, a font's stand-in, a sound's compressed form, the base it is fetched from and what is fetched
  * at start. Named by their contents, the files of every release can be hosted side by side in one place.
  *
  * {{{
  *   val files = RokuAssets.bundle(log, warn, out, RokuRemote("https://cdn.example.com/roku"), bundleDir)
  *   RokuAssets.overBudget(out, RokuRemote.budget).foreach(sys.error)
  * }}} */
object RokuAssets {
  /** The channel's table of its remote files, at the root of its package and of the bundle. */
  val tableName: String = "everyui-assets.json"

  /** Whether `path` matches `pattern`: `**` any run of characters, `*` any run without `/`, `?` one character. */
  def glob(pattern: String, path: String): Boolean = {
    val regex = new StringBuilder
    var i = 0
    while (i < pattern.length) {
      val c = pattern.charAt(i)
      if (c == '*' && i + 1 < pattern.length && pattern.charAt(i + 1) == '*') {
        // `**/` also matches nothing at all, as `images/**/x.png` takes images/x.png
        if (i + 2 < pattern.length && pattern.charAt(i + 2) == '/') {
          regex.append("(?:.*/)?")
          i += 3
        } else {
          regex.append(".*")
          i += 2
        }
      } else {
        if (c == '*') regex.append("[^/]*")
        else if (c == '?') regex.append("[^/]")
        else regex.append(java.util.regex.Pattern.quote(c.toString))
        i += 1
      }
    }
    path.matches(regex.toString)
  }

  /** Every file under `root`, by its path in the package (`/`-separated), sorted. */
  def files(root: File): Seq[String] = {
    val base = root.toPath
    if (!root.isDirectory) Nil
    else {
      val stream = Files.walk(base)
      try {
        val all = scala.collection.mutable.ArrayBuffer.empty[String]
        stream.filter(p => Files.isRegularFile(p)).forEach(p => all += base.relativize(p).toString.replace('\\', '/'))
        all.toSeq.sorted
      } finally stream.close()
    }
  }

  /** What a channel needs to start, which never goes remote: its manifest, code and table, its icons and splash
    * screens (which the device reads from the package before anything runs), and a test sideload's flags (`<name>.txt`
    * at the root). */
  def kept(path: String): Boolean =
    path == "manifest" || path == tableName || path.startsWith("source/") || path.startsWith("components/") ||
      glob("images/icon*", path) || glob("images/splash*", path) || (!path.contains('/') && path.endsWith(".txt"))

  /** Whether `path` is a translation into one of `languages`: a file under `l10n/` whose name, its extension off, is
    * the code or ends `_<code>` (`app_en.arb`, `app_zh_Hant.arb` for "zh_Hant"). */
  def language(path: String, languages: Seq[String]): Boolean = path.startsWith("l10n/") && {
    val name = path.substring(path.lastIndexOf('/') + 1)
    val base = if (name.contains('.')) name.substring(0, name.indexOf('.')) else name
    languages.exists(code => base == code || base.endsWith("_" + code))
  }

  /** Whether `path` goes to the bundle under `settings`. */
  def remote(path: String, settings: RokuRemote): Boolean =
    settings.base.nonEmpty && !kept(path) && settings.remote.exists(glob(_, path)) &&
      !settings.local.exists(glob(_, path)) && !language(path, settings.languages) &&
      !(path.startsWith("fonts/") && settings.coreFonts.contains(path.substring("fonts/".length)))

  /** The extension a file is hosted under: its last, or `9.png` for a 9-patch (SceneGraph stretches a picture as one
    * by its name), lower-cased. */
  def ext(name: String): String = {
    val lower = name.substring(name.lastIndexOf('/') + 1).toLowerCase
    if (lower.endsWith(".9.png")) "9.png"
    else if (lower.contains('.')) lower.substring(lower.lastIndexOf('.') + 1)
    else "bin"
  }

  /** The SHA-256 of `file`'s bytes, as lower-case hex. */
  def sha256(file: File): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(file.toPath)
    try {
      val buffer = new Array[Byte](65536)
      var n = in.read(buffer)
      while (n > 0) {
        digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    } finally in.close()
    digest.digest().map(b => f"${b & 0xff}%02x").mkString
  }

  /** A face's weight by the style in its file name (`Roboto-SemiBold.ttf` 600), 400 where it names none. */
  def weightOf(name: String): Int = {
    val style = name.substring(name.lastIndexOf('/') + 1).toLowerCase
    val weights = Seq("extralight" -> 200, "ultralight" -> 200, "semibold" -> 600, "demibold" -> 600,
      "extrabold" -> 800, "ultrabold" -> 800, "thin" -> 100, "light" -> 300, "medium" -> 500, "bold" -> 700,
      "black" -> 900, "heavy" -> 900)
    weights.collectFirst { case (word, weight) if style.contains(word) => weight }.getOrElse(400)
  }

  // a font file's family: its name before the first '-' (`Roboto` of Roboto-Bold.ttf, `RobotoMono` of
  // RobotoMono-Regular.ttf)
  private def family(name: String): String = {
    val file = name.substring(name.lastIndexOf('/') + 1)
    val dash = file.indexOf('-')
    if (dash > 0) file.substring(0, dash) else file.takeWhile(_ != '.')
  }

  /** The face `font` (a path under `fonts/`) is drawn in until it arrives: of the `core` faces the package ships, one
    * of its family at the nearest weight; else the plainest that is not icons (a script's face, a family not shipped);
    * "" where there is none. */
  def standIn(font: String, core: Seq[String]): String = {
    val text = core.filterNot(c => family(c).toLowerCase.contains("icons"))
    val kin = text.filter(c => family(c) == family(font))
    val from = if (kin.nonEmpty) kin else text
    if (from.isEmpty) ""
    else {
      val weight = if (kin.nonEmpty) weightOf(font) else 400
      from.minBy(c => (math.abs(weightOf(c) - weight), weightOf(c), c))
    }
  }

  /** Whether ffmpeg is on the build machine's path. */
  def ffmpeg: Boolean =
    scala.util.Try(new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true)
      .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0).getOrElse(false)

  /** `wav` as `format` ("mp3": LAME at a variable rate near 130 kbit/s; "m4a": AAC at 128 kbit/s) in `out`, by ffmpeg;
    * whether it was made. */
  def transcode(wav: File, out: File, format: String): Boolean = {
    val codec = format match {
      case "mp3" => Seq("-c:a", "libmp3lame", "-q:a", "4")
      case "m4a" => Seq("-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart")
      case other => throw new IllegalArgumentException(s"RokuRemote.transcode: $other is not mp3 or m4a")
    }
    val command = Seq("ffmpeg", "-y", "-loglevel", "error", "-i", wav.getPath, "-vn") ++ codec :+ out.getPath
    val process = new ProcessBuilder(command*).inheritIO().start()
    process.waitFor() == 0 && out.isFile && out.length > 0
  }

  /** Move the files of the channel in `root` that `settings` sends remote into `out` (emptied first), each named by
    * its hash, a WAV with its compressed form beside it where `settings.transcode` asks and `transcoder` (ffmpeg's
    * `transcode` by default; None where there is no ffmpeg) can make it; and write the table to both. Nothing moves,
    * and no table is written, where `settings.base` is empty. The files moved, by path. A `base` that is not https
    * (or http, with `allowHttp`) is an error, as is a missing transcoder where `settings.needTranscoder`. */
  def bundle(log: String => Unit, warn: String => Unit, root: File, settings: RokuRemote, out: File,
             transcoder: Option[(File, File, String) => Boolean] = if (ffmpeg) Some(transcode) else None)
  : Seq[RokuRemoteFile] = {
    if (settings.base.isEmpty) Nil
    else {
      val lower = settings.base.toLowerCase
      if (!lower.startsWith("https://") && !(settings.allowHttp && lower.startsWith("http://")))
        throw new IllegalArgumentException(s"RokuRemote: the base ${settings.base} is not https " +
          "(a Roku fetches its assets over https; allowHttp is for a test against a local server)")
      settings.transcode.foreach { format =>
        if (format != "mp3" && format != "m4a")
          throw new IllegalArgumentException(s"RokuRemote: transcode to $format: only mp3 and m4a")
      }
      val paths = files(root)
      val going = paths.filter(remote(_, settings))
      val wavs = going.filter(p => p.startsWith("sounds/") && p.toLowerCase.endsWith(".wav"))
      val making = settings.transcode.filter(_ => wavs.nonEmpty)
      if (making.nonEmpty && transcoder.isEmpty) {
        val why = s"no ffmpeg to give the ${wavs.size} remote WAVs a compressed form (${making.get})"
        if (settings.needTranscoder) throw new IllegalStateException(s"RokuRemote: $why; install ffmpeg")
        else warn(s"RokuRemote: $why: they go as WAVs alone")
      }
      deleteTree(out)
      out.mkdirs()
      // the core faces the package keeps, which a remote one is drawn in until it arrives
      val core = paths.filter(p => p.startsWith("fonts/") && !going.contains(p))
      val moved = going.map { path =>
        val file = new File(root, path)
        val hash = sha256(file)
        val kind = ext(path)
        val target = new File(out, hash + "." + kind)
        if (!target.exists) Files.copy(file.toPath, target.toPath)
        val media = for {
          format <- making if kind == "wav"
          make <- transcoder
          made <- {
            val tmp = new File(out, hash + ".transcoding." + format)
            if (make(file, tmp, format)) {
              val mediaHash = sha256(tmp)
              val named = new File(out, mediaHash + "." + format)
              Files.move(tmp.toPath, named.toPath, StandardCopyOption.REPLACE_EXISTING)
              Some(RokuRemoteFile(path, mediaHash, format, named.length))
            } else {
              tmp.delete()
              warn(s"RokuRemote: ffmpeg could not make $path a $format: it goes as a WAV alone")
              None
            }
          }
        } yield made
        val font = if (path.startsWith("fonts/")) standIn(path, core) else ""
        Files.delete(file.toPath)
        RokuRemoteFile(path, hash, kind, target.length, font, media)
      }
      pruneEmpty(root)
      val written = table(moved, settings)
      Files.write(new File(root, tableName).toPath, written.getBytes("UTF-8"))
      Files.write(new File(out, tableName).toPath, written.getBytes("UTF-8"))
      log(s"RokuRemote: ${moved.size} files (${megabytes(moved.map(_.size).sum)}) to the bundle in $out, " +
        s"${moved.count(_.media.nonEmpty)} with a compressed form, for ${settings.base}")
      moved
    }
  }

  /** The table the channel reads (`pkg:/everyui-assets.json`): `base`, whether it may be http (`http`), what is
    * fetched at start (`prefetch`), and `files`, each remote path's `h`ash, `e`xtension and `s`ize, a font's stand-in
    * (`f`) and a sound's compressed form (`mh`, `me`, `ms`). */
  def table(moved: Seq[RokuRemoteFile], settings: RokuRemote): String = {
    def quote(s: String): String = {
      val out = new StringBuilder("\"")
      s.foreach {
        case '"' => out.append("\\\"")
        case '\\' => out.append("\\\\")
        case c if c < ' ' => out.append(f"\\u${c.toInt}%04x")
        case c => out.append(c)
      }
      out.append('"').toString
    }
    val prefetch = moved.map(_.path)
      .filter(p => settings.prefetch.exists(glob(_, p)) && !p.startsWith("fonts/fallback-"))
    val entries = moved.map { f =>
      val fields = Seq("\"h\":" + quote(f.hash), "\"e\":" + quote(f.ext), "\"s\":" + f.size) ++
        (if (f.standIn.nonEmpty) Seq("\"f\":" + quote(f.standIn)) else Nil) ++
        f.media.toSeq.flatMap(m => Seq("\"mh\":" + quote(m.hash), "\"me\":" + quote(m.ext), "\"ms\":" + m.size))
      quote(f.path) + ":{" + fields.mkString(",") + "}"
    }
    "{\"base\":" + quote(settings.base.stripSuffix("/")) + ",\"http\":" + settings.allowHttp +
      ",\"prefetch\":[" + prefetch.map(quote).mkString(",") + "]" +
      ",\"files\":{\n" + entries.mkString(",\n") + "\n}}\n"
  }

  /** The package the channel in `root` makes, zipped as a sideload or the packager zips it: its size, and each file's
    * size in it, by path. */
  def packageSize(root: File): (Long, Seq[(String, Long)]) = {
    var count = 0L
    val counting = new OutputStream {
      override def write(b: Int): Unit = count += 1
      override def write(b: Array[Byte], off: Int, len: Int): Unit = count += len
    }
    val zip = new ZipOutputStream(counting)
    val sizes = files(root).map { path =>
      val entry = new ZipEntry(path)
      zip.putNextEntry(entry)
      Files.copy(new File(root, path).toPath, zip)
      zip.closeEntry()
      path -> entry.getCompressedSize
    }
    zip.close()
    (count, sizes)
  }

  /** What makes the package in `root` up, largest first: each directory's files together (`images/rr`, `fonts`,
    * `components`), with how many there are. */
  def contributors(sizes: Seq[(String, Long)]): Seq[(String, Long, Int)] =
    sizes.groupBy { case (path, _) => if (path.contains('/')) path.substring(0, path.lastIndexOf('/')) else "(root)" }
      .toSeq.map { case (dir, fs) => (dir, fs.map(_._2).sum, fs.size) }.sortBy(c => (-c._2, c._1))

  /** Why the package in `root` is over `budget` bytes zipped, listing what is biggest in it, or None where it is
    * within it. */
  def overBudget(root: File, budget: Long): Option[String] = {
    val (total, sizes) = packageSize(root)
    if (total <= budget) None
    else {
      val parts = contributors(sizes).take(8).map { case (dir, size, n) =>
        s"$dir ${megabytes(size)} ($n file${if (n == 1) "" else "s"})" }
      val biggest = sizes.sortBy(s => (-s._2, s._1)).take(8).map { case (path, size) => s"$path ${megabytes(size)}" }
      Some(s"the Roku package is ${megabytes(total)} zipped, over its budget of ${megabytes(budget)} (certification " +
        s"3.7): its biggest parts ${parts.mkString(", ")}; its biggest files ${biggest.mkString(", ")}. Send more to " +
        "the remote bundle (RokuRemote's remote, local, coreFonts, languages), or ship less")
    }
  }

  /** `bytes` in megabytes (of 1,000,000), to two places. */
  def megabytes(bytes: Long): String = f"${bytes / 1000000.0}%.2f MB"

  private def deleteTree(dir: File): Unit = if (dir.exists) {
    Option(dir.listFiles).toSeq.flatten.foreach(f => if (f.isDirectory) deleteTree(f) else f.delete())
    dir.delete()
  }

  // directories left empty by what went, taken away (not `root` itself)
  private def pruneEmpty(root: File): Unit =
    Option(root.listFiles).toSeq.flatten.filter(_.isDirectory).foreach { d =>
      pruneEmpty(d)
      if (Option(d.listFiles).forall(_.isEmpty)) d.delete()
    }
}

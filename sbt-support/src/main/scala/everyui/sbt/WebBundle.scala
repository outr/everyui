package everyui.sbt

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.regex.{Matcher, Pattern}

/** Seals a staged EveryUI web bundle for a CDN, so everything but index.html can be cached for good and a new release
  * is never served stale. The last step of the bundle task, once everything is in `out`:
  *
  * {{{
  *   everyuiWebScripts.value.writeTo(out, baseDirectory.value / "index.html")
  *   IO.copyDirectory(everyuiWebFonts.value, out / "fonts")
  *   IO.copyDirectory(everyuiWebGuests.value, out)
  *   // l10n, images, WebApp.write, …
  *   WebBundle.seal(out)
  * }}}
  *
  * Every directory at the top of `out` (fonts/, l10n/, images/, everyui-hls/…, the WebAssembly build's wasm-…/) moves
  * under `a/<hash>/`, the hash being of all the files in them, and the page names that one base for the runtime
  * (`<meta name="everyui-assets">`, which everyui-web's WebAssets reads for everything it loads by path). The scripts
  * the staging wrote are named by a hash of their bytes: main.js as `main.<hash>.js`, everyui-loader.js and webapp.js
  * the same. What names them (index.html, the loader, manifest.webmanifest) is rewritten to the new places: a path
  * straight after a quote or `url(`, with `./` or `/` before it or nothing. index.html and the other files at the top
  * (sw.js, manifest.webmanifest, a favicon, robots.txt) stay where they are, to be revalidated; so do dot directories
  * (.well-known) and those named in `keep`. The service worker's cache (WebApp) is named by the bundle's hash.
  * `web-assets.json`, written beside index.html, lists what is immutable, for the host's `Cache-Control`.
  *
  * The page's files are then found relative to its base, so a single-page host that answers a deep route with
  * index.html needs `<base href="/">` in it (as main.js already did). Answers a hash naming the bundle. */
object WebBundle {
  /** The directory the bundle's files go under, by their hash. */
  val assets: String = "a"
  /** The file listing what may be cached for good. */
  val listing: String = "web-assets.json"
  /** The scripts the staging writes, in the order they are named: the loader names main.js. */
  val scripts: Seq[String] = Seq("main.js", WasmLoader.fileName, "webapp.js")

  def seal(out: File, keep: Seq[String] = Nil): String = {
    val page = new File(out, "index.html")
    if (!page.isFile) sys.error(s"$out has no index.html to seal")
    if (new File(out, assets).exists) sys.error(s"$out has an $assets/ already: sealed twice, or a directory of that name")
    val dirs = out.listFiles().filter(d => d.isDirectory && !d.getName.startsWith(".") && !keep.contains(d.getName))
      .map(_.getName).sorted.toSeq
    val files = dirs.flatMap(d => walk(new File(out, d), d)).sortBy(_._1)
    val version = WebScripts.hash(files.map { case (path, f) => path + " " + WebScripts.hash(read(f)) }.mkString("\n").getBytes(UTF_8))
    val base = s"$assets/$version/"
    val target = new File(out, base)
    target.mkdirs()
    dirs.foreach(d => Files.move(new File(out, d).toPath, new File(target, d).toPath))

    val intoBase = if (dirs.isEmpty) identity[String] else {
      val pattern = Pattern.compile("""(["'(]\s*(?:\./|/)?)(""" + dirs.map(Pattern.quote).mkString("|") + ")/")
      (text: String) => pattern.matcher(text).replaceAll("$1" + Matcher.quoteReplacement(base) + "$2/")
    }
    // each script named by its bytes, once what it names has its new name: main.js as it was linked, the loader
    // naming main.js and the WebAssembly build in their new places
    val renamed = scripts.foldLeft(Seq.empty[(String, String)]) { (done, name) =>
      val file = new File(out, name)
      if (!file.isFile) done else {
        val bytes = if (name == WasmLoader.fileName) rename(intoBase(new String(read(file), UTF_8)), done).getBytes(UTF_8) else read(file)
        val hashed = name.stripSuffix(".js") + "." + WebScripts.hash(bytes) + ".js"
        Files.write(new File(out, hashed).toPath, bytes)
        Files.delete(file.toPath)
        done :+ (name -> hashed)
      }
    }
    val bundle = WebScripts.hash((version +: renamed.map(_._2)).mkString(" ").getBytes(UTF_8))

    val meta = s"""  <meta name="everyui-assets" content="$base" data-dirs="${dirs.mkString(" ")}">\n"""
    val html = rename(intoBase(new String(read(page), UTF_8)), renamed)
    write(page, html.replaceFirst("(?i)</head>", Matcher.quoteReplacement(meta + "</head>")))
    val manifest = new File(out, "manifest.webmanifest")
    if (manifest.isFile) write(manifest, intoBase(new String(read(manifest), UTF_8)))
    val worker = new File(out, "sw.js")
    if (worker.isFile) write(worker, new String(read(worker), UTF_8).replaceFirst("const CACHE = 'everyui-[^']*';", s"const CACHE = 'everyui-$bundle';"))

    val immutable = base +: renamed.map(_._2)
    val revalidate = out.listFiles().map(f => if (f.isDirectory) f.getName + "/" else f.getName)
      .filterNot(n => n == assets + "/" || immutable.contains(n)).sorted.toSeq :+ listing
    def list(names: Seq[String]): String = names.map(n => "    \"" + n + "\"").mkString("[\n", ",\n", "\n  ]")
    write(new File(out, listing), s"""{
  "version": "$bundle",
  "immutable": ${list(immutable)},
  "revalidate": ${list(revalidate.distinct)}
}
""")
    bundle
  }

  // the scripts in `renamed` named by their new names: a quote or `url(` and `./` or `/` before, and any query after
  private def rename(text: String, renamed: Seq[(String, String)]): String = renamed.foldLeft(text) { case (t, (from, to)) =>
    Pattern.compile("""(["'(]\s*(?:\./|/)?)""" + Pattern.quote(from) + """(?:\?[^"')\s]*)?(?=["')\s])""").matcher(t)
      .replaceAll("$1" + Matcher.quoteReplacement(to))
  }

  private def walk(dir: File, path: String): Seq[(String, File)] =
    dir.listFiles().toSeq.flatMap(f => if (f.isDirectory) walk(f, path + "/" + f.getName) else Seq((path + "/" + f.getName) -> f))

  private def read(f: File): Array[Byte] = Files.readAllBytes(f.toPath)

  private def write(f: File, text: String): Unit = Files.write(f.toPath, text.getBytes(UTF_8))
}

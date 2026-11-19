package everyui.sbt

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.regex.Matcher

/** An EveryUI web app's faces, as its build declares them (`everyuiWebFaces`), shipped the way a browser fetches
  * least: each as WOFF2, cut to what the app draws (WebGlyphs), with the page's `@font-face` rules and preloads written
  * for them. `fonts` is the directory of the files they name (`everyuiWebFonts`: EveryUI's and `everyuiAppFonts`). From
  * the app's bundle task, once the scripts are written (their icons are read out of them):
  *
  * {{{
  *   everyuiWebScripts.value.writeTo(out, baseDirectory.value / "index.html")
  *   everyuiWebFaceFiles.value.writeTo(out)
  * }}}
  *
  * It writes `out/fonts/<file>.woff2` (`<file>.<slice>.woff2` for a face in slices) and, before index.html's
  * `</head>`, a `<style id="everyui-fonts">` of the rules, a preload for each face at the start (its Latin slice, for
  * one in slices), and `<meta name="everyui-fonts">`, which tells the runtime which faces to wait for before the first
  * frame and which the build declared. A face nothing reaches (an icon face no script names an icon of) is left out.
  * An icon face whose every icon the scripts list (`MaterialIcon.all`) ships the rest of them apart, never preloaded:
  * the browser fetches that file the first time it draws one of them.
  * The page must not declare a family the build does: it would be fetched twice. Plain Scala, for an sbt build to
  * compile in (CONSUMING.md). */
final case class WebFaces(fonts: File, faces: Seq[WebFace]) {
  def writeTo(out: File): Seq[File] = {
    val page = new File(out, "index.html")
    if (!page.isFile) sys.error(s"$out has no index.html to write the faces into")
    val html = new String(Files.readAllBytes(page.toPath), UTF_8)
    val families = faces.map(_.family).toSet
    val twice = WebFaces.pageFamilies(html).filter(families)
    if (twice.nonEmpty) sys.error(s"$page declares @font-face for ${twice.mkString(", ")}, which the build now writes " +
      "(everyuiWebFaces): take those rules out of the page")
    lazy val scripts = WebFaces.scan(out)
    val dir = new File(out, "fonts")
    dir.mkdirs()
    val shipped = Parallel.map(faces)(face => face -> WebFaces.files(fonts, face, scripts._1, scripts._2))
    val written = shipped.flatMap { case (_, files) =>
      files.map { f =>
        val file = new File(dir, f.name)
        Files.write(file.toPath, f.bytes)
        file
      }
    }
    val drawn = shipped.filter(_._2.nonEmpty)
    val rules = drawn.flatMap { case (face, files) =>
      files.map { f =>
        s"""  @font-face { font-family: '${face.family}'; font-style: ${if (face.italic) "italic" else "normal"}; """ +
          s"""font-weight: ${face.weight}; src: url(fonts/${f.name}) format('woff2');""" +
          f.range.map(r => s" unicode-range: $r;").getOrElse("") +
          (if (f.slice.contains(WebFaces.rest)) " font-display: block;" else "") + " }"
      }
    }
    val preloads = drawn.filter(_._1.start).flatMap { case (_, files) =>
      files.find(f => f.slice.isEmpty || f.slice.contains("latin")).map(f =>
        s"""  <link rel="preload" href="fonts/${f.name}" as="font" type="font/woff2" crossorigin>""")
    }
    val (start, later) = drawn.map(_._1).partition(_.start)
    val meta = s"""  <meta name="everyui-fonts" content="${start.map(_.css).mkString("; ")}" """ +
      s"""data-later="${later.map(_.css).mkString("; ")}">"""
    val block = (preloads ++ Seq("  <style id=\"everyui-fonts\">") ++ rules ++ Seq("  </style>", meta))
      .mkString("", "\n", "\n")
    Files.write(page.toPath, html.replaceFirst("(?i)</head>", Matcher.quoteReplacement(block + "</head>")).getBytes(UTF_8))
    written
  }
}

object WebFaces {
  /** One file of a face: its name in fonts/, its bytes, and the characters it holds where it is one slice of several. */
  final case class FaceFile(name: String, bytes: Array[Byte], slice: Option[String], range: Option[String])

  /** The slices a face in slices is cut into, by the characters they hold (Google Fonts' groups, but for the combining
    * marks, which are in every slice); "other" holds the rest. */
  val slices: Seq[(String, Seq[Range])] = Seq(
    "latin" -> Seq(0x0 to 0xFF, 0x131 to 0x131, 0x152 to 0x153, 0x2BB to 0x2BC, 0x2C6 to 0x2C6, 0x2DA to 0x2DA,
      0x2DC to 0x2DC, 0x2000 to 0x206F, 0x20AC to 0x20AC, 0x2122 to 0x2122, 0x2191 to 0x2191, 0x2193 to 0x2193,
      0x2212 to 0x2212, 0x2215 to 0x2215, 0xFEFF to 0xFEFF, 0xFFFD to 0xFFFD),
    "latin-ext" -> Seq(0x100 to 0x2BA, 0x2BD to 0x2C5, 0x2C7 to 0x2CC, 0x2CE to 0x2D7, 0x2DD to 0x2FF, 0x1D00 to 0x1DBF,
      0x1E00 to 0x1E9F, 0x1EF2 to 0x1EFF, 0x2020 to 0x2020, 0x20A0 to 0x20AB, 0x20AD to 0x20C0, 0x2113 to 0x2113,
      0x2C60 to 0x2C7F, 0xA720 to 0xA7FF),
    "vietnamese" -> Seq(0x1EA0 to 0x1EF9, 0x20AB to 0x20AB),
    "cyrillic" -> Seq(0x400 to 0x52F, 0x1C80 to 0x1C8A, 0x20B4 to 0x20B4, 0x2116 to 0x2116, 0x2DE0 to 0x2DFF,
      0xA640 to 0xA69F, 0xFE2E to 0xFE2F),
    "greek" -> Seq(0x370 to 0x3FF, 0x1F00 to 0x1FFF))

  /** The slice of an icon face that holds the icons the scripts only list (`listed`, less those they name), shipped
    * apart and never preloaded: `<file>.rest.woff2`, its rule naming exactly those characters (`unicode-range`), so
    * the browser fetches it the first time one of them is drawn, and draws nothing for them till then
    * (`font-display: block`) rather than a fallback's boxes. */
  val rest = "rest"

  /** The files `face` ships as, out of `fonts`: none where it is an icon face none of the `reached` characters is in.
    * An icon face ships the icons `reached` in one file, and those only `listed` (MaterialIcon.all) in another, the
    * `rest`. */
  def files(fonts: File, face: WebFace, reached: => Set[Int], listed: => Set[Int] = Set.empty): Seq[FaceFile] = {
    val source = new File(fonts, face.file)
    if (!source.isFile) sys.error(s"everyuiWebFaces: ${face.file} (${face.css}) is not among the fonts in $fonts")
    val font = Sfnt.read(source)
    val base = face.file.replaceAll("\\.[^.]*$", "")
    val glyphs = if (font.tables.contains("glyf")) face.glyphs else WebGlyphs.All
    glyphs match {
      case WebGlyphs.All => Seq(FaceFile(base + ".woff2", Woff2(font), None, None))
      case WebGlyphs.Reached =>
        val icons = font.characters.keySet.filter(reached)
        val others = font.characters.keySet.filter(listed) -- icons
        Seq(icons -> None, others -> Some(rest)).filter(_._1.nonEmpty).map { case (cs, slice) =>
          FaceFile(base + slice.fold("")("." + _) + ".woff2", Woff2(FontSubset.compact(font, cs)), slice,
            slice.map(_ => unicodeRange(cs)))
        }
      case WebGlyphs.Scripts =>
        // the combining marks go in every slice, so a letter and the accent after it are drawn from one file, the
        // accent placed on the letter (a browser keeps them together where one file has both)
        val marks = font.characters.keySet.filter(c => c >= 0x300 && c <= 0x36F)
        val characters = font.characters.keySet -- marks
        val named = slices.map { case (name, ranges) => name -> characters.filter(c => ranges.exists(_.contains(c))) }
        // a character two slices name is in the first
        val each = named.foldLeft(Seq.empty[(String, Set[Int])]) { case (done, (name, cs)) =>
          done :+ (name -> (cs -- done.flatMap(_._2)))
        }
        val all = each :+ ("other" -> (characters -- each.flatMap(_._2)))
        all.filter(_._2.nonEmpty).map { case (name, letters) =>
          val cs = letters ++ marks
          FaceFile(s"$base.$name.woff2", Woff2(FontSubset.retained(font, cs)), Some(name), Some(unicodeRange(cs)))
        }
    }
  }

  /** CSS's `unicode-range` for `characters`: `U+20-7E, U+A0-FF`. */
  def unicodeRange(characters: Set[Int]): String = {
    val runs = characters.toSeq.sorted.foldLeft(List.empty[(Int, Int)]) {
      case ((s, e) :: rest, c) if c == e + 1 => (s, c) :: rest
      case (done, c) => (c, c) :: done
    }.reverse
    runs.map((s, e) => if (s == e) f"U+$s%X" else f"U+$s%X-$e%X").mkString(", ")
  }

  /** The families of the `@font-face` rules `html` writes itself. */
  def pageFamilies(html: String): Set[String] =
    """(?s)@font-face\s*\{[^}]*?font-family\s*:\s*['"]?([^'";}]+)""".r.findAllMatchIn(html).map(_.group(1).trim).toSet

  /** The characters in Unicode's private use areas the scripts in `out` name (main.js, the loader, and the WebAssembly
    * build's directory): a string in main.js is the character or its `\u` escape, in main.wasm the name of an import
    * (Scala.js imports each string constant). Those the scripts only list, as an icon table's `name=glyph`
    * (MaterialIcon.all), are not among them, but `listed`. */
  def reached(out: File): Set[Int] = scan(out)._1

  /** The characters in Unicode's private use areas the scripts in `out` list as a table of icons, each written
    * `name=glyph`, as MaterialIcon.all's table has every icon of the face. */
  def listed(out: File): Set[Int] = scan(out)._2

  // what the scripts in `out` name and what they list, read once for both
  private[sbt] def scan(out: File): (Set[Int], Set[Int]) = {
    val top = Option(out.listFiles()).getOrElse(Array.empty[File]).toSeq
    val scripts = top.filter(f => f.isFile && f.getName.endsWith(".js")) ++
      top.filter(d => d.isDirectory && d.getName.startsWith("wasm-")).flatMap(d => d.listFiles().toSeq.filter(_.isFile))
    if (!scripts.exists(_.getName.startsWith("main")))
      sys.error(s"$out has no main.js or WebAssembly build: write the scripts before the faces")
    val each = scripts.map { f =>
      val bytes = Files.readAllBytes(f.toPath)
      if (f.getName.endsWith(".wasm")) wasmCharacters(bytes) else jsCharacters(new String(bytes, UTF_8))
    }
    (each.flatMap(_._1).filter(privateUse).toSet, each.flatMap(_._2).filter(privateUse).toSet)
  }

  def privateUse(c: Int): Boolean = (c >= 0xE000 && c <= 0xF8FF) || c >= 0xF0000

  // a character as a script's string writes it: its `\uXXXX` escape (a surrogate pair of them too) or `\u{X}` one
  private val escape =
    """\\u(?:\{([0-9a-fA-F]+)\}|([dD][89abAB][0-9a-fA-F]{2})\\u([dD][c-fC-F][0-9a-fA-F]{2})|([0-9a-fA-F]{4}))""".r
  // an icon's glyph so, or as itself
  private val glyph = """\\u\{[0-9a-fA-F]+\}|\\u[dD][89abAB][0-9a-fA-F]{2}\\u[dD][c-fC-F][0-9a-fA-F]{2}|""" +
    """\\u[0-9a-fA-F]{4}|[\x{E000}-\x{F8FF}\x{F0000}-\x{10FFFF}]"""
  // an icon table's entry, `name=glyph`, wherever a string has one (Closure joins the table's lines into one string,
  // by `;`, and writes `=` as `\x3d`); and one as a word on its own
  private val entry = ("(?<![a-z0-9_])[a-z0-9_]+(?:=|\\\\x3[dD]|\\\\u003[dD])(" + glyph + ")").r
  private val word = ("[a-z0-9_]+=(?:" + glyph + ")").r

  // the characters a script names (every one, its escapes read as the characters they are, but its icon tables') and
  // those its icon tables list
  private def jsCharacters(text: String): (Iterator[Int], Iterator[Int]) =
    (characters(entry.replaceAllIn(text, "")), entry.findAllMatchIn(text).flatMap(m => characters(m.group(1))))

  private def characters(text: String): Iterator[Int] = {
    def hex(s: String): Int = Integer.parseInt(s, 16)
    text.codePoints().toArray.iterator ++ escape.findAllMatchIn(text).map { m =>
      if (m.group(1) != null) hex(m.group(1))
      else if (m.group(2) != null) Character.toCodePoint(hex(m.group(2)).toChar, hex(m.group(3)).toChar)
      else hex(m.group(4))
    }
  }

  // the same of a WebAssembly module's string constants, word by word
  private def wasmCharacters(b: Array[Byte]): (Iterator[Int], Iterator[Int]) = {
    val (listing, naming) = wasmStrings(b).flatMap(_.split(" ")).partition(word.matches)
    (naming.iterator.flatMap(_.codePoints().toArray), listing.iterator.flatMap(_.codePoints().toArray))
  }

  // a WebAssembly module's string constants: Scala.js imports each from the module "", named by the string itself
  private def wasmStrings(b: Array[Byte]): Seq[String] = {
    var p = 8
    def leb(): Long = {
      var result = 0L
      var shift = 0
      var byte = 0
      while ({ byte = b(p) & 0xFF; p += 1; result |= (byte & 0x7FL) << shift; shift += 7; (byte & 0x80) != 0 }) ()
      result
    }
    def name(): String = {
      val length = leb().toInt
      p += length
      new String(b, p - length, length, UTF_8)
    }
    def valueType(): Unit = { val t = b(p) & 0xFF; p += 1; if (t == 0x63 || t == 0x64) leb() }
    def limits(): Unit = { val flags = b(p); p += 1; leb(); if ((flags & 1) != 0) leb() }
    val found = Seq.newBuilder[String]
    while (p < b.length) {
      val id = b(p) & 0xFF
      p += 1
      val end = leb().toInt + p
      if (id == 2) {
        (0 until leb().toInt).foreach { _ =>
          val module = name()
          val field = name()
          if (module.isEmpty) found += field
          val kind = b(p)
          p += 1
          kind match {
            case 0 => leb()
            case 1 => valueType(); limits()
            case 2 => limits()
            case 3 => valueType(); p += 1
            case _ => p += 1; leb()
          }
        }
      }
      p = end
    }
    found.result()
  }
}

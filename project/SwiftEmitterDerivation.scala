import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern

/** The Swift emitter is the Roku emitter with the syntax swapped: the same lowering decisions, printed as Swift.
  * Rather than a second 2,000-line file that drifts, `SwiftEmitter.scala` is DERIVED at build time from
  * `RokuEmitter.scala` by the replacements in `compiler/swift-derivation.txt` — a source generator on the
  * compiler project. Every replacement must match exactly the number of times the table says, so a change to the
  * Roku emitter that moves a template fails the build instead of leaving a stale twin.
  *
  * The table is a plain record file: `@@ kind` lines start a record (`header-pattern`, `header-replacement`,
  * `old` / `old N`, `new`, `output`); a record's text runs to the next `@@` line, without its trailing newline.
  */
object SwiftEmitterDerivation {
  private def read(f: File): String = new String(Files.readAllBytes(f.toPath), StandardCharsets.UTF_8)

  def parse(table: String): Vector[(String, String)] = {
    val records = Vector.newBuilder[(String, String)]
    var kind: Option[String] = None
    val text = new StringBuilder
    def flush(): Unit = kind.foreach { k => records += k -> text.toString.stripSuffix("\n"); text.clear() }
    table.linesIterator.foreach { line =>
      if (line.startsWith("@@ ")) { flush(); kind = Some(line.drop(3).trim) }
      else if (kind.isDefined) text.append(line).append('\n')
    }
    flush()
    records.result()
  }

  def derive(roku: File, table: File, out: File): File = {
    val records = parse(read(table))
    def one(kind: String): String = records.collectFirst { case (`kind`, t) => t }
      .getOrElse(sys.error(s"swift-derivation: no `$kind` record"))
    var s = read(roku)

    val header = Pattern.compile(one("header-pattern"), Pattern.DOTALL).matcher(s)
    if (!header.find()) sys.error("swift-derivation: the header pattern no longer matches RokuEmitter.scala")
    s = s.substring(0, header.start()) + one("header-replacement") + s.substring(header.end())

    val pairs = records.sliding(2).collect { case Vector((oldKind, o), ("new", n)) if oldKind.startsWith("old") =>
      val count = oldKind.stripPrefix("old").trim
      (o, n, if (count.isEmpty) 1 else count.toInt)
    }.toList
    pairs.zipWithIndex.foreach { case ((o, n, expected), i) =>
      val found = s.split(Pattern.quote(o), -1).length - 1
      if (found != expected)
        sys.error(s"swift-derivation: replacement ${i + 1} expected $expected match(es), found $found:\n  ${o.linesIterator.next().take(100)}")
      s = s.replace(o, n)
    }

    val start = s.indexOf("  // ── output ─────")
    if (start < 0) sys.error("swift-derivation: no output section in RokuEmitter.scala")
    s = s.substring(0, start) + one("output") + "\n"

    out.getParentFile.mkdirs()
    Files.write(out.toPath, s.getBytes(StandardCharsets.UTF_8))
    out
  }
}

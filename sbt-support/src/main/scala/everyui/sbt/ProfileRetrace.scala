package everyui.sbt

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** Profile rules read from a device, named as the shrinker named them (`HSPLa/b/c;->d(La/b/e;)V`), given their
  * names in the source again through R8's mapping (`mapping.txt`, kept gzipped by `AndroidApk.keepMapping`), so
  * they can be kept with the app and applied to the next build, which R8 renames again. A method whose code R8
  * folded into another is taken as the one it was folded into (the outermost of an inlined group); the classes R8
  * made itself (lambdas, outlines) are left out, since R8 makes them again for the methods that need them. */
final class ProfileRetrace(mapping: Seq[String]) {
  // a method line of the mapping: its class (renamed), line range, source signature, renamed name, and the signature
  // R8 gave it where it changed its parameters
  private case class Line(renamedClass: String, sourceClass: String, range: String, returns: String, name: String,
                          params: String, renamed: String, residual: Option[String])

  private val (classes, synthesized, lines) = {
    val classes = Map.newBuilder[String, String]
    val synthesized = Set.newBuilder[String]
    val lines = scala.collection.mutable.ArrayBuffer.empty[Line]
    var current: Option[(String, String)] = None
    var last = -1
    val ClassLine = """(\S+) -> (\S+):""".r
    val MethodLine = """\s+(?:(\d+:\d+):)?(\S+) ([^\s(]+)\(([^)]*)\)(?::\d+(?::\d+)?)? -> (\S+)""".r
    mapping.foreach {
      case ClassLine(source, renamed) =>
        classes += renamed -> source
        current = Some(source -> renamed)
        last = -1
      // the class's own note (unindented): R8 made it
      case line if line.startsWith("# {") && line.contains("com.android.tools.r8.synthesized") =>
        current.foreach(c => synthesized += c._2)
      case line if line.trim.startsWith("# {") && line.contains("com.android.tools.r8.residualsignature") && last >= 0 =>
        lines(last) = lines(last).copy(residual = Some(line.substring(line.indexOf("\"signature\":\"") + 13).takeWhile(_ != '"')))
      case line if line.trim.startsWith("#") => ()
      case MethodLine(range, returns, name, params, renamed) =>
        current.foreach { case (source, obfuscated) =>
          lines += Line(obfuscated, source, Option(range).getOrElse(""), returns, name, params, renamed, None)
          last = lines.size - 1
        }
      case _ => last = -1
    }
    (classes.result(), synthesized.result(), lines.toSeq)
  }
  private val renamedOf = classes.map(_.swap)

  // a source type (`int`, `java.lang.String[]`) as a descriptor, renamed or not
  private def descriptor(t: String, rename: Boolean): String = t match {
    case _ if t.endsWith("[]") => "[" + descriptor(t.dropRight(2), rename)
    case "void" => "V"
    case "boolean" => "Z"
    case "byte" => "B"
    case "char" => "C"
    case "short" => "S"
    case "int" => "I"
    case "long" => "J"
    case "float" => "F"
    case "double" => "D"
    case _ => "L" + (if (rename) renamedOf.getOrElse(t, t) else t).replace('.', '/') + ";"
  }

  private def signature(l: Line, rename: Boolean): String =
    l.params.split(',').filter(_.nonEmpty).map(descriptor(_, rename)).mkString("(", "", ")") + descriptor(l.returns, rename)

  // (renamed class, renamed name, renamed signature) to the source's (class, name, signature). Of the lines of one
  // range (an inlined group: the innermost first) the outermost is the method itself, less a frame R8 made itself
  // (`_BOUNDARY.m`); its signature as renamed is the one R8 noted where it changed it, on whichever range it did.
  private val byRenamed: Map[(String, String, String), (String, String, String)] = {
    val groups = lines.foldLeft(Vector.empty[Vector[Line]]) { (all, l) =>
      all.lastOption match {
        case Some(g) if l.range.nonEmpty && g.head.range == l.range && g.head.renamed == l.renamed &&
          g.head.renamedClass == l.renamedClass => all.init :+ (g :+ l)
        case _ => all :+ Vector(l)
      }
    }
    // each group's method, and the line whose signature is the renamed one: a frame R8 made, where there is one
    val outer = groups.flatMap(g => g.reverse.find(!_.name.startsWith("_BOUNDARY.")).map(_ -> g.last))
    val residuals = lines.flatMap(l => l.residual.map((l.renamedClass, l.renamed, l.name, l.params) -> _)).toMap
    outer.map { case (l, residualFrame) =>
      // a method moved here from another class names it: `void other.Owner.m()`
      val (owner, name) =
        if (l.name.contains('.')) (l.name.substring(0, l.name.lastIndexOf('.')), l.name.substring(l.name.lastIndexOf('.') + 1))
        else (l.sourceClass, l.name)
      val renamedSignature = residuals.getOrElse((l.renamedClass, l.renamed, l.name, l.params), signature(residualFrame, rename = true))
      (l.renamedClass, l.renamed, renamedSignature) -> (owner, name, signature(l, rename = false))
    }.reverse.toMap   // the first wins where two give the same key
  }

  private def internal(dotted: String): String = dotted.replace('.', '/')

  // a class as in the source, or None where R8 made it
  private def sourceClass(renamed: String): Option[String] =
    if (synthesized(renamed)) None else Some(classes.getOrElse(renamed, renamed))

  // a descriptor's class names, back to the source's
  private def sourceTypes(desc: String): String =
    """L([^;]+);""".r.replaceAllIn(desc, m => java.util.regex.Matcher.quoteReplacement(
      "L" + internal(classes.getOrElse(m.group(1).replace('/', '.'), m.group(1).replace('/', '.'))) + ";"))

  private val Rule = """([HSP]*)L([^;]+);(?:->([^(]+)(\([^)]*\)[^+\s]+))?(?:\+.*)?""".r

  /** A rule (`HSPLa/b;->c(I)V`, `La/b;`) named as in the source, or None where it names a class R8 made. */
  def apply(rule: String): Option[String] = rule.trim match {
    case Rule(flags, owner, null, _) => sourceClass(owner.replace('/', '.')).map(c => s"${flags}L${internal(c)};")
    case Rule(flags, owner, name, desc) =>
      val renamed = owner.replace('/', '.')
      sourceClass(renamed).map { c =>
        byRenamed.get((renamed, name, desc)) match {
          case Some((o, n, d)) => s"${flags}L${internal(o)};->$n$d"
          case None => s"${flags}L${internal(c)};->$name${sourceTypes(desc)}"
        }
      }
    case _ => None
  }

  /** Every rule of `rules` retraced, each once, those naming classes R8 made left out. */
  def all(rules: Seq[String]): Seq[String] = rules.filter(_.trim.nonEmpty).flatMap(apply).distinct
}

object ProfileRetrace {
  /** The mapping in `file`, gzipped (`.gz`, as `AndroidApk.keepMapping` keeps it) or not. */
  def apply(file: File): ProfileRetrace = {
    val bytes = Files.readAllBytes(file.toPath)
    val text = if (file.getName.endsWith(".gz"))
      new String(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes)).readAllBytes(), UTF_8)
    else new String(bytes, UTF_8)
    new ProfileRetrace(text.linesIterator.toSeq)
  }
}

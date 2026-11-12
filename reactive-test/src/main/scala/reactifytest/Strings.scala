package reactifytest

/** The emitter's String.split as the JVM splits: a String separator is a pattern unless it has none of a pattern's
  * marks (or escapes one character), a Char is itself, empty pieces before and between separators are kept and those
  * at the end dropped unless a limit says otherwise. */
object Strings {
  import ReactifySuite.check

  private def show(parts: Array[String]): String = parts.length.toString + parts.map(p => "<" + p + ">").mkString

  def splits(ok: Box, total: Box): Unit = {
    check(ok, total, "emit.split.char", show("a/b//c/".split('/')), "4<a><b><><c>")
    check(ok, total, "emit.split.char.dot", show("a.b".split('.')), "2<a><b>")
    check(ok, total, "emit.split.char.lead", show("?q=1".split('?')), "2<><q=1>")
    check(ok, total, "emit.split.plain", show("a, b, c".split(", ")), "3<a><b><c>")
    check(ok, total, "emit.split.none", show("abc".split(",")), "1<abc>")
    check(ok, total, "emit.split.empty.text", show("".split(",")), "1<>")
    check(ok, total, "emit.split.only.seps", show(",,".split(",")), "0")
    check(ok, total, "emit.split.class", show("en-US_x".split("[-_]")), "3<en><US><x>")
    check(ok, total, "emit.split.class.none", show("en".split("[-_]")), "1<en>")
    check(ok, total, "emit.split.spaces", show(" 1  2,3 ".split("[\\s,]+")), "4<><1><2><3>")
    check(ok, total, "emit.split.escaped", show("a|b|c".split("\\|")), "3<a><b><c>")
    check(ok, total, "emit.split.escaped.dot", show("1.2.3".split("\\.")), "3<1><2><3>")
    check(ok, total, "emit.split.digits", show("12:30pm".split("[^0-9]+")), "2<12><30>")
    check(ok, total, "emit.split.dot.pattern", show("a.b".split(".")), "0")
    check(ok, total, "emit.split.chars", show("abc".split("")), "3<a><b><c>")
    check(ok, total, "emit.split.lookahead", show("oneTwoThree".split("(?=[A-Z])")), "3<one><Two><Three>")
    check(ok, total, "emit.split.limit.keep", show("a\n\nb\n\n".split("\n", -1)), "5<a><><b><><>")
    check(ok, total, "emit.split.limit.two", show("k=v=w".split("=", 2)), "2<k><v=w>")
    check(ok, total, "emit.split.limit.one", show("k=v".split("=", 1)), "1<k=v>")
    check(ok, total, "emit.split.limit.pattern", show("a1b22c".split("[0-9]+", 2)), "2<a><b22c>")
    check(ok, total, "emit.split.limit.zero", show("a,b,,".split(",", 0)), "2<a><b>")
    check(ok, total, "emit.reverse", "abc".reverse + "|" + "".reverse, "cba|")
  }
}

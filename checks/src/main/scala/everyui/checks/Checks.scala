package everyui.checks

import java.io.{File, FileOutputStream}
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.collection.mutable
import scala.sys.process.*
import scala.util.matching.Regex

/** The helpers behind tools/check.sh and the device scripts — one small CLI, run from its staged classpath
  * (`sbt checks/checksClasspath` writes `checks/out/classpath.txt`).
  *
  * {{{
  *   extract-tree LOG OUT                    the last frozen frame's component tree from a Chrome, JavaFX, logcat or Roku log
  *   compare-tree GOLDEN ACTUAL [--tol PX]   structure must match exactly; positions and sizes within a tolerance
  *   faces FONTS_DIR FACES_SCALA             the shared face table repeats the bundled fonts' own metrics
  *   fps LOG [--median-min F] [--min F]      the live frame rate after the last app start meets a floor
  *   motion A.png B.png [--min-fraction F]   two screenshots of a running app differ: at least F of the pixels moved (0.01)
  *   gaps CHANNEL_COMPONENTS_DIR             the static gap meter over an emitted Roku channel (0 on every line is clean)
  *   suite-lines RAW OUT                     the latest run of the reactify suite (`RX` lines) from a Roku console capture
  *   android-wait SERIAL OUT                 wait for the app on an Android device to log its frozen frame, then save its log
  *   roku-install CHANNEL [--add NAME=TEXT]  sideload a channel directory (extra files for test runs)
  *   roku-console SECONDS OUT                capture the Roku debug console
  * }}}
  *
  * Roku commands read ROKU_IP (default 192.168.1.112) and ROKU_PASS.
  */
object Checks:
  private val Num = "[-+0-9.eE]+"
  private val TreeLog = new Regex(s"\\[ui\\] tree ( *)(.+?) x=($Num) y=($Num) w=($Num) h=($Num) rot=($Num)")
  private val TreeGolden = new Regex(s"^tree ( *)(.+?) x=($Num) y=($Num) w=($Num) h=($Num) rot=($Num)$$")

  def main(args: Array[String]): Unit =
    val code = args.toList match
      case "extract-tree" :: log :: out :: Nil => extractTree(log, out)
      case "compare-tree" :: golden :: actual :: rest => compareTree(golden, actual, opt(rest, "--tol", 1.0), opt(rest, "--rot-tol", 0.001))
      case "faces" :: fonts :: table :: Nil => faces(fonts, table)
      case "fps" :: log :: rest =>
        fps(log, opt(rest, "--median-min", 59.5), opt(rest, "--min", 58.5), opt(rest, "--skip", 3.0).toInt, opt(rest, "--samples-min", 20.0).toInt)
      case "gaps" :: dir :: Nil => gaps(dir)
      case "motion" :: a :: b :: rest => motion(a, b, opt(rest, "--min-fraction", 0.01))
      case "suite-lines" :: raw :: out :: Nil => suiteLines(raw, out)
      case "android-wait" :: serial :: out :: rest => androidWait(serial, out, opt(rest, "--timeout", 90.0))
      case "roku-install" :: channel :: rest => rokuInstall(channel, rest.sliding(2, 2).collect { case List("--add", spec) => spec }.toList)
      case "roku-console" :: seconds :: out :: Nil => rokuConsole(seconds.toDouble, out)
      case _ =>
        System.err.println("usage: see everyui.checks.Checks")
        2
    sys.exit(code)

  private def opt(rest: List[String], name: String, default: Double): Double =
    rest.sliding(2).collectFirst { case List(`name`, v) => v.toDouble }.getOrElse(default)

  private def fail(message: String): Int =
    println(message)
    1

  private def lines(path: String): Vector[String] =
    new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).linesIterator.toVector

  private def write(path: String, text: String): Unit =
    Files.write(Paths.get(path), text.getBytes(StandardCharsets.UTF_8))

  // ── frames ──────────────────────────────────────────────────────────────────

  def extractTree(log: String, out: String): Int =
    val all = lines(log)
    val starts = all.zipWithIndex.collect { case (l, i) if l.contains("[ui] frozen at") => i }
    if starts.isEmpty then return fail(s"no frozen frame in $log (did the app start, and was it frozen?)")
    val tree = mutable.ListBuffer[String]()
    var done = false
    for l <- all.drop(starts.last + 1) if !done do
      TreeLog.findFirstMatchIn(l) match
        case Some(m) => tree += s"tree ${m.group(1)}${m.group(2)} x=${m.group(3)} y=${m.group(4)} w=${m.group(5)} h=${m.group(6)} rot=${m.group(7)}"
        case None => if tree.nonEmpty then done = true
    if tree.isEmpty then return fail("the frozen frame logged no component tree")
    write(out, tree.mkString("", "\n", "\n"))
    println(s"${tree.size} components in the frozen frame")
    0

  private def parseTree(path: String): Vector[(String, Map[String, Double])] =
    lines(path).zipWithIndex.filter(_._1.trim.nonEmpty).map { case (l, n) =>
      TreeGolden.findFirstMatchIn(l) match
        case Some(m) =>
          (m.group(1) + m.group(2), Map("x" -> m.group(3).toDouble, "y" -> m.group(4).toDouble, "w" -> m.group(5).toDouble,
            "h" -> m.group(6).toDouble, "rot" -> m.group(7).toDouble))
        case None =>
          println(s"$path:${n + 1}: not a tree line: $l"); sys.exit(1)
    }

  def compareTree(goldenPath: String, actualPath: String, tol: Double, rotTol: Double): Int =
    val golden = parseTree(goldenPath)
    val actual = parseTree(actualPath)
    val problems = mutable.ListBuffer[String]()
    if golden.size != actual.size then problems += s"component count: golden ${golden.size}, actual ${actual.size}"
    var worst = 0.0
    for (((gk, gv), (ak, av)), i) <- golden.zip(actual).zipWithIndex do
      if gk != ak then problems += s"component ${i + 1}: golden '${gk.trim}', actual '${ak.trim}'"
      else
        for key <- Seq("x", "y", "w", "h", "rot") do
          val delta = math.abs(gv(key) - av(key))
          if key != "rot" then worst = math.max(worst, delta)
          if delta > (if key == "rot" then rotTol else tol) then
            problems += f"${gk.trim}: $key golden ${gv(key)}%g, actual ${av(key)}%g (off by $delta%g)"
    if problems.nonEmpty then
      println(s"${problems.size} difference(s) from the golden:")
      problems.take(25).foreach(p => println("  " + p))
      if problems.size > 25 then println(s"  ... and ${problems.size - 25} more")
      return 1
    println(f"${golden.size} components match the golden (largest position/size difference $worst%.3g px)")
    0

  // ── fonts ───────────────────────────────────────────────────────────────────

  /** unitsPerEm, ascender and descender (positive), as a browser reads them: OS/2's typographic metrics when the
    * font sets USE_TYPO_METRICS, otherwise hhea's. */
  private def fontMetrics(path: File): (Double, Double, Double) =
    val d = java.nio.ByteBuffer.wrap(Files.readAllBytes(path.toPath))
    val n = d.getShort(4) & 0xffff
    val tables = (0 until n).map { i =>
      val at = 12 + 16 * i
      new String(Array(d.get(at), d.get(at + 1), d.get(at + 2), d.get(at + 3)), "ISO-8859-1") -> d.getInt(at + 8)
    }.toMap
    val units = d.getShort(tables("head") + 18) & 0xffff
    var ascender = d.getShort(tables("hhea") + 4).toInt
    var descender = d.getShort(tables("hhea") + 6).toInt
    tables.get("OS/2").foreach { os2 =>
      if ((d.getShort(os2 + 62) & 0xffff) & 128) != 0 then
        ascender = d.getShort(os2 + 68); descender = d.getShort(os2 + 70)
    }
    (units.toDouble, ascender.toDouble, -descender.toDouble)

  def faces(fontsDir: String, tablePath: String): Int =
    val source = new String(Files.readAllBytes(Paths.get(tablePath)), StandardCharsets.UTF_8)
    val row = """new Face\("([^"]+)", (\d+), "([^"]+)", ([0-9.]+), ([0-9.]+), ([0-9.]+)\)""".r
    val rows = row.findAllMatchIn(source).map(m => (m.group(1), m.group(2), m.group(3), m.group(4).toDouble, m.group(5).toDouble, m.group(6).toDouble)).toList
    if rows.isEmpty then return fail(s"no `new Face(...)` rows in $tablePath")
    val problems = mutable.ListBuffer[String]()
    for (family, weight, name, units, asc, desc) <- rows do
      val f = new File(fontsDir, name)
      if !f.exists then problems += s"$name: not in $fontsDir"
      else
        val actual = fontMetrics(f)
        if actual != (units, asc, desc) then problems += s"$name: the table has unitsPerEm/ascender/descender ($units, $asc, $desc), the font has $actual"
        else println(f"$family $weight ($name): unitsPerEm ${actual._1}%g, ascender ${actual._2}%g, descender ${actual._3}%g")
    val listed = rows.map(_._3).toSet
    for f <- Option(new File(fontsDir).list()).getOrElse(Array.empty[String]).sorted if f.endsWith(".ttf") && !listed(f) do
      problems += s"$f is bundled but not in the table"
    if problems.nonEmpty then fail(problems.mkString("\n")) else 0

  // ── frame rate ──────────────────────────────────────────────────────────────

  def fps(log: String, medianMin: Double, min: Double, skip: Int, samplesMin: Int): Int =
    val all = lines(log)
    val starts = all.zipWithIndex.collect { case (l, i) if l.contains("[ui] Image loaded") => i }
    if starts.isEmpty then return fail("the app did not start (no '[ui] Image loaded' in the console)")
    val fpsLine = new Regex(s"\\[ui\\] fps=($Num)")
    val samples = all.drop(starts.last).flatMap(l => fpsLine.findFirstMatchIn(l).map(_.group(1).toDouble)).drop(skip)
    if samples.size < samplesMin then return fail(s"only ${samples.size} frame-rate samples (need $samplesMin)")
    val sorted = samples.sorted
    val median = if sorted.size % 2 == 1 then sorted(sorted.size / 2) else (sorted(sorted.size / 2 - 1) + sorted(sorted.size / 2)) / 2
    val low = sorted.head
    println(f"${samples.size} samples: median $median%.1f fps, minimum $low%.1f fps (floors: median $medianMin%.1f, minimum $min%.1f)")
    if median < medianMin || low < min then 1 else 0

  // ── motion ──────────────────────────────────────────────────────────────────

  /** The animation is visibly running: two screenshots taken a moment apart differ in at least `minFraction` of
    * their pixels (a channel off by more than 12/255 counts). A frozen frame, a black screen, or a tick loop
    * that reports 60 fps while nothing on screen moves all fail here. */
  def motion(aPath: String, bPath: String, minFraction: Double): Int =
    val a = javax.imageio.ImageIO.read(new File(aPath))
    val b = javax.imageio.ImageIO.read(new File(bPath))
    if a == null || b == null then return fail("could not decode a screenshot")
    if a.getWidth != b.getWidth || a.getHeight != b.getHeight then return fail(s"screenshots differ in size: ${a.getWidth}x${a.getHeight} vs ${b.getWidth}x${b.getHeight}")
    var changed = 0L
    var y = 0
    while y < a.getHeight do
      var x = 0
      while x < a.getWidth do
        val p = a.getRGB(x, y); val q = b.getRGB(x, y)
        if math.abs(((p >> 16) & 0xff) - ((q >> 16) & 0xff)) > 12 || math.abs(((p >> 8) & 0xff) - ((q >> 8) & 0xff)) > 12 || math.abs((p & 0xff) - (q & 0xff)) > 12 then changed += 1
        x += 1
      y += 1
    val fraction = changed.toDouble / (a.getWidth.toLong * a.getHeight)
    println(f"${fraction * 100}%.2f%% of pixels changed between the two screenshots (floor ${minFraction * 100}%.2f%%)")
    if fraction < minFraction then 1 else 0

  // ── the gap meter ───────────────────────────────────────────────────────────

  /** Counts what the emitter could not translate, or translated into something that cannot run: `RtTodo` markers,
    * functions called but never defined, two functions with one name, and dotted calls that are neither a
    * BrightScript interface method nor a vtable slot. It proves the output self-consistent, not correct. */
  def gaps(dir: String): Int =
    def read(name: String) = new String(Files.readAllBytes(Paths.get(dir, name)), StandardCharsets.UTF_8)
    val raw = read("App.brs")
    val extra = new File(dir, "UniversalRuntime.brs")
    val runtime = read("ScalaRuntime.brs") + (if extra.exists then read("UniversalRuntime.brs") else "")
    // code only: a string literal such as "Var(" is not a call
    val code = raw.replaceAll("\"(?:[^\"\\n]|\"\")*\"", "\"\"").replaceAll("'[^\\n]*", "")
    val defRe = """(?m)^\s*(?:function|sub)\s+([A-Za-z_$][A-Za-z0-9_$]*)""".r
    val defsApp = defRe.findAllMatchIn(code).map(_.group(1)).toList
    val defined = defsApp.map(_.toLowerCase).toSet ++ defRe.findAllMatchIn(runtime).map(_.group(1).toLowerCase)
    val todos = """RtTodo\("([A-Za-z ]+)"\)""".r.findAllMatchIn(raw).map(_.group(1)).toList.groupBy(identity).view.mapValues(_.size).toMap
    val keywords = Set("if", "while", "for", "return", "print", "and", "or", "not", "then", "end", "sub", "function",
      "invalid", "true", "false", "stop", "each", "to", "step", "as", "throw", "createobject", "getglobalaa",
      "getinterface", "type", "box", "chr", "val", "str", "int", "rnd", "sqr", "abs", "sgn", "cint", "fix", "log",
      "exp", "atn", "sin", "cos", "tan", "asc", "left", "right", "mid", "len", "instr", "string", "stringi", "ucase",
      "lcase", "strtoi", "formatjson", "parsejson", "wait", "eval", "line_num")
    val called = """(?<![.\w$])([A-Za-z_$][A-Za-z0-9_$]*)\s*\(""".r.findAllMatchIn(code).map(_.group(1)).toSet
    val dangling = called.filter(n => !defined(n.toLowerCase) && !keywords(n.toLowerCase)).toList.sorted
    val dupes = defsApp.groupBy(_.toLowerCase).collect { case (n, xs) if xs.size > 1 => n }.toList.sorted
    val interfaceMethods = Set("count", "push", "pop", "peek", "delete", "clear", "append", "join", "split", "trim",
      "tostr", "toint", "left", "right", "mid", "instr", "len", "replace", "startswith", "endswith", "tokenize", "keys",
      "items", "lookup", "doesexist", "setmodecasesensitive", "sortby", "sort", "reverse", "getfield", "setfield",
      "observefield", "callfunc", "findnode", "appendchild", "removechild", "getchild", "getchildcount", "createchild",
      "update", "isempty", "addreplace", "getentityencode", "subtype", "setfocus", "initialvalue", "closure_call")
    val slots = """(?m)^\s*self\.([A-Za-z_][A-Za-z0-9_]*)\s*=\s*[A-Za-z_][A-Za-z0-9_]*\s*$""".r.findAllMatchIn(code).map(_.group(1).toLowerCase).toSet
    // `ga.<Module>.<member>(…)` is a facade namespace the runtime installs; `cl.fn(cl.env, …)` a closure called in place
    val facadeFree = code
      .replaceAll("""\bga\.[A-Za-z_][A-Za-z0-9_]*\.[A-Za-z_][A-Za-z0-9_]*\s*\(""", "ga_facade(")
      .replaceAll("""\.fn\(([A-Za-z_][A-Za-z0-9_.]*)\.env\s*,""", ".closure_call($1.env,")
    val unknown = """\.([A-Za-z_][A-Za-z0-9_]*)\s*\(""".r.findAllMatchIn(facadeFree).map(_.group(1)).toSet
      .filter(n => !interfaceMethods(n.toLowerCase) && !slots(n.toLowerCase)).toList.sorted
    def show(xs: List[String]) = if xs.isEmpty then "" else xs.mkString("[", ", ", "]")
    println(s"functions emitted : ${defsApp.size}")
    println(s"vtable slots      : ${slots.size}")
    println(s"RtTodo markers  : ${todos.values.sum}  ${if todos.isEmpty then "" else todos.toString}")
    println(s"dangling symbols  : ${dangling.size}  ${show(dangling)}")
    println(s"duplicate defs    : ${dupes.size}  ${show(dupes)}")
    println(s"unknown methods   : ${unknown.size}  ${show(unknown)}")
    val total = todos.values.sum + dangling.size + dupes.size + unknown.size
    println(s"TOTAL GAPS        : $total")
    if total == 0 then 0 else 1

  // ── Roku ────────────────────────────────────────────────────────────────────

  /** The latest run of the suite in a console capture (the console replays history on connect): its `RX` lines. */
  def suiteLines(raw: String, out: String): Int =
    val all = lines(raw)
    val starts = all.zipWithIndex.collect { case (l, i) if l.startsWith("RX ") && l.contains("var.initial") => i }
    val rx = (if starts.isEmpty then Vector.empty else all.drop(starts.last)).filter(_.startsWith("RX ")).map(_.stripTrailing)
    write(out, if rx.isEmpty then "" else rx.mkString("", "\n", "\n"))
    println(s"captured ${rx.size} suite lines")
    0

  private def rokuTarget(): (String, String) =
    val ip = sys.env.getOrElse("ROKU_IP", "192.168.1.112")
    val password = sys.env.getOrElse("ROKU_PASS", { println("set ROKU_PASS to the Roku developer password"); sys.exit(1) })
    (ip, password)

  def rokuInstall(channel: String, adds: List[String]): Int =
    val (ip, password) = rokuTarget()
    val work = Files.createTempDirectory("everyui-roku")
    val zip = work.resolve("channel.zip")
    val zout = new java.util.zip.ZipOutputStream(new FileOutputStream(zip.toFile))
    val root = Paths.get(channel)
    Files.walk(root).filter(Files.isRegularFile(_)).sorted().forEach { p =>
      zout.putNextEntry(new java.util.zip.ZipEntry(root.relativize(p).toString.replace('\\', '/')))
      zout.write(Files.readAllBytes(p)); zout.closeEntry()
    }
    for spec <- adds do
      val (name, text) = spec.span(_ != '=')
      zout.putNextEntry(new java.util.zip.ZipEntry(name)); zout.write(text.drop(1).getBytes(StandardCharsets.UTF_8)); zout.closeEntry()
    zout.close()
    // curl: the Roku's digest auth closes the connection on a client that sends the body before the challenge
    val html = Seq("curl", "-s", "--digest", "-u", s"rokudev:$password", "-F", "mysubmit=Install", "-F", s"archive=@$zip", s"http://$ip/plugin_install").!!
    val status = "Install Success|Identical|Failed".r.findFirstIn(html)
    println(s"install: ${status.getOrElse("no status in the response")}")
    status match
      case None | Some("Failed") => fail("the Roku rejected the package (a BrightScript compile error?)")
      case Some("Identical") =>
        // the same package is already installed: restart it so this run's output is fresh
        for path <- Seq("/keypress/Home", "/launch/dev") do
          Seq("curl", "-s", "-o", "/dev/null", "-X", "POST", s"http://$ip:8060$path").!
          Thread.sleep(2000)
        0
      case _ => 0

  def rokuConsole(seconds: Double, out: String): Int =
    val (ip, _) = rokuTarget()
    val socket = new Socket(ip, 8085)
    socket.setSoTimeout(1000)
    val in = socket.getInputStream
    val captured = new java.io.ByteArrayOutputStream()
    val buffer = new Array[Byte](4096)
    val end = System.currentTimeMillis() + (seconds * 1000).toLong
    var open = true
    while open && System.currentTimeMillis() < end do
      try
        val n = in.read(buffer)
        if n < 0 then open = false else captured.write(buffer, 0, n)
      catch case _: java.net.SocketTimeoutException => ()
    socket.close()
    Files.write(Paths.get(out), captured.toByteArray)
    println(s"captured ${captured.size} bytes of console output")
    0

  // ── Android ─────────────────────────────────────────────────────────────────

  def androidWait(serial: String, out: String, timeout: Double): Int =
    def log() = Seq("adb", "-s", serial, "logcat", "-d", "-s", "everyui:I", "AndroidRuntime:E").!!
    val end = System.currentTimeMillis() + (timeout * 1000).toLong
    var last: Option[String] = None
    while System.currentTimeMillis() < end do
      val text = log()
      if text.contains("FATAL EXCEPTION") then
        write(out, text)
        return fail("the app crashed:\n" + text.takeRight(4000))
      if text.contains("[ui] frozen at") then
        if last.contains(text) then   // nothing new since the last look: the whole tree is in
          write(out, text)
          println("the frozen frame is logged")
          return 0
        last = Some(text)
      Thread.sleep(1000)
    val text = log()
    write(out, text)
    fail(s"no frozen frame within ${timeout.toInt}s; the app's log:\n" + text.takeRight(4000))

package everyui.log

import everyui.PlatformKind
import everyui.clipboard.Clipboard
import everyui.component.{Button, ButtonStyle, Container, TextView}
import everyui.component.types.{Semantics, WhiteSpace}
import everyui.event.{Key, KeyEvent}
import everyui.font.MaterialIcon
import everyui.list.{ListSource, VirtualList}
import everyui.menu.{Menu, MenuItem}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A log as a terminal shows it, natively: lines in their ANSI colours and styles (`AnsiParser`), in a monospaced
  * face, the newest at the bottom, any number of them scrolled as easily as a screenful (a `VirtualList` that makes
  * only the rows in view).
  *
  * Text comes in as it is written: `append` takes pieces of a stream (a line is shown once its newline comes),
  * `appendLine` a whole line, and `append(event)` a server's `LogEvent`, kept in its order and shown once however often
  * it comes (`LogFeed` streams them from a durable socket). It keeps the newest `maxLines`, letting the oldest go.
  * Each line can show its number, its time and its source (where the app or the event gives them); long lines scroll
  * sideways with the arrows, the wheel or a finger, or wrap (`wrap`).
  *
  * While the newest line is in view it stays there as lines arrive (`following`); scrolled up, a reader keeps their
  * place (the oldest going from above does not move it) and "Jump to latest" takes them back down.
  *
  * `search` marks its matches in the lines in view and counts them all (`matches`, `matchIndex`: "3 of 41"),
  * searching a stretch of lines at a time so a long log never holds the screen up; `nextMatch` and `previousMatch`
  * (Enter and Shift+Enter in `LogPanel`'s field, F3 and Shift+F3) go from one to the next, scrolled into view. `filter`
  * shows only the lines holding some words and of some levels (`hidden` counts the rest); a line's level is read from
  * its words (`level`, `LogClassifier` by default) or given, and with `continuations` a line naming none (a stack
  * trace's) takes the one before it.
  *
  * Lines are chosen as a list's rows are (a click, Shift and the primary modifier; the keys), and Ctrl or Command+C
  * copies their words, without escapes. The context menu copies the line under it, the lines chosen, or chooses them
  * all.
  *
  * {{{
  *   val log = new LogView(LogOptions(maxLines = 50000, lineNumbers = true))
  *   log.append(chunk)
  *   log.filter @= LogFilter(text = "timeout", levels = Set(LogLevel.Warn, LogLevel.Error))
  *   log.search @= "socket"
  * }}} */
class LogView(val options: LogOptions = LogOptions()) extends Container {
  override def kind: String = "LogView"

  val wrap: Var[Boolean] = own(Var(options.wrap))
  val lineNumbers: Var[Boolean] = own(Var(options.lineNumbers))
  val timestamps: Var[Boolean] = own(Var(options.timestamps))
  val sources: Var[Boolean] = own(Var(options.sources))
  val continuations: Var[Boolean] = own(Var(options.continuations))
  /** Whether it is kept at the newest line: it lets go as it is scrolled up, and takes hold again at the bottom. */
  val following: Var[Boolean] = own(Var(options.follow))
  val filter: Var[LogFilter] = own(Var(LogFilter()))
  /** What to look for: words, in any case unless `searchCase`, or a regular expression with `searchRegex`. */
  val search: Var[String] = own(Var(""))
  val searchCase: Var[Boolean] = own(Var(false))
  val searchRegex: Var[Boolean] = own(Var(false))
  /** A line's level from its words. A line given its level (an event's) keeps it. */
  val level: Var[String => Option[LogLevel]] = own(Var(LogClassifier.default))
  /** Its colours: following the theme's palette unless the app sets its own. */
  val theme: Var[LogTheme] = own(Var(LogTheme.of(Theme.palette())))
  theme := LogTheme.of(Theme.palette())
  /** How far the lines are scrolled sideways, in pixels, where they do not wrap. */
  val scrollX: Var[Double] = own(Var(0.0))

  /** How many lines it holds, and how many of them the filter hides. */
  val count: Var[Int] = own(Var(0))
  val hidden: Var[Int] = own(Var(0))
  /** How many matches the search has found, which of them it is on (1 the first, 0 none), whether it is still
    * looking, and whether its regular expression does not compile. */
  val matches: Var[Int] = own(Var(0))
  val matchIndex: Var[Int] = own(Var(0))
  val searching: Var[Boolean] = own(Var(false))
  val broken: Var[Boolean] = own(Var(false))
  /** How many lines have arrived below while it is not following. */
  val unseen: Var[Int] = own(Var(0))

  /** The words' size, and how tall a line is. */
  val points: Double = options.points
  val lineHeight: Double = math.round(points * 1.5).toDouble
  /** How wide a column is: the face is monospaced. Measured once the face has loaded. */
  val advance: Var[Double] = own(Var(points * 0.6))
  /** How far down its line a line's words start, to sit in the middle of it: measured with `advance`. */
  val textTop: Var[Double] = own(Var(points * 0.12))

  private val parser = new AnsiParser
  private val store = new LogStore(math.max(1, options.maxLines))
  private val shown = new LogShown
  private val finder = new LogFinder(shown, id => store.line(id))
  private var filtering = new LogMatcher("")
  private var finding = new LogMatcher("")
  // the level of the last line, for the next to follow; the longest line held; the newest seq shown
  private var lastLevel: Option[LogLevel] = None
  private var widest = 0
  private var newestSeq = 0L
  private var sourceWidth = 0
  private var numbered = 4
  // the columns lines wrap at, worked out again as their room changes
  private var cols = -1

  private val rows = new ListSource[LogLine] {
    val changed: Channel[Unit] = Channel[Unit]
    def count: Int = shown.size
    def at(index: Int): Option[LogLine] = if (index < 0 || index >= shown.size) None else store.line(shown.id(index))
    def keyAt(index: Int): String = shown.id(index).toString
    def indexOf(key: String): Int = LogView.idOf(key).map(id => shown.indexOf(id)).getOrElse(-1)
    def here: Vector[LogLine] = {
      var out = Vector.empty[LogLine]
      var i = 0
      while (i < shown.size) {
        at(i).foreach(l => out = out :+ l)
        i += 1
      }
      out
    }
    def showing(first: Int, last: Int): Unit = ()
    override def sized: Boolean = wrap()
    override def top(index: Int): Double = shown.rowsBefore(index, id => rowsOf(id)) * lineHeight
    override def indexAt(y: Double): Int = shown.indexAtRow((y / lineHeight).toInt, id => rowsOf(id))
  }

  val list: VirtualList[LogLine] = new VirtualList[LogLine](Var(Vector.empty), _.id.toString, _.text, lineHeight,
    source = Some(rows))(() => new LogRow(this))
  list.placeholder @= None
  list.semantics @= Semantics("list", label = LogWords.log)

  private val jump: Button = Button.bound(
    if (unseen() > 0) LogWords.newCount(unseen()) else LogWords.jumpToLatest,
    () => toLatest(), ButtonStyle.Filled, Some(MaterialIcon.ArrowDownward), 12.5)
  jump.visible := !following() && count() > 0
  jump.shadow @= Some(everyui.Shadow(0.0, 2.0, 8.0, everyui.Color.Black.withAlpha(0.3)))
  jump.position.left := (size.width() - jump.size.width()) / 2.0
  jump.position.top := size.height() - jump.size.height() - 12.0

  // a column's width, measured from a long run of the face's letters
  private val probe = new TextView {
    font.family @= LogView.face
    font.size @= points
    whiteSpace @= WhiteSpace.Pre
    content @= "0" * 64
    visible @= false
  }

  backgroundColor := theme().background
  list.size.width := size.width()
  list.size.height := size.height()
  size.width @= 640.0
  size.height @= 400.0
  children ++= List(probe, list, jump)

  // ---- what comes in ----

  /** A piece of a stream: the lines it ends are shown, and what follows its last newline waits for the rest. */
  def append(text: String): Unit = parser.lines(text).foreach(raw => toRead(() => read(raw)))

  /** A whole line (ending one the stream left open), with its time and source where the app has them, and its level
    * where the app knows it (else it is read from its words). */
  def appendLine(text: String, at: Long = 0L, level: Option[LogLevel] = None, source: String = ""): Unit =
    parser.lines(text + "\n").foreach(raw => toRead(() => {
      val l = parser.line(raw)
      add(LogLine(0L, l.text, l.spans, level, 0L, at, source, givenLevel = level.nonEmpty))
    }))

  /** A server's event, its level its own: where its `seq` is already shown it is not shown again, and one older than
    * the newest is put in its place among the rest. Text of more than one line is shown as that many, the first
    * carrying the seq. */
  def append(event: LogEvent): Unit = {
    if (event.seq > comingSeq) comingSeq = event.seq
    toRead(() => readEvent(event))
  }

  private def read(raw: String): Unit = {
    val l = parser.line(raw)
    add(LogLine(0L, l.text, l.spans))
  }

  private def readEvent(event: LogEvent): Unit = {
    val parsed = new AnsiParser().feed(event.text + "\n")
    val level = event.logLevel
    val lines = parsed.zipWithIndex.map { case (l, i) =>
      LogLine(0L, l.text, l.spans, level, if (i == 0) event.seq else -event.seq, event.at, event.source,
        givenLevel = level.nonEmpty)
    }
    if (event.seq <= 0L || event.seq > newestSeq) {
      if (event.seq > newestSeq) newestSeq = event.seq
      lines.foreach(add)
    } else place(event.seq, lines)
  }

  /** The newest seq it has: where a client resumes from (those still to be read among them). */
  def lastSeq: Long = math.max(newestSeq, comingSeq)

  /** A note that `n` lines were missed here (a reconnect could not have them back). */
  def missed(n: Long): Unit = if (n > 0L) toRead(() => add(LogLine(0L, LogWords.missed(n), marker = true)))

  /** A note that the server's log started again, and its numbering with it: seqs shown before no longer count. */
  def restarted(): Unit = {
    comingSeq = 0L
    toRead(() => {
      newestSeq = 0L
      add(LogLine(0L, LogWords.restarted, marker = true))
    })
  }

  /** The line a stream left open, shown as it is. */
  def flush(): Unit = parser.rest().foreach(raw => toRead(() => read(raw)))

  /** Every line let go, and anything held of one or still to be read. */
  def clear(): Unit = {
    while (comingFrom < comingTo) {
      coming(comingFrom) = null
      comingFrom += 1
    }
    comingFrom = 0
    comingTo = 0
    comingSeq = 0L
    store.clear()
    shown.clear()
    parser.reset()
    lastLevel = None
    widest = 0
    newestSeq = 0L
    list.selection.clear()
    scrollX @= 0.0
    unseen @= 0
    finder.restart(finding)
    changing()
  }

  private def add(line: LogLine): Unit = {
    changing()
    val (placed, gone) = store.add(classify(line, lastLevel))
    if (!placed.marker) lastLevel = placed.level
    if (gone.nonEmpty) dropFront()
    note(placed)
    if (passes(placed)) {
      shown.append(placed.id)
      finder.appended(placed)
      if (!following()) unseenLines += 1
    }
  }

  // an event's lines put in among the rest by their seq, unless that seq is already shown
  private def place(seq: Long, lines: List[LogLine]): Unit = {
    var i = store.size - 1
    while (i >= 0 && !(store(i).seq > 0L && store(i).seq <= seq)) i -= 1
    val known = i >= 0 && store(i).seq == seq
    // older than every line held, where older ones have gone: it went with them
    val gone = i < 0 && store.dropped > 0L
    if (!known && !gone) {
      changing()
      var at = i + 1
      if (i >= 0) while (at < store.size && store(at).seq == -store(i).seq) at += 1
      val before = if (at > 0) store(at - 1).level else None
      lines.foreach { l =>
        store.insert(at, classify(l, before)).foreach { case (placed, dropped) =>
          if (dropped.nonEmpty) {
            dropFront()
            at -= 1
          }
          note(placed)
          if (passes(placed)) shown.insert(placed.id)
          at += 1
        }
      }
      // what was searched has moved: searched again
      research()
    }
  }

  private def note(line: LogLine): Unit = {
    // a number of more digits widens the gutter
    if (lineNumbers() && digits != numbered) {
      numbered = digits
      reflow()
    }
    if (line.text.length > widest) widest = line.text.length
    if (math.min(16, line.source.length) > sourceWidth) {
      sourceWidth = math.min(16, line.source.length)
      reflow()
    }
  }

  private def classify(line: LogLine, before: Option[LogLevel]): LogLine =
    if (line.givenLevel || line.marker) line
    else {
      val own = level()(line.text)
      line.copy(level = if (own.isEmpty && continuations()) before else own)
    }

  private def passes(line: LogLine): Boolean = line.marker || {
    val f = filter()
    (f.levels.isEmpty || line.level.exists(f.levels.contains)) && (filtering.empty || filtering.matches(line.text))
  }

  // the oldest let go: from what shows and what the search found
  private def dropFront(): Unit = if (store.size > 0) {
    val first = store(0).id
    finder.dropped(shown.dropBefore(first), first)
  }

  // ---- what shows ----

  private var unseenLines = 0
  private var pending = false

  // the line at the top of the view, and where it is, kept as the lines change: the oldest going from above, or one
  // put in, must not move what a reader is reading
  private var anchor: Option[(Long, Double)] = None

  // what has come and is still to be read, in order (from `comingFrom` to `comingTo`), and the newest seq among it:
  // read a stretch a turn, so a burst (a server's backlog, a long paste) never holds the screen up, whatever the
  // platform. (An array filled in place: a Roku's lists copy themselves to grow.)
  private var coming: Array[() => Unit] = new Array[() => Unit](64)
  private var comingFrom = 0
  private var comingTo = 0
  private var comingSeq = 0L

  private def toRead(work: () => Unit): Unit = {
    if (comingTo == coming.length) {
      val count = comingTo - comingFrom
      val next = if (comingFrom > coming.length / 2) coming else new Array[() => Unit](coming.length * 2)
      var i = 0
      while (i < count) {
        next(i) = coming(comingFrom + i)
        i += 1
      }
      while (i < comingTo) {
        next(i) = null
        i += 1
      }
      coming = next
      comingFrom = 0
      comingTo = count
    }
    coming(comingTo) = work
    comingTo += 1
    changing()
  }

  // what is about to change shown once this turn is done: lines arriving many at a time are laid out once
  private def changing(): Unit = if (!pending) {
    pending = true
    anchor = if (following() || shown.size == 0) None else {
      val i = math.max(0, list.indexAt(list.offset()))
      Some((shown.id(i), list.top(i) - list.offset()))
    }
    Platform.later(() => turn())
  }

  // a stretch of what has come read and shown, and a turn later the next
  private def turn(): Unit = if (pending) {
    readFor(LogView.budget)
    show()
    if (comingFrom < comingTo) changing()
  }

  // what has come read, for `budget` milliseconds (all of it, where negative)
  private def readFor(budget: Double): Unit = {
    val began = Platform.ticks()
    var n = 0
    while (comingFrom < comingTo && (budget < 0.0 || n % 16 != 0 || Platform.ticks() - began < budget)) {
      val work = coming(comingFrom)
      coming(comingFrom) = null
      comingFrom += 1
      work()
      n += 1
    }
    if (comingFrom == comingTo) {
      comingFrom = 0
      comingTo = 0
    }
  }

  /** Everything that has come read and shown now, rather than a stretch a turn. */
  def settle(): Unit = if (pending) {
    readFor(-1.0)
    show()
  }

  private def show(): Unit = {
    pending = false
    if (following()) unseenLines = 0
    rows.changed @= ()
    anchor.foreach { case (id, dy) =>
      val i = shown.indexOf(id)
      if (i >= 0 && !following()) list.scrollTop(list.top(i) - dy)
    }
    anchor = None
    report()
  }

  private def report(): Unit = {
    count @= store.size
    hidden @= math.max(0, store.size - shown.size)
    if (unseen() != unseenLines) unseen @= unseenLines
    matches @= finder.total
    matchIndex @= finder.ordinal
  }

  // the lines laid out again: their room, their wrapping, the gutter
  private def reflow(): Unit = {
    cols = -1
    shown.rewrap()
    rows.changed @= ()
    list.redecorateAll()
  }

  // every line's level read again, and what the filter shows with it
  private def reclassify(): Unit = {
    var before: Option[LogLevel] = None
    var i = 0
    while (i < store.size) {
      val line = classify(store(i), before)
      if (!line.marker) before = line.level
      if (line.level != store(i).level) store.replace(i, line)
      i += 1
    }
    lastLevel = before
    refilter()
  }

  private def refilter(): Unit = {
    val f = filter()
    filtering = new LogMatcher(f.text, f.regex, f.caseSensitive)
    shown.clear()
    var i = 0
    while (i < store.size) {
      if (passes(store(i))) shown.append(store(i).id)
      i += 1
    }
    research()
    pending = true
    settle()
    list.redecorateAll()
    if (following()) toLatest()
  }

  /** The gutter's width: the line numbers, times and sources it shows. */
  def gutterWidth: Double = {
    val chars = gutterChars
    if (chars == 0) 0.0 else chars * advance() + 8.0
  }

  private def gutterChars: Int =
    (if (lineNumbers()) digits + 1 else 0) + (if (timestamps()) 13 else 0) + (if (sources()) sourceWidth + 1 else 0)

  private def digits: Int = math.max(4, (store.dropped + store.size).toString.length)

  /** What the gutter says for `line`: its number, its time and its source, as far as they are shown. */
  def gutterText(line: LogLine): String = {
    var out = ""
    if (lineNumbers()) {
      val i = store.find(line.id)
      val n = if (i < 0 || line.marker) "" else (store.dropped + i + 1L).toString
      out = " " * (digits - n.length) + n + " "
    }
    if (timestamps()) out = out + (if (line.at <= 0L || line.marker) " " * 12 else LogView.time(line.at)) + " "
    if (sources()) {
      val s = if (line.source.length > sourceWidth) line.source.substring(0, sourceWidth) else line.source
      out = out + s + " " * (sourceWidth - s.length) + " "
    }
    out
  }

  /** How many columns a line wraps at, or Int.MaxValue where lines do not wrap. */
  def columns: Int = {
    if (cols < 0) cols = if (!wrap()) Int.MaxValue else math.max(8, ((size.width() - gutterWidth - 12.0) / advance()).toInt)
    cols
  }

  // how many rows line `id` takes
  private def rowsOf(id: Long): Int = store.line(id).map(l => LogRow.segmentsOf(l.text.length, columns)).getOrElse(1)

  // ---- following ----

  /** Scrolled to the newest line, and kept there. */
  def toLatest(): Unit = {
    following @= true
    unseenLines = 0
    unseen @= 0
    list.stopFling()
    list.scrollTop(list.total)
  }

  private def furthest: Double = math.max(0.0, list.total - list.size.height())

  // a move to the bottom takes hold of it; one up from it lets go
  private def moved(): Unit = {
    val bottom = list.offset() >= furthest - 8.0
    if (bottom && !following()) {
      following @= true
      unseenLines = 0
      unseen @= 0
    } else if (!bottom && following() && list.total > list.size.height()) following @= false
  }

  private def cursorInView(): Unit = if (shown.size > 0) {
    val first = list.indexAt(list.offset())
    val last = list.indexAt(math.max(list.offset(), list.offset() + list.size.height() - lineHeight))
    val at = list.current().map(k => list.indexOf(k)).getOrElse(-1)
    if (at < first || at > last) list.current @= Some(shown.id(if (following()) last else first).toString)
  }

  // ---- sideways ----

  /** The furthest the lines scroll sideways. */
  def furthestX: Double = if (wrap()) 0.0 else math.max(0.0, (widest + 2) * advance() - (size.width() - gutterWidth))

  /** Scrolled `delta` pixels sideways, within the lines. */
  def scrollSideways(delta: Double): Unit = {
    val to = math.max(0.0, math.min(furthestX, scrollX() + delta))
    if (to != scrollX()) scrollX @= to
  }

  // ---- searching ----

  private var searchRun = 0

  /** Where `line` holds what is searched for, each from and to. */
  def matchesIn(line: LogLine): List[(Int, Int)] = if (finding.empty) Nil else finding.spans(line.text)

  /** Which of `line`'s matches the search is on, -1 for none. */
  def currentMatch(id: Long): Int = if (finder.currentId == id) finder.currentOccurrence else -1

  /** On to the next match, round to the first after the last, scrolled into view. */
  def nextMatch(): Unit = if (finder.next(topId)) toMatch()

  /** Back to the match before, round to the last before the first, scrolled into view. */
  def previousMatch(): Unit = if (finder.previous(topId)) toMatch()

  // the id of the first line in view
  private def topId: Long = if (shown.size == 0) 0L else shown.id(math.max(0, list.indexAt(list.offset())))

  private def research(): Unit = {
    searchRun += 1
    finding = new LogMatcher(search(), searchRegex(), searchCase())
    broken @= finding.broken
    finder.restart(finding)
    searching @= !finding.empty
    val run = searchRun
    if (!finding.empty) Platform.later(() => searchOn(run))
    report()
    list.redecorateAll()
  }

  // a stretch of the lines searched, and on with the rest a turn later; the first match from the top of the view on
  // is the one it is on (else the last before it)
  private def searchOn(run: Int): Unit = if (run == searchRun) {
    val done = finder.step(LogView.budget, () => Platform.ticks())
    if (finder.currentId < 0L && finder.lines > 0) {
      val top = topId
      val after = finder.hitIndex(top) < finder.lines
      if (after || done) {
        if (after) finder.next(top) else finder.previous(top)
        toMatch()
      }
    }
    report()
    if (done) searching @= false else Platform.later(() => searchOn(run))
  }

  // the match it is on scrolled into view, across as well where the lines do not wrap
  private def toMatch(): Unit = {
    val i = shown.indexOf(finder.currentId)
    if (i >= 0) {
      store.line(finder.currentId).foreach { line =>
        val spans = matchesIn(line)
        if (finder.currentOccurrence < spans.length) {
          val (from, to) = spans(finder.currentOccurrence)
          val cols = columns
          val row = if (cols == Int.MaxValue) 0 else from / cols
          val y = list.top(i) + row * lineHeight
          if (y < list.offset() || y + lineHeight > list.offset() + list.size.height()) {
            list.stopFling()
            list.scrollTop(y - (list.size.height() - lineHeight) / 2.0)
          }
          if (cols == Int.MaxValue) {
            val room = size.width() - gutterWidth - 16.0
            val left = from * advance()
            val right = to * advance()
            if (left < scrollX()) scrollSideways(left - scrollX() - room / 4.0)
            else if (right > scrollX() + room) scrollSideways(right - scrollX() - room * 3.0 / 4.0)
          }
        }
      }
      list.current @= Some(shown.id(i).toString)
    }
    report()
    list.redecorateAll()
  }

  // ---- copying ----

  /** The words of the lines chosen (else the line under the cursor), in order, one to a line, without escapes. */
  def selectedText: String = {
    val chosen = list.selection
    var out: List[String] = Nil
    if (!chosen.isEmpty) {
      var i = 0
      while (i < shown.size) {
        if (chosen.contains(shown.id(i).toString)) store.line(shown.id(i)).foreach(l => out = l.text :: out)
        i += 1
      }
    } else list.currentItem.foreach(l => out = List(l.text))
    out.reverse.mkString("\n")
  }

  /** The lines chosen copied, as `selectedText`. */
  def copySelected(): Unit = Clipboard.copy(selectedText)

  /** What the context menu on `line` offers: Copy line, the lines chosen where there are more, and Select all. */
  def menuFor(line: LogLine): List[MenuItem] = if (!Clipboard.copies) Nil else {
    val n = list.selection.size(shown.size)
    List(MenuItem(LogWords.copyLine, () => Clipboard.copy(line.text), MaterialIcon.ContentCopy)) ++
      (if (n > 1 && list.selection.contains(line.id.toString)) List(MenuItem(LogWords.copyLines(n), () => copySelected()))
      else Nil) ++
      List(MenuItem(LogWords.selectAll, () => list.selectAll()))
  }

  list.menu.attach { p =>
    val items = menuFor(p.item)
    if (items.nonEmpty) {
      if (Platform.remoteNavigation()) Menu.show(items) else Menu.at(p.x, p.y, items)
    }
  }

  // the keys the lines take before the list: copying, sideways, the next match
  list.keys = (e: KeyEvent) => {
    val primary = if (Platform.commandKey()) e.meta else e.ctrl
    if (primary && !e.alt && !e.shift && e.name.toLowerCase == "c" && Clipboard.copies && Platform.selectedText().isEmpty &&
      (!list.selection.isEmpty || list.current().nonEmpty)) Some(() => copySelected())
    else if (e.key == Key.ArrowLeft && !wrap() && scrollX() > 0.0) Some(() => scrollSideways(-advance() * 8.0))
    else if (e.key == Key.ArrowRight && !wrap() && scrollX() < furthestX) Some(() => scrollSideways(advance() * 8.0))
    else if (e.name == "F3" && matches() > 0) Some(() => if (e.shift) previousMatch() else nextMatch())
    else None
  }

  // the wheel's sideways turn, and a finger dragged across
  list.event.pointer.wheel.attach(e => if (!wrap() && e.x != 0.0) scrollSideways(e.x))
  list.dragAcross.attach(d => if (!wrap()) scrollSideways(d))

  // ---- following what changes ----

  // the focus arriving puts the cursor on a line in view (the newest while following), not on the first of thousands
  list.focused.attach(on => if (on) cursorInView())
  list.followsEnd := following()
  list.offset.attach(_ => moved(), Priority.Low)
  following.attach(on => if (on) toLatest())
  scrollX.attach(_ => list.rows.foreach {
    case r: LogRow => r.slide()
    case _ => ()
  })
  wrap.attach { _ =>
    scrollX @= 0.0
    reflow()
    if (following()) toLatest()
  }
  lineNumbers.attach(_ => reflow())
  timestamps.attach(_ => reflow())
  sources.attach(_ => reflow())
  theme.attach(_ => list.redecorateAll())
  advance.attach(_ => reflow())
  textTop.attach(_ => list.redecorateAll())
  size.width.attach(_ => if (wrap()) reflow())
  filter.attach(_ => refilter())
  level.attach(_ => reclassify())
  continuations.attach(_ => reclassify())
  search.attach(_ => research())
  searchCase.attach(_ => research())
  searchRegex.attach(_ => research())
  probe.size.width.attach(w => if (w > 0.0) advance @= w / 64.0)
  probe.size.height.attach(h => if (h > 0.0) textTop @= math.max(0.0, (lineHeight - h) / 2.0))
  if (probe.size.width() > 0.0) advance @= probe.size.width() / 64.0

  override def focus(): Unit = list.focus()
}

object LogView {
  /** The face lines are set in: EveryUI's monospaced one. */
  val face: String = "Roboto Mono"

  /** Whether the platform draws a paragraph's styled runs (the web, the desktop, Android); a Roku and an Apple TV
    * draw a run a piece. */
  val runsDrawn: Boolean =
    Platform.kind != PlatformKind.Roku && Platform.kind != PlatformKind.TvOS && Platform.kind != PlatformKind.IOS

  /** How long a stretch of reading or searching may take before the screen has a turn, in milliseconds. */
  val budget: Double = if (Platform.kind == PlatformKind.Roku) 25.0 else 8.0

  /** `millis` since the epoch as the local time of day, to the millisecond: "17:46:50.123". */
  def time(millis: Long): String = {
    val local = Platform.localDateTime(millis)
    val ms = (millis % 1000L).toString
    (if (local.length >= 19) local.substring(11, 19) else local) + "." + "0" * (3 - ms.length) + ms
  }

  // a line's id from its key
  private def idOf(key: String): Option[Long] = {
    var digits = key.nonEmpty
    var i = 0
    while (digits && i < key.length) {
      digits = key.charAt(i) >= '0' && key.charAt(i) <= '9'
      i += 1
    }
    if (digits) Some(key.toLong) else None
  }
}

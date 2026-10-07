package everyui.log

import everyui.Color
import everyui.component.{Component, Container, RichText, TextView}
import everyui.component.types.{FontWeight, TextOverflow, WhiteSpace}
import everyui.list.ListRow
import everyui.text.TextRun
import everyui.theme.Theme
import reactify.*

/** A line of a `LogView`: its number, time and source in the gutter, and its words in their colours, scrolled
  * sideways with the view's other lines or wrapped at its columns. The words are drawn as one paragraph of styled
  * runs where the platform draws them (the web, the desktop, Android), and as a piece per colour where it does not
  * (a Roku, an Apple TV), set at their columns: the face is monospaced, so a column is `advance` wide on every
  * platform, and grounds, a reversed run's and a search's matches, are boxes behind the words at their columns. */
private[log] class LogRow(view: LogView) extends ListRow[LogLine] {
  private val stripe = new Container {
    size.width @= 3.0
  }
  private val gutter = new TextView {
    font.family @= LogView.face
    font.size @= view.points
    whiteSpace @= WhiteSpace.Pre
    textOverflow @= TextOverflow.Visible
  }
  // the words' room, past the gutter, and what scrolls sideways in it
  private val room = new Container
  private val inner = new Container
  private val words: Option[RichText] = if (!LogView.runsDrawn) None else Some(new RichText {
    font.family @= LogView.face
    font.size @= view.points
    font.lineHeight @= view.lineHeight / view.points
    whiteSpace @= WhiteSpace.Pre
  })
  private var boxes: Vector[Container] = Vector.empty
  private var pieces: Vector[TextView] = Vector.empty

  room.children += inner
  words.foreach(w => inner.children += w)
  children ++= List(room, gutter, stripe)

  item.attach(_ => draw())
  // a new size moves only the edges: what is drawn stays (a Roku draws slowly)
  size.width.attach(_ => fit())
  size.height.attach(_ => fit())

  // the room for the words, and the level's stripe, to the row's size
  private def fit(): Unit = {
    val lead = view.gutterWidth
    stripe.size.height @= size.height()
    room.position.left @= lead
    room.size.width @= math.max(0.0, size.width() - lead)
    room.size.height @= size.height()
    room.node.setClip(room.size.width(), room.size.height())
  }

  /** Its words moved across to where the view is scrolled sideways, where lines do not wrap. */
  def slide(): Unit = if (view.columns == Int.MaxValue) inner.position.left @= -view.scrollX()

  /** Drawn again from the line it has and the view as it is now. */
  def draw(): Unit = item() match {
    case None =>
      room.visible @= false
      gutter.visible @= false
      stripe.visible @= false
    case Some(line) =>
      val theme = view.theme()
      val advance = view.advance()
      val lead = view.gutterWidth
      val cols = view.columns
      gutter.visible @= lead > 0.0
      gutter.content @= view.gutterText(line)
      gutter.color @= theme.muted
      gutter.position.left @= 4.0
      gutter.position.top @= view.textTop()
      stripe.visible @= line.level.contains(LogLevel.Error) || line.level.contains(LogLevel.Warn)
      stripe.backgroundColor @= (if (line.level.contains(LogLevel.Error)) Theme.danger() else Theme.warning())
      room.visible @= true
      fit()
      inner.position.left @= (if (cols < Int.MaxValue) 0.0 else -view.scrollX())
      val segments = LogRow.segmentsOf(line.text.length, cols)
      inner.size.width @= (if (cols < Int.MaxValue) cols else line.text.length + 1) * advance
      inner.size.height @= segments * view.lineHeight
      val shapes = grounds(line, theme, cols, advance) ++ marks(line, theme, cols, advance)
      shapes.zipWithIndex.foreach { case ((x, y, w, c), i) =>
        val b = box(i)
        b.position.left @= x
        b.position.top @= y
        b.size.width @= w
        b.size.height @= view.lineHeight
        b.backgroundColor @= c
        b.visible @= true
      }
      var i = shapes.length
      while (i < boxes.length) {
        boxes(i).visible @= false
        i += 1
      }
      words match {
        case Some(w) => paragraph(w, line, theme, cols, advance)
        case None => strip(line, theme, cols, advance)
      }
  }

  // the words as one paragraph of runs, a line break at each column's end where they wrap
  private def paragraph(w: RichText, line: LogLine, theme: LogTheme, cols: Int, advance: Double): Unit = {
    w.color @= (if (line.marker) theme.muted else theme.foreground)
    w.font.italic @= line.marker
    w.size.width @= inner.size.width() + advance
    w.position.top @= 0.0
    var runs: List[TextRun] = Nil
    LogRow.pieces(line).foreach { span =>
      LogRow.split(span.from, span.to, cols).foreach { case (seg, a, b) =>
        val text = line.text.substring(seg * cols + a, seg * cols + b)
        val broken = seg > 0 && a == 0
        runs = run(if (broken) "\n" + text else text, span, theme) :: runs
      }
    }
    w.runs @= runs.reverse
  }

  private def run(text: String, span: LogSpan, theme: LogTheme): TextRun =
    if (span.style.plain && span.href.isEmpty) TextRun(text)
    else TextRun(text, color = Component.css(letters(span.style, theme)), weight = if (span.style.bold) 700 else 0,
      italic = span.style.italic, strike = span.style.strike, underline = span.style.underline, href = span.href)

  // the words as a piece per run, each at its column
  // (runs next to each other in one colour are one piece: each is a label of its own, and a Roku makes them slowly)
  private def strip(line: LogLine, theme: LogTheme, cols: Int, advance: Double): Unit = {
    var n = 0
    // (a row, from, to and the colour: no wider, a Roku has no tuple of five)
    var at: List[(Int, Int, Int, (Color, AnsiStyle))] = Nil
    LogRow.pieces(line).foreach { span =>
      val colour = if (line.marker) theme.muted else letters(span.style, theme)
      LogRow.split(span.from, span.to, cols).foreach { case (seg, a, b) =>
        at = at match {
          case (s, from, to, look) :: rest if s == seg && to == a && look._1 == colour => (s, from, b, look) :: rest
          case _ => (seg, a, b, (colour, span.style)) :: at
        }
      }
    }
    at.reverse.foreach { case (seg, a, b, (colour, style)) =>
      val p = piece(n)
      p.content @= line.text.substring(seg * cols + a, seg * cols + b)
      p.color @= colour
      p.font.weight @= (if (style.bold) FontWeight.Bold else FontWeight.Normal)
      p.font.italic @= style.italic || line.marker
      p.position.left @= a * advance
      p.position.top @= seg * view.lineHeight + view.textTop()
      p.visible @= true
      n += 1
    }
    while (n < pieces.length) {
      pieces(n).visible @= false
      n += 1
    }
  }

  // a concealed run's letters in its ground, so they hide but are still there to copy
  private def letters(style: AnsiStyle, theme: LogTheme): Color =
    if (style.conceal) theme.ground(style.copy(conceal = false)).getOrElse(theme.background)
    else theme.letters(style)

  // the boxes behind runs with a ground of their own: (left, top, width, colour)
  private def grounds(line: LogLine, theme: LogTheme, cols: Int, advance: Double): List[(Double, Double, Double, Color)] =
    line.spans.flatMap { span =>
      theme.ground(span.style) match {
        case Some(c) => LogRow.split(span.from, span.to, cols).map { case (seg, a, b) =>
          (a * advance, seg * view.lineHeight, (b - a) * advance, c)
        }
        case None => Nil
      }
    }

  // the boxes behind the search's matches, the one it is on the strongest
  private def marks(line: LogLine, theme: LogTheme, cols: Int, advance: Double): List[(Double, Double, Double, Color)] =
    if (line.marker) Nil else {
      val at = view.currentMatch(line.id)
      view.matchesIn(line).zipWithIndex.flatMap { case ((from, to), k) =>
        val c = if (k == at) theme.current else theme.hit
        LogRow.split(from, to, cols).map { case (seg, a, b) => (a * advance, seg * view.lineHeight, (b - a) * advance, c) }
      }
    }

  private def box(i: Int): Container = {
    while (i >= boxes.length) {
      val b = new Container {
        radius @= 2.0
      }
      boxes = boxes :+ b
      // behind the words
      inner.children @= b +: inner.children()
    }
    boxes(i)
  }

  private def piece(i: Int): TextView = {
    while (i >= pieces.length) {
      val p = new TextView {
        font.family @= LogView.face
        font.size @= view.points
        whiteSpace @= WhiteSpace.Pre
        textOverflow @= TextOverflow.Visible
      }
      pieces = pieces :+ p
      inner.children += p
    }
    pieces(i)
  }

  override def kind: String = "LogRow"
}

private[log] object LogRow {
  /** How many rows a line of `length` characters takes wrapped at `cols` (Int.MaxValue: not wrapped). */
  def segmentsOf(length: Int, cols: Int): Int =
    if (cols == Int.MaxValue || length <= cols) 1 else (length + cols - 1) / cols

  /** The characters from `from` to `to` cut at each row's end: (row, from, to) within each row. */
  def split(from: Int, to: Int, cols: Int): List[(Int, Int, Int)] =
    if (to <= from) Nil
    else if (cols == Int.MaxValue) List((0, from, to))
    else {
      var out: List[(Int, Int, Int)] = Nil
      var seg = from / cols
      while (seg * cols < to) {
        val a = math.max(from, seg * cols) - seg * cols
        val b = math.min(to, (seg + 1) * cols) - seg * cols
        out = (seg, a, b) :: out
        seg += 1
      }
      out.reverse
    }

  /** The whole line as spans: its own, and plain ones between them. */
  def pieces(line: LogLine): List[LogSpan] = {
    var out: List[LogSpan] = Nil
    var at = 0
    line.spans.foreach { s =>
      if (s.from > at) out = LogSpan(at, s.from, AnsiStyle.Plain) :: out
      out = s :: out
      at = s.to
    }
    if (at < line.text.length || line.text.isEmpty) out = LogSpan(at, line.text.length, AnsiStyle.Plain) :: out
    out.reverse
  }
}

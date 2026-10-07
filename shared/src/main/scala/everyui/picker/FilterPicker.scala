package everyui.picker

import everyui.async.Task
import everyui.component.*
import everyui.event.Key
import everyui.i18n.Words
import everyui.layout.FlowLayout
import everyui.list.{ListRow, VirtualList}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, Shadow, ui}
import reactify.*

/** A field over a list of what matches it, for going to one of very many things by typing part of its name: a file, a
  * symbol, a command. Each change to the words asks `query` for the matches, once the typing pauses for `debounce`
  * seconds; an answer to words since changed is dropped, so a slow one never replaces a newer one. The list makes only
  * the rows that show (`everyui.list.VirtualList`), so tens of thousands of matches cost what a screenful does.
  *
  * Up and Down (and the page keys) move through the matches from the field, Enter or a click chooses one, which closes
  * the picker and is heard on `chosen`; Escape, Back or a click outside closes it with nothing chosen. Rows are
  * `row`'s: by default the item's `labelOf` with what the query matched lit (`Match.spans`), and `detailOf` after it.
  *
  * Given `segments`, a row of chips over the list narrows the matches to one group (`groupOf`), on top of the query:
  * a model picker's providers, say. The first segment is chosen to begin with, and `Segment.all` shows every match.
  * A click or a tap chooses a chip; from the field, Tab and Shift+Tab go round the segments, and on a remote the
  * chips sit between the field and the list, Left and Right along them and OK choosing.
  *
  * {{{
  *   FilterPicker.show[File](q => index.search(q), _.path, _.name, _.folder, placeholder = "Go to file") { f =>
  *     open(f)
  *   }
  *
  *   new FilterPicker[Model](q => models.find(q), _.id, _.name, _.provider,
  *     segments = List(Segment.all("All"), Segment("local", "Local"), Segment("anthropic", "Anthropic")),
  *     groupOf = _.providerKey).open()
  * }}} */
class FilterPicker[T](query: String => Task[Vector[T]], keyOf: T => String, labelOf: T => String,
                      detailOf: T => String = (_: T) => "", placeholder: String = Words.search,
                      width: Double = FilterPicker.width, debounce: Double = FilterPicker.debounce,
                      row: Option[Var[String] => ListRow[T]] = None, segments: List[Segment] = Nil,
                      groupOf: T => String = (_: T) => "") extends Container {
  /** A match chosen. */
  val chosen: Channel[T] = Channel[T]
  /** The words in the field. */
  val words: Var[String] = own(Var(""))
  /** The words the matches showing are for. */
  val answered: Var[Option[String]] = own(Var(None))
  /** The matches for the words, as `query` last answered. */
  val matches: Var[Vector[T]] = own(Var(Vector.empty))
  /** The key of the segment chosen: the first segment's to begin with, "" (every match) without segments. */
  val segment: Var[String] = own(Var(segments.headOption.map(_.key).getOrElse("")))
  /** The matches the list shows: those in the chosen segment. */
  val shown: Var[Vector[T]] = own(Var(Vector.empty))

  private val across = math.min(width, ui.size.width() - 32.0)
  private var asked = 0
  private val keyboard = Platform.keyboardSupport()

  val field: TextInput = new TextInput(placeholder, false, across - 16.0) {
    // the matches move under the words while they are typed; on a remote the arrows go down into the list instead
    override def keepsKey(key: Key): Boolean = super.keepsKey(key) || (keyboard &&
      (key == Key.ArrowUp || key == Key.ArrowDown || key == Key.PageUp || key == Key.PageDown)) ||
      (key == Key.Tab && segments.size > 1)
  }
  /** The segments' chips, between the field and the list, wrapping to more rows where they are many; empty without
    * segments. */
  val chips: Container = new Container {
    layout @= Some(FlowLayout(6.0, 6.0))
    size.width @= across - 24.0
    children ++= segments.map(s => new SegmentChip(s, segment))
  }
  val list: VirtualList[T] = new VirtualList[T](shown, keyOf, labelOf)(() =>
    row.map(make => make(words)).getOrElse(new HighlightRow[T](labelOf,
      (t: T) => Match.spans(words(), labelOf(t)).getOrElse(Nil), detailOf)))
  private val empty = new TextView {
    content := Words.noResults
    font.size @= 14.0
    color := Theme.muted()
    visible := answered().nonEmpty && shown().isEmpty
  }
  private val rows: Val[Double] = own(Val(math.max(1, math.min(FilterPicker.rowsShown, shown().size)).toDouble))

  field.value.attach(v => if (words() != v) words @= v)
  words.attach { w =>
    if (field.value() != w) field.value @= w
    ask(w)
  }
  field.event.key.down.attach { k =>
    val at = math.max(0, list.current().map(list.indexOf).getOrElse(0))
    if (k.key == Key.ArrowDown) list.moveTo(math.min(list.count - 1, at + 1))
    else if (k.key == Key.ArrowUp) list.moveTo(math.max(0, at - 1))
    else if (k.key == Key.PageDown) list.moveTo(math.min(list.count - 1, at + FilterPicker.rowsShown))
    else if (k.key == Key.PageUp) list.moveTo(math.max(0, at - FilterPicker.rowsShown))
    else if (k.key == Key.Tab && segments.size > 1) step(if (k.shift) -1 else 1)
  }
  field.submitted.attach(_ => list.currentItem.foreach(choose))
  list.activated.attach(choose)
  // one click chooses, with a mouse too: it is a choice to make and be done with, not a list to pick rows in (where a
  // click only puts the cursor on a row and a double click opens it)
  list.clickActivates @= true
  matches.attach(_ => narrow())
  segment.attach(_ => narrow())
  if (segments.isEmpty) {
    field.exitFocus(Key.ArrowDown, list)
    list.exitFocus(Key.ArrowUp, field)
  } else {
    // on a remote the chips are a stop of their own between the field and the list, landed on at the one chosen
    field.exitFocus(Key.ArrowDown, chips)
    chips.exitFocus(Key.ArrowUp, field)
    chips.exitFocus(Key.ArrowDown, list)
    list.exitFocus(Key.ArrowUp, chips)
    segment.attach(_ => chip.foreach(chips.entryFocus))
    chip.foreach(chips.entryFocus)
  }

  field.position.left @= 8.0
  field.position.top @= 8.0
  chips.position.left @= 12.0
  chips.position.top := field.size.height() + 16.0
  list.position.left @= 4.0
  list.position.top := field.size.height() + 16.0 + (if (segments.isEmpty) 0.0 else chips.size.height() + 10.0)
  list.size.width @= across - 8.0
  list.size.height := rows() * list.rowHeight
  empty.position.left @= 20.0
  empty.position.top := list.position.top() + (list.rowHeight - empty.size.height()) / 2.0
  size.width @= across
  size.height := list.position.top() + list.size.height() + 8.0
  backgroundColor := Theme.raised()
  borderWidth @= 1.0
  borderColor := Theme.border()
  radius @= 10.0
  shadow @= Some(Shadow(0.0, 8.0, 32.0, Color.Black.withAlpha(0.5)))
  children ++= List(field, chips, list, empty)

  /** Put it up, a fifth of the way down the screen, with `initial` in the field. */
  def open(initial: String = ""): Unit = {
    position.left := (ui.size.width() - size.width()) / 2.0
    position.top := math.max(16.0, ui.size.height() * 0.12)
    Modal.show(this, field, dim = false, centre = false)
    if (words() == initial) ask(initial) else words @= initial
  }

  /** Take it down, with nothing chosen. */
  def close(): Unit = Modal.remove(this)

  /** Ask `query` for `w`, once the typing has paused, keeping the answer only while `w` are still the words. */
  private def ask(w: String): Unit = {
    asked += 1
    val mine = asked
    val run = () => if (mine == asked) query(w).map { found =>
      if (mine == asked) {
        answered @= Some(w)
        matches @= found
      }
    }.start()
    if (debounce <= 0.0) run() else Platform.after(debounce, run)
  }

  /** The chip of the segment chosen. */
  private def chip: Option[Component] = chips.children().find {
    case c: SegmentChip => c.segment.key == segment()
    case _ => false
  }

  /** The segment `by` along from the one chosen, round from the last to the first. */
  private def step(by: Int): Unit = {
    val at = math.max(0, segments.indexWhere(_.key == segment()))
    segment @= segments((at + by + segments.size) % segments.size).key
  }

  /** The matches in the chosen segment shown, the first of them under the cursor. */
  private def narrow(): Unit = {
    val key = segment()
    shown @= (if (key.isEmpty) matches() else matches().filter(t => groupOf(t) == key))
    if (shown().nonEmpty) list.moveTo(0)
  }

  private def choose(t: T): Unit = {
    close()
    chosen @= t
  }

  override def kind: String = "FilterPicker"
}

object FilterPicker {
  val width: Double = 640.0
  /** How long the typing pauses before the query is asked, in seconds. */
  val debounce: Double = 0.12
  /** How many rows show before the list scrolls. */
  val rowsShown: Int = 12

  /** A picker put up at once; `choose` hears what is chosen. */
  def show[T](query: String => Task[Vector[T]], keyOf: T => String, labelOf: T => String,
              detailOf: T => String = (_: T) => "", placeholder: String = Words.search, initial: String = "")
             (choose: T => Unit): FilterPicker[T] = {
    val picker = new FilterPicker[T](query, keyOf, labelOf, detailOf, placeholder)
    picker.chosen.attach(choose)
    picker.open(initial)
    picker
  }
}

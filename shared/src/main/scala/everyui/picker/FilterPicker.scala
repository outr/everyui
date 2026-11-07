package everyui.picker

import everyui.async.Task
import everyui.component.*
import everyui.component.types.{Autofill, WhiteSpace}
import everyui.event.Key
import everyui.font.MaterialIcon
import everyui.i18n.{Direction, Words}
import everyui.keymap.{Chord, Command, Keymap}
import everyui.kit.{Mirror, SearchField}
import everyui.layout.{FlowLayout, HorizontalLayout}
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
  * a model picker's providers, say, each with an icon before its words if it likes (`SegmentIcon`). The first segment
  * is chosen to begin with, and `Segment.all` shows every match.
  * A click or a tap chooses a chip, the focus going back to the field where there is a keyboard; from the field, Tab
  * and Shift+Tab go round the segments, and on a remote the chips sit between the field and the list, Left and Right
  * along them and OK choosing. Given `groupsOf`, a match is in each group it names, not the one `groupOf` does: a
  * "Recent" chip whose matches are each in their own group's chip too.
  *
  * Given `subtitleOf`, each row has a muted second line under its words where there are any (a description under a
  * name), and the rows are two lines tall.
  *
  * Given `toggles`, options the query reads (Match case, a regular expression, whole words: `PickerToggle`) are toggle
  * buttons at the field's end, and given `extraField`, a second, smaller field under it (a file mask: `PickerField`),
  * each shown only for its segments. Turning an option over, or typing in the second field (once the typing pauses),
  * asks the query again, and so does choosing a chip that shows or hides one, since the options in force have
  * changed. An option's chord (Alt+C, or Cmd+Option+C on a Mac, for Match case) turns it over from anywhere in the
  * picker, and its tooltip says so; on a remote the options are Right of the field (Left, right to left) and the
  * second field is below it, before the chips.
  *
  * Given a `heading`, its words are a muted line under the field (and the second field), over the chips and the list
  * they speak of: what is in effect among the choices ("Current: Auto"), say, where the button that opened the picker
  * shows only an icon. It wraps to the picker's width and follows its value; with no words it is not drawn and takes
  * no room. It is a label, never focused and no row of the list, and the field, which has the focus, says it after
  * its name.
  *
  * Given `inUse`, the matches it holds for the one in use (the model a conversation runs on, the topic shown) are drawn
  * apart from the rest, in an accent ring on a faint accent ground, their words in the accent (`ListRow.inUse`), and
  * the cursor starts on the first of them while no words narrow the list. Rows of the app's own (`row`) get the ring
  * and the ground, and read `inUse` for their words.
  *
  * {{{
  *   FilterPicker.show[File](q => index.search(q), _.path, _.name, _.folder, placeholder = "Go to file") { f =>
  *     open(f)
  *   }
  *
  *   new FilterPicker[Model](q => models.find(q), _.id, _.name, _.provider,
  *     segments = List(Segment.all("All"), Segment("local", "Local", Some(SegmentIcon(MaterialIcon.Computer))),
  *       Segment("acme", "Acme", Some(SegmentIcon.Url("/providers/acme.svg")))),
  *     groupOf = _.providerKey, heading = Some(Val("Current: " + current().name))).open()
  *
  *   lazy val find: FilterPicker[Hit] = new FilterPicker[Hit](q => search(q, find.segment(), matchCase(), mask()),
  *     _.id, _.name, _.where, segments = List(Segment.all("All"), Segment("files", "Files")), groupOf = _.kind,
  *     toggles = List(PickerToggle.matchCase(matchCase, Set("files"))),
  *     extraField = Some(PickerField.fileMask(mask, Set("files"))))
  * }}} */
class FilterPicker[T](query: String => Task[Vector[T]], keyOf: T => String, labelOf: T => String,
                      detailOf: T => String = (_: T) => "", placeholder: String = Words.search,
                      width: Double = FilterPicker.width, debounce: Double = FilterPicker.debounce,
                      row: Option[Var[String] => ListRow[T]] = None, segments: List[Segment] = Nil,
                      groupOf: T => String = (_: T) => "", toggles: List[PickerToggle] = Nil,
                      extraField: Option[PickerField] = None, heading: Option[Val[String]] = None,
                      inUse: T => Boolean = (_: T) => false, groupsOf: Option[T => Set[String]] = None,
                      subtitleOf: Option[T => String] = None) extends Container {
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
  // words to find with, never a sign-in's: kept out of autofill and the password managers
  field.autofill @= Some(Autofill.Off)
  /** The options' toggle buttons, at the field's end: those for the segment chosen. Empty without `toggles`. */
  val toggleBar: Container = new Container {
    layout @= Some(HorizontalLayout(4.0, middle = true))
    children ++= toggles.map(button)
  }
  /** The second field, under the field, where there is one: shown for its segments. */
  val extraInput: Option[SearchField] = extraField.map { f =>
    new SearchField(f.placeholder, across - 16.0, MaterialIcon.FilterList) {
      literal @= true
      autofill @= Some(Autofill.Off)
      value @= f.text()
      tooltip @= f.placeholder + FilterPicker.said(f.chord)
      visible := f.shows(segment())
    }
  }
  /** The segments' chips, between the field and the list, wrapping to more rows where they are many; empty without
    * segments. */
  val chips: Container = new Container {
    layout @= Some(FlowLayout(6.0, 6.0))
    size.width @= across - 24.0
    children ++= segments.map(s => new SegmentChip(s, segment))
  }
  // a chip clicked or tapped where there is a keyboard gives the focus back to the field, where the arrows and Enter
  // move through the matches and choose (left on the chip, they reached the list no more); one chosen with Enter, by
  // the keys, keeps it, and on a remote the chips are a stop of their own, left by the arrows
  if (keyboard && !Platform.remoteNavigation())
    chips.children().foreach(c => c.event.click.attach(_ =>
      if (everyui.event.Pointer.recent) Platform.later(() => field.focus())))
  /** The heading's line, under the field (and the second field), over the chips and the list: muted words wrapped to
    * the picker's width, drawn only while there are words. Never focused, and no row of the list. */
  val headingLine: Option[TextView] = heading.map { h =>
    new TextView {
      whiteSpace @= WhiteSpace.Normal
      size.width @= across - 24.0
      font.size @= 13.0
      color := Theme.muted()
      content := h()
      visible := h().nonEmpty
    }
  }
  val list: VirtualList[T] = new VirtualList[T](shown, keyOf, labelOf,
    rowHeight = if (subtitleOf.nonEmpty) HighlightRow.twoLines else VirtualList.rowHeight)(() =>
    row.map(make => make(words)).getOrElse(new HighlightRow[T](labelOf,
      (t: T) => Match.spans(words(), labelOf(t)).getOrElse(Nil), detailOf,
      subtitle = subtitleOf.getOrElse((_: T) => ""))))
  private val empty = new TextView {
    content := Words.noResults
    font.size @= 14.0
    color := Theme.muted()
    visible := answered().nonEmpty && shown().isEmpty
  }
  private val rows: Val[Double] = own(Val(math.max(1, math.min(FilterPicker.rowsShown, shown().size)).toDouble))
  // where the heading (or the chips, or the list) starts: under the field, and under the second field where it shows
  private val below: Val[Double] = own(Val(field.size.height() + 16.0 +
    extraInput.filter(_.visible()).map(_.size.height() + 8.0).getOrElse(0.0)))
  // where the chips (or the list) start: under the heading where it has words
  private val under: Val[Double] = own(Val(below() +
    headingLine.filter(_.content().nonEmpty).map(_.size.height() + 8.0).getOrElse(0.0)))
  // the options the query reads, those the segment chosen shows
  private def inForce: List[String] =
    toggles.filter(_.shows(segment())).map(_.key) ++ extraField.filter(_.shows(segment())).map(_.key)
  private var forced = inForce
  /** The options' chords, in force while it is up. */
  private lazy val commands: List[Command] =
    toggles.flatMap(t => t.chord.map(c => command(t.key, t.name, c, t.shows, () => t.on @= !t.on()))) ++
      extraField.flatMap(f => f.chord.map(c => command(f.key, f.placeholder, c, f.shows,
        () => extraInput.foreach(_.focus()))))

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
  list.clickActivates @= Some(true)
  list.inUse @= inUse
  matches.attach(_ => narrow())
  segment.attach(_ => narrow())
  // an option turned over, or the second field's words changed, ask again; the app's query reads them
  toggles.foreach(t => follow(t.on, (_: Boolean) => ask(words())))
  extraField.foreach { f =>
    extraInput.foreach { e =>
      e.value.attach(v => if (f.text() != v) f.text @= v)
      follow(f.text, (v: String) => if (e.value() != v) e.value @= v)
      // Enter there chooses, as in the field; where a remote's keyboard hands the words back with Enter, it does not
      if (Platform.nativeInput()) e.submitted.attach(_ => list.currentItem.foreach(choose))
    }
    follow(f.text, (_: String) => ask(words()))
  }
  segment.attach { _ =>
    if (inForce != forced) {
      forced = inForce
      ask(words())
    }
    wire()
  }
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
  wire()

  field.position.left @= 8.0
  field.position.top @= 8.0
  if (toggles.nonEmpty) {
    // the field short of the options at its end: the field's start on the right and the options on its left, right
    // to left
    field.size.width := across - 16.0 - (if (toggleBar.size.width() > 0.0) toggleBar.size.width() + 6.0 else 0.0)
    Mirror.place(field, () => across, () => 8.0)
    Mirror.place(toggleBar, () => across, () => across - 8.0 - toggleBar.size.width())
    toggleBar.position.top := 8.0 + (field.size.height() - toggleBar.size.height()) / 2.0
  }
  extraInput.foreach { e =>
    e.position.left @= 8.0
    e.position.top := field.size.height() + 16.0
  }
  headingLine.foreach { l =>
    l.position.left @= 12.0
    l.position.top := below()
  }
  // the field, which has the focus, says the heading after its name; the line itself is plain words
  heading.foreach(h => field.accessibleDescription := h())
  chips.position.left @= 12.0
  chips.position.top := under()
  list.position.left @= 4.0
  list.position.top := under() + (if (segments.isEmpty) 0.0 else chips.size.height() + 10.0)
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
  children ++= (field :: (if (toggles.isEmpty) Nil else List(toggleBar))) ++ extraInput.toList ++
    headingLine.toList ++ List(chips, list, empty)

  /** Put it up, a fifth of the way down the screen, with `initial` in the field. */
  def open(initial: String = ""): Unit = {
    position.left := (ui.size.width() - size.width()) / 2.0
    position.top := math.max(16.0, ui.size.height() * 0.12)
    Modal.show(this, field, dim = false, centre = false,
      after = () => Keymap.global.commands @= Keymap.global.commands().filterNot(c => commands.exists(_ eq c)))
    // the options' chords ahead of the app's own while it is up, as the keys of what is in front
    if (commands.nonEmpty) Keymap.global.commands @= commands ++ Keymap.global.commands()
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

  /** An option's toggle button, shown for its segments, its chord in its tooltip. */
  private def button(t: PickerToggle): ToggleButton =
    new ToggleButton(t.on, t.name, t.icon, if (t.icon == MaterialIcon.Empty) t.label else "") {
      tooltip @= t.name + FilterPicker.said(t.chord)
      visible := t.shows(segment())
      // a hidden one takes no room in the row
      size.width := (if (t.shows(segment())) ToggleButton.side else 0.0)
    }

  /** An option's chord, which runs `run` while the segment chosen `shows` the option. */
  private def command(key: String, name: String, chord: Chord, shows: String => Boolean, run: () => Unit): Command = {
    val c = new Command("picker." + key, name, List(chord), run)
    c.enabled := shows(segment())
    c
  }

  /** The arrows' ways between the field, the options, the second field, the chips and the list, as the segment chosen
    * shows them: Right of the field to the options (Left, right to left), and down from either to the second field,
    * then the chips, then the list. Without options or a second field, the ways are as they were. */
  private def wire(): Unit = if (toggles.nonEmpty || extraField.nonEmpty) {
    val bar = if (toggles.exists(_.shows(segment()))) Some(toggleBar) else None
    val extra: Option[Container] = extraInput.filter(_ => extraField.exists(_.shows(segment())))
    val next: Container = if (segments.isEmpty) list else chips
    val onward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
    val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
    field.dropExit(onward)
    bar.foreach { b =>
      field.exitFocus(onward, b)
      b.exitFocus(back, field)
      b.exitFocus(Key.ArrowUp, field)
      b.exitFocus(Key.ArrowDown, extra.getOrElse(next))
    }
    field.exitFocus(Key.ArrowDown, extra.getOrElse(next))
    extra.foreach { e =>
      e.exitFocus(Key.ArrowUp, field)
      e.exitFocus(Key.ArrowDown, next)
    }
    next.exitFocus(Key.ArrowUp, extra.getOrElse(field))
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
    // (in every group it is in, where it is in several: `groupsOf`)
    shown @= (if (key.isEmpty) matches()
      else matches().filter(t => groupsOf.map(f => f(t).contains(key)).getOrElse(groupOf(t) == key)))
    // the cursor on the one in use while no words narrow them, else on the first
    if (shown().nonEmpty) list.moveTo(if (words().trim.nonEmpty) 0 else math.max(0, shown().indexWhere(inUse)))
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

  /** What a tooltip says after an option's name: its chord, " (Alt+C)"; nothing without one. */
  def said(chord: Option[Chord]): String = chord.map(c => " (" + c.label + ")").getOrElse("")

  /** A picker put up at once; `choose` hears what is chosen. */
  def show[T](query: String => Task[Vector[T]], keyOf: T => String, labelOf: T => String,
              detailOf: T => String = (_: T) => "", placeholder: String = Words.search, initial: String = "",
              inUse: T => Boolean = (_: T) => false)
             (choose: T => Unit): FilterPicker[T] = {
    val picker = new FilterPicker[T](query, keyOf, labelOf, detailOf, placeholder, inUse = inUse)
    picker.chosen.attach(choose)
    picker.open(initial)
    picker
  }
}

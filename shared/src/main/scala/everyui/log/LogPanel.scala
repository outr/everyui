package everyui.log

import everyui.component.{Component, Container, IconButton, TextView}
import everyui.component.types.TextAlign
import everyui.event.Key
import everyui.font.MaterialIcon
import everyui.kit.SearchField
import everyui.layout.{FlowLayout, HorizontalLayout, VerticalLayout}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A `LogView` with its toolbar: a search field with its count ("3 of 41"), the matches before and after, and
  * whether case matters and the words are a regular expression; a filter field (words, or a regular expression) and a
  * chip for each level, with how many lines they hide; and whether lines wrap and the view follows the newest, and
  * Clear. The toolbar wraps onto more rows where the panel is narrow (a phone).
  *
  * In the search field Enter goes to the next match and Shift+Enter to the one before, as F3 and Shift+F3 do there and
  * in the lines; Down goes on to the lines. The filter is applied a moment after typing stops.
  *
  * {{{
  *   val panel = new LogPanel(new LogView(LogOptions(lineNumbers = true)))
  *   panel.size.width := screen.size.width()
  *   panel.size.height := screen.size.height()
  *   socketLines.attach(chunk => panel.view.append(chunk))
  * }}} */
class LogPanel(val view: LogView = new LogView()) extends Container {
  override def kind: String = "LogPanel"

  val searchField: SearchField = new SearchField(LogWords.search, 240.0)
  val filterField: SearchField = new SearchField(LogWords.filter, 200.0)
  /** Whether the filter's words are a regular expression. */
  val filterRegex: Var[Boolean] = own(Var(false))

  private val counted = new TextView {
    content := LogPanel.counted(view)
    font.size @= 12.0
    color := (if (view.broken()) Theme.danger() else Theme.muted())
    align @= TextAlign.Center
    size.width @= 68.0
  }
  private val hiddenCount = new TextView {
    content := (if (view.hidden() > 0) LogWords.hidden(view.hidden()) else "")
    font.size @= 12.0
    color := Theme.muted()
  }
  private val chips: List[LevelChip] = LogLevel.all.map(l =>
    new LevelChip(l, view.filter().levels.isEmpty || view.filter().levels.contains(l), () => turn(l)))

  // a group of the toolbar's, each in it on the middle of a field's height (a stand of no width that tall)
  private def group(items: List[Component]): Container = new Container {
    layout @= Some(HorizontalLayout(4.0, middle = true))
    children += new Container {
      size.width @= 0.0
      size.height @= SearchField.height
    }
    children ++= items
  }

  private val toolbar: Container = new Container {
    layout @= Some(FlowLayout(14.0, 6.0))
    children ++= List(
      group(List(searchField, counted,
        new IconButton(MaterialIcon.KeyboardArrowUp, LogWords.previous, () => view.previousMatch()),
        new IconButton(MaterialIcon.KeyboardArrowDown, LogWords.next, () => view.nextMatch()),
        new LogToggle(view.searchCase, LogWords.matchCase, letters = "Aa"),
        new LogToggle(view.searchRegex, LogWords.regex, letters = ".*"))),
      group(List(filterField, new LogToggle(filterRegex, LogWords.regex, letters = ".*"))),
      group(chips ++ List(hiddenCount)),
      group(List(new LogToggle(view.wrap, LogWords.wrap, MaterialIcon.WrapText),
        new LogToggle(view.following, LogWords.follow, MaterialIcon.VerticalAlignBottom),
        new IconButton(MaterialIcon.DeleteSweep, LogWords.clear, () => view.clear()))))
  }
  toolbar.size.width := size.width() - 16.0
  toolbar.size.height := toolbar.children().map(c => c.position.top() + c.size.height()).maxOption.getOrElse(0.0)

  searchField.literal @= true
  filterField.literal @= true
  // narrower fields where the panel is narrow
  searchField.size.width := math.max(140.0, math.min(260.0, size.width() - 16.0 - 230.0))
  filterField.size.width := math.max(140.0, math.min(220.0, size.width() - 16.0 - 60.0))

  layout @= Some(VerticalLayout(6.0))
  padding @= 8.0
  backgroundColor := Theme.background()
  view.size.width := size.width() - 16.0
  view.size.height := math.max(0.0, size.height() - toolbar.size.height() - 22.0)
  size.width @= 800.0
  size.height @= 500.0
  children ++= List(toolbar, view)

  // the search's words both ways; Enter and F3 to the next match, with Shift to the one before
  searchField.value.attach(v => if (view.search() != v) view.search @= v)
  view.search.attach(v => if (searchField.value() != v) searchField.value @= v)
  searchField.event.key.down.attach { k =>
    if ((k.key == Key.Enter && searchField.editing()) || k.name == "F3") {
      if (k.shift) view.previousMatch() else view.nextMatch()
    }
  }
  // where the platform's keyboard takes the words (a Roku), they come back submitted
  if (!Platform.nativeInput()) searchField.submitted.attach(_ => view.nextMatch())
  searchField.exitFocus(Key.ArrowDown, view.list)

  // the filter's words a moment after typing stops
  private var typed = 0
  filterField.value.attach { v =>
    typed += 1
    val turn = typed
    Platform.after(LogPanel.settleSeconds, () => if (turn == typed) applyFilter())
  }
  filterRegex.attach(_ => applyFilter())
  filterField.exitFocus(Key.ArrowDown, view.list)

  private def applyFilter(): Unit = {
    val f = view.filter()
    if (f.text != filterField.value() || f.regex != filterRegex()) view.filter @= f.copy(text = filterField.value(),
      regex = filterRegex())
  }

  // a level's chip turned over: none named shows them all, and the last one shown stays
  private def turn(level: LogLevel): Unit = {
    val levels = view.filter().levels
    val now =
      if (levels.isEmpty) LogLevel.all.toSet - level
      else if (levels.contains(level)) levels - level
      else levels + level
    if (now.nonEmpty) view.filter @= view.filter().copy(levels = if (now.size == LogLevel.all.size) Set.empty else now)
  }

  override def focus(): Unit = view.focus()
}

object LogPanel {
  /** How long after the last letter typed the filter is applied, in seconds. */
  val settleSeconds: Double = 0.2

  /** What the count beside the search says: "3 of 41", how many so far while it looks, "No matches", or that the
    * pattern does not compile. */
  def counted(view: LogView): String =
    if (view.broken()) LogWords.badPattern
    else if (view.search().isEmpty) ""
    else if (view.matches() == 0) (if (view.searching()) "…" else LogWords.noMatches)
    else if (view.matchIndex() > 0) LogWords.matchOf(view.matchIndex(), view.matches()) + (if (view.searching()) "…" else "")
    else view.matches().toString + (if (view.searching()) "…" else "")
}

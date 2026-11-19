package everyui.app

import everyui.async.Task
import everyui.component.{Button, ButtonStyle, Component, Container, MaterialIconView, TextView}
import everyui.font.MaterialIcon
import everyui.i18n.{Direction, Language, Strings}
import everyui.kit.Spinner
import everyui.picker.{FilterPicker, HighlightRow, Match, PickerField, PickerToggle, Segment, SegmentIcon}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A picker narrowed by segment chips: a model picker, one row per model, filtered by its provider (All, Local,
  * Quillon, Orrery, Brightfold, all made up) on top of what is typed. Each provider's chip has an icon before its
  * words: Local a Material icon, Quillon and Orrery path data (Orrery's filled even-odd), and Brightfold an SVG file
  * served beside the page (images/brightfold.svg: fill="currentColor", two paths, a ring that is a hole only by the
  * even-odd rule). It opens at once; `theme=light` shows the light palette, `rtl=1` reads from the right, `lang=ar`
  * is in Arabic, `icons=0` leaves the icons off, `q=` puts words in the field and `segment=` chooses a segment by its
  * key. `find=1` shows a Search Everywhere in its place (All, Files, Conversations, Messages), its Files with Match
  * case, Regex and Whole word and a file mask, which its search follows; `on=crw` turns those three on to begin with,
  * and `mask=` fills the mask. `switch=1` shows a workspace switcher shaped like Voidcraft's in its place (no segments, an
  * icon toggle at the field's end, a picture before each row's words with a spinning ring round those at work, more rows
  * than show, and a menu on a held row), to try taps on its rows with a phone's keyboard up.
  * Logs `picker chose <model>` when one is chosen, and `picker menu <row>` when a row is held. */
object PickerDemo {
  // (name, provider's key, provider's name)
  private val models: Vector[(String, String, String)] = Vector(
    ("llama-3.3-70b", "local", "Local"), ("qwen2.5-coder-32b", "local", "Local"), ("mistral-small-24b", "local", "Local"),
    ("quill-large", "quillon", "Quillon"), ("quill-medium", "quillon", "Quillon"), ("quill-small", "quillon", "Quillon"),
    ("orbit-5", "orrery", "Orrery"), ("orbit-5-mini", "orrery", "Orrery"), ("orbit-4-nano", "orrery", "Orrery"),
    ("glint-v3", "brightfold", "Brightfold"), ("glint-maverick", "brightfold", "Brightfold"),
    ("glint-235b", "brightfold", "Brightfold"))

  // a leaf, drawn in curves
  private val quill = SegmentIcon.Path("M3 21C3 10 10 3 21 3c0 11-7 18-18 18Z")
  // a hexagon with a hexagon cut from it: both run the same way, so only the even-odd rule makes the hole
  private val orbit = SegmentIcon.Path("M12 2 20.66 7v10L12 22 3.34 17V7ZM12 6.5l4.76 2.75v5.5L12 17.5l-4.76-2.75v-5.5Z",
    evenOdd = true)

  private def segments(icons: Boolean): List[Segment] = List(Segment.all("All"),
    Segment("local", "Local", if (icons) Some(SegmentIcon(MaterialIcon.Computer)) else None),
    Segment("quillon", "Quillon", if (icons) Some(quill) else None),
    Segment("orrery", "Orrery", if (icons) Some(orbit) else None),
    Segment("brightfold", "Brightfold", if (icons) Some(SegmentIcon.Url("images/brightfold.svg")) else None))

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    if (Platform.launchArg("lang") == "ar") {
      Strings.language @= Language("ar", "العربية", "Arabic", rtl = true)
      Direction.set(true)
    }
    ui.backgroundColor := Theme.background()
    // the one in use to begin with (`using=`), drawn apart in the picker
    val chose = Var(Platform.launchArg("using"))
    val finding = Platform.launchArg("find") == "1"
    val switching = Platform.launchArg("switch") == "1"
    val open = new Button(if (switching) "Switch workspace" else if (finding) "Search everywhere" else "Choose a model",
      () => if (switching) switch(chose) else if (finding) find(chose) else show(chose), style = ButtonStyle.Filled)
    val said = TextView.bound(if (chose().isEmpty) (if (finding) "Nothing chosen" else "No model chosen")
      else (if (finding) "Chose: " else "Model: ") + chose(), 14.0, Theme.muted())
    open.position.left @= 24.0
    open.position.top @= 24.0
    said.position.left @= 24.0
    said.position.top := open.position.top() + open.size.height() + 12.0
    ui.children ++= List(open, said)
    Platform.after(0.2, () => if (switching) switch(chose) else if (finding) find(chose) else show(chose))
  }

  private def show(chose: Var[String]): Unit = {
    val picker = new FilterPicker[(String, String, String)](
      q => Task.pure(models.filter(m => Match.spans(q, m._1).nonEmpty)), _._1, _._1, _._3,
      placeholder = "Choose a model", segments = segments(Platform.launchArg("icons") != "0"), groupOf = _._2,
      inUse = _._1 == chose())
    picker.chosen.attach { m =>
      chose @= m._1
      Platform.log("picker chose " + m._1)
    }
    picker.open(Platform.launchArg("q"))
    val segment = Platform.launchArg("segment")
    if (segment.nonEmpty) picker.segment @= segment
  }

  // what Search Everywhere finds: (words, kind, where), all made up
  private val everything: Vector[(String, String, String)] = Vector(
    ("val picker = new FilterPicker", "file", "app/Picker.scala:12"),
    ("FilterPicker.show(query)", "file", "app/Main.scala:40"),
    ("class PickerSpec", "file", "test/PickerSpec.scala:9"), ("# Filter pickers", "file", "docs/pickers.md:1"),
    ("filterPicker.open()", "file", "web/main.js:88"), ("Picker toggles", "conversation", "Yesterday"),
    ("Planning the filter picker", "conversation", "Monday"), ("the picker filters by file", "message", "Ada, 10:42"),
    ("PICKER is case sensitive?", "message", "Lin, 09:15"))
  private val kinds = Map("file" -> "Files", "conversation" -> "Conversations", "message" -> "Messages")
  private val matchCase = Var(false)
  private val regex = Var(false)
  private val whole = Var(false)
  private val mask = Var("")

  /** Whether `text` holds `q` as the Files options say: its case or not, a pattern or words, whole words or any. */
  private def holds(text: String, q: String): Boolean = q.isEmpty || {
    val pattern = (if (regex()) q else java.util.regex.Pattern.quote(q))
    val bounded = if (whole()) "\\b" + pattern + "\\b" else pattern
    try java.util.regex.Pattern.compile(bounded, if (matchCase()) 0 else java.util.regex.Pattern.CASE_INSENSITIVE)
      .matcher(text).find()
    catch { case _: Throwable => false }
  }

  /** Whether `path` is one the mask lets in: "*.scala, !test/" (a suffix after a star, a part of the path otherwise; a
    * "!" keeps it out). */
  private def masked(path: String): Boolean = {
    val parts = mask().split(",").map(_.trim).filter(_.nonEmpty).toList
    def hit(p: String): Boolean =
      if (p.startsWith("*")) path.takeWhile(_ != ':').endsWith(p.drop(1)) else path.contains(p)
    val (out, in) = parts.partition(_.startsWith("!"))
    (in.isEmpty || in.exists(hit)) && !out.exists(p => hit(p.drop(1)))
  }

  private def find(chose: Var[String]): Unit = {
    val on = Platform.launchArg("on")
    matchCase @= on.contains("c")
    regex @= on.contains("r")
    whole @= on.contains("w")
    mask @= Platform.launchArg("mask")
    val files = Set("file")
    lazy val picker: FilterPicker[(String, String, String)] = new FilterPicker[(String, String, String)](q =>
      Task.pure(everything.filter { case (words, kind, where) =>
        if (kind == "file" && picker.segment() == "file") holds(words, q) && masked(where)
        else Match.spans(q, words).nonEmpty
      }), e => e._1 + e._3, _._1, e => kinds(e._2) + " · " + e._3, placeholder = "Search everywhere",
      segments = List(Segment.all("All"), Segment("file", "Files"), Segment("conversation", "Conversations"),
        Segment("message", "Messages")), groupOf = _._2,
      toggles = List(PickerToggle.matchCase(matchCase, files), PickerToggle.regex(regex, files),
        PickerToggle.wholeWord(whole, files)),
      extraField = Some(PickerField.fileMask(mask, files)))
    picker.chosen.attach { e =>
      chose @= e._1
      Platform.log("picker chose " + e._1)
    }
    picker.open(Platform.launchArg("q"))
    val segment = Platform.launchArg("segment")
    if (segment.nonEmpty) picker.segment @= segment
  }

  // the workspaces a switcher offers: (name, conversations, whether one is working)
  private val spaces: Vector[(String, Int, Boolean)] = Vector(("All workspaces", 41, true), ("EveryUI", 12, true),
    ("NaboTV", 9, false), ("DataWrangler", 6, false), ("Home", 3, true), ("Writing", 7, false), ("Garden", 2, false),
    ("New workspace", 0, false)) ++ (1 to 14).map(i => (s"Project $i", i, i % 4 == 0))
  private val byName = Var(false)

  /** A workspace switcher: no segments, an icon toggle at the field's end, a picture before each row's words (a
    * spinning ring round those at work) and a menu on a held row. */
  private def switch(chose: Var[String]): Unit = {
    val points = if (ui.size.width() < 900.0) 18.0 else 14.0
    val picker = new FilterPicker[(String, Int, Boolean)](q =>
      Task.pure((if (byName()) spaces.sortBy(_._1) else spaces).filter(_._1.toLowerCase.contains(q.trim.toLowerCase))),
      _._1, _._1, s => if (s._2 == 0) "" else s"${s._2} conversations", placeholder = "Switch workspace",
      toggles = List(PickerToggle("sortByName", "", byName, "Sort by name", icon = MaterialIcon.SortByAlpha)),
      row = Some(words => new HighlightRow[(String, Int, Boolean)](_._1, s => Match.spans(words(), s._1).getOrElse(Nil),
        s => if (s._2 == 0) "" else s"${s._2} conversations", points, leading = Some { item =>
          val face = points + 8.0
          val icon = new Container
          icon.size.width @= face
          icon.size.height @= face
          item.attachAndFire(s => icon.children.replaceMaking(s.toVector.flatMap[Component] { space =>
            val glyph = new MaterialIconView(MaterialIcon.Workspaces, face * 0.8)
            glyph.color := Theme.muted()
            glyph.position.left @= face * 0.1
            glyph.position.top @= face * 0.1
            val ring = if (space._3) {
              val r = new Spinner(face + 6.0, () => Theme.accent())
              r.position.left @= -3.0
              r.position.top @= -3.0
              List(r)
            } else Nil
            glyph :: ring
          }))
          icon
        })))
    picker.chosen.attach { s =>
      chose @= s._1
      Platform.log("picker chose " + s._1)
    }
    picker.list.menu.attach(p => Platform.log("picker menu " + p.item._1))
    picker.open()
  }
}

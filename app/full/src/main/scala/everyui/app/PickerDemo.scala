package everyui.app

import everyui.async.Task
import everyui.component.{Button, ButtonStyle, TextView}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.picker.{FilterPicker, Match, Segment, SegmentIcon}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A picker narrowed by segment chips: a model picker, one row per model, filtered by its provider (All, Local,
  * Quillon, Orrery, Brightfold, all made up) on top of what is typed. Each provider's chip has an icon before its
  * words: Local a Material icon, Quillon and Orrery path data (Orrery's filled even-odd), and Brightfold an SVG file
  * served beside the page (images/brightfold.svg: fill="currentColor", two paths, a ring that is a hole only by the
  * even-odd rule). It opens at once; `theme=light` shows the light palette, `rtl=1` reads from the right, `icons=0`
  * leaves the icons off, `q=` puts words in the field and `segment=` chooses a segment by its key.
  * Logs `picker chose <model>` when one is chosen. */
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
    ui.backgroundColor := Theme.background()
    val chose = Var("")
    val open = new Button("Choose a model", () => show(chose), style = ButtonStyle.Filled)
    val said = TextView.bound(if (chose().isEmpty) "No model chosen" else "Model: " + chose(), 14.0, Theme.muted())
    open.position.left @= 24.0
    open.position.top @= 24.0
    said.position.left @= 24.0
    said.position.top := open.position.top() + open.size.height() + 12.0
    ui.children ++= List(open, said)
    Platform.after(0.2, () => show(chose))
  }

  private def show(chose: Var[String]): Unit = {
    val picker = new FilterPicker[(String, String, String)](
      q => Task.pure(models.filter(m => Match.spans(q, m._1).nonEmpty)), _._1, _._1, _._3,
      placeholder = "Choose a model", segments = segments(Platform.launchArg("icons") != "0"), groupOf = _._2)
    picker.chosen.attach { m =>
      chose @= m._1
      Platform.log("picker chose " + m._1)
    }
    picker.open(Platform.launchArg("q"))
    val segment = Platform.launchArg("segment")
    if (segment.nonEmpty) picker.segment @= segment
  }
}

package everyui.app

import everyui.async.Task
import everyui.component.{Button, ButtonStyle, TextView}
import everyui.picker.{FilterPicker, Match, Segment}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A picker narrowed by segment chips: a model picker, one row per model, filtered by its provider (All, Local,
  * Anthropic, OpenAI, Fireworks.ai) on top of what is typed. It opens at once; `theme=light` shows the light palette, `q=` puts
  * words in the field and `segment=` chooses a segment by its key.
  * Logs `picker chose <model>` when one is chosen. */
object PickerDemo {
  // (name, provider's key, provider's name)
  private val models: Vector[(String, String, String)] = Vector(
    ("llama-3.3-70b", "local", "Local"), ("qwen2.5-coder-32b", "local", "Local"), ("mistral-small-24b", "local", "Local"),
    ("opus-4.5", "anthropic", "Anthropic"), ("sonnet-4.5", "anthropic", "Anthropic"),
    ("haiku-4.5", "anthropic", "Anthropic"), ("gpt-5", "openai", "OpenAI"), ("gpt-5-mini", "openai", "OpenAI"),
    ("o4-mini", "openai", "OpenAI"), ("deepseek-v3", "fireworks", "Fireworks.ai"),
    ("llama-4-maverick", "fireworks", "Fireworks.ai"), ("qwen3-235b", "fireworks", "Fireworks.ai"))

  private val segments = List(Segment.all("All"), Segment("local", "Local"), Segment("anthropic", "Anthropic"),
    Segment("openai", "OpenAI"), Segment("fireworks", "Fireworks.ai"))

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
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
      placeholder = "Choose a model", segments = segments, groupOf = _._2)
    picker.chosen.attach { m =>
      chose @= m._1
      Platform.log("picker chose " + m._1)
    }
    picker.open(Platform.launchArg("q"))
    val segment = Platform.launchArg("segment")
    if (segment.nonEmpty) picker.segment @= segment
  }
}

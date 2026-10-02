package everyui.app

import everyui.component.{Button, ButtonStyle, Container, Row, ScrollContainer, TextView}
import everyui.markdown.{MarkdownStyle, MarkdownView}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** Markdown drawn by EveryUI (`MarkdownView`): a document with every kind of block in a chat's bubble, and the same
  * document streamed in a few characters at a time, as an answer arrives. `theme=light` shows the light palette,
  * `stream=1` streams from the start, and `at=<characters>` stops the stream there (a frame of it, part written).
  * Each stream logs what an append cost:
  *
  *   [ui] markdown streamed 3021 characters in 756 appends, 0.21 ms an append
  */
object MarkdownDemo {
  val sample: String = List(
    "# Markdown in EveryUI",
    "",
    "A paragraph with *emphasis*, **strong words**, ***both***, ~~struck~~, `inline code`, a [link](https://everyui.com)",
    "and a bare one: https://example.com/docs. Line ends are soft,",
    "unless a backslash (or two spaces) ends one:\\",
    "this starts a new line.",
    "",
    "## Lists",
    "",
    "- Bullets, with **bold** in them",
    "- Nested:",
    "  - a second level",
    "    - and a third",
    "- [x] a task that is done",
    "- [ ] one that is not",
    "",
    "1. Numbered",
    "2. In order",
    "3. With `code`",
    "",
    "## Code",
    "",
    "```scala",
    "// a comment, then code",
    "case class Message(id: String, text: String, streaming: Boolean = false)",
    "val answer = messages.filter(_.streaming).map(m => m.text.length).sum + 42 // a line longer than a phone is wide",
    "```",
    "",
    "```bash",
    "sbt \"desktop/testOnly everyui.markdown.MarkdownSpec\" # run the spec",
    "```",
    "",
    "> A quote, with *emphasis*,",
    "> over two lines.",
    ">",
    "> > And one inside it.",
    "",
    "### A table",
    "",
    "| Provider | Models | Local | Price |",
    "|:---------|:------:|:-----:|------:|",
    "| Anthropic | 3 | no | $$$ |",
    "| Fireworks.ai | 12 | no | $ |",
    "| Ollama | any | yes | free |",
    "",
    "One wider than a phone, whose cells wrap their words:",
    "",
    "| Step | What it does | Where it fails | Fix |",
    "|------|--------------|----------------|-----|",
    "| Parse | Reads the request and checks its fields | A field missing or of the wrong type | `validate(request)` before use |",
    "| Load | Fetches the user's settings from the store | The store times out under load | Retry with backoff, then catch the error |",
    "| Render | Lays the page out and draws it | `reveal(row)` with nothing focused | Focus the first row |",
    "",
    "One whose code cannot wrap, which scrolls sideways:",
    "",
    "| File | Notes |",
    "|------|-------|",
    "| `shared/src/main/scala/everyui/markdown/TableView.scala` | Where tables are drawn |",
    "",
    "---",
    "",
    "An image, sized to fit: ![The EveryUI mark](images/everyui-mark.png)",
    "",
    "Raw HTML shows as text: <b>not bold</b>.",
    "",
    "#### Smaller heading",
    "",
    "The end, with an entity &copy; and an escaped \\*star\\*.").mkString("\n")

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val width = Val(math.min(760.0, ui.size.width() - 32.0))
    val view = new MarkdownView(sample, MarkdownStyle.compact, base = "")
    val bubble = new Container {
      backgroundColor := Theme.surface()
      radius @= 14.0
      borderWidth @= 1.0
      borderColor := Theme.border()
    }
    view.position.left @= 14.0
    view.position.top @= 12.0
    view.size.width := width() - 28.0
    bubble.size.width := width()
    bubble.size.height := view.size.height() + 24.0
    bubble.children += view
    val status = Var("")
    val stream = new Button("Stream it", () => streamed(view, -1, status), style = ButtonStyle.Filled)
    val theme = new Button("Light / dark", () =>
      Theme.palette @= (if (Theme.palette() eq Palette.light) Palette.dark else Palette.light))
    val bar = Row(10.0, middle = true)(stream, theme, TextView.bound(status(), 12.0, Theme.muted()))
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height() - 64.0
      position.top @= 64.0
    }
    bar.position.left @= 16.0
    bar.position.top @= 12.0
    bubble.position.left := (ui.size.width() - width()) / 2.0
    bubble.position.top @= 4.0
    scroll.content.children += bubble
    ui.children ++= List(bar, scroll)
    val at = Platform.launchArg("at")
    if (Platform.launchArg("stream") == "1" || at.nonEmpty) streamed(view, if (at.isEmpty) -1 else at.toInt, status)
  }

  /** `view` emptied and the sample streamed into it three or four characters every frame or so, stopping at `until`
  * characters (the whole of it where that is -1). */
  private def streamed(view: MarkdownView, until: Int, status: Var[String]): Unit = {
    view.content @= ""
    step(view, 0, if (until < 0) sample.length else math.min(until, sample.length), 0, 0.0, status)
  }

  private def step(view: MarkdownView, at: Int, until: Int, appends: Int, spent: Double, status: Var[String]): Unit =
    if (at >= until) {
      val each = if (appends == 0) 0.0 else math.round(spent / appends * 100.0) / 100.0
      val said = "streamed " + at.toString + " characters in " + appends.toString + " appends, " + each.toString +
        " ms an append"
      status @= said
      Platform.log("markdown " + said)
    }
    else {
      val next = math.min(until, at + 3 + (at % 2))
      val start = Platform.ticks()
      view.content @= sample.substring(0, next)
      val took = Platform.ticks() - start
      status @= "streaming: " + next.toString + " of " + until.toString
      Platform.after(0.016, () => step(view, next, until, appends + 1, spent + took, status))
    }
}

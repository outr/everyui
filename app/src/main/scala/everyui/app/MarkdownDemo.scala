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
  val sample: String = (List(
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
  ) ++ (if (everyui.render.TableAccess.rich()) MarkdownDemo.tables else Nil) ++ List(
    "---",
    "",
    "An image, sized to fit: ![The EveryUI mark](images/everyui-mark.png)",
    "",
    "Raw HTML shows as text: <b>not bold</b>.",
    "",
    "#### Smaller heading",
    "",
    "The end, with an entity &copy; and an escaped \\*star\\*.")).mkString("\n")

  /** Tables an agent might answer with: sixty personas and their conversations (long enough to offer a filter and to
    * scroll under its header), numbers, dates and currency that sort by their values, and one of many columns. Where
    * tables sort and filter (not a Roku or an Apple TV, whose plain tables would only take long to draw sixty rows). */
  def tables: List[String] = {
    val names = Vector("Ada", "Grace", "Alan", "Edsger", "Barbara", "Donald", "Margaret", "Ken", "Dennis", "Frances",
      "Leslie", "Radia")
    val roles = Vector("Researcher", "Reviewer", "Planner", "Coder", "Tester")
    val personas = (0 until 60).toList.map { i =>
      val count = (i * 7919 + 13) % 2400
      val month = 1 + (i * 5) % 12
      val day = 1 + (i * 11) % 28
      "| " + names(i % names.size) + " the " + roles(i % roles.size) + " | " + grouped(count) + " | " +
        "2026-" + (if (month < 10) "0" else "") + month.toString + "-" + (if (day < 10) "0" else "") + day.toString +
        " | " + (if (i % 9 == 4) "—" else "$" + grouped((i * 104729) % 90000)) + " |"
    }
    List("A long one, of sixty personas and their conversations, that offers a filter and scrolls under its header:",
      "",
      "| Persona | Conversations | Last active | Spend |",
      "|---------|--------------:|-------------|------:|") ++ personas ++ List(
      "",
      "Numbers, dates and currency, which sort by their values:",
      "",
      "| Release | Shipped | Size | Change | Price |",
      "|---------|---------|-----:|-------:|------:|",
      "| 2.10 | Mar 3, 2026 | 1.2 GB | +4.5% | $1,299.00 |",
      "| 2.9 | 12/28/2025 | 980 MB | −2.1% | $999.00 |",
      "| 2.11 | 2026-06-30 | 1.4 GB | +12% | €1.149,50 |",
      "| 3.0 beta | 9 October 2026 | 2.1 GB | (3.0%) | £849 |",
      "| 2.8 | 2025-07-01 | 15 MB | 0% | free |",
      "",
      "One of many columns:",
      "",
      "| Model | Context | Input | Output | Cached | Vision | Tools | Streaming | Region | Notes |",
      "|-------|--------:|------:|-------:|-------:|:------:|:-----:|:---------:|--------|-------|",
      "| Large | 1,000,000 | $15.00 | $75.00 | $1.50 | yes | yes | yes | us-east-1 | The most capable, for long work |",
      "| Medium | 200,000 | $3.00 | $15.00 | $0.30 | yes | yes | yes | eu-west-1 | Fast and capable |",
      "| Small | 200,000 | $0.80 | $4.00 | $0.08 | no | yes | yes | ap-south-1 | Cheapest, for routing |",
      "")
  }

  /** `n` with commas between its thousands. */
  private def grouped(n: Int): String =
    if (n < 1000) n.toString else grouped(n / 1000) + "," + (n % 1000 + 1000).toString.substring(1)

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

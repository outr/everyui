package everyui.app

import everyui.Color
import everyui.chat.*
import everyui.component.Backdrop
import everyui.font.MaterialIcon
import everyui.markdown.MarkdownView
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import everyui.util.Clock
import reactify.*

/** Bubbles whose words wrap to a second line in a face other than the page's own: inline code (Roboto Mono, which the
  * page loads only once it is drawn), bold and italic mixed, a link, one's own, and after a second one streamed in
  * over four seconds. Each bubble must hold every line, the meta line under it. `theme=light`. Logs `bubbles ok`, and
  * `bubbles streamed` once the last is whole.
  *
  * `code=1` adds a reply with a code block and a terminal's coloured output, and a tool call that ran the tests, its
  * command and output in its body as code, opened onto its details (`tool=closed` leaves it closed);
  * `backdrop=<picture>` draws the picture behind the conversation with the theme's surfaces at `see` (0.8 unless
  * given, `Theme.surfaceOpacity`), so the bubbles, the tool call's card and the code wash let it through.
  * `codeOpacity=<0 to 1>` draws the code wash that whole (`MarkdownView.codeOpacity`), and `codeContrast=<ratio>`
  * gives the contrast its words keep where it is worked out (`MarkdownView.codeContrast`, 4.5 unless given). */
object BubbleDemo {
  private def luma: Participant = Participant("agent-luma", "Luma")
  private def you: Participant = Participant("u-you", "You")

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val at = Clock.real()
    def said(id: String, who: Participant, text: String, meta: String, self: Boolean = false): ChatEntry.Message =
      ChatEntry.message(id, who, self, text, Some(at), meta = Some(meta))
    val entries: Var[Vector[ChatEntry]] = Var(Vector(
      // a title and a paragraph that wraps to three lines on a phone at a larger font scale, its last line's descenders
      // (g, y) inside the bubble
      said("gate", Participant("agent-gate", "The Gatekeeper"), "**The Gatekeeper**\n\nI stand here at the threshold " +
        "of the void. I am the Gatekeeper. Seekers of passage must prove their identity before entry is granted. What " +
        "brings you to these gates?", "6m"),
      said("code1", luma, "I see the Firebase key is live and in `resources/`. I'm going to move it to " +
        "`.env.firebase` now, then we can delete the old file.", "Qwen3.5-9b · 43s"),
      said("code2", luma, "Oh for heaven's sake, I already moved that Firebase key to `.env.firebase` before you " +
        "stopped me. It's not in `resources/` anymore. I just confirmed by reading the old path—it's gone.",
        "Qwen3.5-9b · 28.4k tokens · 23s"),
      said("mix", luma, "The **release notes** are *nearly* done: ***two sections*** left, `CHANGELOG.md` and " +
        "**`build.sbt`**'s version, which _still says_ 1.9 where it should say 2.0 by now.", "Qwen3.5-9b · 12s"),
      said("link", luma, "The failing run is [run 412 on the CI server](https://ci.example.com/runs/412), and its " +
        "log says `SocketSpec` timed out on reconnect after **30 seconds** of waiting.", "Qwen3.5-9b · 9s"),
      said("own", you, "Move `voidcraft-eb24f-firebase-adminsdk.json` out of `backend/src/main/resources/` and " +
        "into `.env.firebase`, then delete it.", "Sent", self = true)
    ))
    if (Platform.launchArg("code") == "1") {
      val ran = ChatEntry.activity("ran", "Ran the tests", ActivityStatus.Failed,
        Some(() => ActivityView.textDetails("sbt test", "1 failed")), Some(at),
        ActivityLead.Icon(MaterialIcon.Terminal), BubbleDemo.ran, aside = "9:42:13 AM · 12 s")
      ran.expanded @= Platform.launchArg("tool") != "closed"
      entries @= entries() :+ ran :+ said("code", luma, BubbleDemo.code, "Qwen3.5-9b · 4s")
    }
    val chat = new ChatView(entries)
    chat.size.width := ui.size.width()
    chat.size.height := ui.size.height()
    val picture = Platform.launchArg("backdrop")
    if (picture.nonEmpty) {
      val see = Platform.launchArg("see")
      Theme.surfaceOpacity @= (if (see.nonEmpty) see.toDouble else 0.8)
      val opacity = Platform.launchArg("codeOpacity")
      if (opacity.nonEmpty) MarkdownView.codeOpacity @= Some(opacity.toDouble)
      val contrast = Platform.launchArg("codeContrast")
      if (contrast.nonEmpty) MarkdownView.codeContrast @= contrast.toDouble
      val back = new Backdrop
      back.size.width := ui.size.width()
      back.size.height := ui.size.height()
      back.source @= picture
      chat.backgroundColor @= Color.Clear
      ui.children += back
    }
    ui.children += chat
    Platform.after(1.0, () => {
      val m = ChatEntry.message("stream", luma, self = false, time = Some(Clock.real()), streaming = true)
      entries @= entries() :+ m
      val text = "Streaming now: the key is in `.env.firebase`, **not** in `resources/`, and the old " +
        "`voidcraft-eb24f-firebase-adminsdk.json` is gone. I *checked* `git status` too: nothing staged, nothing " +
        "left behind in `backend/src/main/resources/`, and `.gitignore` names `.env.firebase`."
      stream(m.text, text, 0, () => {
        m.streaming @= false
        m.meta @= Some("Qwen3.5-9b · streamed")
        Platform.log("bubbles streamed")
      })
    })
    Platform.log("bubbles ok")
  }

  // a tool call's body: the command it ran and what the terminal said (`code=1`)
  private val ran: String = "```sh\nsbt 'testOnly *GroundSpec'\n```\n\n```ansi\n" +
    "\u001b[1;32m[info]\u001b[0m Compiling 3 Scala sources\n\u001b[31m[error]\u001b[0m GroundSpec: 1 failed\n```"

  // a reply with a code span, a code block and a terminal's output in its colours (`code=1`)
  private val code: String = "Run `sbt test` again; the spec is:\n\n```scala\nobject Ground {\n  // a comment, " +
    "drawn muted\n  def half(x: Double): Double = x / 2.0\n  val name = " +
    "\"code\"\n}\n```\n\nand the run said:\n\n```ansi\n\u001b[1;32m[info]\u001b[0m Compiling 3 Scala sources\n" +
    "\u001b[33m[warn]\u001b[0m one deprecation\n\u001b[31m[error]\u001b[0m GroundSpec: 1 failed\n" +
    "\u001b[36m[info]\u001b[0m Total time: \u001b[1m12 s\u001b[0m\n```"

  // `text` into `into` a few letters at a time over about four seconds, then `done`
  private def stream(into: Var[String], text: String, at: Int, done: () => Unit): Unit =
    if (at >= text.length) done()
    else Platform.after(0.05, () => {
      val to = math.min(text.length, at + 3)
      into @= text.substring(0, to)
      stream(into, text, to, done)
    })
}

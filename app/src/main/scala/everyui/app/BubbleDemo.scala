package everyui.app

import everyui.chat.*
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import everyui.util.Clock
import reactify.*

/** Bubbles whose words wrap to a second line in a face other than the page's own: inline code (Roboto Mono, which the
  * page loads only once it is drawn), bold and italic mixed, a link, one's own, and after a second one streamed in
  * over four seconds. Each bubble must hold every line, the meta line under it. `theme=light`. Logs `bubbles ok`, and
  * `bubbles streamed` once the last is whole. */
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
    val chat = new ChatView(entries)
    chat.size.width := ui.size.width()
    chat.size.height := ui.size.height()
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

  // `text` into `into` a few letters at a time over about four seconds, then `done`
  private def stream(into: Var[String], text: String, at: Int, done: () => Unit): Unit =
    if (at >= text.length) done()
    else Platform.after(0.05, () => {
      val to = math.min(text.length, at + 3)
      into @= text.substring(0, to)
      stream(into, text, to, done)
    })
}

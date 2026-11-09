package everyui.app

import everyui.async.Task
import everyui.chat.*
import everyui.render.Platform
import everyui.task.Frames
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A conversation with a coding agent in a `Transcript` filling the window, its turns named by the turn header
  * (`Transcript.turnHeader`): each of your messages starts a turn, followed by the agent's reasoning, a tool call or
  * two and a long answer. Scrolled up past one of your messages, the header names it over the top edge, cut to one
  * line, with the file that went with it where one did (the third turn's), and a spinner while the newest turn is still
  * running; a press on it goes back to the message. Ten earlier turns come a page at a time as the top nears. Your
  * messages here are given new ids once "sent" (the newest goes from `local-…` to `t…` a second in), as an app's are
  * when its server answers. It runs the frame loop itself (`Frames.run`), as an app's ScreenManager does, so the
  * press glides. `theme=light`, `room=1` names each message's author on the header, `busy=0` leaves the
  * newest turn done, and `at=<pixels>` scrolls that far from the top once built. Logs `turns ok` once built,
  * and `turns header <id>` as the header names another turn (`none` as it hides). */
object TurnHeaderDemo {
  private def you: Participant = Participant("u-you", "You")
  private def agent: Participant = Participant("agent-coder", "Coder")
  private def sam: Participant = Participant("u-sam", "Sam")

  private val asks = Vector(
    "Why does the socket drop after a minute idle? It happens on the staging server only, never locally.",
    "Add a heartbeat, then.",
    "Here is the log from last night's run, can you see where it gives up?",
    "Make the reconnect back off, doubling each time up to a minute, and log each attempt with how long it waited.",
    "Write a spec for that, and run the whole suite.",
    "The release notes need a line for this too, short, for people who run their own server.",
    "Ship it."
  )

  // a turn: your message, the agent's reasoning, a tool call, and its answer
  private def turn(i: Int, base: Int, room: Boolean): Vector[ChatEntry] = {
    val ask = if (i < asks.length) asks(i) else "Earlier question " + (base + i) + ": what did the last run say?"
    val author = if (room && i % 2 == 1) sam else you
    val id = "t" + (base + i)
    Vector(
      ChatEntry.message(id, author, self = author == you, ask),
      ChatEntry.thinking(id + "-think", 0L, "Looking at what the question needs.", Some(4200L)),
      ChatEntry.activity(id + "-run", "Ran a command", ActivityStatus.Succeeded, None, None, ActivityLead.None,
        "`sbt test` in `core`"),
      ChatEntry.message(id + "-answer", agent, self = false, "## Turn " + (base + i) + "\n\nThe socket closes when " +
        "nothing crosses it for sixty seconds, which the staging proxy enforces and a local run never meets. " +
        "A heartbeat every 25 seconds keeps it open, and the reconnect now waits longer each time.\n\n" +
        "```scala\nval delay = math.min(max, base * math.pow(2.0, attempt))\nsend(Ping)\n```\n\n" +
        "I ran the tests again and they pass.")
    )
  }

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val room = Platform.launchArg("room") == "1"
    val busy = Var(Platform.launchArg("busy") != "0")
    // the newest turn's message, as yet under the id it was sent with
    val newest = asks.length - 1
    val earlier = (0 until newest).toVector.flatMap(i => turn(i, 100, room))
    val sent = ChatEntry.message("local-" + newest, you, self = true, asks(newest))
    // a long answer, still coming: at the bottom its question is out of sight above it, and the header names it
    val reply = ChatEntry.message("reply-" + newest, agent, self = false, "## Shipping\n\n" + ("The tag is on its " +
      "way: the release notes have their line, the suite passed on every platform, and the staging server has run " +
      "the new reconnect overnight without a drop. ") * 8 + "\n\n```\n" +
      (1 to 24).map(i => "reconnect attempt " + i + " waited " + math.min(60, 1 << math.min(i, 6)) + "s: ok")
        .mkString("\n") +
      "\n```", streaming = true)
    val entries: Var[Vector[ChatEntry]] = Var(earlier ++ Vector(sent, reply))
    // ten earlier turns, five a page
    var left = 10
    val older: () => Task[Vector[ChatEntry]] = () => Task.async[Vector[ChatEntry]] { done =>
      Platform.after(0.4, () => {
        val to = left
        val from = math.max(0, to - 5)
        left = from
        done((from until to).toVector.flatMap(i => turn(i, 0, room = false)))
      })
    }
    val transcript = new Transcript(entries, Some(older))
    transcript.dayMarkers @= false
    // the files that went with a turn
    val files = Map("t102" -> "staging-socket-2026-11-07.log")
    transcript.turnHeader @= Some((e: ChatEntry) => e match {
      case m: ChatEntry.Message if m.self || m.author.id == "u-sam" =>
        val running = if (m.id == "local-" + newest || m.id == "t" + (100 + newest)) busy else Val(false)
        Some(TurnHeader.of(m, named = room, attachment = files.get(m.id), busy = running))
      case _ => None
    })
    transcript.size.width := ui.size.width()
    transcript.size.height := ui.size.height()
    ui.children += transcript
    transcript.turn.attach(t => Platform.log("turns header " + t.map(_.id).getOrElse("none")))
    // the server's answer: the message sent takes its own id, and the reply streams on
    Platform.after(1.0, () => {
      val kept = ChatEntry.message("t" + (100 + newest), you, self = true, asks(newest))
      entries @= entries().map(e => if (e eq sent) kept else e)
      reply.text @= reply.text() + "\n\nThe build is green and the notes are in."
    })
    // the frame loop an app's ScreenManager runs, for the header's glide back to a turn
    var last = -1.0
    Frames.run(_ => {
      val now = Platform.ticks()
      ui.update(if (last < 0.0) 0.0 else (now - last) / 1000.0)
      last = now
    }, () => ui.needsFrames, () => last = -1.0)
    val at = Platform.launchArg("at")
    if (at.nonEmpty) Platform.after(0.3, () => transcript.list.scrollTop(at.toDouble))
    Platform.log("turns ok")
  }
}

package everyui.app

import everyui.async.Task
import everyui.chat.*
import everyui.component.*
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** How fast a long conversation opens: `n` entries (50 unless given) as a coding agent's transcript has them, long
  * Markdown answers with code blocks, tables and lists, tool calls led by somebody's avatar or an icon with a body under
  * them, pictures, short replies, and some from each of four people, built into a `ChatView` `at` seconds after the app
  * starts (1 unless given), once the page has settled. It logs `chatperf built <ms> from <tick>` for the construction
  * and the view put on the screen, then `chatperf ok <first row shown> <ms> rows <rows made>` once it has drawn.
  * `reply=<words>` adds the agent's answer in those words as the newest entry, and `older=<seconds>` has earlier
  * pages come that long after they are asked for. `theme=light` for the light theme,
  * `rtl=1` for right to left. */
object ChatPerfDemo {
  private def you: Participant = Participant("u-you", "You")
  private def sam: Participant = Participant("u-sam", "Sam", Some(Platform.resolvePath("/images/everyui-mark.png")))
  private def agent: Participant = Participant("agent-coder", "Coder", Some(Platform.resolvePath("/images/icon.png")))
  private def scout: Participant = Participant("agent-scout", "Scout")

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val n = Platform.launchArg("n").toIntOption.getOrElse(50)
    val at = Platform.launchArg("at").toDoubleOption.getOrElse(1.0)
    Platform.after(at, () => {
      // (and with reply=, the agent's answer in those words last)
      val reply = Platform.launchArg("reply")
      val entries = Var(page(n, 1790000000000L) ++ (if (reply.isEmpty) Vector.empty
        else Vector(ChatEntry.message("reply", agent, self = false, reply, Some(1790000000000L),
          meta = Some("model large")))))
      val started = Platform.ticks()
      val chat = new ChatView(entries, older)
      chat.size.width := ui.size.width()
      chat.size.height := ui.size.height()
      ui.children += chat
      Platform.log("chatperf built " + (Platform.ticks() - started).toInt.toString + " from " + started.toInt.toString)
      Platform.after(0.0, () => {
        val t = chat.transcript
        val first = if (t.list.count == 0) "" else t.list.keyAt(t.list.indexAt(t.list.offset()))
        val made = t.list.rows.count(r => r.visible() && r.item().nonEmpty)
        Platform.log("chatperf ok " + first + " " + (Platform.ticks() - started).toInt.toString + " rows " +
          made.toString)
      })
    })
  }

  // with older=<seconds>, earlier pages of three short messages, each that long in coming, sixty of them
  private def older: Option[() => Task[Vector[ChatEntry]]] = Platform.launchArg("older").toDoubleOption.map { wait =>
    var pages = 0
    () => Task.async[Vector[ChatEntry]] { done =>
      Platform.after(wait, () => {
        pages += 1
        done(if (pages > 60) Vector.empty else (0 until 3).toVector.map(i => ChatEntry.message("o" + pages.toString +
          "-" + i.toString, sam, self = false, "Earlier " + pages.toString, Some(1780000000000L + pages * 3000L + i))))
      })
    }
  }

  /** `n` entries, oldest first, one a minute up to `end`. */
  def page(n: Int, end: Long): Vector[ChatEntry] = (0 until n).toVector.map(i => entry(i, end - (n - i).toLong * 60000L))

  private def entry(i: Int, time: Long): ChatEntry = i % 10 match {
    case 0 => ChatEntry.message("p" + i.toString, you, self = true, ask(i), Some(time))
    case 1 => ChatEntry.activity("p" + i.toString, "Read " + file(i), ActivityStatus.Succeeded,
      Some(ChatEntry.textDetails("read " + file(i), "212 lines")), Some(time), ActivityLead.Icon(MaterialIcon.Description),
      "`" + file(i) + "`, lines 1 to 212")
    case 2 => ChatEntry.activity("p" + i.toString, "Ran a command", ActivityStatus.Succeeded,
      Some(ChatEntry.textDetails("sbt test", "[info] 412 passed")), Some(time), ActivityLead.Icon(MaterialIcon.Terminal),
      "```\nsbt \"core/testOnly *SocketSpec\" 2>&1 | tail -40\n```")
    case 3 => ChatEntry.message("p" + i.toString, agent, self = false, answer(i), Some(time),
      meta = Some("model large · " + (900 + i * 7).toString + " tokens · 4.2s"))
    case 4 => ChatEntry.activity("p" + i.toString, "Message to Scout", if (i % 3 == 0) ActivityStatus.Failed
      else ActivityStatus.Succeeded, None, Some(time), ActivityLead.Person(scout),
      "Scout, can you look through **" + file(i) + "** for anything that still calls the old reconnect policy? " +
        "List each place with its line, and say whether it needs the new backoff or can keep the fixed delay. " +
        "Don't change anything yet; I want to see the list first.")
    case 5 => ChatEntry.message("p" + i.toString, scout, self = false, "Found " + (i % 7 + 2).toString +
      " places:\n\n- `" + file(i) + ":88` needs the backoff\n- `" + file(i + 1) + ":140` can keep it\n- the test " +
      "helper in `SocketSpec` fakes the clock, so it is fine either way", Some(time))
    case 6 => ChatEntry.message("p" + i.toString, sam, self = false, if (i % 20 == 6) "Looks good to me 👍" else
      "Here is how it looks on my machine:\n\n![the reconnect banner](/images/everyui-mark.png)\n\nThe banner stays " +
        "up a second too long after the socket is back.", Some(time))
    case 7 => ChatEntry.activity("p" + i.toString, "Searched the repository for “ReconnectPolicy”",
      ActivityStatus.Succeeded, Some(ChatEntry.textDetails("grep -rn ReconnectPolicy src/", "src/net/Socket.scala:88")),
      Some(time), ActivityLead.Icon(MaterialIcon.Search))
    case 8 => ChatEntry.message("p" + i.toString, agent, self = false, table(i), Some(time),
      meta = Some("model large · " + (400 + i * 3).toString + " tokens · 2.1s"))
    case _ => ChatEntry.message("p" + i.toString, you, self = true, if (i % 20 == 9) "Thanks, go ahead." else
      "Yes, but keep the fixed delay for the tests, and add a note to the changelog.", Some(time))
  }

  private def file(i: Int): String = "src/main/scala/net/transport/" + List("Socket", "ReconnectPolicy", "Backoff",
    "DurableSocket", "Heartbeat")(i % 5) + ".scala"

  private def ask(i: Int): String = "Step " + (i / 10 + 1).toString + ": the socket drops after a minute idle on the " +
    "staging server. Can you find why, and fix it so it reconnects with a backoff instead of hammering the server? " +
    "Keep the change small."

  private def answer(i: Int): String = "## What I found\n\nThe socket is closed by the proxy after **60 s** without " +
    "traffic, and `" + file(i) + "` reconnects at once, every time, so a dead server is asked again and again. Two " +
    "changes fix it:\n\n1. A heartbeat every 25 s keeps the connection alive through the proxy.\n2. Reconnecting " +
    "waits longer each time, up to 30 s, with some jitter so a fleet of clients does not come back together.\n\n" +
    "```scala\nfinal case class Backoff(base: Double = 0.5, max: Double = 30.0) {\n  def delay(attempt: Int): Double " +
    "= {\n    val d = math.min(max, base * math.pow(2.0, attempt.toDouble))\n    d * (0.8 + Random.nextDouble() * " +
    "0.4)\n  }\n}\n```\n\nThe heartbeat goes in `DurableSocket`, where the timer already is:\n\n```scala\nheartbeat" +
    " = Platform.every(25.0, () => if (open) send(Ping))\n```\n\n> The proxy's idle limit is not ours to change; " +
    "the heartbeat is the usual answer.\n\nI ran `SocketSpec` again and it passes, " + (400 + i).toString +
    " tests in all. See [the proxy docs](https://example.com/proxy#idle) for the limit."

  private def table(i: Int): String = "The runs before and after:\n\n| Run | Drops | Reconnects | Longest gap |\n" +
    "|---|---|---|---|\n| before | 14 | 1,212 | 0.1 s |\n| after | 0 | 3 | 2.4 s |\n| after, server down | — | " +
    (11 + i % 4).toString + " | 30 s |\n\nEverything else is unchanged."
}

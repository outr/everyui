package everyui.app

import everyui.async.Task
import everyui.Color
import everyui.chat.*
import everyui.component.*
import everyui.component.types.{FontWeight, TextDirection, WhiteSpace}
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import everyui.util.Clock
import reactify.*

/** A group room in `everyui.chat.ChatView`: three people (one of them you, one writing in Arabic) and two agents, with
  * 2000 earlier entries to scroll back through, fetched a hundred at a time. Once it opens, timers play a conversation:
  * an agent thinks, messages The Oracle (the words it sent arriving after it starts, and failing), runs a tool that
  * succeeds and one that fails, and streams a long reply while the status bar shows who is working; another asks for
  * approval with a card of its own. Before them, tool calls led by The Oracle's portrait (with the message sent, cut
  * to three lines), a search icon and a terminal icon (with the command). Each message runs the way its own words do,
  * the Arabic ones right to left and the English ones left to right whichever way the app reads, and a long link, path
  * and hash break inside their bubble. Messages are Markdown: the long reply has a list, a table, code and a link, and an
  * Arabic one a list whose bullets stand on its right. What is typed in the field at the bottom is sent as yours, and
  * answered. Resting the pointer on a message, or the cursor with the focus on the transcript, shows Copy message
  * beside it (`Transcript.messageActions`), where there is a clipboard. Sam has a picture, which a click on his avatar
  * or name shows whole; `author=log` logs the press (`chat author <id> <message>`) in its place. `theme=light` for
  * the light theme, `rtl=1` for right to left. `rails=1` gives every entry its topic's rail (`ChatEntry.rail`), one
  * of twelve colours by the topic's index (`topicColors`), each topic begun by its marker; a reply gets its rail once
  * it is whole. Logs `chat ok` once built. */
object ChatDemo {
  private def you: Participant = Participant("u-you", "You")
  private def sam: Participant = Participant("u-sam", "Sam", Some(Platform.resolvePath("/images/everyui-mark.png")))
  private def helper: Participant = Participant("agent-helper", "Helper")
  private def scout: Participant = Participant("agent-scout", "Scout")
  private def layla: Participant = Participant("u-layla", "ليلى")
  private def oracle: Participant =
    Participant("agent-oracle", "The Oracle", Some(Platform.resolvePath("/images/icon.png")))

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val start = Clock.real()
    val entries: Var[Vector[ChatEntry]] = Var(opening(start))
    val working: Var[Vector[Working]] = Var(Vector.empty)
    val connection: Var[ConnectionState] = Var(ConnectionState.Connected)
    // the earlier history, fetched from the newest back: how many are still to come
    val left: Var[Int] = Var(ChatDemo.history)
    val older: () => Task[Vector[ChatEntry]] = () => Task.async[Vector[ChatEntry]] { done =>
      Platform.after(0.6, () => {
        val to = left()
        val from = math.max(0, to - ChatDemo.page)
        left @= from
        done((from until to).toVector.map(i => past(i, start)))
      })
    }
    val field = new TextInput("Message the room")
    val composer = new Container {
      size.height @= 72.0
      backgroundColor := Theme.surface()
      children += field
    }
    field.position.left @= 12.0
    field.position.top @= 8.0
    field.size.width := composer.size.width() - 24.0
    topicAt = 21
    val chat = new ChatView(entries, Some(older), working, connection, Some(composer))
    chat.rails @= railed
    chat.size.width := ui.size.width()
    chat.size.height := ui.size.height()
    field.submitted.attach { text =>
      if (text.trim.nonEmpty) {
        field.value @= ""
        add(entries, now(ChatEntry.message("you-" + Clock.real().toString, you, self = true, text, Some(Clock.real()))))
        Platform.after(0.8, () => reply(entries, working, scout, "Noted: \"" + text.trim + "\". I'll fold it into the plan."))
      }
    }
    if (Platform.launchArg("author") == "log")
      chat.onAuthor @= Some((p: AuthorPressed) =>
        Platform.log("chat author " + p.participant.id + " " + p.messageId.getOrElse("-")))
    ui.children += chat
    chat.focus()
    play(entries, working, connection, chat)
    if (Platform.launchArg("probe") == "1") probe(chat)
    Platform.log("chat ok " + chat.size.width().toInt.toString + "x" + chat.size.height().toInt.toString)
  }

  // twice a second, where the transcript stands: whether it is pinned, how far down of how far, what shows first
  private def probe(chat: ChatView): Unit = Platform.after(0.5, () => {
    val t = chat.transcript
    val first = if (t.list.count == 0) -1 else t.list.indexAt(t.list.offset())
    Platform.log("chat probe pinned=" + t.pin.pinned().toString + " unseen=" + t.pin.unseen().toString +
      " offset=" + t.list.offset().toInt.toString + " furthest=" + (t.list.total - t.list.size.height()).toInt.toString +
      " entries=" + t.entries().size.toString + " loading=" + t.loading().toString +
      " first=" + (if (first < 0) "" else t.list.keyAt(first) + "@" + (t.list.top(first) - t.list.offset()).toInt.toString))
    probe(chat)
  })

  /** What was sent to The Oracle: long enough to be cut to three lines until its line is opened. */
  val toOracle: String = "Hello Oracle. Luma here. Can you help me create a new Persona? I would like one that reviews " +
    "pull requests the way our team does: it reads the diff, runs the tests, points out anything that breaks the style " +
    "guide in `CONTRIBUTING.md`, and writes its findings as **short** comments rather than one long essay. It should " +
    "also know when to stay quiet, and say so."

  /** How many earlier entries there are, and how many come at a time. */
  val history: Int = 2000
  val page: Int = 100

  private def add(entries: Var[Vector[ChatEntry]], e: ChatEntry): Unit = entries @= entries() :+ e

  /** A topic's rail colour, by its index, wrapping: twelve that read on the dark theme and the light. */
  val topicColors: Vector[Color] = Vector("#e5484d", "#f76b15", "#ffb224", "#99d52a", "#30a46c", "#12a594",
    "#05a2c2", "#0090ff", "#3e63dd", "#8e4ec6", "#d6409f", "#a18072").map(Color.fromHex)

  private def railed: Boolean = Platform.launchArg("rails") == "1"
  // the topic the conversation is in, by its index in the topic history: the earlier history's are Sprint 0 to 20
  private var topicAt: Int = 21
  private def topicName(index: Int): String = index match {
    case 21 => "Release planning"
    case 22 => "Release 2.0 plan"
    case _ => "Sprint " + index.toString
  }

  // `e` in topic `index`, where the demo shows rails
  private def topic[E <: ChatEntry](e: E, index: Int): E = {
    if (railed) {
      e.rail @= Some(topicColors(index % topicColors.length))
      e.railLabel @= Some(topicName(index))
    }
    e
  }
  private def now[E <: ChatEntry](e: E): E = topic(e, topicAt)

  // entry `i` of the earlier history: a day's talk every few dozen entries, back over some weeks
  private def past(i: Int, start: Long): ChatEntry = topic(pastEntry(i, start), i / 97)

  private def pastEntry(i: Int, start: Long): ChatEntry = {
    val time = start - 3600000L * 3L - (history - i).toLong * 900000L
    val who = i % 4 match {
      case 0 => sam
      case 1 => helper
      case 2 => you
      case _ => scout
    }
    if (i % 97 == 0) ChatEntry.marker("h-" + i.toString, "Topic renamed: Sprint " + (i / 97).toString, Some(time))
    else if (i % 41 == 0) ChatEntry.activity("h-" + i.toString, "Ran the nightly build #" + i.toString,
      if (i % 3 == 0) ActivityStatus.Failed else ActivityStatus.Succeeded,
      Some(ChatEntry.textDetails("build --nightly " + i.toString, "exit " + (if (i % 3 == 0) "1" else "0"))), Some(time))
    else ChatEntry.message("h-" + i.toString, who, who == you,
      "Message " + i.toString + (if (i % 5 == 0) ": a longer one, so that some of the history wraps onto a second line " +
        "and the rows it is drawn in are of different heights, as a real conversation's are." else ""), Some(time))
  }

  private def opening(start: Long): Vector[ChatEntry] = Vector[ChatEntry](
    ChatEntry.marker("topic", "Topic: Release planning", Some(start - 600000L)),
    ChatEntry.message("o1", sam, self = false, "Morning! Where are we with the release?", Some(start - 540000L)),
    ChatEntry.message("o2", sam, self = false, "I need the notes by noon.", Some(start - 530000L)),
    ChatEntry.message("o3", you, self = true, "Close. Helper, can you check what is left?", Some(start - 300000L)),
    ChatEntry.message("o4", helper, self = false, "On it. I'll look through the open issues and the test runs first.",
      Some(start - 280000L), meta = Some("model small · 212 tokens · 1.4s")),
    ChatEntry.activity("o-msg", "Message to The Oracle", ActivityStatus.Succeeded,
      Some(ChatEntry.textDetails("message_conversation \"The Oracle\"", "delivered")), Some(start - 270000L),
      ActivityLead.Person(oracle), ChatDemo.toOracle),
    ChatEntry.activity("o-look", "Looked up “Creating Custom Persona”: nothing found", ActivityStatus.Succeeded,
      time = Some(start - 265000L), lead = ActivityLead.Icon(MaterialIcon.Search)),
    ChatEntry.activity("o-ls", "Ran a command", ActivityStatus.Succeeded,
      Some(ChatEntry.textDetails("ls -la backend/src", "drwxr-xr-x  main\ndrwxr-xr-x  test")), Some(start - 260000L),
      ActivityLead.Icon(MaterialIcon.Terminal), "`ls -la backend/src`"),
    ChatEntry.message("o5", layla, self = false, "صباح الخير! هل ملاحظات الإصدار جاهزة؟", Some(start - 240000L)))
    .map(e => topic(e, 21))

  // the conversation, played on timers
  private def play(entries: Var[Vector[ChatEntry]], working: Var[Vector[Working]], connection: Var[ConnectionState],
                   chat: ChatView): Unit = {
    val think = now(ChatEntry.thinking("t1", Clock.real()))
    Platform.after(1.0, () => {
      working @= working() :+ Working("w-helper", helper, "thinking", Clock.real(), Some(() => working @= working().filterNot(_.id == "w-helper")))
      add(entries, think)
      stream(think.text, "The release has two blockers in the tracker. I should check the test runs before I say it is " +
        "ready, then summarise what changed since the last one.", 0, 12, () => ())
    })
    Platform.after(5.0, () => {
      think.done @= Some(Clock.real() - think.started)
      val search = now(ChatEntry.activity("a1", "Searched the repository for open TODOs",
        details = Some(ChatEntry.textDetails("grep -rn TODO src/", "src/app/Main.scala:12: // TODO: remove the flag\n" +
          "src/net/Socket.scala:88: // TODO: back off on reconnect")), time = Some(Clock.real())))
      add(entries, search)
      Platform.after(2.0, () => search.status @= ActivityStatus.Succeeded)
    })
    // a message to The Oracle that goes, its words arriving after it started, and fails
    Platform.after(2.0, () => {
      val ask = now(ChatEntry.activity("a0", "Message to The Oracle", lead = ActivityLead.Person(oracle),
        time = Some(Clock.real())))
      add(entries, ask)
      Platform.after(1.0, () => ask.body @= "Are you there? The Persona needs a `name` before it can be saved.")
      Platform.after(6.0, () => ask.status @= ActivityStatus.Failed)
    })
    Platform.after(7.5, () => {
      val tests = now(ChatEntry.activity("a2", "Ran the test suite", details = Some(ChatEntry.textDetails("sbt test",
        "[error] SocketSpec: reconnects after a drop *** FAILED ***\n[info] 412 passed, 1 failed")),
        time = Some(Clock.real())))
      add(entries, tests)
      Platform.after(2.0, () => tests.status @= ActivityStatus.Failed)
    })
    Platform.after(10.0, () => {
      working @= working().map(w => if (w.id == "w-helper") w.copy(what = "writing the summary") else w)
      reply(entries, working, helper, "Here is where things stand:\n\n" +
        "1. Two `TODO`s remain, both small.\n2. The test suite has **one failure**, in `SocketSpec` (reconnecting after " +
        "a drop).\n3. Everything else since 1.4 is merged.\n\n| Area | Status |\n|---|---|\n| Tests | 412 passed, 1 failed |\n" +
        "| TODOs | 2 open |\n\nThe check that fails:\n\n```scala\nsocket.drop()\nassert(socket.reconnected)\n```\n\n" +
        "I'd fix the reconnect test before we cut the release; the TODOs can wait. The log is in " +
        "[run 412](https://ci.example.com/runs/412).", "w-helper")
    })
    Platform.after(13.0, () => {
      working @= working() :+ Working("w-scout", scout, "reading 14 files", Clock.real(), Some(() => working @= working().filterNot(_.id == "w-scout")))
      working @= working() :+ Working("w-sam", sam, "reviewing", Clock.real())
      working @= working() :+ Working("w-notes", helper, "drafting the release notes", Clock.real(),
        Some(() => working @= working().filterNot(_.id == "w-notes")))
    })
    Platform.after(21.0, () => working @= working().filterNot(_.id == "w-notes"))
    Platform.after(16.0, () => {
      add(entries, now(ChatEntry.custom("approve-1", () => approval(chat), Some(Clock.real()))))
      working @= working().filterNot(_.id == "w-scout")
    })
    Platform.after(18.0, () => {
      add(entries, now(ChatEntry.message("s1", sam, self = false, "Nice. Let's aim for Thursday then.",
        Some(Clock.real()))))
      // a new topic, from its marker on
      topicAt = 22
      add(entries, now(ChatEntry.marker("rename", "Topic renamed: Release 2.0 plan", Some(Clock.real()))))
      working @= working().filterNot(_.id == "w-sam")
    })
    Platform.after(19.0, () => {
      add(entries, now(ChatEntry.message("l1", layla, self = false,
        "ممتاز. سأراجع SocketSpec بعد الظهر وأرسل لكم النتيجة.", Some(Clock.real()))))
      add(entries, now(ChatEntry.message("l2", layla, self = false, "الخميس مناسب لي.", Some(Clock.real()))))
      add(entries, now(ChatEntry.message("l3", layla, self = false, "قبل الإصدار:\n\n- إصلاح `SocketSpec`\n- مراجعة " +
        "**ملاحظات** الإصدار", Some(Clock.real()))))
    })
    Platform.after(20.0, () => add(entries, now(ChatEntry.message("you-log", you, self = true,
      "The failing run's log: " +
      "https://ci.example.com/builds/2026-09-29/release-2.0/artifacts/logs/socket-spec-reconnect-after-drop-full-output.log " +
      "and the fix goes in src/main/scala/everyui/network/transport/reconnect/SocketReconnectPolicyWithExponentialBackoff.scala " +
      "(a00a382f6b789870a62db369bf580a006d7f1c2e).", Some(Clock.real())))))
    if (Platform.launchArg("offline") == "1") connection @= ConnectionState.Offline(Some("the server is not answering"),
      Some(() => connection @= ConnectionState.Connecting))
  }

  /** `who` answers with `text`, streamed in a few words at a time; `job` is their work to clear once it is whole. */
  private def reply(entries: Var[Vector[ChatEntry]], working: Var[Vector[Working]], who: Participant, text: String,
                    job: String = ""): Unit = {
    val m = ChatEntry.message(who.id + "-" + Clock.real().toString, who, self = false, time = Some(Clock.real()),
      streaming = true)
    add(entries, m)
    stream(m.text, text, 0, 6, () => {
      m.streaming @= false
      m.meta @= Some("model large · " + (text.length / 4).toString + " tokens")
      // its thread known once it is whole
      now(m)
      if (job.nonEmpty) working @= working().filterNot(_.id == job)
    })
  }

  // `text` into `into`, `step` letters every 50 ms from `at`, then `done`
  private def stream(into: Var[String], text: String, at: Int, step: Int, done: () => Unit): Unit =
    if (at >= text.length) done()
    else Platform.after(0.05, () => {
      val to = math.min(text.length, at + step)
      into @= text.substring(0, to)
      stream(into, text, to, step, done)
    })

  // an agent's request, answered in place: it keeps what was chosen while it is scrolled away
  private def approval(chat: ChatView): Component = {
    val decided: Var[Option[String]] = Var(None)
    val title = new TextView {
      content @= "Scout wants to run a command"
      font.size @= 13.5
      font.weight @= FontWeight.SemiBold
      color := Theme.text()
    }
    val command = new TextView {
      content @= "git tag v2.0.0-rc1 && git push --tags"
      direction @= TextDirection.Auto
      font.size @= 12.5
      whiteSpace @= WhiteSpace.PreWrap
      color := Theme.muted()
    }
    val approve = Button.bound(decided().getOrElse("Approve"), () => if (decided().isEmpty) decided @= Some("Approved"),
      ButtonStyle.Filled)
    approve.enabled := decided().isEmpty
    val decline = new Button("Decline", () => decided @= Some("Declined"))
    decline.visible := decided().isEmpty
    val column = Column(8.0)(title, command, Row(8.0, middle = true)(approve, decline))
    val card = new Container {
      size.width := math.min(360.0, chat.transcript.widest())
      size.height := column.size.height() + 24.0
      radius @= 12.0
      borderWidth @= 1.0
      borderColor := Theme.accent()
      backgroundColor := Theme.raised()
      children += column
    }
    command.size.width := card.size.width() - 24.0
    column.position.left @= 12.0
    column.position.top @= 12.0
    card
  }
}

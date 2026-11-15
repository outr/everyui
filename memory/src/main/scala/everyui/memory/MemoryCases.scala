package everyui.memory

import everyui.Color
import everyui.chat.{ActivityStatus, ChatEntry, Participant, Transcript}
import everyui.component.{Button, Component, Container, ImageView, ScrollContainer, TextView}
import everyui.kit.TabGroups
import everyui.layout.VerticalLayout
import everyui.list.VirtualList
import everyui.render.Platform
import everyui.table.{DataCell, DataColumn, DataTable}
import reactify.*

/** The cases EveryUI's memory is measured by, the same on every platform that measures (the web's page,
  * `MemoryPage`; the desktop's `MemorySpec`): each common kind of component, a transcript's cost per message of each
  * kind, and a tab group's per tab not shown. */
object MemoryCases {
  val all: List[MemoryCase] = List(
    MemoryCase("TextView", "view", 200, () => stacked(i => words("A line of words, number " + i.toString))),
    MemoryCase("Button", "button", 200, () => stacked(i => new Button("Button " + i.toString))),
    MemoryCase("Container", "container", 200, () => stacked(_ => box())),
    MemoryCase("ImageView", "view", 100, () => stacked(_ => ImageView(Platform.resolvePath("images/everyui-mark.png"),
      32.0))),
    MemoryCase("ScrollContainer", "scroll", 50, () => stacked(i => scroll(i))),
    MemoryCase("ScrollContainer of cards", "card", 100, () => cards()),
    MemoryCase("VirtualList of 1,000 rows", "list", 4, () => stacked(_ => list())),
    MemoryCase("DataTable of 100 rows", "table", 4, () => stacked(_ => table())),
    MemoryCase("DataTable of 100 rows in a page", "table", 4, () => pageOfTables()),
    MemoryCase("TabGroups", "tab group", 10, () => stacked(i => tabGroups(i))),
    MemoryCase("TabGroups, tabs not shown", "hidden tab", 10, () => hiddenTabs()),
    MemoryCase("Transcript, plain", "message", 100, () => transcript(i => message(i, "Sounds good, go ahead."))),
    MemoryCase("Transcript, Markdown paragraph", "message", 100, () => transcript(i => message(i, paragraph))),
    MemoryCase("Transcript, code block of 40 lines", "message", 20, () => transcript(i => message(i, code))),
    MemoryCase("Transcript, Markdown table", "message", 20, () => transcript(i => message(i, markdownTable))),
    MemoryCase("Transcript, tool call folded", "message", 100, () => transcript(i => call(i, open = false))),
    MemoryCase("Transcript, tool call open", "message", 50, () => transcript(i => call(i, open = true))),
    MemoryCase("Transcript in 900 px, plain", "message", 200,
      () => transcript(i => message(i, "Sounds good, go ahead."), height = 900.0))
  )

  // units in a column, one after another
  private def stacked(make: Int => Component): MemoryRig = new MemoryRig {
    private val column = new Container
    column.layout @= Some(VerticalLayout(4.0))
    private var made = 0
    def root: Component = column
    def add(n: Int): Unit = {
      column.children ++= (made until made + n).map(make)
      made += n
    }
  }

  private def words(s: String): TextView = {
    val t = new TextView
    t.content @= s
    t
  }

  private def box(): Container = {
    val c = new Container
    c.size.width @= 40.0
    c.size.height @= 40.0
    c.backgroundColor @= Color.fromHex("336699FF")
    c
  }

  private def scroll(i: Int): ScrollContainer = {
    val s = new ScrollContainer
    s.size.width @= 300.0
    s.size.height @= 60.0
    s.content.children += words("Words in scroll " + i.toString)
    s
  }

  // one scroll a screen tall, its content a column of cards of words: most out of its view
  private def cards(): MemoryRig = new MemoryRig {
    private val s = new ScrollContainer
    s.size.width @= 400.0
    s.size.height @= 600.0
    s.content.layout @= Some(VerticalLayout(8.0))
    private var made = 0
    def root: Component = s
    def add(n: Int): Unit = {
      s.content.children ++= (made until made + n).map(i => card(i))
      made += n
    }
  }

  private def card(i: Int): Container = {
    val c = new Container
    c.layout @= Some(VerticalLayout(4.0))
    c.padding @= 8.0
    c.backgroundColor @= Color.fromHex("33669922")
    c.children ++= List(words("Card " + i.toString), words("From someone about something, number " + i.toString),
      words("A line of what it says, long enough to be a line of a preview of a message"))
    c
  }

  private def list(): VirtualList[String] = {
    val l = VirtualList.text[String](Var((0 until 1000).toVector.map(i => "Row " + i.toString)), s => s, s => s)
    l.size.width @= 400.0
    l.size.height @= 600.0
    l
  }

  private def table(): DataTable = new DataTable(
    Vector("Name", "Kind", "Size", "Changed", "Owner").map(t => DataColumn(t)),
    (0 until 100).toVector.map(r => Vector("file-" + r.toString + ".scala", "Scala", (r * 37 % 900).toString + " KB",
      "2026-11-" + (r % 28 + 1).toString, "Ada").map(c => DataCell(c))))

  // tables as tall as their rows in a page a screen tall, which scrolls them
  private def pageOfTables(): MemoryRig = new MemoryRig {
    private val s = new ScrollContainer
    s.size.width @= 800.0
    s.size.height @= 600.0
    s.content.layout @= Some(VerticalLayout(8.0))
    private var made = 0
    def root: Component = s
    def add(n: Int): Unit = {
      s.content.children ++= (made until made + n).map { _ =>
        val t = table()
        t.maxHeight @= 0.0
        t
      }
      made += n
    }
  }

  private def tabGroups(i: Int): TabGroups[String] = {
    val g = new TabGroups[String](k => k, k => words("The tab " + k), k => k)
    g.size.width @= 400.0
    g.size.height @= 200.0
    g.open("Tab " + i.toString, focus = false)
    g
  }

  // one tab group, each tab a panel of words and buttons, the last opened shown and the rest not
  private def hiddenTabs(): MemoryRig = new MemoryRig {
    private val tabs = new TabGroups[String](k => k, k => panel(k), k => k)
    tabs.size.width @= 900.0
    tabs.size.height @= 700.0
    private var made = 0
    def root: Component = tabs
    def add(n: Int): Unit = {
      (made until made + n).foreach(i => tabs.open("Tab " + i.toString, focus = false))
      made += n
    }
  }

  private def panel(k: String): Component = {
    val p = new Container
    p.layout @= Some(VerticalLayout(6.0))
    p.children ++= (0 until 20).map(i => words(k + ", line " + i.toString + " of what this tab shows"))
    p.children ++= (0 until 4).map(i => new Button("Action " + i.toString))
    p
  }

  // a transcript tall enough (by default) to show every message it holds, so each is drawn
  private def transcript(entry: Int => ChatEntry, height: Double = 200000.0): MemoryRig = new MemoryRig {
    private val entries = Var(Vector.empty[ChatEntry])
    private val t = new Transcript(entries)
    t.size.width @= 800.0
    t.size.height @= height
    private var made = 0
    def root: Component = t
    def add(n: Int): Unit = {
      entries @= entries() ++ (made until made + n).map(entry)
      made += n
    }
  }

  private val agent = Participant("agent", "Coder")

  private def message(i: Int, text: String): ChatEntry =
    ChatEntry.message("m" + i.toString, agent, self = false, text, Some(1790000000000L + i * 60000L))

  private def call(i: Int, open: Boolean): ChatEntry = {
    val a = ChatEntry.activity("a" + i.toString, "Ran the test suite", ActivityStatus.Succeeded,
      Some(ChatEntry.textDetails("sbt test", "[info] 412 tests passed")), Some(1790000000000L + i * 60000L))
    a.expanded @= open
    a
  }

  private val paragraph = "The socket is closed by the proxy after **60 s** without traffic, and `DurableSocket` " +
    "reconnects at once, every time, so a dead server is asked again and again. A heartbeat every 25 s keeps it " +
    "alive; " +
    "see [the proxy docs](https://example.com/proxy#idle) for the limit."

  private val code = "```scala\n" + (1 to 40).map(i => "  val step" + i.toString + " = Backoff(base = 0.5, max = " +
    i.toString + ".0).delay(attempt + " + i.toString + ") // try " + i.toString).mkString("\n") + "\n```"

  private val markdownTable = "| Run | Drops | Reconnects | Longest gap |\n|---|---|---|---|\n" +
    (1 to 5).map(i => "| run " + i.toString + " | " + (i * 3).toString + " | " + (i * 101).toString + " | " +
      i.toString + ".4 s |").mkString("\n")
}

package everyui.app

import everyui.chat.ChatView
import everyui.component.*
import everyui.component.types.TextOverflow
import everyui.kit.{TabGroups, TabLayout, TabsAside}
import everyui.layout.VerticalLayout
import everyui.perf.PerfCounters
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** What putting a kept view back costs, as an app switching workspaces keeps each one's views and puts them back:
  * `kept` panels (4 unless given), each a `TabGroups` of six tabs of wrapped words over a column of paragraphs beside
  * a conversation of 40 entries (`ChatView`), shown in turn once each and then switched between `switches` times (40),
  * one every `every` milliseconds (150), each timed from the change of `children` until what it set off is done (the
  * turn's sizes read, the words looked at against their boxes). It logs `kept switch <n> <ms>` for each and `kept
  * done median <ms> p95 <ms> max <ms> measured <n> fits <n> placed <n> reads <n>` at the end, the counts over all the
  * switches (`PerfCounters`). `narrow=1` puts each back 40 px narrower than it left every other time, `change=1`
  * changes the words of its first paragraph while it is away, `check=1` logs every word whose size differs from what
  * it measured at, once it is back (`kept checked <n> words <n> differ <n>`), `theme=light`. `shared=restore` has one
  * `TabGroups` over every panel, its tabs laid out again from each panel's `tabLayout` as it shows (`restore`), and
  * `shared=aside` the same set aside and put back whole (`setAside`, `putBack`). */
object KeptDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val n = Platform.launchArg("kept").toIntOption.getOrElse(4)
    val switches = Platform.launchArg("switches").toIntOption.getOrElse(40)
    val every = Platform.launchArg("every").toDoubleOption.getOrElse(150.0) / 1000.0
    val narrow = Platform.launchArg("narrow") == "1"
    val change = Platform.launchArg("change") == "1"
    val check = Platform.launchArg("check") == "1"
    // one set of tab groups for every panel, its tabs laid out again for each (`restore`) or set aside and put back
    val shared = Platform.launchArg("shared")
    val one = if (shared == "restore" || shared == "aside") Some(new TabGroups[String](k => k, k => page(k), k => k))
      else None
    var layouts: Map[Int, TabLayout] = Map.empty
    var asides: Map[Int, TabsAside[String]] = Map.empty
    var showing = -1
    val holder = new Container
    val wide = Var(1.0)
    holder.size.width := ui.size.width() - (if (wide() < 0.5) 40.0 else 0.0)
    holder.size.height := ui.size.height()
    ui.children += holder
    val firsts = (0 until n).toVector.map(_ => Var(""))
    one.foreach { t =>
      t.size.width := holder.size.width()
      t.size.height := holder.size.height() * 0.4
    }
    val panels = (0 until n).toVector.map(i => panel(i, holder, firsts(i), one.isEmpty))
    var times: List[Double] = Nil
    def show(i: Int, step: Int): Unit = if (step < n + switches) {
      if (step == n) PerfCounters.reset()
      if (narrow && step >= n) wide @= (if (step % 2 == 0) 1.0 else 0.0)
      // its first paragraph's words changed while it is away: read again as it comes back
      if (change && step >= n) firsts(i) @= (if ((step / n) % 2 == 0) "" else " Changed while it was away, at switch " +
        (step - n).toString + ", these words take more lines than they did, wrapped at the column's width as before.")
      val started = Platform.ticks()
      one.foreach { t =>
        if (shared == "aside") {
          if (showing >= 0) asides = asides.updated(showing, t.setAside())
          asides.get(i) match {
            case Some(a) =>
              asides = asides - i
              t.putBack(a)
            case None => fill(t, i)
          }
        }
        else {
          if (showing >= 0) layouts = layouts.updated(showing, t.tabLayout)
          layouts.get(i) match {
            case Some(l) => t.restore(l, k => Some(k))
            case None =>
              t.restore(TabLayout(), k => Some(k))
              fill(t, i)
          }
        }
      }
      showing = i
      holder.children @= Vector(panels(i)) ++ one.toVector
      // done once the turn's work is: what it put off to the end of the turn (sizes read, words looked at) and what
      // that put off in turn, each queued behind the last
      def settle(hops: Int): Unit = if (hops > 0) Platform.afterTurn(() => settle(hops - 1)) else {
        val ms = Platform.ticks() - started
        if (step >= n) {
          times = ms :: times
          Platform.log("kept switch " + (step - n).toString + " " + rounded(ms))
          if (check) compare(panels(i), step - n)
        }
        Platform.after(every, () => show((i + 1) % n, step + 1))
      }
      settle(24)
    } else {
      val sorted = times.sorted.toVector
      def at(q: Double): Double = sorted(math.min(sorted.size - 1, (q * sorted.size).toInt))
      val counts = PerfCounters.counts.toMap
      Platform.log("kept done median " + rounded(at(0.5)) + " p95 " + rounded(at(0.95)) + " max " +
        rounded(sorted.last) + " measured " + counts.getOrElse("measured", 0).toString + " fits " +
        counts.getOrElse("fits", 0).toString + " placed " + counts.getOrElse("placed", 0).toString + " reads " +
        counts.getOrElse("reads", 0).toString)
    }
    Platform.after(1.0, () => show(0, 0))
    Platform.log("kept ok")
  }

  // every size in what was put back as the page has it now, against what it was measured at (but a size set, which
  // is kept rather than measured): logged where they differ
  private def compare(root: Component, at: Int): Unit = {
    var all: List[Component] = List(root)
    var differ = 0
    var seen = 0
    while (all.nonEmpty) {
      val c = all.head
      all = all.tail
      all = c.childComponents.toList ++ all
      c match {
        case t: TextView if t.notHidden =>
          seen += 1
          val (w, h) = t.node.getSize()
          if ((!t.size.width.explicit && math.abs(w - t.measured.width()) > 0.5) ||
            (!t.size.height.explicit && math.abs(h - t.measured.height()) > 0.5)) {
            differ += 1
            Platform.log("kept differs " + Component.lineage(t) + " " + t.kind.take(40) + " measured " +
              t.measured.width().toString + " by " +
              t.measured.height().toString + ", drawn " + w.toString + " by " + h.toString)
          }
        case _ => ()
      }
    }
    Platform.log("kept checked " + at.toString + " words " + seen.toString + " differ " + differ.toString)
  }

  private def rounded(ms: Double): String = (math.round(ms * 10.0) / 10.0).toString

  // tabs of words (its own, or the shared ones over it) over a column of paragraphs beside a conversation, as wide as
  // `holder`
  private def panel(i: Int, holder: Container, first: Var[String], ownTabs: Boolean): Container = {
    val root = new Container
    root.size.width := holder.size.width()
    root.size.height := holder.size.height()
    val top = Val(root.size.height() * 0.4)
    val column = new Container {
      layout @= Some(VerticalLayout(8.0))
      padding @= 12.0
    }
    column.size.width := root.size.width() * 0.4
    column.position.top := top()
    (1 to 30).foreach(j => column.children += words(i, j, if (j == 1) first else Var("")))
    val chat = new ChatView(Var(ChatPerfDemo.page(40, 1790000000000L + i * 100000L)))
    chat.position.left := column.size.width()
    chat.position.top := top()
    chat.size.width := root.size.width() - column.size.width()
    chat.size.height := root.size.height() - top()
    root.children ++= List(column, chat)
    if (ownTabs) {
      val tabs = new TabGroups[String](k => k, k => page(k), k => k)
      fill(tabs, i)
      tabs.size.width := root.size.width()
      tabs.size.height := top()
      root.children += tabs
    }
    root
  }

  // six tabs of panel `i`'s files, the last split off to the right
  private def fill(tabs: TabGroups[String], i: Int): Unit = {
    (1 to 6).foreach(j => tabs.open("Panel " + (i + 1).toString + " file " + j.toString + ".scala", focus = false))
    tabs.groups().headOption.foreach(g => g.selected().foreach(k => tabs.openRight(g, k)))
  }

  // what a tab shows: a column of lines wrapped to its box
  private def page(k: String): Component = new Container {
    layout @= Some(VerticalLayout(4.0))
    padding @= 10.0
    (1 to 12).foreach(j => children += words(k.length, j, Var("")))
  }

  private def words(i: Int, j: Int, more: Var[String]): TextView = {
    val t = TextView.bound("Paragraph " + j.toString + " of panel " + (i + 1).toString + ": the reconnect policy " +
      "waits longer each time, up to thirty seconds, with some jitter so a fleet of clients does not come back together " +
      "and hammer the server it lost." + more(), 14.0, Theme.text())
    t.textOverflow @= TextOverflow.Wrap
    t
  }
}

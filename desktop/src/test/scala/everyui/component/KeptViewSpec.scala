package everyui.component

import everyui.focus.Focus
import everyui.i18n.{Language, Strings, Words}
import everyui.kit.TabGroups
import everyui.layout.VerticalLayout
import everyui.perf.PerfCounters
import everyui.theme.{Palette, Theme}
import everyui.{FxSpec, LeakPaths, ui}

import java.lang.ref.WeakReference

/** A kept view, taken off the screen and put back, as an app switching workspaces keeps each one's views: put back
  * where it was, unchanged, nothing in it is measured again, no words are fitted again, nothing is laid out again and
  * nothing is made again; put back in a room of another width it is measured again and laid out to it; a theme or a
  * language changed while it was out shows as it comes back; and taken apart it is let go of. Tab groups set aside
  * (`TabGroups.setAside`) are put back whole (`putBack`): the same groups, tabs and contents, none made again.
  * Measuring is counted by `PerfCounters` (`measured`: each size read for a component's `measured`). */
class KeptViewSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
    fx {
      Words.add("en", Map("kept.greeting" -> "Hello"))
      Words.add("de", Map("kept.greeting" -> "Guten Tag, und willkommen zurück an deinem Arbeitsplatz"))
    }
  }

  override def afterAll(): Unit = {
    fx {
      PerfCounters.on = false
      ui.children.clear()
      Focus.focused @= None
    }
    offStage()
    super.afterAll()
  }

  /** What a kept view holds: a column of words wrapped to its width, one bound to the language and the theme, over tab
    * groups of three tabs, the last split off, each tab's content counted as it is made. */
  private class Panel(room: Container) {
    var made: List[Component] = Nil
    val greeting: TextView = TextView.bound(Words("kept.greeting"), 14.0, Theme.text())
    val lines: Vector[TextView] = (1 to 12).toVector.map(i => TextView("Line " + i.toString + " of a kept view: the " +
      "reconnect policy waits longer each time, up to thirty seconds, so the clients do not all come back at once."))
    val column: Container = new Container {
      layout @= Some(VerticalLayout(6.0))
      padding @= 8.0
      children ++= (greeting +: lines)
    }
    val tabs: TabGroups[String] = new TabGroups[String](k => k, k => {
      val c = new Container {
        children += TextView("What " + k + " shows, wrapped to its group", 14.0, Theme.text())
      }
      made = c :: made
      c
    }, k => k)
    val root: Container = new Container
    root.size.width := room.size.width()
    root.size.height @= 500.0
    column.size.width := root.size.width()
    column.position.top @= 220.0
    tabs.size.width := root.size.width()
    tabs.size.height @= 200.0
    List("a.scala", "b.scala", "c.scala").foreach(k => tabs.open(k, focus = false))
    tabs.groups().head.selected().foreach(k => tabs.openRight(tabs.groups().head, k))
    root.children ++= List(tabs, column)

    /** `root` and everything under it. */
    def all: Vector[Component] = under(root)
  }

  private def room(width: Double): Container = {
    val c = new Container
    c.size.width @= width
    c.size.height @= 600.0
    ui.children += c
    c
  }

  // a few turns, for what a change put off (sizes read, words looked at against their boxes, kept views settled)
  private def turns(): Unit = (1 to 3).foreach(_ => fx(()))

  // what `f` counts, counted from nothing, and each component whose size was read for its `measured` meanwhile
  private def counted(f: => Unit): (Map[String, Int], Vector[Component]) = {
    var read = Vector.empty[Component]
    fx {
      PerfCounters.on = true
      PerfCounters.reset()
      everyui.component.feature.MeasuredFeature.heard = Some(c => read = read :+ c)
      f
    }
    turns()
    fx {
      val counts = PerfCounters.counts.toMap
      PerfCounters.on = false
      everyui.component.feature.MeasuredFeature.heard = None
      (counts, read)
    }
  }

  /** `c` and everything under it. */
  private def under(c: Component): Vector[Component] = {
    var out = Vector.empty[Component]
    var pending: List[Component] = List(c)
    while (pending.nonEmpty) {
      out = out :+ pending.head
      pending = pending.head.childComponents.toList ++ pending.tail
    }
    out
  }

  "A kept view" should {
    "be put back where it was with nothing measured, fitted, laid out or made again" in {
      val (holder, panel) = fx {
        ui.children.clear()
        val holder = room(600.0)
        val panel = new Panel(holder)
        holder.children += panel.root
        (holder, panel)
      }
      turns()
      val (madeBefore, groupsBefore, barsBefore, sizes) = fx((panel.made, panel.tabs.groups(),
        panel.tabs.groups().map(_.bar.tabs()), panel.all.map(c => (c.size.width(), c.size.height()))))
      fx(holder.children @= Vector.empty)
      turns()
      val (counts, read) = counted(holder.children += panel.root)
      // what is under it was measured once it went in, and only the room it went into is read now
      val all = fx(panel.all)
      all.size should be > 60
      read.filter(c => all.exists(_ eq c)) shouldBe empty
      read.filterNot(_ eq holder) shouldBe empty
      counts("measured") shouldBe read.size
      counts("fits") shouldBe 0
      counts("placed") shouldBe 0
      fx {
        panel.made shouldBe madeBefore
        panel.tabs.groups().zip(groupsBefore).forall(p => p._1 eq p._2) shouldBe true
        val bars = panel.tabs.groups().map(_.bar.tabs())
        bars.zip(barsBefore).forall(p => p._1.size == p._2.size && p._1.zip(p._2).forall(t => t._1 eq t._2)) shouldBe
          true
        panel.all.map(c => (c.size.width(), c.size.height())) shouldBe sizes
        panel.root.parent() shouldBe Some(holder)
      }
    }

    "be measured again and laid out to a room of another width" in {
      val (narrow, panel, whole) = fx {
        ui.children.clear()
        val wide = room(900.0)
        val panel = new Panel(wide)
        wide.children += panel.root
        (room(320.0), panel, panel.lines.head.size.height())
      }
      turns()
      fx(panel.root.parent().foreach(_.asContainer.foreach(_.children.clear())))
      turns()
      val (_, read) = counted {
        panel.root.size.width := narrow.size.width()
        narrow.children += panel.root
      }
      // everything in it, read again
      fx(panel.all).filterNot(c => read.exists(_ eq c)) shouldBe empty
      fx {
        panel.column.size.width() shouldBe 320.0
        panel.lines.foreach { t =>
          t.size.width() should be <= 320.0 - 16.0 + 0.5
          t.measured.width() shouldBe t.node.getWidth() +- 0.5
        }
        // wrapped onto more lines than the wide room gave them
        panel.lines.head.size.height() should be > whole
        panel.tabs.groups().map(_.size.width()).sum should be < 320.0
      }
    }

    "show a theme and a language changed while it was out as it comes back" in {
      val (holder, panel, was, hello) = fx {
        ui.children.clear()
        val holder = room(600.0)
        val panel = new Panel(holder)
        holder.children += panel.root
        (holder, panel, Theme.palette(), panel.greeting)
      }
      turns()
      val helloWidth = fx(hello.measured.width())
      try {
        fx {
          holder.children @= Vector.empty
          Theme.palette @= (if (was == Palette.light) Palette.dark else Palette.light)
          Strings.language @= Language("de", "Deutsch", "German")
        }
        turns()
        fx(holder.children += panel.root)
        turns()
        fx {
          hello.content() shouldBe "Guten Tag, und willkommen zurück an deinem Arbeitsplatz"
          hello.color() shouldBe Theme.text()
          hello.color() should not be was.text
          hello.measured.width() should be > helloWidth
          hello.measured.width() shouldBe hello.node.getWidth() +- 0.5
          hello.size.width() should be <= 600.0 - 16.0 + 0.5
        }
      } finally fx {
        Theme.palette @= was
        Strings.language @= Language("en", "English", "English")
      }
    }

    "be let go of once it is taken apart, and so are tab groups set aside and disposed of" in {
      val (refs, aside, contents) = fx {
        ui.children.clear()
        val holder = room(600.0)
        val panel = new Panel(holder)
        holder.children += panel.root
        holder.children @= Vector.empty
        holder.children += panel.root
        holder.children @= Vector.empty
        val refs = panel.all.map(c => new WeakReference(c))
        panel.root.dispose()
        // tab groups of their own, set aside and disposed of
        val other = new Panel(holder)
        val tabs = other.tabs
        other.made should not be empty
        val contents = other.made.map(c => new WeakReference(c))
        val aside = tabs.setAside()
        aside.dispose()
        other.root.dispose()
        (refs, new WeakReference(aside), contents)
      }
      fx(contents.flatMap(r => Option(r.get())).forall(_.takenApart) shouldBe true)
      fx(aside.get().isSpent shouldBe true)
      val everything = refs ++ contents
      def count = everything.count(_.get() != null)
      val until = System.currentTimeMillis() + 5000L
      while (count > 0 && System.currentTimeMillis() < until) {
        fx(())
        System.gc()
        Thread.sleep(50L)
      }
      if (count > 0) LeakPaths.find(everything.flatMap(r => Option(r.get()))).foreach(p => info("held: " + p))
      count shouldBe 0
    }
  }

  "Tab groups set aside" should {
    "be put back whole: the same groups, tabs and contents, none made again, nothing measured" in {
      val (holder, panel) = fx {
        ui.children.clear()
        val holder = room(600.0)
        val panel = new Panel(holder)
        holder.children += panel.root
        (holder, panel)
      }
      turns()
      val (layout, groups, made) = fx((panel.tabs.tabLayout, panel.tabs.groups(), panel.made))
      val aside = fx(panel.tabs.setAside())
      turns()
      val other = fx {
        // what shows meanwhile: one empty group, then a tab of its own
        panel.tabs.groups().size shouldBe 1
        panel.tabs.groups().head.keys() shouldBe empty
        panel.tabs.open("other.scala", focus = false)
        panel.made.head
      }
      turns()
      val (counts, read) = counted(panel.tabs.putBack(aside))
      // nothing of what came back was read, only the tab groups' own room (and what closed as it came back)
      val back = fx(under(panel.tabs))
      read.filter(c => back.exists(_ eq c) && !(c eq panel.tabs)) shouldBe empty
      fx {
        panel.tabs.tabLayout shouldBe layout
        panel.tabs.groups().zip(groups).forall(p => p._1 eq p._2) shouldBe true
        panel.tabs.groups().size shouldBe groups.size
        // only "other.scala" was made since, and it closed as they came back
        panel.made.tail shouldBe made
        other.takenApart shouldBe true
        aside.isSpent shouldBe true
      }
      counts("fits") shouldBe 0
      // and again, with nothing in between: the empty group shown meanwhile is the one put back over before
      val (_, readAgain) = counted {
        val next = panel.tabs.setAside()
        panel.tabs.putBack(next)
      }
      val backAgain = fx(under(panel.tabs))
      readAgain.filter(c => backAgain.exists(_ eq c) && !(c eq panel.tabs)) shouldBe empty
      fx {
        panel.tabs.tabLayout shouldBe layout
        panel.made.tail shouldBe made
        // put back again, a spent one changes nothing
        panel.tabs.putBack(aside)
        panel.tabs.tabLayout shouldBe layout
        holder.children().head shouldBe panel.root
      }
    }
  }
}

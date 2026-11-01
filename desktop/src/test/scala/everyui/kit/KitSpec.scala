package everyui.kit

import everyui.event.{Key, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.component.*
import everyui.i18n.Direction
import everyui.render.{Nodes, Platform}
import everyui.{FxSpec, ui}
import reactify.*

/** The general components: tabs, toasts, a press that takes a while, work in turns, rows made as they are neared,
  * lines that close up, parts that mirror, and words that scroll. */
class KitSpec extends FxSpec {
  // the focus as the keys move it, which these are about
  override protected def keyed: Boolean = true

  private def click(c: Component): Unit = c.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0)

  /** `parts` under `ui` for the test, taken away after. */
  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      ui.children ++= parts
    }
    try test finally fx {
      Toast.hide()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A tab bar" should {
    "show a tab clicked, and ask before closing one" in {
      val current = Var("a")
      val bar = fx(new TabBar(current, 600.0, showOnFocus = false))
      onScreen(bar) {
        val (a, b) = fx((bar.add("a", "Alpha"), bar.add("b", "Beta", closable = true)))
        a.closeButton shouldBe None
        var asked: List[String] = Nil
        fx(bar.closing.attach(k => asked = asked :+ k))
        fx(click(b))
        current() shouldBe "b"
        fx(click(b.closeButton.get))
        asked shouldBe List("b")
        current() shouldBe "b"
        fx(bar.remove("b"))
        fx(bar.tabs().map(_.key)) shouldBe List("a")
      }
    }
    "hold what an app puts beside a label, and enter at the tab shown" in {
      val current = Var("b")
      val bar = fx(TabBar(List("a" -> "Alpha", "b" -> "Beta"), current, 600.0))
      onScreen(bar) {
        val dot = fx(new Container(Nodes.container()))
        val b = fx(bar("b").get)
        fx(b.marker @= Some(dot))
        fx(Focus.within(dot, b)) shouldBe true
        fx(bar.focusEntry) shouldBe Some(b)
        // a tab shows itself as the focus lands on it, where the bar says so
        fx(bar("a").get.focus())
        eventually(current() == "a") shouldBe true
        fx(bar.focusEntry) shouldBe bar.tabs().headOption
      }
    }
  }

  "A tab added at a place" should {
    "go where it is put, beside another, without moving what is shown, and a closed tab hand over" in {
      val current = Var("a")
      val bar = fx(new TabBar(current, 600.0, showOnFocus = false))
      onScreen(bar) {
        fx {
          bar.add("a", "A.scala", closable = true)
          bar.add("b", "B.scala", closable = true)
          bar.addAfter("a", "diff:a", "A.scala (diff)", closable = true)
          bar.add("first", "First", at = 0)
        }
        fx(bar.tabs().map(_.key)) shouldBe List("first", "a", "diff:a", "b")
        current() shouldBe "a"
        // opened again, a tab keeps its place unless told otherwise
        fx(bar.add("diff:a", "A.scala (diff)", closable = true))
        fx(bar.tabs().map(_.key)) shouldBe List("first", "a", "diff:a", "b")
        fx(bar.add("b", "B.scala", closable = true, at = 1))
        fx(bar.tabs().map(_.key)) shouldBe List("first", "b", "a", "diff:a")
        // the tab shown closed: the one now in its place shows, else the one before
        fx(current @= "diff:a")
        fx(bar.remove("diff:a"))
        current() shouldBe "a"
        fx(current @= "b")
        fx(bar("b").get.focus())
        fx(bar.remove("b"))
        current() shouldBe "a"
        eventually(Focus.focused().exists(_ eq bar("a").get)) shouldBe true
      }
    }
  }

  "A toast" should {
    "show one message at a time, and go" in onScreen() {
      val before = fx(ui.children().size)
      fx(Toast.show("Saved"))
      fx(Toast.show("Saved again", seconds = 0.1, top = true))
      fx(ui.children().size) shouldBe before + 1
      eventually(ui.children().size == before) shouldBe true
    }
  }

  // The presses' time (AsyncPress.clock, AsyncPress.after), kept by the spec: what is due runs as `advance` passes it,
  // in the order it is due, each on a turn of its own; what the work asks for later runs on the turn after. Exactly
  // when each thing happens is then what the press makes of it, however late a busy machine would run a real timer.
  @volatile private var now = 0.0
  @volatile private var due: Vector[(Double, () => Unit)] = Vector.empty

  private def timed[T](test: => T): T = {
    now = 0.0
    due = Vector.empty
    AsyncPress.clock = () => math.round(now * 1000.0)
    AsyncPress.after = (seconds, f) => due = due :+ (now + seconds -> f)
    try onScreen()(test)
    finally {
      AsyncPress.clock = () => System.currentTimeMillis()
      AsyncPress.after = (seconds, f) => Platform.after(seconds, f)
    }
  }

  /** `seconds` passed, what fell due in them run. */
  private def advance(seconds: Double): Unit = {
    val until = now + seconds
    var next = fx(due.filter(_._1 <= until + 1e-9).sortBy(_._1).headOption)
    while (next.nonEmpty) {
      val (at, f) = next.get
      fx {
        due = due.filterNot(_._2 eq f)
        now = at
        f()
      }
      fx(())
      next = fx(due.filter(_._1 <= until + 1e-9).sortBy(_._1).headOption)
    }
    now = until
  }

  "An async press" should {
    "ignore a press while one runs, spin only when it is slow, and toast a failure" in timed {
      var spinning: List[Boolean] = Nil
      var finish: AsyncPress.Done = null
      var pressed = 0
      val press = new AsyncPress(on => spinning = spinning :+ on)
      fx(press.press { done => pressed += 1; finish = done })
      fx(press.press { _ => pressed += 1 })
      pressed shouldBe 1
      advance(0.39)
      spinning shouldBe Nil
      advance(0.01)
      spinning shouldBe List(true)
      val before = fx(ui.children().size)
      fx(finish(Left("It did not work")))
      fx(press.running) shouldBe false
      fx(ui.children().size) shouldBe before + 1
      advance(0.49)
      spinning shouldBe List(true)
      advance(0.01)
      spinning shouldBe List(true, false)
    }
    "keep the spinner up its minimum once shown, though the work ends just after, and the answer at once" in timed {
      var events: List[(Boolean, Double)] = Nil
      val press = new AsyncPress(on => events = events :+ (on -> now))
      val before = fx(ui.children().size)
      fx(press.press(done => AsyncPress.after(0.42, () => done(Left("Answered")))))
      advance(0.42)
      // answered at once, while it is still up, and the failure is told
      fx(press.running) shouldBe false
      fx(events) shouldBe List(true -> 0.4)
      fx(ui.children().size) shouldBe before + 1
      fx(press.spinning) shouldBe true
      advance(1.0)
      // up its half second from when it showed, and no longer
      fx(events.map(_._1)) shouldBe List(true, false)
      fx(events(1)._2) shouldBe 0.9 +- 1e-9
      fx(press.spinning) shouldBe false
    }
    "never show the spinner for work done before spinAfter" in timed {
      var spinning: List[Boolean] = Nil
      val press = new AsyncPress(on => spinning = spinning :+ on)
      fx(press.press(done => AsyncPress.after(0.2, () => done(Right(())))))
      advance(0.2)
      fx(press.running) shouldBe false
      // pressed again at once, the first press's timer does not show it early for the second
      fx(press.press(done => AsyncPress.after(0.25, () => done(Right(())))))
      advance(0.7)
      fx(press.running) shouldBe false
      spinning shouldBe Nil
    }
    "keep spinning, once, for a press while the spinner is still up" in timed {
      var spinning: List[Boolean] = Nil
      var finish: AsyncPress.Done = null
      val press = new AsyncPress(on => spinning = spinning :+ on)
      fx(press.press(done => AsyncPress.after(0.42, () => done(Right(())))))
      advance(0.42)
      spinning shouldBe List(true)
      fx(press.running) shouldBe false
      // pressed again in the half second it is held up: it stays up, not shown twice, past where the first would end
      fx(press.press(done => finish = done))
      fx(press.running) shouldBe true
      advance(0.8)
      spinning shouldBe List(true)
      fx(press.spinning) shouldBe true
      fx(finish(Right(())))
      // up well past its minimum, it goes at once
      fx(())
      spinning shouldBe List(true, false)
      fx(press.press(done => done(Right(()))))
      fx(press.running) shouldBe false
      advance(0.6)
      spinning shouldBe List(true, false)
    }
  }

  "Work in turns" should {
    "run every step, then say it is done" in {
      var ran: List[Int] = Nil
      var done = false
      fx(Turns.run(List(1, 2, 3).map(i => () => ran = ran :+ i), () => done = true))
      eventually(done) shouldBe true
      ran shouldBe List(1, 2, 3)
    }
  }

  "Lazy rows" should {
    "make the first at once and the rest as they are neared" in {
      val scroll = fx(new ScrollContainer {
        size.width @= 400.0
        size.height @= 200.0
      })
      val column = fx(new Container {
        layout @= Some(everyui.layout.VerticalLayout(0.0))
      })
      onScreen(scroll) {
        val lazyRows = fx(new LazyRows(scroll, column, first = 5))
        val made = fx {
          scroll.content.children += column
          column.children ++= lazyRows.parts((1 to 100).toList.map(_ => () => new Container {
            size.width @= 400.0
            size.height @= 50.0
          }))
          column.children().size
        }
        made shouldBe 5
        // filled to two screens below what can be seen, and no further
        eventually(column.children().size >= 12) shouldBe true
        fx(column.children().size) should be < 100
      }
    }
  }

  "A line" should {
    "take no room with nothing to say" in {
      val words = Var("")
      val line = fx(TextView.line(words(), 14.0, everyui.Color.White))
      onScreen(line) {
        fx((line.size.width(), line.size.height())) shouldBe ((0.0, 0.0))
        fx(words @= "Hello")
        fx(line.size.width()) should be > 0.0
        fx(line.size.height()) should be > 0.0
      }
    }
  }

  "A part placed by hand" should {
    "read from the right in a right-to-left language" in {
      val part = fx(new Container {
        size.width @= 50.0
      })
      fx(Mirror.place(part, () => 400.0, () => 10.0))
      fx(part.position.left()) shouldBe 10.0
      try {
        fx(Direction.rtl @= true)
        fx(part.position.left()) shouldBe 340.0
        fx(Mirror.onward) shouldBe everyui.font.MaterialIcon.ChevronLeft
      }
      finally fx(Direction.rtl @= false)
    }
  }

  "Words that scroll" should {
    "keep Up and Down until there is no further to go" in {
      val scroll = fx(new TextScroll {
        size.width @= 400.0
        size.height @= 100.0
      })
      onScreen(scroll) {
        fx(scroll.content.children += new Container {
          size.width @= 300.0
          size.height @= 500.0
        })
        fx(scroll.keepsKey(Key.ArrowUp)) shouldBe false
        fx(scroll.keepsKey(Key.ArrowDown)) shouldBe true
        fx(scroll.offset @= 400.0)
        fx(scroll.keepsKey(Key.ArrowDown)) shouldBe false
        fx(scroll.keepsKey(Key.ArrowUp)) shouldBe true
      }
    }
  }
}

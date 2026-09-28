package everyui.kit

import everyui.event.{Key, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.component.*
import everyui.i18n.Direction
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import reactify.*

/** The general components: tabs, toasts, a press that takes a while, work in turns, rows made as they are neared,
  * lines that close up, parts that mirror, and words that scroll. */
class KitSpec extends FxSpec {
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

  "A toast" should {
    "show one message at a time, and go" in onScreen() {
      val before = fx(ui.children().size)
      fx(Toast.show("Saved"))
      fx(Toast.show("Saved again", seconds = 0.1, top = true))
      fx(ui.children().size) shouldBe before + 1
      eventually(ui.children().size == before) shouldBe true
    }
  }

  "An async press" should {
    "ignore a press while one runs, spin only when it is slow, and toast a failure" in onScreen() {
      var spinning: List[Boolean] = Nil
      var finish: AsyncPress.Done = null
      var pressed = 0
      val press = new AsyncPress(on => spinning = spinning :+ on)
      fx(press.press { done => pressed += 1; finish = done })
      fx(press.press { _ => pressed += 1 })
      pressed shouldBe 1
      eventually(spinning == List(true)) shouldBe true
      val before = fx(ui.children().size)
      fx(finish(Left("It did not work")))
      eventually(!press.running) shouldBe true
      spinning shouldBe List(true, false)
      fx(ui.children().size) shouldBe before + 1
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

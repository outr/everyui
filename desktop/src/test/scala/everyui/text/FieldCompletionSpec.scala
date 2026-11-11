package everyui.text

import everyui.async.Task
import everyui.chat.{Completion, CompletionAction, CompletionPopup, ComposerAction, ComposerKeys, ComposerState}
import everyui.component.*
import everyui.dialog.{DialogAction, Dialogs}
import everyui.event.{Key, KeyEvent, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, MouseButton, MouseEvent, KeyEvent as FxKeyEvent}
import reactify.*

/** A field's completions on the desktop's real nodes: the piece asked about (the whole field, or what lies between its
  * separators, by where the caret is), an answer for a piece since typed on dropped, the list under the field (over it
  * where there is no room under it) as wide as it and scrolling past its rows, its keys (Down and Up going round, Enter
  * and Tab choosing, Escape closing the list alone, in a dialog too), a choice going in with what follows it, the caret
  * after it and told on `completed`, a click choosing, and the list closing as the field loses the focus, when the
  * source answers nothing and as the field is taken apart; and the composer's keys as they were. */
class FieldCompletionSpec extends FxSpec {
  private val cities = Vector("Paris", "Parma", "Porto", "Prague", "Oslo")
  private val people = Vector(("Ossi Maki", "ossi@example.com"), ("Oscar Lind", "oscar@example.com"),
    ("Ann Berg", "ann@example.com"))
  // what each source was asked, in order
  private var asked: List[String] = Nil

  private def citySource: CompletionSource = CompletionSource(t => {
    asked = asked :+ t
    Task(cities.filter(_.toLowerCase.startsWith(t.toLowerCase)).map(c => Completion(c, c)))
  })

  private def addressSource: CompletionSource = CompletionSource(t => {
    asked = asked :+ t
    Task(people.filter { case (n, e) => n.toLowerCase.startsWith(t.toLowerCase) || e.startsWith(t.toLowerCase) }
      .map { case (n, e) => Completion(n + " <" + e + ">", e, Some(e)) })
  }, separators = ",;", after = ", ")

  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  /** Everything under `c`, `c` first. */
  private def all(c: Component): List[Component] = c :: c.childComponents.toList.flatMap(all)

  private def popups: Vector[CompletionPopup] = ui.children().collect { case p: CompletionPopup => p }.toVector

  private def labels: Vector[String] = popups.flatMap(_.completions.map(_.label))

  private def lit: Int = popups.head.lit()

  /** `text` in `f`, the caret at `caret` (its end, by default), as typing leaves it. */
  private def typeIn(f: TextInput, text: String, caret: Int = -1): Unit = fx {
    f.value @= text
    f.field.get.moveCaret(if (caret < 0) text.length else caret)
  }

  /** The screen emptied: the focus let go of first, so no platform field is taken off still holding the window's focus
    * (its caret blinking on, and JavaFX pulsing for it, after this spec), and what was on it taken apart. */
  private def clear(): Unit = {
    Focus.focused @= None
    val left = ui.children().toList
    ui.children.clear()
    left.foreach(_.dispose())
  }

  private def onScreen[T](source: => CompletionSource, top: Double = 100.0)(test: TextInput => T): T = {
    val f = fx {
      asked = Nil
      clear()
      val input = new TextInput("To", width = 400.0)
      input.position.left @= 50.0
      input.position.top @= top
      ui.children += input
      input.completions @= Some(source)
      input.focus()
      input
    }
    try test(f) finally fx {
      while (Modal.showing) Modal.close()
      clear()
    }
  }

  "A completion token" should {
    "be the whole field, its spaces left out, wherever the caret is" in {
      CompletionToken.at("  par ", 3, "") shouldBe CompletionToken(2, 5, "par")
      CompletionToken.at("new york", 2, "") shouldBe CompletionToken(0, 8, "new york")
      CompletionToken.replace("par", CompletionToken(0, 3, "par"), "Paris") shouldBe (("Paris", 5))
    }
    "be what lies between separators, up to the caret" in {
      CompletionToken.at("ann@example.com, os", 19, ",;") shouldBe CompletionToken(17, 19, "os")
      CompletionToken.at("ann@example.com; os", 19, ",;") shouldBe CompletionToken(17, 19, "os")
      // the caret within a piece: what comes before it is asked about, and the whole piece replaced
      CompletionToken.at("ann, ossi, bob", 7, ",;") shouldBe CompletionToken(5, 9, "os")
      CompletionToken.at("ann, ", 5, ",;").text shouldBe ""
      CompletionToken.at("os", 2, ",;") shouldBe CompletionToken(0, 2, "os")
    }
    "go in with what follows it, unless a separator follows already" in {
      CompletionToken.replace("ann@example.com, os", CompletionToken(17, 19, "os"), "ossi@example.com", ", ",
        ",;") shouldBe
        (("ann@example.com, ossi@example.com, ", 35))
      CompletionToken.replace("ann, ossi, bob", CompletionToken(5, 9, "os"), "ossi@example.com", ", ", ",;") shouldBe
        (("ann, ossi@example.com, bob", 21))
      CompletionToken.replace("Trip to pr\nthen", CompletionToken(8, 10, "pr"), "Prague", " ", " \n") shouldBe
        (("Trip to Prague\nthen", 14))
    }
  }

  "A field with completions" should {
    "list the source's answers for the whole field, under it and as wide as it" in onScreen(citySource) { f =>
      typeIn(f, "pa")
      eventually(labels == Vector("Paris", "Parma")) shouldBe true
      asked shouldBe List("pa")
      fx(f.completing()) shouldBe true
      val p = fx(popups.head)
      fx(p.size.width()) shouldBe fx(f.size.width())
      fx(p.absoluteLeft) shouldBe fx(f.absoluteLeft)
      fx(p.absoluteTop) should be >= fx(f.absoluteTop + f.size.height())
      // a combo box with its list showing
      fx(f.field.get.semantics().role) shouldBe "combobox"
      fx(f.field.get.semantics().expanded) shouldBe Some(true)
      fx(p.rowAt(0).get.semantics().selected) shouldBe Some(true)
      fx(p.rowAt(1).get.semantics().selected) shouldBe Some(false)
      // the field named by its lit choice for JavaFX's reader, and by its own name again as the list closes
      fx(f.field.get.node.fx.getAccessibleText) shouldBe "Paris"
      press("Escape")
      fx(f.field.get.node.fx.getAccessibleText) shouldBe "To"
      fx(f.field.get.semantics().expanded) shouldBe Some(false)
    }
    "ask about the piece at the caret between separators" in onScreen(addressSource) { f =>
      typeIn(f, "ann@example.com, os")
      eventually(labels.length == 2) shouldBe true
      asked shouldBe List("os")
      typeIn(f, "ann@example.com, oss, bob", 19)
      eventually(asked == List("os", "os")) shouldBe true
      fx(labels.length) shouldBe 2
      // the caret moved into another piece (Home): asked about that one
      fx(f.field.get.moveCaret(2))
      eventually(asked.last == "an") shouldBe true
      eventually(labels == Vector("Ann Berg <ann@example.com>")) shouldBe true
      // and to a piece of nothing: closed
      fx(f.field.get.moveCaret(17))
      eventually(popups.isEmpty) shouldBe true
    }
    "drop an answer for a piece since typed on" in {
      var waiting: List[(String, Vector[Completion] => Unit)] = Nil
      val late = CompletionSource(t => Task.async[Vector[Completion]](done => waiting = waiting :+ (t, done)))
      onScreen(late) { f =>
        typeIn(f, "p")
        eventually(waiting.map(_._1) == List("p")) shouldBe true
        typeIn(f, "po")
        eventually(waiting.map(_._1) == List("p", "po")) shouldBe true
        fx(waiting(1)._2(Vector(Completion("Porto", "Porto"))))
        fx(labels) shouldBe Vector("Porto")
        // the answer for "p" coming last changes nothing
        fx(waiting.head._2(Vector(Completion("Paris", "Paris"), Completion("Prague", "Prague"))))
        fx(labels) shouldBe Vector("Porto")
        // nor does one coming after the list was closed
        typeIn(f, "pr")
        eventually(waiting.length == 3) shouldBe true
        press("Escape")
        fx(waiting(2)._2(Vector(Completion("Prague", "Prague"))))
        fx(popups) shouldBe empty
      }
    }
    "move the lit row round with Down and Up" in onScreen(citySource) { f =>
      typeIn(f, "p")
      eventually(labels.length == 4) shouldBe true
      fx(lit) shouldBe 0
      press("ArrowDown")
      fx(lit) shouldBe 1
      press("ArrowDown")
      press("ArrowDown")
      press("ArrowDown")
      fx(lit) shouldBe 0
      press("ArrowUp")
      fx(lit) shouldBe 3
      fx(f.field.get.node.fx.getAccessibleText) shouldBe "Prague"
      fx(Focus.focused().contains(f)) shouldBe true
    }
    "put the lit one in with Enter, submitting nothing, the caret after it" in onScreen(citySource) { f =>
      var submitted: List[String] = Nil
      var completed: List[Completion] = Nil
      fx {
        f.submitted.attach(s => submitted = submitted :+ s)
        f.completed.attach(c => completed = completed :+ c)
      }
      typeIn(f, "pa")
      eventually(labels.length == 2) shouldBe true
      press("ArrowDown")
      press("Enter")
      fx(f.value()) shouldBe "Parma"
      fx(popups) shouldBe empty
      fx(f.completing()) shouldBe false
      submitted shouldBe Nil
      completed shouldBe List(Completion("Parma", "Parma"))
      eventually(f.field.get.caret == 5) shouldBe true
      fx(Focus.focused().contains(f) && f.editing()) shouldBe true
      // what went in asks for nothing more; and Enter with no list submits as it did
      fx(asked) shouldBe List("pa")
      press("Enter")
      submitted shouldBe List("Parma")
    }
    "put the lit one in with Tab and what follows it, the focus staying" in onScreen(addressSource) { f =>
      var completed: List[Completion] = Nil
      fx(f.completed.attach(c => completed = completed :+ c))
      typeIn(f, "ann@example.com, os")
      eventually(labels.length == 2) shouldBe true
      press("ArrowDown")
      press("Tab")
      fx(f.value()) shouldBe "ann@example.com, oscar@example.com, "
      eventually(f.field.get.caret == 36) shouldBe true
      completed.map(_.insert) shouldBe List("oscar@example.com")
      fx(Focus.focused().contains(f)) shouldBe true
      fx(popups) shouldBe empty
      // typing on asks about the next piece
      typeIn(f, "ann@example.com, oscar@example.com, a")
      eventually(labels == Vector("Ann Berg <ann@example.com>")) shouldBe true
    }
    "choose the row clicked, the field still being typed in" in onScreen(citySource) { f =>
      typeIn(f, "p")
      eventually(labels.length == 4) shouldBe true
      val row = fx(popups.head.rowAt(2).get)
      fx(row.event.pointer.enter @= PointerEvent(PointerKind.Enter, row.absoluteLeft + 5.0, row.absoluteTop + 5.0))
      fx(lit) shouldBe 2
      fx(row.event.click @= PointerEvent(PointerKind.Click, row.absoluteLeft + 5.0, row.absoluteTop + 5.0))
      fx(f.value()) shouldBe "Porto"
      fx(Focus.focused().contains(f) && f.editing()) shouldBe true
    }
    "close the list alone on Escape, in a dialog too" in {
      val f = fx {
        asked = Nil
        clear()
        val c = new Component(Nodes.container())
        c.focusable @= true
        c.size.width @= 100.0
        c.size.height @= 40.0
        ui.children += c
        c.focus()
        val input = new TextInput("City")
        input.completions @= Some(citySource)
        Dialogs.show("Where", List(input), close => List(DialogAction("Done", close))).start()
        input.focus()
        input
      }
      try {
        typeIn(f, "po")
        eventually(labels == Vector("Porto")) shouldBe true
        press("Escape")
        fx(popups) shouldBe empty
        fx(Modal.showing) shouldBe true
        fx(f.value()) shouldBe "po"
        fx(Focus.focused().contains(f)) shouldBe true
        // Escape with no list is the dialog's again
        press("Escape")
        fx(Modal.showing) shouldBe false
      } finally fx {
        while (Modal.showing) Modal.close()
        clear()
      }
    }
    "show over the field where there is no room under it" in onScreen(citySource, fx(ui.size.height()) - 70.0) { f =>
      typeIn(f, "p")
      eventually(labels.length == 4) shouldBe true
      val p = fx(popups.head)
      fx(p.absoluteTop + p.size.height()) should be <= fx(f.absoluteTop)
    }
    "show its rows at most, scrolling to the lit one" in {
      val many = CompletionSource(t => Task((1 to 10).toVector.map(i => Completion(t + " " + i, t + i))), rows = 4)
      onScreen(many) { f =>
        typeIn(f, "x")
        eventually(labels.length == 10) shouldBe true
        val p = fx(popups.head)
        val scroll = fx(all(p).collect { case s: ScrollContainer => s }.head)
        val row = fx(p.rowAt(0).get.size.height())
        fx(scroll.size.height()) shouldBe row * 4
        fx(p.size.height()) should be < row * 5
        press("ArrowUp")
        fx(lit) shouldBe 9
        eventually(scroll.offset() > 0.0) shouldBe true
      }
    }
    "close when the source answers nothing" in onScreen(citySource) { f =>
      typeIn(f, "p")
      eventually(labels.length == 4) shouldBe true
      typeIn(f, "pz")
      eventually(popups.isEmpty) shouldBe true
      fx(f.completing()) shouldBe false
      // and on a key the list would have taken, the field's as ever
      fx(f.keepsEvent(KeyEvent(Key.ArrowDown, "ArrowDown"))) shouldBe false
    }
    "close as the field loses the focus" in onScreen(citySource) { f =>
      typeIn(f, "p")
      eventually(labels.length == 4) shouldBe true
      fx {
        val other = new Component(Nodes.container())
        other.focusable @= true
        other.size.width @= 50.0
        other.size.height @= 50.0
        ui.children += other
        other.focus()
      }
      fx(popups) shouldBe empty
      fx(f.completing()) shouldBe false
    }
    "close as the field is taken apart, asking nothing after" in onScreen(citySource) { f =>
      typeIn(f, "p")
      eventually(labels.length == 4) shouldBe true
      val p = fx(popups.head)
      val listeners = fx(f.value.reactions().size)
      fx(f.dispose())
      fx(popups) shouldBe empty
      fx(p.takenApart) shouldBe true
      fx(f.value.reactions().size) should be < listeners
      fx(f.value @= "pa")
      fx(())
      asked shouldBe List("p")
    }
  }

  "A box of lines with completions" should {
    "complete the word at the caret, Enter choosing while the list shows" in {
      val box = fx {
        asked = Nil
        clear()
        val b = new TextArea("Notes", 400.0)
        ui.children += b
        b.completions @= Some(CompletionSource(t => {
          asked = asked :+ t
          Task(cities.filter(_.toLowerCase.startsWith(t.toLowerCase)).map(c => Completion(c, c)))
        }, separators = " \n", after = " "))
        b.focus()
        b
      }
      try {
        fx(box.keepsEvent(KeyEvent(Key.Enter, "Enter"))) shouldBe false
        fx {
          box.value @= "Trip to pr\nthen home"
          box.field.get.moveCaret(10)
        }
        eventually(labels == Vector("Prague")) shouldBe true
        asked shouldBe List("pr")
        fx(box.keepsEvent(KeyEvent(Key.Enter, "Enter"))) shouldBe true
        press("Enter")
        fx(box.value()) shouldBe "Trip to Prague\nthen home"
        eventually(box.field.get.caret == 14) shouldBe true
        fx(popups) shouldBe empty
      } finally fx(clear())
    }
  }

  "A field's completions with actions of their own" should {
    // cities that may be forgotten, each row's action taking it out of what the source answers
    var recent = cities
    var removed: List[String] = Nil
    def forgetting: CompletionSource = CompletionSource(t => {
      asked = asked :+ t
      Task(recent.filter(_.toLowerCase.startsWith(t.toLowerCase)).map(c => Completion(c, c,
        actions = Vector(CompletionAction(MaterialIcon.Close, "Remove", () => {
          removed = removed :+ c
          recent = recent.filterNot(_ == c)
        }, removes = true)))))
    })
    def fresh(): Unit = {
      recent = cities
      removed = Nil
    }
    // the buttons of the row at `i`, in order from its start
    def actionsOf(i: Int): List[Component] = fx(popups.head.rowAt(i).get.childComponents.toList
      .filter(_.semantics().role == "button").sortBy(_.absoluteLeft))
    def click(on: Component): Unit = fx {
      val x = on.absoluteLeft + on.size.width() / 2.0
      val y = on.absoluteTop + on.size.height() / 2.0
      javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
        MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, false, false, true, null))
    }
    def shiftDelete(): Unit = fx {
      javafx.event.Event.fireEvent(window, new FxKeyEvent(FxKeyEvent.KEY_PRESSED, "", "", KeyCode.DELETE, true, false,
        false, false))
      javafx.event.Event.fireEvent(window, new FxKeyEvent(FxKeyEvent.KEY_RELEASED, "", "", KeyCode.DELETE, false,
        false, false, false))
    }

    "run the action pressed and not choose its row, the list staying and asked again" in onScreen(forgetting) { f =>
      fresh()
      var chosen: List[Completion] = Nil
      fx(f.completed.attach(c => chosen = chosen :+ c))
      typeIn(f, "p")
      eventually(labels == Vector("Paris", "Parma", "Porto", "Prague")) shouldBe true
      fx(popups.head.rowAt(2).get.event.pointer.enter @= PointerEvent(PointerKind.Enter, 0.0, 0.0))
      val remove = actionsOf(1)
      remove.length shouldBe 1
      // at the row's end, inside it, named for what it does and the row, its tip what it does
      val row = fx(popups.head.rowAt(1).get)
      fx(remove.head.absoluteLeft + remove.head.size.width()) should be <= fx(row.absoluteLeft + row.size.width())
      fx(remove.head.absoluteLeft) should be > fx(row.absoluteLeft + row.size.width() / 2.0)
      fx(remove.head.semantics().label) shouldBe "Remove, Parma"
      fx(remove.head.tooltip()) shouldBe "Remove"
      fx(row.semantics().description) shouldBe "Remove (Shift+Delete)"
      click(remove.head)
      removed shouldBe List("Parma")
      // not chosen: the field as it was, the focus and the typing kept, nothing told, the list still showing
      fx(f.value()) shouldBe "p"
      chosen shouldBe empty
      fx(Focus.focused().contains(f) && f.editing()) shouldBe true
      fx(f.completing()) shouldBe true
      // the row gone at once, the lit one (Porto) still lit, and the source asked again for the same piece
      eventually(labels == Vector("Paris", "Porto", "Prague")) shouldBe true
      eventually(asked == List("p", "p")) shouldBe true
      fx(popups.head.completions(lit).label) shouldBe "Porto"
    }
    "remove the lit row with Shift+Delete, closing with the last, and leave Delete alone to the field" in
      onScreen(forgetting) { f =>
        fresh()
        typeIn(f, "p")
        eventually(labels.length == 4) shouldBe true
        press("ArrowDown")
        fx(lit) shouldBe 1
        fx(f.keepsEvent(KeyEvent(Key.Other, "Delete", shift = true))) shouldBe true
        // Delete alone, and with another modifier, are the field's
        fx(f.keepsEvent(KeyEvent(Key.Other, "Delete"))) shouldBe false
        fx(f.keepsEvent(KeyEvent(Key.Other, "Delete", ctrl = true, shift = true))) shouldBe false
        shiftDelete()
        removed shouldBe List("Parma")
        fx(f.value()) shouldBe "p"
        eventually(labels == Vector("Paris", "Porto", "Prague")) shouldBe true
        fx(popups.head.completions(lit).label) shouldBe "Porto"
        fx(Focus.focused().contains(f) && f.editing()) shouldBe true
        // the last two lit and removed: the list closes with the last
        shiftDelete()
        shiftDelete()
        shiftDelete()
        removed shouldBe List("Parma", "Porto", "Prague", "Paris")
        eventually(popups.isEmpty) shouldBe true
        fx(f.completing()) shouldBe false
        fx(f.value()) shouldBe "p"
      }
    "leave Shift+Delete to the field where the lit row has no removing action" in onScreen(citySource) { f =>
      typeIn(f, "p")
      eventually(labels.length == 4) shouldBe true
      fx(f.keepsEvent(KeyEvent(Key.Other, "Delete", shift = true))) shouldBe false
      actionsOf(0) shouldBe empty
      fx(popups.head.rowAt(0).get.semantics().description) shouldBe ""
    }
    "be drawn by no list but a field's: the composer's draws its rows as before" in {
      val withActions = Vector(Completion("Ada", "@ada", Some("@ada"),
        actions = Vector(CompletionAction(MaterialIcon.Close, "Remove", () => (), removes = true))))
      val (composer, field) = fx {
        val a = new CompletionPopup(withActions, Var(0), 300.0)
        val b = new CompletionPopup(withActions, Var(0), 300.0, actions = true)
        (a, b)
      }
      try {
        fx(composer.rowAt(0).get.childComponents.length) shouldBe 2
        fx(composer.rowAt(0).get.semantics().description) shouldBe ""
        fx(field.rowAt(0).get.childComponents.length) shouldBe 3
        // the same row, its words where they were and as wide, but for the room the button takes at the end
        val plain = fx(new CompletionPopup(Vector(Completion("Ada", "@ada", Some("@ada"))), Var(0), 300.0))
        fx(composer.rowAt(0).get.childComponents.map(c => (c.absoluteLeft, c.size.width())).toList) shouldBe
          fx(plain.rowAt(0).get.childComponents.map(c => (c.absoluteLeft, c.size.width())).toList)
        fx(plain.dispose())
      } finally fx {
        composer.dispose()
        field.dispose()
      }
    }
  }

  "The composer's keys" should {
    "be as they were while its completions show" in {
      val s = ComposerState(completing = true)
      ComposerKeys.action(KeyEvent(Key.ArrowDown, "ArrowDown"), s) shouldBe ComposerAction.Next
      ComposerKeys.action(KeyEvent(Key.ArrowUp, "ArrowUp"), s) shouldBe ComposerAction.Previous
      ComposerKeys.action(KeyEvent(Key.Enter, "Enter"), s) shouldBe ComposerAction.Complete
      ComposerKeys.action(KeyEvent(Key.Tab, "Tab"), s) shouldBe ComposerAction.Complete
      ComposerKeys.action(KeyEvent(Key.Escape, "Escape"), s) shouldBe ComposerAction.Close
      ComposerKeys.action(KeyEvent(Key.Enter, "Enter", shift = true), s) shouldBe ComposerAction.Pass
      ComposerKeys.action(KeyEvent(Key.ArrowDown, "ArrowDown"), s.copy(editing = false)) shouldBe ComposerAction.Pass
      ComposerKeys.action(KeyEvent(Key.Tab, "Tab"), ComposerState()) shouldBe ComposerAction.Pass
    }
  }
}

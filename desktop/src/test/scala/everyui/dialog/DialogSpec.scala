package everyui.dialog

import everyui.async.Task
import everyui.component.*
import everyui.component.types.WhiteSpace
import everyui.focus.Focus
import everyui.i18n.{Language, Strings, Words}
import everyui.kit.{AsyncPress, Toast}
import everyui.render.{Nodes, Platform}
import everyui.{FxSpec, ui}
import reactify.*

/** The questions: each answered as a Task once it has closed and the focus is back, Back as the answer that changes
  * nothing, a prompt checked as it is answered, async buttons, and EveryUI's own words in the language chosen. */
class DialogSpec extends FxSpec {
  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private def focusedButton: Option[String] = fx(Focus.focused().collect { case b: DialogButton => b.action.label })

  /** `task` started, its answer caught. */
  private def caught[T](task: => Task[T]): Var[Option[T]] = {
    val answer: Var[Option[T]] = Var(None)
    fx(task.map(v => answer @= Some(v)).start())
    answer
  }

  private def onScreen[T](test: Component => T): T = {
    val card = fx {
      ui.children.clear()
      val c = new Component(Nodes.container())
      c.focusable @= true
      c.size.width @= 100.0
      c.size.height @= 40.0
      ui.children += c
      c.focus()
      c
    }
    try test(card) finally fx {
      while (Modal.showing) Modal.close()
      Toast.hide()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A confirmation" should {
    "answer true for its button and false for Back, once the focus is back" in onScreen { card =>
      val yes = caught(Dialogs.confirm("Publish?", "Everyone sees it.", "Publish"))
      focusedButton shouldBe Some("Publish")
      yes() shouldBe None
      press("Enter")
      yes() shouldBe Some(true)
      fx(Focus.focused()) shouldBe Some(card)
      val no = caught(Dialogs.confirm("Publish?", "Everyone sees it.", "Publish"))
      press("Escape")
      no() shouldBe Some(false)
      fx(Modal.showing) shouldBe false
    }
    "open a dangerous one on Cancel" in onScreen { _ =>
      val answer = caught(Dialogs.confirm("Delete?", "It cannot be undone.", Words.delete, danger = true))
      focusedButton shouldBe Some("Cancel")
      press("ArrowRight")
      focusedButton shouldBe Some("Delete")
      press("Enter")
      answer() shouldBe Some(true)
    }
  }

  "Unsaved work" should {
    "be saved, left unsaved or kept" in onScreen { _ =>
      val first = caught(Dialogs.saveChanges("Save notes.txt?", "Your changes are lost if you don't save them."))
      focusedButton shouldBe Some("Save")
      press("ArrowLeft")
      press("ArrowLeft")
      focusedButton shouldBe Some("Don't save")
      press("Enter")
      first() shouldBe Some(SaveChoice.Discard)
      val second = caught(Dialogs.saveChanges("Save notes.txt?", ""))
      press("Enter")
      second() shouldBe Some(SaveChoice.Save)
    }
  }

  "A prompt" should {
    "show what is wrong under the field until it changes, and answer what passes" in onScreen { _ =>
      var check: Option[String] => Unit = _ => ()
      val answer = caught(Dialogs.prompt("Rename", "notes.txt", "Name",
        validate = n => Task.async[Option[String]](done => check = done)))
      val field = fx(Focus.focused().collect { case t: TextInput => t }.get)
      fx(field.value()) shouldBe "notes.txt"
      fx(field.submitted @= field.value())
      fx(check(Some("Already taken")))
      eventually(Focus.candidates(ui).exists(_.kind == "DialogButton(OK)") &&
        TextSeen("Already taken")) shouldBe true
      answer() shouldBe None
      fx(field.value @= "plans.txt")
      fx(TextSeen("Already taken")) shouldBe false
      fx(field.submitted @= field.value())
      fx(check(None))
      eventually(answer().nonEmpty) shouldBe true
      answer() shouldBe Some(Some("plans.txt"))
      fx(Modal.showing) shouldBe false
    }
    "answer None when cancelled" in onScreen { _ =>
      val answer = caught(Dialogs.prompt("Rename", "notes.txt"))
      press("Escape")
      answer() shouldBe Some(None)
    }
  }

  "A dialog" should {
    "spin an async button, toast its failure, and let it be pressed again" in onScreen { _ =>
      var finish: AsyncPress.Done = null
      var runs = 0
      val closed = caught(Dialogs.show("Publish", Nil, close => List(
        DialogAction(Words.cancel, close),
        DialogAction.async("Publish", default = true) { done =>
          runs += 1
          finish = done
        })))
      press("Enter")
      press("Enter")
      runs shouldBe 1
      fx(finish(Left("Publishing failed")))
      eventually(TextSeen("Publishing failed")) shouldBe true
      press("Enter")
      runs shouldBe 2
      fx(Modal.close())
      closed() shouldBe Some(())
    }
    "read through a long body of words with the arrows" in onScreen { _ =>
      val long = fx(new TextView {
        whiteSpace @= WhiteSpace.Normal
        size.width @= 400.0
        content @= List.fill(200)("A long message.").mkString(" ")
      })
      val panel = fx(new DialogPanel("Terms", List(long), List(DialogAction(Words.ok, () => Modal.close(), default = true))))
      fx(panel.open())
      val scroll = fx(Focus.candidates(panel).head)
      fx(scroll.kind) shouldBe "ScrollContainer"
      fx(panel.size.height()) should be <= fx(ui.size.height())
    }
  }

  "EveryUI's words" should {
    "follow the language, falling back to its base and to English" in {
      fx(Words.cancel) shouldBe "Cancel"
      fx(Strings.language @= Language("de", "Deutsch", "German"))
      fx(Words.cancel) shouldBe "Abbrechen"
      fx(Strings.language @= Language("pt_BR", "Português", "Portuguese"))
      fx(Words.save) shouldBe "Salvar"
      fx(Words.add("pt_BR", Map("save" -> "Gravar")))
      fx(Words.save) shouldBe "Gravar"
      fx(Strings.language @= Language("xx", "X", "X"))
      fx(Words.ok) shouldBe "OK"
      fx(Strings.language @= Language("en", "English", "English"))
    }
  }

  /** Whether `words` are on the screen. */
  private def TextSeen(words: String): Boolean = {
    var pending: List[Component] = List(ui)
    var found = false
    while (!found && pending.nonEmpty) {
      pending.head match {
        case t: TextView if t.content() == words => found = true
        case _ => ()
      }
      pending = pending.head.childComponents.toList ++ pending.tail
    }
    found
  }
}

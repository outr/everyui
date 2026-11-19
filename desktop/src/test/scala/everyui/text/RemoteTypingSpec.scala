package everyui.text

import everyui.component.{Container, TextInput}
import everyui.focus.Focus
import everyui.kit.SearchField
import everyui.layout.VerticalLayout
import everyui.{FxSpec, ui}

/** A field worked by a remote, its keyboard on the screen (Android TV's, a TV's web runtime: `keyboardSupport` false):
  * the arrows move the focus onto it and off it, lighting it and no more; OK starts the typing (the platform's field
  * given the keyboard); and while it is typed in, Enter or Done ends the typing and submits, Back ends it and goes no
  * further, Up and Down end it and move the focus on, and the platform's field let go by itself (its keyboard put away)
  * ends it, the field keeping the focus each time. Run as the desktop runs on a remote (`remote=true`). */
class RemoteTypingSpec extends FxSpec {
  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  // a form as a sign-in screen has it: an email, a password and a search, one under another, Back counted
  private def form[T](test: (TextInput, TextInput, SearchField, () => Int, () => List[String]) => T): T = {
    val was = fx(everyui.render.Host.args)
    fx(everyui.render.Host.args = was + ("remote" -> "true"))
    var backs = 0
    var submitted = List.empty[String]
    val (email, password, search, release) = fx {
      ui.children.clear()
      Focus.focused @= None
      val email = new TextInput("Email")
      val password = new TextInput("Password", secure = true)
      val search = new SearchField()
      email.submitted.attach(v => submitted :+= "email " + v)
      password.submitted.attach(v => submitted :+= "password " + v)
      search.submitted.attach(v => submitted :+= "search " + v)
      val column = new Container
      column.layout @= Some(VerticalLayout(10.0))
      column.children ++= List(email, password, search)
      ui.children += column
      (email, password, search, Focus.onBack(_ => {
        backs += 1
        true
      }))
    }
    try test(email, password, search, () => backs, () => submitted) finally fx {
      release()
      ui.children.clear()
      Focus.focused @= None
      everyui.render.Host.args = was
    }
  }

  private def focused(c: everyui.component.Component): Boolean = fx(Focus.focused().exists(_ eq c))

  "A field worked by a remote" should {
    "light as the arrows move onto it, take the keyboard only on OK, and let the arrows pass on" in {
      form { (email, password, _, _, submitted) =>
        fx(email.focus())
        fx(email.editing()) shouldBe false
        press("ArrowDown")
        focused(password) shouldBe true
        fx((email.editing(), password.editing())) shouldBe ((false, false))
        press("ArrowUp")
        focused(email) shouldBe true
        fx(email.editing()) shouldBe false
        // OK starts the typing, and submits nothing
        press("Enter")
        fx(email.editing()) shouldBe true
        submitted() shouldBe Nil
        focused(email) shouldBe true
      }
    }

    "end the typing on Enter and submit, the focus kept" in {
      form { (email, _, _, _, submitted) =>
        fx(email.focus())
        press("Enter")
        fx(email.value @= "ada@example.com")
        press("Enter")
        fx(email.editing()) shouldBe false
        submitted() shouldBe List("email ada@example.com")
        focused(email) shouldBe true
        // and OK again starts it again, submitting nothing more
        press("Enter")
        fx(email.editing()) shouldBe true
        submitted() shouldBe List("email ada@example.com")
      }
    }

    "end the typing on Back, which goes no further, the focus kept" in {
      form { (_, password, _, backs, submitted) =>
        fx(password.focus())
        press("Enter")
        fx(password.editing()) shouldBe true
        press("GoBack")
        fx(password.editing()) shouldBe false
        focused(password) shouldBe true
        backs() shouldBe 0
        submitted() shouldBe Nil
        // not typing, Back is the screen's
        press("GoBack")
        backs() shouldBe 1
      }
    }

    "end the typing on Up or Down and move the focus on, and on Up with nowhere to go, keep it" in {
      form { (email, password, search, _, _) =>
        fx(password.focus())
        press("Enter")
        press("ArrowDown")
        fx(password.editing()) shouldBe false
        focused(search) shouldBe true
        press("Enter")
        fx(search.editing()) shouldBe true
        press("ArrowUp")
        fx(search.editing()) shouldBe false
        focused(password) shouldBe true
        fx(email.focus())
        press("Enter")
        press("ArrowUp")
        fx(email.editing()) shouldBe false
        focused(email) shouldBe true
      }
    }

    "keep Left and Right in the words while typing" in {
      form { (email, _, _, _, _) =>
        fx(email.focus())
        press("Enter")
        fx(email.typesEvent(everyui.event.KeyEvent(everyui.event.Key.ArrowLeft, "ArrowLeft"))) shouldBe true
        press("ArrowRight")
        fx(email.editing()) shouldBe true
        focused(email) shouldBe true
      }
    }

    "end the typing when the platform's field lets go by itself, its keyboard put away, the focus kept" in {
      // in a window, where the platform's field takes the keyboard
      onStage()
      try form { (email, _, _, _, submitted) =>
        fx(email.focus())
        press("Enter")
        eventually(email.field.exists(_.node.fx.isFocused)) shouldBe true
        fx(everyui.render.Host.root.requestFocus())
        eventually(!email.editing()) shouldBe true
        focused(email) shouldBe true
        submitted() shouldBe Nil
      } finally offStage()
    }

    "end a search's typing on Back without emptying it" in {
      form { (_, _, search, backs, _) =>
        fx(search.focus())
        press("Enter")
        fx(search.value @= "lovelace")
        press("Escape")
        fx(search.editing()) shouldBe false
        fx(search.value()) shouldBe "lovelace"
        backs() shouldBe 0
      }
    }
  }
}

package everyui.drive

import everyui.component.{Button, Column, Modal, TextView}
import everyui.focus.Focus
import everyui.{FxSpec, ui}

/** The drive's lines (`Drive.parse`, `answer`): verbs, numbers and quoted words taken apart; points on the screen
  * turned into the app's units; keys by their short names; a press finding the button named so (exactly, in any case,
  * a part of it), a focusable one before the words inside it, nothing for a hidden one or a name nothing has; find,
  * labels and an unknown verb's answer. (The platform's side, the touches and keys these become, is the Apple
  * runtime's, checked on the simulators with tools/apple-drive.sh.) */
class DriveSpec extends FxSpec {
  private def action(answer: String): String = answer.substring(0, answer.indexOf(Drive.Sep))
  private def reply(answer: String): String = answer.substring(answer.indexOf(Drive.Sep) + 1)

  private def screen(): (Button, Button, Button) = fx {
    while (Modal.showing) Modal.close()
    ui.children.clear()
    Focus.focused @= None
    val english = new Button("English")
    val spanish = new Button("Español")
    val hidden = new Button("Hidden one")
    hidden.visible @= false
    val column = Column(spacing = 10.0)(TextView("Choose a language"), english, spanish, hidden)
    column.position.left @= 40.0
    column.position.top @= 20.0
    ui.children += column
    (english, spanish, hidden)
  }

  "Drive.parse" should {
    "take a line apart into its verb, its numbers and its words" in {
      Drive.parse("tap 120 300") shouldBe DriveCommand("tap", Vector(120.0, 300.0), "")
      Drive.parse("  SWIPE 1 2.5 3 4 250 ") shouldBe DriveCommand("swipe", Vector(1.0, 2.5, 3.0, 4.0, 250.0), "")
      Drive.parse("press \"Sign in\"") shouldBe DriveCommand("press", Vector.empty, "Sign in")
      Drive.parse("type 'a b c'") shouldBe DriveCommand("type", Vector.empty, "a b c")
      Drive.parse("key down") shouldBe DriveCommand("key", Vector.empty, "down")
      Drive.parse("labels") shouldBe DriveCommand("labels", Vector.empty, "")
      Drive.parse("tap here") shouldBe DriveCommand("tap", Vector.empty, "here")
    }
    "name keys by the web's names" in {
      List("up", "Down", "left", "right", "select", "ok", "back", "menu", "esc", "space", "play", "F5")
        .map(Drive.keyName) shouldBe
        List("ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Enter", "Enter", "Back", "Back", "Escape", " ",
          "MediaPlayPause", "F5")
    }
  }

  "Drive.answer" should {
    "turn the screen's points into the app's units, with the default times" in {
      action(Drive.answer(Drive.parse("tap 120 300"), ui)) shouldBe "tap 120 300"
      action(Drive.answer(Drive.parse("hold 5 6"), ui)) shouldBe "hold 5 6 600"
      action(Drive.answer(Drive.parse("swipe 200 600 200 200"), ui)) shouldBe "swipe 200 600 200 200 300"
      action(Drive.answer(Drive.parse("key back"), ui)) shouldBe "key Back"
      action(Drive.answer(Drive.parse("type abc"), ui)) shouldBe "type abc"
      reply(Drive.answer(Drive.parse("tap 1 2"), ui)) should startWith("ok")
    }
    "press the button named so, at its middle" in {
      val (english, spanish, _) = screen()
      eventually(english.size.width() > 0.0) shouldBe true
      fx {
        Drive.find(ui, "English") shouldBe Some(english)
        Drive.find(ui, "español") shouldBe Some(spanish)
        Drive.find(ui, "Esp") shouldBe Some(spanish)
        val x = english.absoluteLeft + english.size.width() / 2.0
        val y = english.absoluteTop + english.size.height() / 2.0
        val answer = Drive.answer(Drive.parse("press English"), ui)
        val at = action(answer).split(' ').toList
        at.head shouldBe "tap"
        at(1).toDouble shouldBe x +- 0.06
        at(2).toDouble shouldBe y +- 0.06
        reply(answer) should startWith("ok pressed")
      }
    }
    "find nothing hidden or unnamed" in {
      screen()
      fx {
        Drive.find(ui, "Hidden one") shouldBe None
        Drive.find(ui, "Deutsch") shouldBe None
        reply(Drive.answer(Drive.parse("press Deutsch"), ui)) shouldBe "error nothing showing is named Deutsch"
        action(Drive.answer(Drive.parse("press Deutsch"), ui)) shouldBe "say"
      }
    }
    "say where things are, what is focusable, and what is not a command" in {
      val (english, _, _) = screen()
      eventually(english.size.width() > 0.0) shouldBe true
      fx {
        reply(Drive.answer(Drive.parse("find English"), ui)) shouldBe "ok " + Drive.where(english)
        val labels = reply(Drive.answer(Drive.parse("labels"), ui)).split("\n").toList
        labels.head shouldBe "ok 2"
        labels.tail.map(_.takeWhile(_ != ' ')) shouldBe List("English", "Español")
        reply(Drive.answer(Drive.parse("dance"), ui)) shouldBe "error not a command: dance"
      }
    }
  }
}

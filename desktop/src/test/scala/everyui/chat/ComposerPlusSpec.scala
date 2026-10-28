package everyui.chat

import everyui.async.Task
import everyui.component.{Button, Component, IconButton}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.menu.Menu
import everyui.{FxSpec, ui}
import reactify.*

/** A composer's "+" at the start of its line: a menu of what can go with a message, each entry only where it applies
  * (Upload files where files can go, Mention someone where the app completes mentions, Emoji where the app moves the
  * picker there), then the app's own (`plusItems`) after a rule; choosing one runs it, Mention someone putting an "@" in
  * at the caret and listing who; the "+" not there where its menu would be empty; and on a phone, in the tray. */
class ComposerPlusSpec extends FxSpec {
  private def make(mentions: Boolean): Composer = fx {
    val c = new Composer(_ => (), touch = false,
      complete = if (mentions) Some((t: Char, p: String) => Task(Vector(Completion("Ada", "@ada")))) else None,
      triggers = if (mentions) List(CompletionTrigger('@')) else Nil)
    ui.children.clear()
    Focus.focused @= None
    ui.children += c
    c.focusField()
    c
  }
  private def under[T](c: Component)(using t: scala.reflect.ClassTag[T]): Vector[T] = {
    var found = Vector.empty[T]
    var pending = List(c)
    while (pending.nonEmpty) {
      val at = pending.head
      pending = at.childComponents.toList ::: pending.tail
      at match {
        case x: T => found = found :+ x
        case _ => ()
      }
    }
    found
  }
  private val words = ComposerWords()
  private def plus(c: Composer): Option[IconButton] =
    under[IconButton](c).find(b => b.icon.icon() == MaterialIcon.Add && b.tooltip() == words.add)
  private def labels(c: Composer): List[String] = c.plusEntries.map(_.label)
  private def settle(): Unit = (1 to 4).foreach(_ => fx(()))

  "A composer's \"+\"" should {
    "offer what applies, then the app's after a rule" in {
      val c = make(mentions = true)
      fx(labels(c)) shouldBe List(words.upload, words.mention)
      fx(c.attachable @= false)
      fx(labels(c)) shouldBe List(words.mention)
      var ran = 0
      fx(c.plusItems @= List(ComposerItem("Import a chat…", Some(MaterialIcon.FileUpload), action = () => ran += 1),
        ComposerItem.separator, ComposerItem("Start a poll")))
      fx(labels(c)) shouldBe List(words.mention, "", "Import a chat…", "", "Start a poll")
      fx(c.plusEntries.find(_.label == "Import a chat…").get.action())
      ran shouldBe 1
      // the picker moved into it, and its own button gone
      fx(c.emojiInMenu @= true)
      fx(labels(c)) should contain(everyui.emoji.EmojiWords.emoji)
      fx(under[IconButton](c).exists(_.icon.icon() == MaterialIcon.InsertEmoticon)) shouldBe false
      fx(c.dispose())
    }
    "put an \"@\" in at the caret for Mention someone, and list who" in {
      val c = make(mentions = true)
      fx(c.text @= "ask")
      fx(c.field.field.foreach(_.moveCaret(3)))
      settle()
      fx(c.plusEntries.find(_.label == words.mention).get.action())
      settle()
      fx(c.text()) shouldBe "ask @"
      eventually(ui.children().exists(_.isInstanceOf[CompletionPopup])) shouldBe true
      fx(c.dispose())
    }
    "open its menu from the button, and not be there where the menu would be empty" in {
      val c = make(mentions = false)
      fx(plus(c).nonEmpty) shouldBe true
      fx(plus(c).get.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0.0, 0.0))
      settle()
      fx(ui.children().exists(c => c.childComponents.exists(_.isInstanceOf[Menu]))) shouldBe true
      fx(while (everyui.component.Modal.showing) everyui.component.Modal.close())
      fx(c.attachable @= false)
      settle()
      fx(plus(c)) shouldBe None
      fx(c.plusItems @= List(ComposerItem("Add a web page")))
      settle()
      fx(plus(c).nonEmpty) shouldBe true
      fx(c.dispose())
    }
    "be in the tray on a phone" in {
      val c = make(mentions = true)
      val mine = fx(new IconButton(MaterialIcon.Mic, "Talk"))
      fx {
        c.leading.children += mine
        c.size.width @= 360.0
      }
      settle()
      val more = fx(under[IconButton](c).find(b => b.icon.icon() == MaterialIcon.Add && b.tooltip() == words.more).get)
      fx(more.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0.0, 0.0))
      settle()
      fx(under[Button](c).count(_.label() == words.add)) shouldBe 1
      fx(c.dispose())
    }
  }
}

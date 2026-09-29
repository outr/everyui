package everyui.chat

import everyui.event.{Key, KeyEvent}
import everyui.file.FileTypes
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The composer's rules on their own: what each key does in each state, the word a completion replaces and how, and
  * which files a list of types takes. */
class ComposerKeysSpec extends AnyWordSpec with Matchers {
  private def key(name: String, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false): KeyEvent =
    KeyEvent(everyui.event.Keys.of(name), name, ctrl = ctrl, shift = shift, alt = alt)

  private val ready = ComposerState(sendable = true)

  "Enter" should {
    "send what there is, and nothing when there is nothing" in {
      ComposerKeys.action(key("Enter"), ready) shouldBe ComposerAction.Send
      ComposerKeys.action(key("Enter"), ready.copy(sendable = false)) shouldBe ComposerAction.Hold
    }
    "type a new line with Shift, or where the keyboard is on the screen" in {
      ComposerKeys.action(key("Enter", shift = true), ready) shouldBe ComposerAction.Pass
      ComposerKeys.action(key("Enter"), ready.copy(touch = true)) shouldBe ComposerAction.Pass
    }
    "send with Primary, a touch screen's keyboard too" in {
      ComposerKeys.action(key("Enter", ctrl = true), ready) shouldBe ComposerAction.Send
      ComposerKeys.action(key("Enter", ctrl = true), ready.copy(touch = true)) shouldBe ComposerAction.Send
    }
    "be kept and do nothing while a reply comes, or out of use" in {
      ComposerKeys.action(key("Enter"), ready.copy(busy = true)) shouldBe ComposerAction.Hold
      ComposerKeys.action(key("Enter"), ready.copy(enabled = false)) shouldBe ComposerAction.Hold
    }
    "be the input method's while it composes" in {
      ComposerKeys.action(key("Enter"), ready, composing = true) shouldBe ComposerAction.Pass
    }
    "be nothing of the composer's while the field is not being typed in" in {
      ComposerKeys.action(key("Enter"), ready.copy(editing = false)) shouldBe ComposerAction.Pass
    }
  }

  "A suggestion" should {
    val suggesting = ComposerState(suggesting = true)
    "be taken by Tab or Right, and put away by Escape" in {
      ComposerKeys.action(key("Tab"), suggesting) shouldBe ComposerAction.Accept
      ComposerKeys.action(key("ArrowRight"), suggesting) shouldBe ComposerAction.Accept
      ComposerKeys.action(key("Escape"), suggesting) shouldBe ComposerAction.Dismiss
    }
    "leave Tab to move the focus when there is none, and Shift+Tab always" in {
      ComposerKeys.action(key("Tab"), ComposerState()) shouldBe ComposerAction.Pass
      ComposerKeys.action(key("Tab", shift = true), suggesting) shouldBe ComposerAction.Pass
      ComposerKeys.action(key("ArrowRight", ctrl = true), suggesting) shouldBe ComposerAction.Pass
    }
  }

  "Completions" should {
    val completing = ComposerState(completing = true, suggesting = true, sendable = true)
    "take the arrows, Enter, Tab and Escape before the suggestion and the send" in {
      ComposerKeys.action(key("ArrowDown"), completing) shouldBe ComposerAction.Next
      ComposerKeys.action(key("ArrowUp"), completing) shouldBe ComposerAction.Previous
      ComposerKeys.action(key("Enter"), completing) shouldBe ComposerAction.Complete
      ComposerKeys.action(key("Tab"), completing) shouldBe ComposerAction.Complete
      ComposerKeys.action(key("Escape"), completing) shouldBe ComposerAction.Close
    }
  }

  "The word at the caret" should {
    val triggers = List(CompletionTrigger('@'), CompletionTrigger('/', atStart = true))
    "start with a trigger after a space or at the start" in {
      TriggerWord.at("hi @gr", 6, triggers) shouldBe Some(TriggerWord('@', 3, 6, "gr"))
      TriggerWord.at("@", 1, triggers) shouldBe Some(TriggerWord('@', 0, 1, ""))
      TriggerWord.at("mail@gr", 7, triggers) shouldBe None
      TriggerWord.at("hi @gr there", 12, triggers) shouldBe None
    }
    "run on past the caret to the next space" in {
      TriggerWord.at("hi @grace ok", 6, triggers) shouldBe Some(TriggerWord('@', 3, 9, "gr"))
    }
    "take a command only at the start" in {
      TriggerWord.at("/su", 3, triggers) shouldBe Some(TriggerWord('/', 0, 3, "su"))
      TriggerWord.at("  /su", 5, triggers).map(_.prefix) shouldBe Some("su")
      TriggerWord.at("x /su", 5, triggers) shouldBe None
    }
    "be replaced by the completion, a space after it and the caret after that" in {
      TriggerWord.replace("hi @gr", TriggerWord('@', 3, 6, "gr"), "@grace") shouldBe ("hi @grace ", 10)
      TriggerWord.replace("hi @gr ok", TriggerWord('@', 3, 6, "gr"), "@grace") shouldBe ("hi @grace ok", 10)
      TriggerWord.replace("hi @grxx ok", TriggerWord('@', 3, 8, "gr"), "@grace") shouldBe ("hi @grace ok", 10)
    }
  }

  "A list of types" should {
    "take files by type, by family and by extension" in {
      FileTypes.accepts(Nil, "a.exe", "application/octet-stream") shouldBe true
      FileTypes.accepts(List("image/*"), "a.png", "image/png") shouldBe true
      FileTypes.accepts(List("image/*"), "a.txt", "text/plain") shouldBe false
      FileTypes.accepts(List(".MD"), "notes.md", "") shouldBe true
      FileTypes.accepts(List("text/plain"), "notes.txt", "text/plain") shouldBe true
    }
    "know a file's type by its name, and a picker's extensions" in {
      FileTypes.of("photo.JPG") shouldBe "image/jpeg"
      FileTypes.of("blob") shouldBe "application/octet-stream"
      FileTypes.extensions(List(".txt", "text/markdown")) shouldBe List("txt", "md")
      FileTypes.extensions(List("image/*")) should contain allOf ("png", "jpg", "gif")
    }
  }

  "A size" should {
    "read as a person reads it" in {
      Attachment.sizeText(812L) shouldBe "812 B"
      Attachment.sizeText(14L * 1024L) shouldBe "14 KB"
      Attachment.sizeText((3.2 * 1024 * 1024).toLong) shouldBe "3.2 MB"
    }
  }
}

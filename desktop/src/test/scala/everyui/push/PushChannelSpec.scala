package everyui.push

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A push channel's importance: every word "channelImportance" takes read, in any case, an unknown word or none read as
  * default, kept out of the push's data and written back as it was sent; the channel model's defaults; and declaring
  * channels and opening one's settings doing nothing where there are no channels (the desktop). */
class PushChannelSpec extends AnyWordSpec with Matchers {
  private def asked(importance: String): Option[PushChannel] =
    PushMessage.of(Map("title" -> "Hi", "channel" -> "calls", "channelImportance" -> importance)).channel

  "A push's channel importance" should {
    "be read from every word it is named by" in {
      List("min", "low", "default", "high", "urgent").map(w => asked(w).map(_.importance)) shouldBe
        List(NoticeImportance.Min, NoticeImportance.Low, NoticeImportance.Default, NoticeImportance.High,
          NoticeImportance.Urgent).map(Some(_))
    }
    "be read in any case, and around spaces" in {
      asked("HIGH").map(_.importance) shouldBe Some(NoticeImportance.High)
      asked(" Low ").map(_.importance) shouldBe Some(NoticeImportance.Low)
    }
    "be default for a word it does not know, or none" in {
      List("loud", "5", "max", "").map(w => asked(w).map(_.importance)) shouldBe
        List.fill(4)(Some(NoticeImportance.Default))
      PushMessage.of(Map("channel" -> "calls")).channel.map(_.importance) shouldBe Some(NoticeImportance.Default)
    }
    "say nothing where the push names no channel, and stay out of its data" in {
      val m = PushMessage.of(Map("title" -> "Hi", "channelImportance" -> "high", "uid" -> "7"))
      m.channel shouldBe None
      m.data shouldBe Map("uid" -> "7")
      PushMessage.of(Map("channel" -> "c", "channelImportance" -> "high")).data shouldBe Map.empty
    }
    "be written back as it was sent, and not at all where it is default" in {
      NoticeImportance.values.toList.foreach { i =>
        val m = PushMessage.of(Map("title" -> "Hi", "channel" -> "calls", "channelImportance" -> i.word))
        PushMessage.of(m.fields) shouldBe m
        m.fields.get("channelImportance") shouldBe (if (i == NoticeImportance.Default) None else Some(i.word))
      }
    }
    "name each importance by its own word" in {
      NoticeImportance.values.toList.map(i => NoticeImportance.named(i.word)) shouldBe NoticeImportance.values.toList
    }
  }

  "A channel" should {
    "be default importance with no group or description unless it says so" in {
      val c = PushChannel("messages", "Messages")
      (c.group, c.groupName, c.importance, c.description) shouldBe ("", "", NoticeImportance.Default, "")
      PushChannel.default.importance shouldBe NoticeImportance.Default
    }
    "keep its group, importance and description when read from a push" in {
      asked("urgent") shouldBe Some(PushChannel("calls", "calls", importance = NoticeImportance.Urgent))
      PushMessage.of(Map("channel" -> "calls", "channelName" -> "Calls", "channelGroup" -> "talk",
        "channelGroupName" -> "Talk", "channelImportance" -> "high")).channel shouldBe
        Some(PushChannel("calls", "Calls", "talk", "Talk", NoticeImportance.High))
    }
  }

  "Declaring channels and opening one's settings" should {
    "do nothing on the desktop, which has none" in {
      Push.channels(List(PushChannel("calls", "Calls", importance = NoticeImportance.High)))
      var opened: Option[Boolean] = None
      Push.openChannelSettings("calls").map(o => opened = Some(o)).start()
      opened shouldBe Some(false)
    }
  }
}

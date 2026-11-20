package everyui.push

import everyui.Color
import everyui.font.MaterialIcon
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The large icon Android shows a notice with, where its kind shows in the shade: its picture first, then its icon
  * white on a circle of its colour, else none; each glyph kept by icon, colour and size; and a group's summary that
  * names no icon taking its latest child's. */
class LargeIconSpec extends AnyWordSpec with Matchers {
  private def sent(keys: (String, String)*): PushMessage = PushMessage.of(Map("title" -> "Hi") ++ keys)
  private val blue = new Color(0x1e, 0x88, 0xe5, 255)
  private val green = new Color(0x43, 0xa0, 0x47, 255)

  "A push's large icon" should {
    "be its picture, then its icon on its colour, where it names both" in {
      LargeIcon.of(sent("noticePicture" -> "https://example.com/ann.png", "noticeIcon" -> "mail",
        "noticeColor" -> "#1E88E5")) shouldBe
        List(LargeIcon.Picture("https://example.com/ann.png"), LargeIcon.Glyph(MaterialIcon.Mail, blue))
    }
    "be its icon on its colour where it has no picture to fetch" in {
      LargeIcon.of(sent("noticeIcon" -> "forum", "noticeColor" -> "#43A047")) shouldBe
        List(LargeIcon.Glyph(MaterialIcon.Forum, green))
      LargeIcon.of(sent("noticeIcon" -> "forum", "noticeColor" -> "#43A047",
        "noticePicture" -> "file:///ann.png")) shouldBe
        List(LargeIcon.Glyph(MaterialIcon.Forum, green))
    }
    "fill its circle grey where it names no colour" in {
      LargeIcon.of(sent("noticeIcon" -> "error")) shouldBe
        List(LargeIcon.Glyph(MaterialIcon.Error, LargeIcon.defaultColor))
      LargeIcon.of(sent("noticeIcon" -> "error", "noticeColor" -> "red")) shouldBe
        List(LargeIcon.Glyph(MaterialIcon.Error, LargeIcon.defaultColor))
    }
    "be its picture alone where it names no icon the font has" in {
      LargeIcon.of(sent("noticePicture" -> "https://example.com/ann.png", "noticeIcon" -> "not_an_icon")) shouldBe
        List(LargeIcon.Picture("https://example.com/ann.png"))
    }
    "be none where it names neither, as before" in {
      LargeIcon.of(sent()) shouldBe Nil
      LargeIcon.of(sent("noticeColor" -> "#1E88E5")) shouldBe Nil
      LargeIcon.of(sent("noticeIcon" -> "not_an_icon", "noticeColor" -> "#1E88E5")) shouldBe Nil
    }
  }

  "A notice's large icon" should {
    "be its icon on its colour, or none" in {
      LargeIcon.of(Notice("Hi", noticeIcon = Some(MaterialIcon.Mail), noticeColor = Some(blue))) shouldBe
        List(LargeIcon.Glyph(MaterialIcon.Mail, blue))
      LargeIcon.of(Notice("Hi", noticeIcon = Some(MaterialIcon.Mail))) shouldBe
        List(LargeIcon.Glyph(MaterialIcon.Mail, LargeIcon.defaultColor))
      LargeIcon.of(Notice("Hi", icon = "https://example.com/ann.png", noticeColor = Some(blue))) shouldBe Nil
      val channel = NoticeChannel("sync", "Sync")
      LargeIcon.of(OngoingNotice("sync", "Syncing", channel = channel, noticeIcon = Some(MaterialIcon.Sync),
        noticeColor = Some(green))) shouldBe List(LargeIcon.Glyph(MaterialIcon.Sync, green))
      LargeIcon.of(OngoingNotice("sync", "Syncing", channel = channel)) shouldBe Nil
    }
  }

  "A glyph" should {
    "be kept by its icon, its colour and its size" in {
      LargeIcon.key(MaterialIcon.Mail, blue, 168) shouldBe
        LargeIcon.key(MaterialIcon.Mail, new Color(0x1e, 0x88, 0xe5, 255), 168)
      val keys = List(LargeIcon.key(MaterialIcon.Mail, blue, 168), LargeIcon.key(MaterialIcon.Forum, blue, 168),
        LargeIcon.key(MaterialIcon.Mail, green, 168), LargeIcon.key(MaterialIcon.Mail, blue, 112),
        LargeIcon.key(MaterialIcon.Mail, blue.withAlpha(0.5), 168))
      keys.distinct.size shouldBe keys.size
    }
  }

  "A group's summary" should {
    val child = sent("group" -> "kind:mail", "noticeIcon" -> "mail", "noticeColor" -> "#1E88E5")
    "look as its latest child where it names no icon" in {
      val summary = sent("group" -> "kind:mail", "groupSummary" -> "true").lookingAs(child)
      summary.shownIcon shouldBe Some(MaterialIcon.Mail)
      summary.noticeColor shouldBe Some(blue)
      LargeIcon.of(summary) shouldBe List(LargeIcon.Glyph(MaterialIcon.Mail, blue))
    }
    "keep a colour of its own" in {
      sent("group" -> "kind:mail", "groupSummary" -> "true", "noticeColor" -> "#43A047").lookingAs(child)
        .noticeColor shouldBe Some(green)
    }
    "keep an icon of its own, with its colour or none" in {
      val own = sent("group" -> "kind:mail", "groupSummary" -> "true", "noticeIcon" -> "forum")
      own.lookingAs(child) shouldBe own
    }
    "leave a push that is no summary as it is" in {
      val plain = sent("group" -> "kind:mail")
      plain.lookingAs(child) shouldBe plain
    }
  }
}

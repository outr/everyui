package everyui.push

import everyui.Color
import everyui.font.MaterialIcon
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** How Android shows a push, so kinds of push are told apart: its "noticeIcon" (a Material icon by the font's name,
  * known or not), "noticeColor" (`#RRGGBB` or `#AARRGGBB`, anything else ignored) and "noticePicture" (fetched only
  * over http or https), kept out of its data and written back as sent; a plain "icon", "color" or "largeIcon" left as
  * the app's own data; and `Notice` and `OngoingNotice` naming no icon unless they say so. */
class PushNoticeLookSpec extends AnyWordSpec with Matchers {
  private def sent(keys: (String, String)*): PushMessage = PushMessage.of(Map("title" -> "Hi") ++ keys)

  "A push's noticeIcon" should {
    "name the Material icon the font calls it" in {
      List("mail", "forum", "error", "terminal", "event").map(n => sent("noticeIcon" -> n).shownIcon) shouldBe
        List(MaterialIcon.Mail, MaterialIcon.Forum, MaterialIcon.Error, MaterialIcon.Terminal, MaterialIcon.Event)
          .map(Some(_))
      sent("noticeIcon" -> " mail ").noticeIcon shouldBe Some("mail")
    }
    "be kept as sent but name no icon where the font has none, or the push names none" in {
      val m = sent("noticeIcon" -> "Mail")
      m.noticeIcon shouldBe Some("Mail")
      m.shownIcon shouldBe None
      sent("noticeIcon" -> "not_an_icon").shownIcon shouldBe None
      sent().noticeIcon shouldBe None
      sent("noticeIcon" -> "  ").noticeIcon shouldBe None
    }
  }

  "A push's noticeColor" should {
    "be read from #RRGGBB, opaque, and #AARRGGBB, alpha first, in any case" in {
      sent("noticeColor" -> "#1E88E5").noticeColor shouldBe Some(new Color(0x1e, 0x88, 0xe5, 255))
      sent("noticeColor" -> "#801e88e5").noticeColor shouldBe Some(new Color(0x1e, 0x88, 0xe5, 0x80))
      sent("noticeColor" -> " #ffD32F2F ").noticeColor shouldBe Some(new Color(0xd3, 0x2f, 0x2f, 255))
    }
    "be ignored where it is no such colour" in {
      List("1E88E5", "#1E88E", "#1E88E5F", "#GG88E5", "red", "#fff", "#", "", "rgb(1,2,3)", "#1E88E5FF00")
        .map(c => PushMessage.colorOf(c)) shouldBe List.fill(10)(None)
      val m = sent("noticeColor" -> "blue")
      m.noticeColor shouldBe None
      m.data shouldBe Map.empty
    }
    "be written back as it was read" in {
      PushMessage.hex(new Color(0x1e, 0x88, 0xe5, 255)) shouldBe "#1e88e5"
      PushMessage.hex(new Color(0x1e, 0x88, 0xe5, 0x80)) shouldBe "#801e88e5"
    }
  }

  "A push's noticePicture" should {
    "be fetched only over http or https, from a host" in {
      List("https://x.io/a.png", "http://x.io/a.png", "HTTPS://X.IO/A.PNG", " https://x.io/a ")
        .map(PushMessage.fetchable) shouldBe List.fill(4)(true)
      List("file:///sdcard/a.png", "content://media/1", "data:image/png;base64,AA", "/a.png", "a.png", "https://",
        "https:///a.png", "ftp://x.io/a.png", "javascript:alert(1)", "")
        .map(PushMessage.fetchable) shouldBe List.fill(10)(false)
    }
    "be kept as sent, fetched only where it may be" in {
      sent("noticePicture" -> "https://x.io/a.png").fetchedPicture shouldBe Some("https://x.io/a.png")
      val local = sent("noticePicture" -> "/avatars/7.png")
      local.noticePicture shouldBe Some("/avatars/7.png")
      local.fetchedPicture shouldBe None
      sent().noticePicture shouldBe None
    }
  }

  "A push's notice keys" should {
    "stay out of its data, and be written back as sent" in {
      val m = sent("noticeIcon" -> "mail", "noticeColor" -> "#801E88E5", "noticePicture" -> "https://x.io/a.png",
        "uid" -> "7")
      m.data shouldBe Map("uid" -> "7")
      m.fields.get("noticeIcon") shouldBe Some("mail")
      m.fields.get("noticeColor") shouldBe Some("#801e88e5")
      m.fields.get("noticePicture") shouldBe Some("https://x.io/a.png")
      PushMessage.of(m.fields) shouldBe m
      sent().fields.keySet.intersect(Set("noticeIcon", "noticeColor", "noticePicture")) shouldBe Set.empty
    }
    "leave a plain icon, color or largeIcon as the app's own data" in {
      val m = sent("icon" -> "/icon.png", "color" -> "#ff0000", "largeIcon" -> "https://x.io/l.png")
      (m.noticeIcon, m.noticeColor, m.noticePicture) shouldBe (None, None, None)
      m.data shouldBe Map("icon" -> "/icon.png", "color" -> "#ff0000", "largeIcon" -> "https://x.io/l.png")
    }
    "default to none where a push is made in code" in {
      val m = PushMessage("Hi", "there", None)
      (m.noticeIcon, m.noticeColor, m.noticePicture, m.shownIcon, m.fetchedPicture) shouldBe
        (None, None, None, None, None)
    }
  }

  "A notice and an ongoing notice" should {
    "name no icon unless they say so" in {
      Notice("Hi").noticeIcon shouldBe None
      Notice("Hi", noticeIcon = Some(MaterialIcon.Mail)).noticeIcon shouldBe Some(MaterialIcon.Mail)
      val channel = NoticeChannel("status", "Status")
      OngoingNotice("k", "Running", channel = channel).noticeIcon shouldBe None
      OngoingNotice("k", "Running", channel = channel, noticeIcon = Some(MaterialIcon.Sync)).noticeIcon shouldBe
        Some(MaterialIcon.Sync)
    }
  }
}

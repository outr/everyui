package everyui.push

import everyui.{FxSpec, Permission}
import everyui.render.Platform

/** The unread count's badge text, and the desktop's notifications, which it has none of: the permission refused, asking
  * changing nothing, a notice never shown or clicked, and a badge set and cleared without a Dock to put it on. */
class NoticeSpec extends FxSpec {
  "Badge.text" should {
    "say nothing for none, the number up to 9 and 9+ over it" in {
      List(-3, 0, 1, 9, 10, 250).map(Badge.text) shouldBe List("", "", "1", "9", "9+", "9+")
    }
  }

  "Notices on the desktop" should {
    "be refused, and stay refused when asked for" in {
      Platform.notificationPermission() shouldBe Permission.Refused
      fx(Platform.askNotificationPermission())
      Platform.notificationPermission() shouldBe Permission.Refused
    }

    "show nothing, so nothing is clicked" in {
      var clicked = false
      fx(Platform.showNotice(Notice("3 new messages", "From Ada", tag = "inbox", onlyWhenHidden = false), () => clicked = true))
      clicked shouldBe false
    }

    "take a badge and clear it" in {
      fx(Platform.setBadge(12))
      fx(Platform.setBadge(0))
    }
  }
}

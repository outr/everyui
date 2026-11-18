package everyui.push

import everyui.async.Task
import everyui.background.{Background, BackgroundApp}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A data push: told from a notification (no title and no body, or "dataOnly"), its keys handed over as sent, and
  * heard by the app's `BackgroundApp.onPush` while the app is not in front, by `Push.data` while it is, or where no
  * `BackgroundApp` is made ready; and "silent", which still means a notification with no sound. */
class PushDataSpec extends AnyWordSpec with Matchers {
  "A data push" should {
    "be one with neither title nor body, or none at all" in {
      PushMessage.isData(Map("sync" -> "mail")) shouldBe true
      PushMessage.isData(Map.empty) shouldBe true
      PushMessage.isData(Map("title" -> "", "body" -> "", "sync" -> "mail")) shouldBe true
      PushMessage.isData(Map("title" -> "New mail", "sync" -> "mail")) shouldBe false
      PushMessage.isData(Map("body" -> "3 new", "sync" -> "mail")) shouldBe false
    }
    "be one with dataOnly true, whatever text it carries" in {
      PushMessage.isData(Map("title" -> "Synced", "body" -> "3 new", "dataOnly" -> "true")) shouldBe true
      PushMessage.isData(Map("title" -> "Synced", "dataOnly" -> "false")) shouldBe false
      PushMessage.isData(Map("title" -> "Synced", "dataOnly" -> "yes")) shouldBe false
    }
    "not be a close alone, but be one where the close carries keys of its own" in {
      PushMessage.isData(Map("close" -> "c42")) shouldBe false
      PushMessage.isData(Map("close" -> "c42", "google.message_id" -> "0:1")) shouldBe false
      PushMessage.isData(Map("close" -> "c42", "sync" -> "mail")) shouldBe true
      PushMessage.isData(Map("close" -> "c42", "title" -> "Read")) shouldBe false
    }
    "hand its keys over as sent, less dataOnly, close and the push service's own" in {
      PushMessage.dataOf(Map("sync" -> "mail", "account" -> "a1", "dataOnly" -> "true", "close" -> "c42",
        "google.message_id" -> "0:1", "gcm.n.e" -> "1", "title" -> "Synced", "path" -> "/mail")) shouldBe
        Map("sync" -> "mail", "account" -> "a1", "title" -> "Synced", "path" -> "/mail")
    }
    "keep dataOnly out of a push's data" in {
      PushMessage.of(Map("title" -> "t", "dataOnly" -> "true", "sync" -> "mail")).data shouldBe Map("sync" -> "mail")
    }
  }

  "silent" should {
    "still mean a notification shown with no sound, never a data push" in {
      val sent = Map("title" -> "New mail", "body" -> "From Ann", "silent" -> "true")
      PushMessage.isData(sent) shouldBe false
      PushMessage.of(sent).silent shouldBe true
      PushMessage.of(sent).fields("silent") shouldBe "true"
      PushMessage.isData(Map("silent" -> "true", "sync" -> "mail")) shouldBe true
    }
  }

  "A data push arriving" should {
    def heard(app: Option[BackgroundApp], front: Boolean, sent: Map[String, String]): Vector[Map[String, String]] = {
      var data = Vector.empty[Map[String, String]]
      val listener = Push.data.attach(d => data :+= d)
      Background.forget()
      app.foreach(Background.start)
      var answered = false
      try Push.dataArrived(sent, front).map(_ => answered = true).start()
      finally {
        Push.data.reactions -= listener
        Background.forget()
      }
      answered shouldBe true
      data
    }

    "reach onPush with the app not in front, and not Push.data" in {
      var pushed = Vector.empty[Map[String, String]]
      val app = new BackgroundApp {
        override def onPush(data: Map[String, String]): Task[Unit] = Task(pushed :+= data)
      }
      heard(Some(app), front = false, Map("sync" -> "mail", "dataOnly" -> "true")) shouldBe Vector.empty
      pushed shouldBe Vector(Map("sync" -> "mail"))
    }
    "reach Push.data with the app in front, and not onPush" in {
      var pushed = Vector.empty[Map[String, String]]
      val app = new BackgroundApp {
        override def onPush(data: Map[String, String]): Task[Unit] = Task(pushed :+= data)
      }
      heard(Some(app), front = true, Map("sync" -> "calendar")) shouldBe Vector(Map("sync" -> "calendar"))
      pushed shouldBe Vector.empty
    }
    "reach Push.data where no BackgroundApp is made ready" in {
      heard(None, front = false, Map("sync" -> "mail", "gcm.n.e" -> "1")) shouldBe Vector(Map("sync" -> "mail"))
    }
    "do nothing by default in a BackgroundApp that does not hear it" in {
      heard(Some(new BackgroundApp {}), front = false, Map("sync" -> "mail")) shouldBe Vector.empty
    }
  }
}

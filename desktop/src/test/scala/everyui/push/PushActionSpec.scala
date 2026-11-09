package everyui.push

import everyui.async.Task
import everyui.background.{Background, BackgroundApp}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A push notification's buttons: read from the JSON text under "actions" and written back, a push made from its data
  * with the rest as its data, the button chosen with where it goes, `Push.acted` hearing a choice and the
  * BackgroundApp hearing one made with the app closed, and the desktop, which has no push. */
class PushActionSpec extends AnyWordSpec with Matchers {
  private val done = PushButton("done", "Done", background = true)
  private val open = PushButton("open", "Open", path = Some("/tasks/42"))

  "A push's buttons" should {
    "be read from JSON text, in order" in {
      val text = """[{"id":"done","label":"Done","background":true},""" +
        """{"id":"open","label":"Open","path":"/tasks/42"}]"""
      PushButton.parse(text) shouldBe Vector(done, open)
    }
    "take background as text too, an id for a missing label, and leave out those with no id" in {
      val text = """ [ {"id": "a", "background": "true"}, {"label": "No id"}, {"id": 7, "label": "Seven"} ] """
      PushButton.parse(text) shouldBe
        Vector(PushButton("a", "a", background = true), PushButton("7", "Seven"))
    }
    "read escapes, and nothing from what is not an array of them" in {
      PushButton.parse("""[{"id":"q","label":"Say \"yes\" é\n"}]""").head.label shouldBe "Say \"yes\" é\n"
      List("", "nonsense", "{}", "[1,2]", "[{\"id\":\"x\"", "[{\"id\":\"x\"}] extra").map(PushButton.parse) shouldBe
        List.fill(6)(Vector.empty)
    }
    "be written back as the same JSON text" in {
      val buttons = Vector(done, open, PushButton("q", "Say \"yes\"\t\\ \u0001"))
      PushButton.parse(PushButton.json(buttons)) shouldBe buttons
      PushButton.json(Vector(done)) shouldBe """[{"id":"done","label":"Done","background":true}]"""
    }
  }

  "A push from its data" should {
    "say what it is with its keys, and keep the rest as its data" in {
      val m = PushMessage.of(Map("title" -> "Take pills", "body" -> "8:00", "path" -> "/tasks", "tag" -> "t-42",
        "renotify" -> "true", "actions" -> PushButton.json(Vector(done, open)), "task" -> "42", "close" -> "x",
        "google.message_id" -> "0:1", "gcm.notification.title" -> "t"))
      m shouldBe PushMessage("Take pills", "8:00", Some("/tasks"), Some("t-42"), renotify = true, Vector(done, open),
        Map("task" -> "42"))
    }
    "have no path, tag or buttons where it says none" in {
      PushMessage.of(Map("title" -> "Hello")) shouldBe PushMessage("Hello", "", None)
    }
  }

  "A button chosen" should {
    val message = PushMessage("Take pills", "8:00", Some("/tasks"), actions = Vector(done, open))
    "know its button, whether it runs in the background, and where it goes" in {
      PushAction("done", message).background shouldBe true
      PushAction("open", message).background shouldBe false
      PushAction("open", message).path shouldBe Some("/tasks/42")
      PushAction("done", message).path shouldBe Some("/tasks")
      PushAction("gone", message).button shouldBe None
    }
    "be heard by Push.acted, with the push's data" in {
      var heard = Vector.empty[PushAction]
      val listener = Push.acted.attach(a => heard :+= a)
      Push.chose(PushAction("done", message.copy(data = Map("task" -> "42"))), go = true)
      Push.acted.reactions -= listener
      heard.map(a => a.id -> a.message.data) shouldBe Vector("done" -> Map("task" -> "42"))
    }
    "reach the BackgroundApp with the app closed" in {
      var heard = Vector.empty[String]
      Background.forget()
      Background.start(new BackgroundApp {
        override def onPushAction(action: PushAction): Task[Unit] =
          Task(heard :+= action.id + " " + action.message.title)
      })
      var answered = false
      Background.pushAction(PushAction("done", message)).map(_ => answered = true).start()
      Background.forget()
      answered shouldBe true
      heard shouldBe Vector("done Take pills")
    }
  }

  "Push on the desktop" should {
    "not be here, and take a delivered push as nothing" in {
      Push.supported shouldBe false
      Push.deliver(Map("title" -> "Take pills", "actions" -> PushButton.json(Vector(done))))
    }
  }
}

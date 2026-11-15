package everyui.push

import everyui.async.Task
import everyui.background.{Background, BackgroundApp}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A push's group, summary, silence, channel and reply buttons: read from the keys a mail server sends, kept out of
  * its data, the tag each is shown under, written back as the same keys (as Android carries a push through its
  * intents), and a reply reaching the BackgroundApp with its words and answering what becomes of the notification. */
class PushGroupSpec extends AnyWordSpec with Matchers {
  private val mail = Map("group" -> "ann@example.com", "channel" -> "mail.a", "channelName" -> "Mail: ann@example.com",
    "channelGroup" -> "mail", "channelGroupName" -> "Mail")
  private val reply = PushButton("reply", "Reply", input = true, hint = "Reply to Bea")
  private val archive = PushButton("archive", "Archive", background = true)

  "A message in a group" should {
    "say its group and channel, with neither in its data" in {
      val m = PushMessage.of(mail ++ Map("title" -> "Bea", "body" -> "Lunch?", "tag" -> "a-1", "uid" -> "17",
        "actions" -> PushButton.json(Vector(reply, archive))))
      m.group shouldBe Some("ann@example.com")
      m.summary shouldBe false
      m.silent shouldBe false
      m.channel shouldBe Some(PushChannel("mail.a", "Mail: ann@example.com", "mail", "Mail"))
      m.actions shouldBe Vector(reply, archive)
      m.data shouldBe Map("uid" -> "17")
      m.shownTag shouldBe Some("a-1")
    }
    "be shown under no tag where it has none of its own" in {
      PushMessage.of(mail ++ Map("title" -> "Bea")).shownTag shouldBe None
    }
  }

  "A group's summary" should {
    "read its lines in order, be silent where it says so, and be shown under its group's tag" in {
      val s = PushMessage.of(mail ++ Map("title" -> "3 new", "body" -> "ann@example.com", "groupSummary" -> "true",
        "silent" -> "true", "lines" -> """["Re: tickets", "The draft, \"attached\"", 7]"""))
      s.summary shouldBe true
      s.silent shouldBe true
      s.lines shouldBe Vector("Re: tickets", "The draft, \"attached\"", "7")
      s.shownTag shouldBe Some("ann@example.com")
      s.copy(tag = Some("summary")).shownTag shouldBe Some("summary")
    }
    "have no lines where they are not a JSON array, and be no summary but for \"true\"" in {
      List("", "nonsense", "{}", "\"one\"").map(l => PushMessage.of(Map("lines" -> l)).lines) shouldBe
        List.fill(4)(Vector.empty)
      PushMessage.of(Map("groupSummary" -> "yes", "silent" -> "1")) shouldBe PushMessage("", "", None)
    }
  }

  "A push's channel" should {
    "be the default where it names none, and be named by its id where it has no name" in {
      PushMessage.of(Map("title" -> "Hi")).channel shouldBe None
      PushChannel.default shouldBe PushChannel("everyui.push", "Notifications")
      PushMessage.of(Map("channel" -> "alerts")).channel shouldBe Some(PushChannel("alerts", "alerts"))
      PushMessage.of(Map("channel" -> "alerts", "channelGroup" -> "ops")).channel shouldBe
        Some(PushChannel("alerts", "alerts", "ops", "ops"))
    }
  }

  "A push's keys" should {
    "be written back as it was sent" in {
      val m = PushMessage("Bea", "Lunch?\n\"Friday\"", Some("/mail/17"), Some("a-1"), renotify = true,
        Vector(reply, archive), Map("uid" -> "17"), Some("ann@example.com"), summary = true,
        Vector("Lunch?", "Re: \"tickets\""), silent = true, Some(PushChannel("mail.a", "Mail: ann", "mail", "Mail")))
      PushMessage.of(m.fields) shouldBe m
      val plain = PushMessage("Hello", "", None)
      plain.fields shouldBe Map("title" -> "Hello")
      PushMessage.of(plain.fields) shouldBe plain
    }
  }

  "A reply button" should {
    "be read with its hint, and written back" in {
      PushButton.parse("""[{"id":"r","label":"Reply","input":"true","hint":"Say something"},{"id":"x","input":false}]""")
        .shouldBe(Vector(PushButton("r", "Reply", input = true, hint = "Say something"), PushButton("x", "x")))
      PushButton.json(Vector(reply)) shouldBe
        """[{"id":"reply","label":"Reply","input":true,"hint":"Reply to Bea"}]"""
      PushButton.parse(PushButton.json(Vector(reply, archive))) shouldBe Vector(reply, archive)
    }
  }

  "A reply" should {
    val message = PushMessage.of(mail ++ Map("title" -> "Bea", "body" -> "Lunch?", "tag" -> "a-1",
      "actions" -> PushButton.json(Vector(reply, archive))))
    val typed = PushAction("reply", message, Some("Yes, at noon"))

    "reach onPushAction with its words, and be shown with the reply, by default" in {
      var heard = Vector.empty[String]
      Background.forget()
      Background.start(new BackgroundApp {
        override def onPushAction(action: PushAction): Task[Unit] = Task(heard :+= action.id + ": " + action.text.get)
      })
      var outcome = Option.empty[ReplyOutcome]
      Background.pushReply(typed).map(o => outcome = Some(o)).start()
      Background.forget()
      heard shouldBe Vector("reply: Yes, at noon")
      outcome shouldBe Some(ReplyOutcome.ReplyShown)
      typed.button shouldBe Some(reply)
      typed.background shouldBe false
    }
    "answer what the app makes of the notification" in {
      Background.forget()
      Background.start(new BackgroundApp {
        override def onPushReply(action: PushAction): Task[ReplyOutcome] = Task(
          if (action.message.group.contains("ann@example.com")) ReplyOutcome.ReplyUpdated(action.message.copy(
            body = "Sent: " + action.text.getOrElse(""), actions = Vector.empty))
          else ReplyOutcome.ReplyClosed)
      })
      var outcomes = Vector.empty[ReplyOutcome]
      Background.pushReply(typed).map(o => outcomes :+= o).start()
      Background.pushReply(typed.copy(message = message.copy(group = None))).map(o => outcomes :+= o).start()
      Background.forget()
      outcomes shouldBe Vector(ReplyOutcome.ReplyUpdated(message.copy(body = "Sent: Yes, at noon",
        actions = Vector.empty)), ReplyOutcome.ReplyClosed)
    }
    "be shown with the reply where there is no BackgroundApp" in {
      Background.forget()
      var outcome = Option.empty[ReplyOutcome]
      Background.pushReply(typed).map(o => outcome = Some(o)).start()
      outcome shouldBe Some(ReplyOutcome.ReplyShown)
    }
  }
}

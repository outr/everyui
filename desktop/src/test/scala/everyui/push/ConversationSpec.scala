package everyui.push

import everyui.Color
import everyui.font.MaterialIcon
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A conversation's message (`PushConversation`): its keys read and written back, the title a platform without
  * conversations shows; its lines kept per conversation, the newest last, so many and no more, forgotten as a close
  * takes it away and rebuilt from what the notification keeps; the shortcuts that make room for another; and the
  * picture of a person in it. */
class ConversationSpec extends AnyWordSpec with Matchers {
  private val team = Map("title" -> "Ann", "body" -> "Lunch?", "conversationId" -> "team",
    "conversationTitle" -> "Team chat", "conversationPicture" -> "https://x.io/team.png", "senderName" -> "Ann",
    "senderPicture" -> "https://x.io/ann.png", "isGroup" -> "true")
  private val blue = new Color(0x1e, 0x88, 0xe5, 255)

  private def line(text: String, time: Long): ConversationLine = ConversationLine("Ann", "", text, time)

  "A push's conversation" should {
    "be read from its keys, which are not its data" in {
      val m = PushMessage.of(team ++ Map("room" -> "7"))
      m.conversation shouldBe Some(PushConversation("team", "Team chat", Some("https://x.io/team.png"), "Ann",
        Some("https://x.io/ann.png"), isGroup = true))
      m.data shouldBe Map("room" -> "7")
    }
    "be none without a conversationId, its other keys then read as nothing" in {
      val m = PushMessage.of(team - "conversationId")
      m.conversation shouldBe None
      m.data shouldBe Map.empty
      PushMessage.of(team + ("conversationId" -> "  ")).conversation shouldBe None
    }
    "trim its keys, and be one person's unless isGroup is true" in {
      PushMessage.of(Map("title" -> "Bob", "conversationId" -> " bob ", "senderName" -> " Bob ", "isGroup" -> "yes"))
        .conversation shouldBe Some(PushConversation("bob", senderName = "Bob"))
    }
    "be written back as it was sent" in {
      val m = PushMessage.of(team)
      m.fields shouldBe team
      PushMessage.of(m.fields) shouldBe m
      PushMessage.of(Map("title" -> "Hi")).fields.keySet should not contain "conversationId"
    }
    "be titled with its sender and the conversation where a platform has no conversations" in {
      val c = PushConversation("team", "Team chat", senderName = "Ann")
      c.heading("New message") shouldBe "Ann · Team chat"
      PushConversation("bob", "Bob", senderName = "Bob").heading("Bob") shouldBe "Bob"
      PushConversation("bob").heading("Bob") shouldBe "Bob"
      PushConversation("bob", "Bob").heading("") shouldBe "Bob"
      PushConversation("c", senderName = "Cy").heading("New") shouldBe "Cy"
      PushConversation("team", "Team chat").heading("Ann") shouldBe "Ann · Team chat"
    }
    "name its shortcut by its title, else its sender, else the app" in {
      PushConversation("team", "Team chat", senderName = "Ann").label("x", "App") shouldBe "Team chat"
      PushConversation("bob", senderName = "Bob").label("x", "App") shouldBe "Bob"
      PushConversation("bob").label("Hi", "App") shouldBe "Hi"
      PushConversation("bob").label("", "App") shouldBe "App"
      PushConversation.shortcutId("team") shouldBe "everyui.conversation:team"
    }
    "come with the Notice an app shows itself" in {
      Notice("Hi").conversation shouldBe None
      Notice("Hi", conversation = Some(PushConversation("team"))).conversation.map(_.id) shouldBe Some("team")
    }
  }

  "A conversation's line" should {
    "be its sender, their picture and its body, else its title" in {
      ConversationLine.of(PushMessage.of(team), 5L) shouldBe ConversationLine("Ann", "https://x.io/ann.png", "Lunch?", 5L)
      ConversationLine.of(PushMessage.of(Map("title" -> "Bob", "conversationId" -> "bob")), 6L) shouldBe
        ConversationLine("Bob", "", "Bob", 6L)
      ConversationLine.of(PushMessage.of(Map("title" -> "**Bob**", "body" -> "*hi*", "format" -> "markdown",
        "conversationId" -> "bob")), 7L) shouldBe ConversationLine("Bob", "", "hi", 7L)
      ConversationLine.reply("On my way", 8L) shouldBe ConversationLine("", "", "On my way", 8L, me = true)
    }
    "be kept as text and read back, whatever it says" in {
      val lines = List(ConversationLine("Ann \"A\"", "https://x.io/a.png", "two\nlines \\ ok", 1763677200123L),
        ConversationLine.reply("Sure 👍", 2L), ConversationLine("", "", "", 0L))
      lines.map(l => ConversationLine.decoded(l.encoded)) shouldBe lines.map(Some(_))
      ConversationLine.decoded("not json") shouldBe None
      ConversationLine.decoded("""["a","b","c"]""") shouldBe None
      ConversationLine.decoded("""["a","b","c","d",true]""") shouldBe None
    }
  }

  "The conversations shown" should {
    "stack each conversation's messages, the newest last" in {
      val s = ConversationStore().added("team", "t", line("one", 1L)).added("bob", "b", line("hi", 2L))
        .added("team", "t", line("two", 3L))
      s.of("team").map(_.text) shouldBe Vector("one", "two")
      s.of("bob").map(_.text) shouldBe Vector("hi")
      s.of("none") shouldBe Vector.empty
      s.tagOf("team") shouldBe Some("t")
    }
    "keep the most recent of them, and no more" in {
      val s = (1 to ConversationStore.most + 3).foldLeft(ConversationStore())((s, i) =>
        s.added("team", "t", line(i.toString, i.toLong)))
      s.of("team").length shouldBe ConversationStore.most
      s.of("team").head.text shouldBe "4"
      s.of("team").last.text shouldBe (ConversationStore.most + 3).toString
    }
    "follow the tag its latest was shown under" in {
      val s = ConversationStore().added("team", "t1", line("one", 1L)).added("team", "t2", line("two", 2L))
      s.tagOf("team") shouldBe Some("t2")
      s.closing("t1") shouldBe Nil
      s.closing("t2") shouldBe List("team")
    }
    "be closed by the tag it is shown under, or by its id, and forget it then" in {
      val s = ConversationStore().added("team", "t", line("one", 1L)).added("bob", "bob", line("hi", 2L))
        .added("ann", "everyui.conversation:ann", line("yo", 3L))
      s.closing("t") shouldBe List("team")
      s.closing("team") shouldBe List("team")
      s.closing("bob") shouldBe List("bob")
      s.closing("other") shouldBe Nil
      val closed = s.closing("t").foldLeft(s)((s, id) => s.cleared(id))
      closed.of("team") shouldBe Vector.empty
      closed.tagOf("team") shouldBe None
      closed.of("bob").map(_.text) shouldBe Vector("hi")
      closed.added("team", "t", line("again", 4L)).of("team").map(_.text) shouldBe Vector("again")
    }
    "be rebuilt from the lines a notification keeps, as a new process finds it" in {
      val kept = Vector(line("one", 1L), ConversationLine.reply("ok", 2L), line("two", 3L))
      val read = kept.map(_.encoded).flatMap(ConversationLine.decoded)
      val s = ConversationStore().rebuilt("team", "t", read).added("team", "t", line("three", 4L))
      s.of("team").map(_.text) shouldBe Vector("one", "ok", "two", "three")
      s.of("team")(1).me shouldBe true
      ConversationStore().rebuilt("team", "t", (1 to 15).toVector.map(i => line(i.toString, i.toLong)))
        .of("team").map(_.text).head shouldBe "6"
    }
  }

  "A conversation's shortcut" should {
    "make room by taking away those pushed longest ago (the highest ranks), only where there is none" in {
      val have = List(("c:b", 1), ("c:a", 2), ("c:c", 0))
      ConversationStore.evicting(have, "c:d", 4) shouldBe Nil
      ConversationStore.evicting(have, "c:a", 3) shouldBe Nil
      ConversationStore.evicting(have, "c:d", 3) shouldBe List("c:a")
      ConversationStore.evicting(have, "c:d", 2) shouldBe List("c:a", "c:b")
      ConversationStore.evicting(Nil, "c:d", 0) shouldBe Nil
    }
  }

  "A person's picture" should {
    "be their picture, then the notice's icon on its colour, then their initial on it" in {
      LargeIcon.person(Some("https://x.io/ann.png"), Some(MaterialIcon.Forum), Some(blue), "ann") shouldBe List(
        LargeIcon.Picture("https://x.io/ann.png"), LargeIcon.Glyph(MaterialIcon.Forum, blue),
        LargeIcon.Monogram("A", blue))
    }
    "skip a picture it cannot fetch and an icon it lacks, grey where it names no colour" in {
      LargeIcon.person(Some("file:///ann.png"), None, None, " bob") shouldBe
        List(LargeIcon.Monogram("B", LargeIcon.defaultColor))
      LargeIcon.person(None, Some(MaterialIcon.Forum), None, "") shouldBe
        List(LargeIcon.Glyph(MaterialIcon.Forum, LargeIcon.defaultColor))
      LargeIcon.person(None, None, None, "  ") shouldBe Nil
    }
    "take a whole character for its initial" in {
      LargeIcon.initial("ünal") shouldBe Some("Ü")
      LargeIcon.initial("😀 party") shouldBe Some("😀")
      LargeIcon.initial("") shouldBe None
      LargeIcon.letterKey("A", blue, 168) should not be LargeIcon.key(MaterialIcon.Forum, blue, 168)
    }
  }
}

package everyui.chat

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The chat's model and the logic under the transcript: who leads a group, where the days change, when the view is kept
  * at the bottom and what arrives while it is not, and how times are written. */
class ChatModelSpec extends AnyWordSpec with Matchers {
  private val ada = Participant("u-1", "Ada")
  private val ada2 = Participant("u-2", "Ada")
  private val bot = Participant("agent", "Helper", Some("https://example.com/h.png"))

  private def msg(id: String, who: Participant, time: Option[Long] = None): ChatEntry.Message =
    ChatEntry.message(id, who, self = false, "hi", time)

  "A participant" should {
    "be drawn by the first letter of their name on a colour that follows their id" in {
      ada.initial shouldBe "A"
      Participant("x", "").initial shouldBe "?"
      Participant("x", "émile").initial shouldBe "É"
      ada.colour shouldBe Participant("u-1", "Someone else").colour
      ada.colour should not be ada2.colour
    }
  }

  "Grouping" should {
    "lead with the first message of each author's run, by id and never by name" in {
      val a = msg("1", ada)
      val b = msg("2", ada)
      val c = msg("3", ada2)
      val d = msg("4", bot)
      Grouping.leads(None, a) shouldBe true
      Grouping.leads(Some(a), b) shouldBe false
      Grouping.leads(Some(b), c) shouldBe true
      Grouping.leads(Some(c), d) shouldBe true
      // anything between two of the same author's messages starts the group again
      val note = ChatEntry.marker("n", "Topic renamed")
      Grouping.leads(Some(note), msg("5", bot)) shouldBe true
      Grouping.leads(Some(ChatEntry.activity("t", "Ran the tests")), msg("6", bot)) shouldBe true
      Grouping.leads(Some(d), ChatEntry.activity("t2", "Ran the tests")) shouldBe true
    }
    "put a separator where the day changes, keeping each day's separator the same entry" in {
      val day = 86400000L
      val entries = Vector(msg("1", ada, Some(0L)), msg("2", ada, Some(10L)), ChatEntry.marker("m", "no time"),
        msg("3", bot, Some(day + 5L)), msg("4", bot, Some(3 * day)))
      val made = scala.collection.mutable.HashMap.empty[String, ChatEntry]
      def separator(d: String): ChatEntry = made.getOrElseUpdate(d, ChatEntry.marker(Grouping.dayKey(d), d))
      val dayOf = (e: ChatEntry) => e.time.map(t => (t / day).toString)
      val shown = Grouping.withDays(entries, dayOf, separator)
      shown.map(_.id) shouldBe Vector(Grouping.dayKey("0"), "1", "2", "m", Grouping.dayKey("1"), "3", Grouping.dayKey("3"), "4")
      shown.count(Grouping.isDay) shouldBe 3
      Grouping.withDays(entries, dayOf, separator).head shouldBe theSameInstanceAs(shown.head)
      Grouping.withDays(Vector.empty, dayOf, separator) shouldBe empty
    }
  }

  "The bottom pin" should {
    "hold at the bottom, let go on a scroll up, count what arrives, and pin again at the bottom" in {
      val pin = new BottomPin(8.0)
      pin.entries(Vector("a", "b"))
      pin.pinned() shouldBe true
      pin.scrolled(996.0, 1000.0)
      pin.pinned() shouldBe true // within the slack
      pin.scrolled(600.0, 1000.0)
      pin.pinned() shouldBe false
      pin.unseen() shouldBe 0
      pin.entries(Vector("a", "b", "c"))
      pin.entries(Vector("a", "b", "c", "d"))
      pin.unseen() shouldBe 2
      // earlier history put before them is not new
      pin.entries(Vector("x", "y", "a", "b", "c", "d"))
      pin.unseen() shouldBe 2
      pin.scrolled(1200.0, 1200.0)
      pin.pinned() shouldBe true
      pin.unseen() shouldBe 0
      pin.entries(Vector("x", "y", "a", "b", "c", "d", "e"))
      pin.unseen() shouldBe 0
      pin.scrolled(0.0, 1400.0)
      pin.entries(Vector("x", "y", "a", "b", "c", "d", "e", "f"))
      pin.unseen() shouldBe 1
      pin.pin()
      pin.pinned() shouldBe true
      pin.unseen() shouldBe 0
    }
  }

  "Chat times" should {
    "write spans and weekdays" in {
      ChatTime.span(0L) shouldBe "0s"
      ChatTime.span(12400L) shouldBe "12s"
      ChatTime.span(69000L) shouldBe "1m 9s"
      ChatTime.span(7500000L) shouldBe "2h 5m"
      ChatTime.weekday(1970, 1, 1) shouldBe 4
      ChatTime.weekday(2026, 9, 29) shouldBe 2
      ChatTime.weekday(2000, 2, 29) shouldBe 2
      ChatTime.weekday(2024, 12, 31) shouldBe 2
    }
    "say how long ago, then the time of day, then the date" in {
      val now = System.currentTimeMillis()
      ChatTime.relative(now - 20000L, now) shouldBe "now"
      ChatTime.relative(now - 5L * 60000L - 1000L, now) shouldBe "5m"
      ChatTime.relative(now - 3L * 86400000L, now) should include(":")
      ChatTime.dayName(ChatTime.dayOf(now), now) shouldBe "Today"
      ChatTime.dayName(ChatTime.dayOf(now - 86400000L), now) shouldBe "Yesterday"
      ChatTime.dayName("2019-03-04", now) shouldBe "Mar 4, 2019"
    }
  }
}

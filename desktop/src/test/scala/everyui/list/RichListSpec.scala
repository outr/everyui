package everyui.list

import everyui.chat.{ChatTime, Participant}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.menu.{MenuItem, MenuRow}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** The rich list: sections under headers the app opens and closes, items drawn as their looks say (the time as how
  * long ago, the count, the mark, bold when unread), the app's chosen item, activation, menus, narrowing by words,
  * dragging into another place, and five thousand items. */
class RichListSpec extends FxSpec {
  case class Chat(id: String, title: String, snippet: Option[String] = None, time: Option[Long] = None, unread: Int = 0,
                  pinned: Boolean = false)

  private val minute = 60000L
  private val now = 1790000000000L

  private def look(c: Chat): RichLook = RichLook(c.title, c.snippet, Some(Participant("p-" + c.id, c.title)),
    time = c.time, count = c.unread, unread = c.unread > 0, mark = if (c.pinned) Some(MaterialIcon.PushPin) else None)

  private def press(code: KeyCode, text: String = ""): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, false, false, false, false))
  }

  private def three(each: Int): Vector[RichSection[Chat]] = Vector("unread", "recent", "older").map { s =>
    RichSection(s, s.capitalize, (0 until each).map(i => Chat(s + i, s + " " + i)).toVector)
  }

  private def keys(list: RichList[Chat]): List[String] =
    fx((0 until list.list.count).map(i => list.list(i)).map(l => l.item.map(_.id).getOrElse("#" + l.section.key)).toList)

  private def row(list: RichList[Chat], index: Int): RichRow[Chat] = list.list.rowAt(index).get.asInstanceOf[RichRow[Chat]]

  private def onScreen[T](list: RichList[?])(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += list
      list.focus()
    }
    try test finally fx {
      while (everyui.component.Modal.showing) everyui.component.Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "How long ago" should {
    "be said as now, minutes, hours, days, and then the date" in {
      ChatTime.ago(now - 20000L, now) shouldBe "now"
      ChatTime.ago(now + 90000L, now) shouldBe "now"
      ChatTime.ago(now - 5L * minute - 1000L, now) shouldBe "5m"
      ChatTime.ago(now - 3L * 60L * minute, now) shouldBe "3h"
      ChatTime.ago(now - 2L * 1440L * minute, now) shouldBe "2d"
      val date = ChatTime.dayOf(now - 30L * 1440L * minute)
      ChatTime.ago(now - 30L * 1440L * minute, now) shouldBe
        (date.substring(5, 7).toInt.toString + "/" + date.substring(8, 10).toInt.toString)
      ChatTime.ago(now - 400L * 1440L * minute, now) should fullyMatch regex "\\d+/\\d+/\\d\\d"
      RichList.countText(7) shouldBe "7"
      RichList.countText(128) shouldBe "99+"
    }
  }

  "A rich list" should {
    "show its sections under headers, closed and opened by the app's Var, the keys and a click" in {
      val collapsed = Var(Set("older"))
      val list = fx(new RichList[Chat](Var(three(2)), _.id, look, collapsed))
      onScreen(list) {
        keys(list) shouldBe List("#unread", "unread0", "unread1", "#recent", "recent0", "recent1", "#older")
        fx(row(list, 0).size.height()) shouldBe RichRow.headerHeight
        fx(row(list, 1).size.height()) shouldBe RichRow.shortHeight
        // Enter on a header, Right and Left on it, and a click, each through the app's Var
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        fx(list.list.currentItem.map(_.key)) shouldBe Some(GroupedList.headerKey("recent"))
        press(KeyCode.LEFT)
        collapsed() shouldBe Set("older", "recent")
        press(KeyCode.RIGHT)
        collapsed() shouldBe Set("older")
        press(KeyCode.ENTER)
        collapsed() shouldBe Set("older", "recent")
        fx(collapsed @= Set.empty)
        keys(list).size shouldBe 9
        fx(list.list.clicks(0, 20.0)) shouldBe true
        collapsed() shouldBe Set("unread")
        // Right on a closed header is the list's; Left and Right on an item are the focus layer's, to leave the list
        fx(list.list.keepsEvent(everyui.event.KeyEvent(everyui.event.Key.ArrowRight, "ArrowRight"))) shouldBe true
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        fx(list.list.currentItem.flatMap(_.item).map(_.id)) shouldBe Some("recent0")
        fx(list.list.keepsEvent(everyui.event.KeyEvent(everyui.event.Key.ArrowLeft, "ArrowLeft"))) shouldBe false
      }
    }
    "draw each item as its look says, its time moving on as time passes" in {
      val sections = Var(Vector(RichSection("all", "", Vector(
        Chat("a", "Ada", Some("Sounds good"), Some(now - 3L * minute), unread = 128, pinned = true),
        Chat("b", "Bob", None, Some(now - 3L * 60L * minute))))))
      val list = fx(new RichList[Chat](sections, _.id, look))
      onScreen(list) {
        fx(list.now @= now)
        // a section without a title has no header
        keys(list) shouldBe List("a", "b")
        val ada = fx(row(list, 0))
        fx(ada.size.height()) shouldBe RichRow.tallHeight
        fx(ada.title.content()) shouldBe "Ada"
        fx(ada.title.font.weight().value) shouldBe 700
        fx(ada.detail.content()) shouldBe "Sounds good"
        fx(ada.time.content()) shouldBe "3m"
        fx(ada.countText.content()) shouldBe "99+"
        fx(ada.mark.visible()) shouldBe true
        fx(ada.tallFace.visible()) shouldBe true
        val bob = fx(row(list, 1))
        fx(bob.size.height()) shouldBe RichRow.shortHeight
        fx(bob.title.font.weight().value) shouldBe 400
        fx(bob.time.content()) shouldBe "3h"
        fx(bob.mark.visible()) shouldBe false
        fx(list.now @= now + 2L * 60L * minute)
        fx(ada.time.content()) shouldBe "2h"
        // the row handed another item shows it, its avatar with it
        fx(sections @= Vector(RichSection("all", "", Vector(Chat("c", "Cy", Some("Hi"), Some(now))))))
        fx(row(list, 0).title.content()) shouldBe "Cy"
        fx(row(list, 0).tallFace.children().collect { case t: everyui.component.TextView => t.content() }) should contain("C")
      }
    }
    "choose and activate an item with Enter or a click, and follow the app's choice" in {
      val list = fx(new RichList[Chat](Var(three(3)), _.id, look))
      var opened: List[String] = Nil
      fx(list.activated.attach(c => opened = opened :+ c.id))
      onScreen(list) {
        press(KeyCode.DOWN)
        press(KeyCode.ENTER)
        opened shouldBe List("unread0")
        fx(list.selected()) shouldBe Some("unread0")
        fx(row(list, 1).chosen()) shouldBe true
        // one click opens, as a sidebar's does
        fx(list.list.press(6, everyui.event.Modifiers(false, false, false, false)))
        opened shouldBe List("unread0", "recent1")
        fx(row(list, 1).chosen()) shouldBe false
        fx(row(list, 6).chosen()) shouldBe true
        // the app choosing another moves the cursor there
        fx(list.selected @= Some("older2"))
        fx(list.list.currentItem.flatMap(_.item).map(_.id)) shouldBe Some("older2")
        // Enter on a header opens nothing
        fx(list.list.activate(0))
        opened.size shouldBe 2
      }
    }
    "open an item's menu from the menu key, with the app's entries, and none where it has none" in {
      val list = fx(new RichList[Chat](Var(three(2)), _.id, look))
      var chose: List[String] = Nil
      fx(list.menu @= ((c: Chat) => if (c.id.startsWith("older")) Nil else List(
        MenuItem("Rename…", () => chose = chose :+ ("rename " + c.id)), MenuItem("Delete", () => (), danger = true))))
      onScreen(list) {
        press(KeyCode.DOWN)
        fx(row(list, 1).more.visible()) shouldBe true
        fx(row(list, 7).more.visible()) shouldBe false
        press(KeyCode.CONTEXT_MENU)
        fx(Focus.focused().collect { case r: MenuRow => r.item.label }) shouldBe Some("Rename…")
        press(KeyCode.ENTER)
        chose shouldBe List("rename unread0")
        // the menu gone, the focus is back on the list
        fx(Focus.focused().exists(_ eq list.list)) shouldBe true
        fx(list.list.moveTo(7))
        press(KeyCode.CONTEXT_MENU)
        fx(Focus.focused().exists(_ eq list.list)) shouldBe true
      }
    }
    "narrow to what the words find, under their sections, opened" in {
      val collapsed = Var(Set("recent"))
      val list = fx(new RichList[Chat](Var(three(12)), _.id, look, collapsed, search = true))
      onScreen(list) {
        fx(list.field.get.value @= "1")
        fx(list.query()) shouldBe "1"
        keys(list) shouldBe List("#unread", "unread1", "unread10", "unread11", "#recent", "recent1", "recent10",
          "recent11", "#older", "older1", "older10", "older11")
        fx(list.list(0).count) shouldBe 3
        fx(list.query @= "older 11")
        keys(list) shouldBe List("#older", "older11")
        fx(list.list.draggable()) shouldBe false
        fx(list.query @= "")
        collapsed() shouldBe Set("recent")
        keys(list).size shouldBe 27
      }
    }
    "drag items into another place within their section" in {
      val list = fx(new RichList[Chat](Var(three(3)), _.id, look))
      var moves: List[RichMove] = Nil
      fx(list.moved.attach(m => moves = moves :+ m))
      fx(list.reorders @= true)
      onScreen(list) {
        fx(list.list.draggable()) shouldBe true
        val key = (s: String, id: String) => GroupedList.itemKey(s, id)
        def drop(k: String, target: String, at: DropPosition): Drop = Drop(list.list, List(k), Some(target), at)
        // after recent2, the section's end; before recent0; after its header, its start
        val last = drop(key("recent", "recent0"), key("recent", "recent2"), DropPosition.After)
        fx(list.list.accepts()(last)) shouldBe true
        fx(list.list.dropped @= last)
        fx(list.list.dropped @= drop(key("recent", "recent2"), key("recent", "recent0"), DropPosition.Before))
        fx(list.list.dropped @= drop(key("recent", "recent2"), GroupedList.headerKey("recent"), DropPosition.After))
        moves shouldBe List(RichMove(List("recent0"), "recent", "recent", None),
          RichMove(List("recent2"), "recent", "recent", Some("recent0")),
          RichMove(List("recent2"), "recent", "recent", Some("recent0")))
        // into another section is refused by default, and allowed where the app says
        val across = drop(key("recent", "recent0"), key("older", "older1"), DropPosition.Before)
        fx(list.list.accepts()(across)) shouldBe false
        fx(list.accepts @= ((_: RichMove) => true))
        fx(list.list.accepts()(across)) shouldBe true
        // a header is never dragged
        fx(list.list.accepts()(drop(GroupedList.headerKey("recent"), key("older", "older1"), DropPosition.Before))) shouldBe false
      }
    }
    "take five thousand items" in {
      val sections = Var(Vector.empty[RichSection[Chat]])
      val list = fx(new RichList[Chat](sections, _.id, look))
      onScreen(list) {
        val started = System.nanoTime()
        fx(sections @= Vector("a", "b", "c").map(s => RichSection(s, s, (0 until 1667).map(i =>
          Chat(s + i, s + " " + i, Some("snippet " + i), Some(now - i * minute), unread = i % 5)).toVector)))
        info(f"5001 items in three sections shown: ${(System.nanoTime() - started) / 1e6}%.0f ms")
        fx(list.list.count) shouldBe 5004
        fx(list.list.rows.size) should be < 30
        val scrolled = System.nanoTime()
        fx((0 until 200000 by 50).foreach(y => list.list.scrollTop(y.toDouble)))
        info(f"4000 scroll steps: ${(System.nanoTime() - scrolled) / 1e6}%.0f ms")
        fx(list.list.rows.size) should be < 30
      }
    }
  }
}

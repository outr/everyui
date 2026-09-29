package everyui.app

import everyui.async.Task
import everyui.chat.Participant
import everyui.component.{Container, TextView}
import everyui.component.types.FontWeight
import everyui.font.MaterialIcon
import everyui.list.{RichList, RichLook, RichSection}
import everyui.menu.MenuItem
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import everyui.util.Clock
import reactify.*

/** A conversation list (`everyui.list.RichList`): 5000 conversations in three sections (Unread, Recent and Archive),
  * with avatars (one with a picture), icons, snippets, times from now back over months, unread counts, pins and locks,
  * a search field over them, and a menu on each (Rename…, Pin, Lock title, Delete). Conversations in Recent drag into
  * another order. Wide, the list is a sidebar beside the conversation chosen; narrow, it fills the screen.
  * `theme=light` for the light palette, `collapsed=<key>` to start with a section closed, `scroll=<pixels>` to start
  * scrolled, `find=<words>` to start narrowed, and `menu=1` to open the chosen conversation's ⋮ menu. Logs `richlist ok` once built,
  * and each thing done. */
object RichListDemo {
  private case class Chat(id: String, title: String, snippet: String, who: Option[Participant], icon: Option[MaterialIcon],
                          updated: Long, unread: Int, pinned: Boolean, locked: Boolean)

  private val people: Vector[Participant] = Vector(
    Participant("p-ada", "Ada Lovelace"), Participant("p-grace", "Grace Hopper"), Participant("p-alan", "Alan Turing"),
    Participant("p-bot", "EveryUI", Some("images/everyui-mark.png")), Participant("p-linus", "Linus"),
    Participant("p-edsger", "Edsger Dijkstra"), Participant("p-barbara", "Barbara Liskov"), Participant("p-ken", "Ken"))
  private val snippets: Vector[String] = Vector("Sounds good, ship it.", "Can you look at the failing build before lunch?",
    "I pushed the fix; the tests pass locally.", "Here is the plan for the release notes and the rollout after it",
    "Thanks!", "Let's talk it through tomorrow morning.", "The screenshots are attached.",
    "Why does the list jump when a row is drawn taller than the guess?")
  private val topics: Vector[String] = Vector("Release planning", "Build failures", "Design review", "Roadmap",
    "Onboarding", "Incident follow-up", "Weekly sync", "Performance", "Accessibility pass", "Pricing")

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    val start = Clock.real()
    val chats: Var[Vector[Chat]] = Var((0 until 5000).toVector.map(i => made(i, start)))
    val order: Var[Vector[String]] = Var(Vector.empty)
    val collapsed: Var[Set[String]] = Var(Platform.launchArg("collapsed").split(",").filter(_.nonEmpty).toSet)
    val sections: Var[Vector[RichSection[Chat]]] = Var(Vector.empty)
    order @= chats().map(_.id)
    chats.attach(_ => group(chats, order, sections, start))
    order.attach(_ => group(chats, order, sections, start))
    group(chats, order, sections, start)

    val list = new RichList[Chat](sections, _.id, look, collapsed, search = true)
    list.reorders @= true
    list.menu @= ((c: Chat) => List(
      MenuItem("Rename…", () => list.rename(c.id), MaterialIcon.Edit),
      MenuItem(if (c.pinned) "Unpin" else "Pin", () => change(chats, c.id, _.copy(pinned = !c.pinned)), MaterialIcon.PushPin),
      MenuItem.check("Lock title", c.locked, on => change(chats, c.id, _.copy(locked = on))),
      MenuItem.separator,
      MenuItem("Delete", () => {
        chats @= chats().filterNot(_.id == c.id)
        Platform.log("richlist deleted " + c.id)
      }, MaterialIcon.Delete, danger = true)))
    list.commit @= ((c: Chat, text: String) =>
      if (c.locked) Task.pure(Some("The title is locked"): Option[String])
      else if (text.trim.isEmpty) Task.pure(Some("A title can't be empty"): Option[String])
      else {
        change(chats, c.id, _.copy(title = text.trim))
        Platform.log("richlist renamed " + c.id + " " + text.trim)
        Task.pure(None: Option[String])
      })
    list.moved.attach { m =>
      val ids = order().filterNot(m.keys.contains)
      val at = m.before.map(b => ids.indexOf(b)).filter(_ >= 0).getOrElse(ids.length)
      order @= ids.patch(at, m.keys.toVector, 0)
      Platform.log("richlist moved " + m.keys.mkString(",") + " before " + m.before.getOrElse("the end"))
    }

    val open: Val[Option[Chat]] = Val(list.selected().flatMap(id => chats().find(_.id == id)))
    val chosen = new TextView {
      content := open().map(_.title).getOrElse("")
      font.size @= 20.0
      font.weight @= FontWeight.SemiBold
      color := Theme.text()
    }
    val detail = TextView.bound(open().map(_.snippet).getOrElse(""), 13.0, Theme.muted())
    val pane = new Container {
      backgroundColor := Theme.surface()
      children ++= List(chosen, detail)
    }
    chosen.position.left @= 24.0
    chosen.position.top @= 24.0
    detail.position.left @= 24.0
    detail.position.top @= 58.0
    list.activated.attach { c =>
      if (c.unread > 0) change(chats, c.id, _.copy(unread = 0))
      Platform.log("richlist opened " + c.id)
    }
    val wide: Val[Boolean] = Val(ui.size.width() >= 640.0)
    list.size.width := (if (wide()) 320.0 else ui.size.width())
    list.size.height := ui.size.height()
    pane.visible := wide()
    pane.position.left := list.size.width()
    pane.size.width := math.max(0.0, ui.size.width() - list.size.width())
    pane.size.height := ui.size.height()
    list.selected @= Some("c-5")
    ui.children ++= List(list, pane)
    list.focus()
    val scroll = Platform.launchArg("scroll")
    if (scroll.nonEmpty) Platform.later(() => list.list.scrollTop(scroll.toDouble))
    if (Platform.launchArg("find").nonEmpty) list.query @= Platform.launchArg("find")
    if (Platform.launchArg("menu") == "1") Platform.after(0.5, () => list.list.current().foreach { key =>
      list.list.rowAt(list.list.indexOf(key)).foreach {
        case r: everyui.list.RichRow[?] => r.more.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0.0, 0.0)
        case _ => ()
      }
    })
    // (the desktop's --freeze with --screenshot: the frame once it has settled)
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
    Platform.log("richlist ok " + chats().size.toString + " in " + sections().map(s => s.title + " " + s.items.size.toString)
      .mkString(", "))
  }

  private val week: Long = 7L * 86400000L

  // the three sections: Unread, Recent (in the order it was dragged into, pinned first) and Archive
  private def group(chats: Var[Vector[Chat]], order: Var[Vector[String]], sections: Var[Vector[RichSection[Chat]]],
                    start: Long): Unit = {
    val all = chats()
    val rest = all.filter(_.unread == 0)
    val recent = rest.filter(c => start - c.updated < week)
    val byId = recent.map(c => c.id -> c).toMap
    val ranked = order().flatMap(id => byId.get(id).toVector)
    sections @= Vector(RichSection("unread", "Unread", all.filter(_.unread > 0)),
      RichSection("recent", "Recent", ranked.filter(_.pinned) ++ ranked.filterNot(_.pinned)),
      RichSection("archive", "Archive", rest.filter(c => start - c.updated >= week)))
  }

  private def change(chats: Var[Vector[Chat]], id: String, f: Chat => Chat): Unit =
    chats @= chats().map(c => if (c.id == id) f(c) else c)

  private def look(c: Chat): RichLook = RichLook(c.title, Some(c.snippet), c.who, c.icon,
    time = Some(c.updated), count = c.unread, unread = c.unread > 0,
    mark = if (c.locked) Some(MaterialIcon.Lock) else if (c.pinned) Some(MaterialIcon.PushPin) else None)

  // conversation `i`: newer the lower `i` is, a few unread (one past 99), some pinned or locked, some a group's icon
  private def made(i: Int, start: Long): Chat = {
    val ago = if (i < 3) i.toLong * 20000L else if (i < 60) i.toLong * 11L * 60000L
      else if (i < 200) i.toLong * 50L * 60000L else i.toLong * 3L * 3600000L
    val group = i % 9 == 4
    Chat("c-" + i.toString,
      topics(i % topics.size) + (if (i >= topics.size) " " + (i / topics.size + 1).toString else ""),
      snippets(i % snippets.size),
      if (group) None else Some(people(i % people.size)),
      if (group) Some(if (i % 2 == 0) MaterialIcon.Group else MaterialIcon.Tag) else None,
      start - ago,
      if (i == 1) 128 else if (i < 20 && i % 4 == 0) i % 7 + 1 else 0,
      pinned = i % 37 == 5, locked = i % 31 == 7)
  }
}

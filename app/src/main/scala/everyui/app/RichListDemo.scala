package everyui.app

import everyui.async.Task
import everyui.chat.Participant
import everyui.component.{Container, TextView}
import everyui.component.types.FontWeight
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.list.{RichGroup, RichList, RichLook, RichSection}
import everyui.menu.MenuItem
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import everyui.util.Clock
import reactify.*

/** A conversation list (`everyui.list.RichList`): 5000 conversations in four sections, Unread on its own, Recent and
  * Archive in the Workspaces group and Ada Lovelace's in the Personal one, with avatars (one with a picture), icons,
  * snippets, times from now back over months, unread counts, pins and locks, a search field over them, a menu on each
  * (Rename…, Pin, Lock title, Delete), one on the Unread and Recent headers (Mark all read, Collapse others), Archive's
  * marked with an icon and without one, and one on each group's (Close its sections, Open its sections). A few have
  * an agent at work in them, a ring turning around the avatar with what it is doing on it (one unread too), until
  * `stop=<seconds>` stops them. Conversations in Recent drag into another order. Wide, the list is a sidebar beside the
  * conversation chosen; narrow, it fills the screen. `theme=light` for the light palette, `rtl=1` to lay it out right to
  * left, `collapsed=<key>` to start with a section closed, `groups=<key>` with a group (workspaces, personal),
  * `scroll=<pixels>` to start scrolled, `find=<words>` to start narrowed, `menu=1` to open the chosen conversation's ⋮
  * menu, and `live=<ids>` for the conversations open in a tab (`RichLook.live`: c-0, c-3, c-5, the one chosen, and c-7
  * by default; `live=0` none). Logs `richlist ok` once built, and each thing done. */
object RichListDemo {
  private case class Chat(id: String, title: String, snippet: String, who: Option[Participant], icon: Option[MaterialIcon],
                          updated: Long, unread: Int, pinned: Boolean, locked: Boolean,
                          doing: Option[String] = None)

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
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val start = Clock.real()
    val chats: Var[Vector[Chat]] = Var((0 until 5000).toVector.map(i => made(i, start)))
    val order: Var[Vector[String]] = Var(Vector.empty)
    val collapsed: Var[Set[String]] = Var(Platform.launchArg("collapsed").split(",").filter(_.nonEmpty).toSet)
    val closedGroups: Var[Set[String]] = Var(Platform.launchArg("groups").split(",").filter(_.nonEmpty).toSet)
    val sections: Var[Vector[RichSection[Chat]]] = Var(Vector.empty)
    order @= chats().map(_.id)
    chats.attach(_ => group(chats, order, sections, start))
    order.attach(_ => group(chats, order, sections, start))
    group(chats, order, sections, start)

    // the conversations open in a tab
    val live: Set[String] = if (Platform.launchArg("live").isEmpty) Set("c-0", "c-3", "c-5", "c-7")
      else Platform.launchArg("live").split(",").filter(_.nonEmpty).toSet
    val list = new RichList[Chat](sections, _.id, c => look(c, live), collapsed, search = true, collapsedGroups = closedGroups)
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
    // a menu on the Unread and Recent headers, none on Archive's
    list.sectionMenu @= { (s: RichSection[Chat]) =>
      val others = MenuItem("Collapse others", () => {
        collapsed @= sections().map(_.key).filterNot(_ == s.key).toSet
        Platform.log("richlist collapsed all but " + s.key)
      }, MaterialIcon.UnfoldLess)
      if (s.key == "archive") Nil
      else if (s.key == "unread") List(MenuItem("Mark all read", () => {
        chats @= chats().map(_.copy(unread = 0))
        Platform.log("richlist marked all read")
      }, MaterialIcon.DoneAll), others)
      else List(others)
    }
    // a menu on each group's header, over the sections in it
    list.groupMenu @= { (g: RichGroup) =>
      val keys = sections().filter(_.group.exists(_.key == g.key)).map(_.key).toSet
      List(MenuItem("Close its sections", () => {
        collapsed @= collapsed() ++ keys
        Platform.log("richlist closed the sections of " + g.key)
      }, MaterialIcon.UnfoldLess), MenuItem("Open its sections", () => {
        collapsed @= collapsed() -- keys
        Platform.log("richlist opened the sections of " + g.key)
      }, MaterialIcon.UnfoldMore))
    }
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
    Platform.launchArg("stop").toDoubleOption.foreach(s => Platform.after(s, () => {
      chats @= chats().map(_.copy(doing = None))
      Platform.log("richlist stopped the agents")
    }))
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

  private val workspaces: RichGroup = RichGroup("workspaces", "Workspaces", Some(MaterialIcon.Workspaces))
  private val personal: RichGroup = RichGroup("personal", "Personal", Some(MaterialIcon.Person))

  // the four sections: Unread on its own; Recent (in the order it was dragged into, pinned first) and Archive in
  // Workspaces; and Ada's, read, in Personal
  private def group(chats: Var[Vector[Chat]], order: Var[Vector[String]], sections: Var[Vector[RichSection[Chat]]],
                    start: Long): Unit = {
    val all = chats()
    val read = all.filter(_.unread == 0)
    val ada = read.filter(_.who.exists(_.id == "p-ada"))
    val rest = read.filterNot(_.who.exists(_.id == "p-ada"))
    val recent = rest.filter(c => start - c.updated < week)
    val byId = recent.map(c => c.id -> c).toMap
    val ranked = order().flatMap(id => byId.get(id).toVector)
    sections @= Vector(RichSection("unread", "Unread", all.filter(_.unread > 0)),
      RichSection("recent", "Recent", ranked.filter(_.pinned) ++ ranked.filterNot(_.pinned), group = Some(workspaces)),
      RichSection("archive", "Archive", rest.filter(c => start - c.updated >= week), icon = Some(MaterialIcon.Archive),
        group = Some(workspaces)),
      RichSection("ada", "Ada Lovelace", ada, group = Some(personal)))
  }

  private def change(chats: Var[Vector[Chat]], id: String, f: Chat => Chat): Unit =
    chats @= chats().map(c => if (c.id == id) f(c) else c)

  private def look(c: Chat, live: Set[String]): RichLook = RichLook(c.title, Some(c.snippet), c.who, c.icon,
    time = Some(c.updated), count = c.unread, unread = c.unread > 0,
    mark = if (c.locked) Some(MaterialIcon.Lock) else if (c.pinned) Some(MaterialIcon.PushPin) else None,
    busy = c.doing.nonEmpty, busyDetail = c.doing.getOrElse(""), live = live.contains(c.id), liveLabel = Some("open in a tab"))

  // what the agent is doing in the conversations it is at work in: the first, unread, and two pinned near the top of
  // Recent among them
  private val working: Map[Int, String] = Map(0 -> "Running bash", 5 -> "Thinking", 42 -> "Editing RichRow.scala",
    14 -> "Searching the web", 21 -> "Running the tests")

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
      pinned = i % 37 == 5, locked = i % 31 == 7, doing = working.get(i))
  }
}

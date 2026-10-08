package everyui.app

import everyui.chat.Participant
import everyui.component.{Button, Container, TextView}
import everyui.component.types.FontWeight
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.list.{RichGroup, RichList, RichLook, RichSection, RichStyle}
import everyui.menu.MenuItem
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import everyui.util.Clock
import reactify.*

/** A sidebar's conversations as blocks (`RichStyle.Blocks`): Unread on its own, three workspaces in the Workspaces
  * group (one with a long name, closed) and two people in Personal (one closed), with unread counts, a long title, an
  * agent at work, a search field, menus on the items and the sections, and items that drag anywhere. Wide, the list is
  * a 280 wide sidebar beside the conversation chosen and a button that draws it as a tree and back; narrow, it fills
  * the screen. `theme=light`, `rtl=1`, `style=tree` to start as a tree, `collapsed=<keys>` and `groups=<keys>` for
  * what starts closed (Gauss's block and nabo's by default), and `find=<words>` to start narrowed. Logs `blocks ok`
  * once built, and each thing done. */
object BlocksDemo {
  private case class Chat(id: String, title: String, snippet: String, who: Participant, updated: Long, unread: Int,
                          doing: Option[String] = None)

  private val workspaces: RichGroup = RichGroup("workspaces", "Workspaces", Some(MaterialIcon.Workspaces))
  private val personal: RichGroup = RichGroup("personal", "Personal", Some(MaterialIcon.Person))
  private val people: Vector[Participant] = Vector(Participant("p-ada", "Ada Lovelace"),
    Participant("p-grace", "Grace Hopper"), Participant("p-alan", "Alan Turing"), Participant("p-gauss", "Carl Gauss"),
    Participant("p-bot", "EveryUI", Some("images/everyui-mark.png")))
  private val snippets: Vector[String] = Vector("Sounds good, ship it.", "Can you look at the failing build?",
    "I pushed the fix; the tests pass locally.", "Here is the plan for the release notes and the rollout after it",
    "Thanks!", "The screenshots are attached.")

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") Direction.set(true)
    ui.backgroundColor := Theme.background()
    val start = Clock.real()
    // (workspace, title, unread) for each conversation, newest first
    val made: Vector[(String, String, Int)] = Vector(
      ("everyui", "Blocks for the sidebar", 3), ("everyui", "Roku corners", 0), ("everyui", "Why the list jumps", 0),
      ("voidcraft", "A conversation with a title far too long to fit on one line of the sidebar", 12),
      ("voidcraft", "Release planning", 0), ("voidcraft", "Design review", 1), ("voidcraft", "Onboarding", 0),
      ("voidcraft", "Weekly sync", 0), ("nabo", "Player tests", 0), ("nabo", "Backdrop continuity", 0),
      ("nabo", "Screensaver", 128), ("ada", "Notes on the engine", 0), ("ada", "Bernoulli numbers", 2),
      ("gauss", "Least squares", 0), ("gauss", "Congruences", 0))
    val chats: Var[Vector[(String, Chat)]] = Var(made.zipWithIndex.map { case ((place, title, unread), i) =>
      place -> Chat("c-" + i.toString, title, snippets(i % snippets.size), people(i % people.size),
        start - i.toLong * 47L * 60000L, unread, if (i == 1) Some("Running the tests") else None)
    })
    val sections: Var[Vector[RichSection[Chat]]] = Var(Vector.empty)
    def group(): Unit = {
      val all = chats()
      def in(place: String): Vector[Chat] = all.filter(_._1 == place).map(_._2)
      sections @= Vector(RichSection("unread", "Unread", all.map(_._2).filter(_.unread > 0)),
        RichSection("everyui", "everyui", in("everyui"), icon = Some(MaterialIcon.Workspaces), group = Some(workspaces)),
        RichSection("voidcraft", "voidcraft", in("voidcraft"), icon = Some(MaterialIcon.Workspaces), group = Some(workspaces)),
        RichSection("nabo", "nabo-tv-platform-and-every-client-of-it", in("nabo"), icon = Some(MaterialIcon.Workspaces),
          group = Some(workspaces)),
        RichSection("ada", "Ada Lovelace", in("ada"), group = Some(personal)),
        RichSection("gauss", "Carl Gauss", in("gauss"), group = Some(personal)))
    }
    chats.attach(_ => group())
    group()
    def keys(arg: String, otherwise: Set[String]): Set[String] =
      if (Platform.launchArg(arg).isEmpty) otherwise else Platform.launchArg(arg).split(",").filter(_.nonEmpty).toSet
    val collapsed: Var[Set[String]] = Var(keys("collapsed", Set("nabo", "gauss")))
    val closedGroups: Var[Set[String]] = Var(keys("groups", Set.empty))

    val list = new RichList[Chat](sections, _.id, c => RichLook(c.title, Some(c.snippet), Some(c.who),
      time = Some(c.updated), count = c.unread, unread = c.unread > 0, busy = c.doing.nonEmpty,
      busyDetail = c.doing.getOrElse("")), collapsed, search = true, collapsedGroups = closedGroups)
    list.style @= (if (Platform.launchArg("style") == "tree") RichStyle.Tree else RichStyle.Blocks)
    list.reorders @= true
    list.accepts @= ((_: everyui.list.RichMove) => true)
    list.menu @= ((c: Chat) => List(MenuItem("Rename…", () => list.rename(c.id), MaterialIcon.Edit),
      MenuItem("Delete", () => {
        chats @= chats().filterNot(_._2.id == c.id)
        Platform.log("blocks deleted " + c.id)
      }, MaterialIcon.Delete, danger = true)))
    list.sectionMenu @= ((s: RichSection[Chat]) => if (s.key == "unread") Nil else List(MenuItem("Collapse others", () => {
      collapsed @= sections().map(_.key).filterNot(_ == s.key).toSet
      Platform.log("blocks collapsed all but " + s.key)
    }, MaterialIcon.UnfoldLess)))
    list.commit @= ((c: Chat, text: String) => {
      chats @= chats().map(p => if (p._2.id == c.id) p._1 -> p._2.copy(title = text.trim) else p)
      everyui.async.Task.pure(None: Option[String])
    })
    // into another block, the item goes to that workspace (or person), before the item it was dropped before
    list.moved.attach { m =>
      val moving = chats().filter(p => m.keys.contains(p._2.id)).map(p => (if (m.section == "unread") p._1 else m.section) -> p._2)
      val rest = chats().filterNot(p => m.keys.contains(p._2.id))
      val at = m.before.map(b => rest.indexWhere(_._2.id == b)).filter(_ >= 0)
        .getOrElse(rest.lastIndexWhere(_._1 == m.section) + 1)
      chats @= rest.patch(at, moving, 0)
      Platform.log("blocks moved " + m.keys.mkString(",") + " from " + m.from + " into " + m.section + " before " +
        m.before.getOrElse("the end"))
    }
    list.activated.attach { c =>
      if (c.unread > 0) chats @= chats().map(p => if (p._2.id == c.id) p._1 -> p._2.copy(unread = 0) else p)
      Platform.log("blocks opened " + c.id)
    }
    collapsed.attach(c => Platform.log("blocks collapsed " + c.toList.sorted.mkString(",")))
    closedGroups.attach(g => Platform.log("blocks groups closed " + g.toList.sorted.mkString(",")))

    val open: Val[Option[Chat]] = Val(list.selected().flatMap(id => chats().find(_._2.id == id)).map(_._2))
    val chosen = new TextView {
      content := open().map(_.title).getOrElse("")
      font.size @= 20.0
      font.weight @= FontWeight.SemiBold
      color := Theme.text()
      maxWidth := math.max(40.0, ui.size.width() - 280.0 - 48.0)
    }
    val style = new Button("Draw as a tree", () => {
      list.style @= (if (list.style() == RichStyle.Blocks) RichStyle.Tree else RichStyle.Blocks)
      Platform.log("blocks style " + list.style().toString)
    })
    style.label := (if (list.style() == RichStyle.Blocks) "Draw as a tree" else "Draw as blocks")
    val pane = new Container {
      backgroundColor := Theme.surface()
      children ++= List(chosen, style)
    }
    chosen.position.left @= 24.0
    chosen.position.top @= 24.0
    style.position.left @= 24.0
    style.position.top @= 72.0
    val wide: Val[Boolean] = Val(ui.size.width() >= 640.0)
    list.size.width := (if (wide()) 280.0 else ui.size.width())
    list.size.height := ui.size.height()
    pane.visible := wide()
    pane.position.left := list.size.width()
    pane.size.width := math.max(0.0, ui.size.width() - list.size.width())
    pane.size.height := ui.size.height()
    list.selected @= Some("c-4")
    ui.children ++= List(list, pane)
    list.focus()
    if (Platform.launchArg("find").nonEmpty) list.query @= Platform.launchArg("find")
    if (Platform.freezeSeconds() >= 0.0) Platform.after(1.0, () => Platform.frozen())
    Platform.log("blocks ok " + sections().map(s => s.title + " " + s.items.size.toString).mkString(", "))
  }
}

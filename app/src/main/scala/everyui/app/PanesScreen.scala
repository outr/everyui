package everyui.app

import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.chat.Participant
import everyui.component.{Component, Container, TextView}
import everyui.component.types.{FontWeight, WhiteSpace}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.layout.VerticalLayout
import everyui.list.{RichAction, RichList, RichLook, RichSection}
import everyui.net.*
import everyui.render.Platform
import everyui.split.{StackedPane, StackedPanes}
import everyui.theme.Theme
import everyui.util.Clock
import reactify.*

/** The panes demo's one screen (PanesDemo): the stacked panes, and beside them, where there is room, what was chosen
  * and what the app keeps. */
class PanesScreen extends UIScreen {
  override def manager: ScreenManager = PanesDemo
  override def title: String = "Panes"
  override def path: URLPath = path"/panes"

  private var first: Option[Component] = None

  override def defaultFocus(): Component = first.getOrElse(container)

  override def createUI(): Task[Unit] = Task {
    val closed = Platform.launchArg("collapsed").split(",").filter(_.nonEmpty).toSet
    val stored = Platform.launchArg("weights").split(",").filter(_.nonEmpty).map(_.toDouble).toVector
    def weightOf(i: Int, otherwise: Double): Double = if (i < stored.size) stored(i) else otherwise
    // what the app keeps: whether each pane is folded, and its weight
    val foldersClosed = Var(closed.contains("folders"))
    val chatsClosed = Var(closed.contains("chats"))
    val logClosed = Var(closed.contains("log"))
    val foldersWeight = Var(weightOf(0, 1.0))
    val chatsWeight = Var(weightOf(1, 1.5))
    val logWeight = Var(weightOf(2, 0.8))

    val lines: Var[Vector[String]] = Var(Vector.empty)
    def note(s: String): Unit = {
      lines @= (s +: lines()).take(40)
      Platform.log("panes " + s)
    }

    // ---- Folders: a file tree ----
    var folderCount = 0
    val tree: Var[Vector[(String, Vector[String])]] = Var(Vector(
      "src" -> Vector("Main.scala", "Sidebar.scala", "StackedPanes.scala",
        "a-file-with-a-name-far-too-long-for-the-sidebar.scala"),
      "docs" -> Vector("README.md", "CONSUMING.md"), "tests" -> Vector("PanesSpec.scala"),
      "assets" -> Vector("logo.svg")))
    val folderSections: Var[Vector[RichSection[PanesScreen.File]]] = Var(Vector.empty)
    def folders(): Unit = folderSections @= tree().map { case (dir, files) =>
      RichSection(dir, dir, files.map(f => PanesScreen.File(dir + "/" + f, f)), icon = Some(MaterialIcon.Folder),
        empty = Some("Empty folder"))
    }
    tree.attach(_ => folders())
    folders()
    val openFolders: Var[Set[String]] = Var(Set("docs", "tests", "assets"))
    val files = new RichList[PanesScreen.File](folderSections, _.path,
      f => RichLook(f.name, icon = Some(MaterialIcon.Description)), openFolders)
    files.activated.attach(f => note("opened " + f.path))

    // ---- Conversations ----
    val start = Clock.real()
    val people = Vector(Participant("p-ada", "Ada Lovelace"), Participant("p-grace", "Grace Hopper"),
      Participant("p-alan", "Alan Turing"))
    var chatCount = 0
    val titles = Vector("Blocks for the sidebar", "Roku corners", "Why the list jumps", "Release planning",
      "Design review", "Onboarding", "Weekly sync", "Player tests")
    val chats: Var[Vector[PanesScreen.Chat]] = Var(titles.zipWithIndex.map { case (t, i) =>
      PanesScreen.Chat("c-" + i.toString, t, people(i % people.size), start - i.toLong * 3L * 3600000L,
        if (i == 0) 2 else 0)
    })
    val chatSections: Var[Vector[RichSection[PanesScreen.Chat]]] = Var(Vector.empty)
    def sectionsOfChats(): Unit = {
      val all = chats()
      chatSections @= Vector(RichSection("today", "Today", all.filter(c => start - c.updated < 12L * 3600000L)),
        RichSection("earlier", "Earlier", all.filter(c => start - c.updated >= 12L * 3600000L)))
    }
    chats.attach(_ => sectionsOfChats())
    sectionsOfChats()
    val conversations = new RichList[PanesScreen.Chat](chatSections, _.id, c => RichLook(c.title, who = Some(c.who),
      time = Some(c.updated), count = c.unread, unread = c.unread > 0))
    conversations.activated.attach(c => note("opened " + c.id))

    // ---- Log ----
    val state = new TextView {
      font.size @= 12.0
      font.weight @= FontWeight.SemiBold
      color := Theme.accent()
      whiteSpace @= WhiteSpace.Normal
      maxLines @= 3
    }
    val log = new Container {
      backgroundColor := Theme.surface()
    }
    val logLines = new Container {
      layout @= Some(VerticalLayout(4.0))
    }
    def showLines(all: Vector[String]): Unit = {
      val old = logLines.children()
      logLines.children @= all.map { s =>
        val t = TextView(s, 12.0, Theme.muted())
        t.whiteSpace @= WhiteSpace.Normal
        t.size.width := math.max(20.0, log.size.width() - 24.0)
        t
      }
      old.foreach(_.dispose())
    }
    lines.attach(showLines)
    state.position.left @= 12.0
    logLines.position.left @= 12.0
    // (as wide as the pane leaves them, so they wrap in it, and start from the right in a right-to-left language)
    state.size.width := math.max(20.0, log.size.width() - 24.0)
    logLines.size.width := state.size.width()
    state.position.top @= 8.0
    logLines.position.top := state.position.top() + state.size.height() + 8.0
    log.children ++= List(state, logLines)

    // ---- the panes ----
    val folderPane = new StackedPane("Folders", files, foldersClosed, foldersWeight)
    folderPane.icon @= Some(MaterialIcon.Folder)
    folderPane.actions @= List(
      RichAction(MaterialIcon.CreateNewFolder, "New folder", () => {
        folderCount += 1
        tree @= tree() :+ ("folder-" + folderCount.toString -> Vector.empty[String])
        note("new folder folder-" + folderCount.toString)
      }),
      RichAction(MaterialIcon.Refresh, "Refresh", () => note("refreshed")),
      RichAction(MaterialIcon.UnfoldLess, "Collapse folders", () => {
        openFolders @= tree().map(_._1).toSet
        note("collapsed folders")
      }))
    val chatPane = new StackedPane("Conversations", conversations, chatsClosed, chatsWeight)
    chatPane.icon @= Some(MaterialIcon.Forum)
    chatPane.actions @= List(RichAction(MaterialIcon.Add, "New conversation", () => {
      chatCount += 1
      chats @= PanesScreen.Chat("n-" + chatCount.toString, "New conversation " + chatCount.toString, people(0),
        Clock.real(), 0) +: chats()
      note("new conversation n-" + chatCount.toString)
    }, shown = true))
    val logPane = new StackedPane("Log", log, logClosed, logWeight)
    logPane.icon @= Some(MaterialIcon.Notes)
    logPane.minHeight @= 80.0
    logPane.actions @= List(RichAction(MaterialIcon.DeleteSweep, "Clear", () => {
      lines @= Vector.empty
      Platform.log("panes cleared")
    }))
    val panes = new StackedPanes(Vector(folderPane, chatPane, logPane))

    def said(name: String, folded: Boolean, weight: Double): String =
      name + " " + (if (folded) "folded" else "open") + " " + (math.round(weight * 100.0).toDouble / 100.0).toString
    val kept: Val[String] = Val(said("folders", foldersClosed(), foldersWeight()) + ", " +
      said("chats", chatsClosed(), chatsWeight()) + ", " + said("log", logClosed(), logWeight()))
    state.content := "Kept: " + kept()
    kept.attach(k => Platform.log("panes state " + k))

    // beside the panes, where there is room: what was chosen, and what is kept
    val wide: Val[Boolean] = Val(container.size.width() >= 640.0)
    val chosen = new TextView {
      content := conversations.selected().orElse(files.selected()).getOrElse("Nothing chosen")
      font.size @= 20.0
      font.weight @= FontWeight.SemiBold
      color := Theme.text()
    }
    val keptBeside = new TextView {
      content := "What the app keeps: " + kept()
      font.size @= 14.0
      color := Theme.muted()
    }
    val main = new Container {
      backgroundColor := Theme.background()
      children ++= List(chosen, keptBeside)
    }
    List(chosen, keptBeside).foreach { t =>
      t.position.left @= 24.0
      t.maxWidth := math.max(40.0, main.size.width() - 48.0)
    }
    chosen.position.top @= 24.0
    keptBeside.position.top @= 64.0
    panes.size.width := (if (!wide()) container.size.width()
      else if (Platform.launchArg("sidebar") == "1") 280.0 else 360.0)
    panes.size.height := container.size.height()
    panes.position.left := (if (Direction.rtl()) container.size.width() - panes.size.width() else 0.0)
    panes.backgroundColor := Theme.surface()
    main.visible := wide()
    main.size.width := math.max(0.0, container.size.width() - panes.size.width())
    main.size.height := container.size.height()
    main.position.left := (if (Direction.rtl()) 0.0 else panes.size.width())
    container.children ++= List(panes, main)
    first = Some(folderPane.header)
    folderPane.header.focus()
    Platform.log("panes ok " + panes.heights().map(h => math.round(h).toString).mkString(","))
    if (Platform.launchArg("probe") == "1") PanesScreen.probe(panes)
  }
}

object PanesScreen {
  private case class File(path: String, name: String)
  private case class Chat(id: String, title: String, who: Participant, updated: Long, unread: Int)


  /** The panes folded and opened by the keys, as a remote would: each step said with the content heights after it. */
  private def probe(panes: StackedPanes): Unit = {
    def press(key: String): Unit = {
      Focus.key(key, true)
      Focus.key(key, false)
    }
    def where(): String = Focus.focused().map(_.kind).getOrElse("nothing")
    def report(step: String): Unit = Platform.log("panes probe " + step + ": " +
      panes.target().map(h => math.round(h).toString).mkString(",") + " focus " + where())
    val steps: Vector[(String, () => Unit)] = Vector(
      "start" -> (() => ()),
      "enter folds folders" -> (() => press("Enter")),
      "down to conversations" -> (() => press("ArrowDown")),
      "space folds conversations" -> (() => press(" ")),
      "down to log" -> (() => press("ArrowDown")),
      "enter folds log" -> (() => press("Enter")),
      "up to conversations" -> (() => press("ArrowUp")),
      "enter opens conversations" -> (() => press("Enter")),
      "down into the list" -> (() => press("ArrowDown")),
      "up to its header" -> (() => press("ArrowUp")),
      "right to its action" -> (() => press(if (Direction.rtl()) "ArrowLeft" else "ArrowRight")),
      "left back" -> (() => press(if (Direction.rtl()) "ArrowRight" else "ArrowLeft")),
      "up to folders" -> (() => press("ArrowUp")),
      "enter opens folders" -> (() => press("Enter")),
      "down and up" -> (() => {
        press("ArrowDown")
        press("ArrowUp")
      }))
    def step(i: Int): Unit = if (i < steps.size) {
      steps(i)._2()
      Platform.after(0.4, () => {
        report(steps(i)._1)
        step(i + 1)
      })
    }
    else Platform.log("panes probe done")
    Platform.after(0.5, () => step(0))
  }
}

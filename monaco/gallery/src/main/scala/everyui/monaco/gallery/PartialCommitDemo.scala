package everyui.monaco.gallery

import everyui.component.{Button, ButtonStyle, Container}
import everyui.monaco.*
import everyui.monaco.Monaco.uiThread
import reactify.*

/** A partial commit, as IntelliJ's diff preview makes one: a checkbox on each change and on each changed line of the
  * working copy, ticked by default, and a change rolled back from the gutter. What a tick means (what goes into the
  * commit) is this demo's own; the editor gives the ranges, the boxes and the edits. */
object PartialCommitDemo {
  val original: String =
    """object Inventory {
      |  private var items = Map.empty[String, Int]
      |
      |  def add(name: String): Unit =
      |    items = items.updated(name, items.getOrElse(name, 0) + 1)
      |
      |  def remove(name: String): Unit =
      |    items = items - name
      |
      |  def report(): String = items.mkString(", ")
      |}
      |""".stripMargin

  val modified: String =
    """object Inventory {
      |  private var items = Map.empty[String, Int]
      |  private var log = List.empty[String]
      |
      |  def add(name: String, count: Int = 1): Unit = {
      |    items = items.updated(name, items.getOrElse(name, 0) + count)
      |    log = s"add $name" :: log
      |  }
      |
      |  def report(): String = items.toList.sortBy(_._1).map((n, c) => s"$n=$c").mkString(", ")
      |}
      |""".stripMargin

  def apply(demo: Demo): DemoPage = {
    val controller = MonacoDiffController(original, modified, "scala")
    val log = new EventLog("partial commit", "Untick changes or lines; Roll back unticked puts the first unticked change back as it was; Commit says what it would take.")
    // what is left out of the commit, by change (its place in both sides) and by line of the working copy
    val skippedChanges = Var(Set.empty[(Int, Int)])
    val skippedLines = Var(Set.empty[Int])
    def key(c: MonacoLineChange): (Int, Int) = (c.originalStartLine, c.modifiedStartLine)
    def included(c: MonacoLineChange): Boolean = !skippedChanges().contains(key(c))

    def show(): Unit = controller.checks @= MonacoDiffCheck.forChanges(controller.changes(), included, lines = true,
      line => !skippedLines().contains(line) && controller.changeAt(line).forall(included))
    controller.changes.attach(_ => show())
    skippedChanges.attach(_ => show())
    skippedLines.attach(_ => show())

    controller.onGutterClick.attach { click =>
      click.change.foreach { c =>
        if (click.isLine) skippedLines @= (if (skippedLines().contains(click.line)) skippedLines() - click.line else skippedLines() + click.line)
        else skippedChanges @= (if (included(c)) skippedChanges() + key(c) else skippedChanges() - key(c))
      }
    }

    def rollBack(): Unit = controller.changes().find(c => !included(c)) match {
      case Some(c) => controller.revertChange(c).foreach(_ => log.say(s"rolled back the change at line ${c.anchorLine}"))
      case None => log.say("untick a change first: the first unticked one is rolled back")
    }

    def commit(): Unit = {
      val chosen = controller.changes().filter(included)
      val lines = chosen.flatMap(_.modifiedLines).filterNot(skippedLines().contains)
      log.say(s"would commit ${chosen.size} of ${controller.changes().size} changes, ${lines.size} changed lines: " +
        chosen.map(c => if (c.isDeletion) s"-${c.originalStartLine}..${c.originalEndLine}" else s"${c.modifiedStartLine}..${c.modifiedEndLine}").mkString(" "))
    }

    val editor = new MonacoDiffEditor(controller)
    val body = new Container {
      editor.size.width := size.width()
      editor.size.height := size.height() - 120.0
      log.position.top := size.height() - 120.0
      log.size.width := size.width()
      log.size.height @= 120.0
      children ++= List(editor, log)
    }
    new DemoPage(demo, body, List(
      new Button("Roll back unticked", () => rollBack(), ButtonStyle.Tonal),
      new Button("Commit", () => commit(), ButtonStyle.Filled)), () => controller.dispose())
  }
}

package everyui.monaco.gallery

import everyui.component.Container
import everyui.monaco.*
import everyui.monaco.Monaco.uiThread

/** Actions with keybindings and context-menu entries, and a bare command, each saying in the log when it runs. */
object ActionsDemo {
  val code: String =
    """// This demo wires up:
      |//
      |//   Ctrl/Cmd+Shift+U    "Uppercase current line" (an action, in the context menu)
      |//   Ctrl/Cmd+Shift+D    "Duplicate current line" (an action)
      |//   Ctrl/Cmd+K          a command, with no palette entry
      |//
      |// Right-click the editor to see the actions in the "Modification" group of the context menu,
      |// or use the keybindings.
      |
      |def greet(name: String): String = s"hello, $name!"
      |
      |@main def run(): Unit =
      |  List("alice", "bob", "carol").foreach(n => println(greet(n)))
      |""".stripMargin

  def apply(demo: Demo): DemoPage = {
    val controller = MonacoController(initialValue = code, language = "scala")
    val log = new EventLog("event log", "Waiting for actions and commands...")

    def line(f: (List[String], Int) => List[String], said: Int => String): Unit = controller.position().foreach { p =>
      val lines = controller.value().split("\n", -1).toList
      if (p.line >= 1 && p.line <= lines.length) controller.setValue(f(lines, p.line - 1).mkString("\n")).foreach(_ => log.say(said(p.line)))
    }

    controller.addAction(MonacoAction("demo.uppercaseLine", "Uppercase current line",
      List(MonacoKeyMod.CtrlCmd | MonacoKeyMod.Shift | MonacoKeyCode.KeyU), Some("1_modification"), Some(1.5))(_ =>
      line((ls, i) => ls.updated(i, ls(i).toUpperCase), n => s"Uppercased line $n")))
    controller.addAction(MonacoAction("demo.duplicateLine", "Duplicate current line",
      List(MonacoKeyMod.CtrlCmd | MonacoKeyMod.Shift | MonacoKeyCode.KeyD), Some("1_modification"), Some(1.6))(_ =>
      line((ls, i) => ls.patch(i + 1, List(ls(i)), 0), n => s"Duplicated line $n")))
    controller.addCommand(MonacoKeyMod.CtrlCmd | MonacoKeyCode.KeyK)(log.say("Ctrl/Cmd+K fired"))

    val editor = new MonacoEditor(controller)
    val body = new Container {
      editor.size.width := size.width()
      editor.size.height := size.height() - 120.0
      log.position.top := size.height() - 120.0
      log.size.width := size.width()
      log.size.height @= 120.0
      children ++= List(editor, log)
    }
    new DemoPage(demo, body, Nil, () => controller.dispose())
  }
}

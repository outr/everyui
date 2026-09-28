package everyui.monaco.gallery

import everyui.component.{Button, ButtonStyle, Container, TextView}
import everyui.keymap.Chord
import everyui.monaco.*
import everyui.monaco.Monaco.ui
import everyui.theme.Theme
import reactify.*

import scala.concurrent.Future

/** What a code browser needs: a model per open file (each keeping its own undo), a dirty mark and Save on
  * Primary+S (a keymap command the editor leaves to the app), go to definition, find usages, the outline, compile
  * errors, changed-line marks and blame, and a range revealed and lit. */
object NavigationDemo {
  val files: List[(String, String)] = List(
    "file:///demo/Greeter.scala" ->
      """package demo
        |
        |/** Says hello. F12 on a name goes to its definition; Shift+F12 finds its usages. */
        |class Greeter(name: String) {
        |  def greeting: String = "Hello, " + name + "!"
        |
        |  def greet(): Unit = println(greeting)
        |}
        |
        |object Greeter {
        |  def world: Greeter = new Greeter("world")
        |}
        |""".stripMargin,
    "file:///demo/Main.scala" ->
      """package demo
        |
        |/** Edit, then Ctrl+S (Cmd+S) to save: the tab's dot goes away. Ctrl+Shift+O lists the symbols. */
        |object Main {
        |  def main(args: Array[String]): Unit = {
        |    val greeter = Greeter.world
        |    greeter.greet()
        |    greeter.greet(42)
        |  }
        |}
        |""".stripMargin)

  private val declaration = """\b(def|val|var|class|object|trait)\s+([A-Za-z_][A-Za-z0-9_]*)""".r

  /** Every declaration in `text`: what it is, its name, and where the name is. */
  def declarations(text: String): List[(String, String, MonacoRange)] =
    text.split("\n", -1).toList.zipWithIndex.flatMap { case (line, i) =>
      declaration.findAllMatchIn(line).map { m =>
        (m.group(1), m.group(2), MonacoRange(i + 1, m.start(2) + 1, i + 1, m.end(2) + 1))
      }
    }

  /** Every whole-word use of `word` in `text`. */
  def uses(text: String, word: String): List[MonacoRange] =
    if (word.isEmpty) Nil
    else text.split("\n", -1).toList.zipWithIndex.flatMap { case (line, i) =>
      ("""\b""" + java.util.regex.Pattern.quote(word) + """\b""").r.findAllMatchIn(line)
        .map(m => MonacoRange(i + 1, m.start + 1, i + 1, m.end + 1))
    }

  def apply(demo: Demo): DemoPage = {
    val controller = MonacoController(initialValue = files.head._2, language = "scala",
      options = MonacoEditorOptions(glyphMargin = Some(true)))
    val log = new EventLog("what happened", "Save, go to a definition, find usages...")
    val open = Var(files.head._1)
    // which files have been saved since they were last changed, kept per file as the editor moves between them
    val saved = scala.collection.mutable.Map.empty[String, Boolean]

    def name(uri: String): String = uri.substring(uri.lastIndexOf('/') + 1)

    val providers = Future.sequence(List(
      MonacoLanguages.registerDefinitionProvider("scala", MonacoDefinitionProvider { p =>
        val word = IntelliSenseDemo.wordAt(p.value, p.position.line, p.position.column)
        val found = declarations(p.value).filter(_._2 == word).map(d => MonacoLocation(p.uri, d._3))
        log.say(s"definition of $word: ${found.size} found")
        Future.successful(found)
      }),
      MonacoLanguages.registerReferenceProvider("scala", MonacoReferenceProvider { p =>
        val word = IntelliSenseDemo.wordAt(p.value, p.position.line, p.position.column)
        val found = uses(p.value, word).map(r => MonacoLocation(p.uri, r))
        log.say(s"usages of $word: ${found.size}")
        Future.successful(found)
      }),
      MonacoLanguages.registerDocumentSymbolProvider("scala", MonacoDocumentSymbolProvider { p =>
        Future.successful(declarations(p.value).map { case (kind, word, at) =>
          val symbol = kind match {
            case "class" => MonacoSymbolKind.Class
            case "object" => MonacoSymbolKind.Module
            case "trait" => MonacoSymbolKind.Interface
            case "def" => MonacoSymbolKind.Method
            case _ => MonacoSymbolKind.Variable
          }
          MonacoDocumentSymbol(word, symbol, MonacoRange(at.startLine, 1, at.endLine, at.endColumn), Some(at))
        })
      }, "demo")))

    // the compile errors, changed lines and blame of the file showing
    var marks: List[String] = Nil
    def decorate(uri: String): Unit = {
      val errors = if (uri.endsWith("Main.scala")) List(MonacoMarker(MonacoRange(8, 13, 8, 18), MonacoMarkerSeverity.Error,
        "too many arguments (found 1, expected 0) for method greet: (): Unit", Some("scalac"))) else Nil
      controller.setModelMarkers(errors, "scalac")
      controller.deltaDecorations(marks, List(
        MonacoDecoration.gutter(5, 5, "modified", Some("Changed since the last commit")),
        MonacoDecoration.gutter(7, 7, "added", Some("Added since the last commit")),
        MonacoDecoration.blame(4, "alice, 3 days ago: the greeter", Some("**alice** committed `4f2a9c1`\n\nAdd the greeter")),
        MonacoDecoration.blame(5, "bob, yesterday: greeting as a method", Some("**bob** committed `9e03b77`")))).foreach(ids => marks = ids)
    }

    controller.ready.foreach { _ =>
      Future.sequence(files.map { case (uri, text) => controller.createModel(text, "scala", Some(uri)) }).foreach { _ =>
        controller.switchToModel(open()).foreach(_ => decorate(open()))
      }
    }

    val tabs = new Picker(files.map(f => name(f._1)), name(files.head._1))(file => {
      val uri = files.find(f => name(f._1) == file).map(_._1).getOrElse(open())
      open @= uri
      controller.switchToModel(uri).foreach(_ => decorate(uri))
    })
    val dirty = TextView.bound(if (controller.dirty()) "● " + name(open()) + " has unsaved changes" else name(open()) + " saved",
      12.0, if (controller.dirty()) Theme.warning() else Theme.muted())
    val save = MonacoEditor.saveCommand(GalleryScreen.keymap, Chord("S", primary = true), "gallery.save", "Save") {
      controller.markSaved()
      log.say("saved " + name(open()))
    }
    val reveal = new Button("Reveal line 7", () => controller.revealAndHighlight(MonacoRange(7, 1, 7, 40), lines = true), ButtonStyle.Tonal)
    val outline = new Button("Outline", () => controller.trigger("editor.action.quickOutline"), ButtonStyle.Tonal)

    val editor = new MonacoEditor(controller)
    val body = new Container {
      editor.size.width := size.width()
      editor.size.height := size.height() - 120.0
      log.position.top := size.height() - 120.0
      log.size.width := size.width()
      log.size.height @= 120.0
      children ++= List(editor, log)
    }
    new DemoPage(demo, body, List(tabs, dirty, reveal, outline), () => {
      GalleryScreen.keymap.remove(save.id)
      providers.foreach(_.foreach(_.dispose()))
      files.foreach { case (uri, _) => controller.disposeMonacoModel(uri) }
      controller.dispose()
    })
  }
}

package everyui.monaco

import everyui.theme.Palette
import fabric.*
import fabric.rw.*

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** The bridge and the controller against a guest the spec plays: the protocol, not Monaco. */
class MonacoBridgeSpec extends MonacoFxSpec {
  private def answering(method: String, args: Json): Option[Json] = method match {
    case "bridge.init" => Some(obj("version" -> str(Monaco.version)))
    case "never" => None
    case "editor.getValue" => Some(str("from the guest"))
    case "models.getValue" => Some(obj("value" -> str("fetched text"), "versionId" -> num(9)))
    case "editor.deltaDecorations" => Some(arr(str("d1"), str("d2")))
    case _ => Some(Null)
  }

  private def result[T](f: Future[T]): Option[scala.util.Try[T]] = f.value

  "A bridge" should {
    "load Monaco first, holding calls made meanwhile, and give it the themes and providers already given" in {
      var loads = 0
      val guest = fx(new FakeGuest("spec-1", (m, a) => if (m == "bridge.init") { loads += 1; None } else answering(m, a)))
      val bridge = fx(Monaco.bridge(guest))
      fx(MonacoThemes.defineTheme("spec-theme", MonacoTheme.fromPalette(Palette.dark)))
      fx(bridge.attach(guest))
      val early = fx(bridge.call("editor.getValue", obj("editorId" -> str("1"))))
      fx(guest.methods) shouldBe List("bridge.init")
      fx(bridge.isReady) shouldBe false
      early.isCompleted shouldBe false
      fx(guest.reply(0, obj()))
      eventually()(bridge.isReady) shouldBe true
      // the theme first, then the call that waited
      val methods = fx(guest.methods)
      methods.indexOf("editor.defineTheme") should be < methods.indexOf("editor.getValue")
      eventually()(early.isCompleted) shouldBe true
      early.value.get.get.asString shouldBe "from the guest"
      loads shouldBe 1
      fx(bridge.close())
    }

    "answer calls, fail them when nobody answers in time, and fail what is waiting when it closes" in {
      val guest = fx(new FakeGuest("spec-2", answering))
      val bridge = fx(Monaco.bridge(guest))
      fx(bridge.attach(guest))
      eventually()(bridge.isReady) shouldBe true
      // the theme defined before, and any defined since, reached it as it loaded
      fx(guest.methods.contains("bridge.init")) shouldBe true
      val value = fx(bridge.call("editor.getValue"))
      eventually()(value.isCompleted) shouldBe true
      value.value.get.get.asString shouldBe "from the guest"
      Monaco.callTimeout = 0.2
      val lost = fx(bridge.call("never"))
      eventually()(lost.isCompleted) shouldBe true
      lost.value.get.failed.get shouldBe a[MonacoBridgeException]
      Monaco.callTimeout = 15.0
      val waiting = fx(bridge.call("never"))
      fx(bridge.close())
      waiting.value.get.isFailure shouldBe true
      fx(bridge.call("editor.getValue")).value.get.isFailure shouldBe true
    }

    "carry the themes and providers given before a copy of Monaco loads into it as it does" in {
      fx(MonacoThemes.defineTheme("spec-dark", MonacoTheme.fromPalette(Palette.dark)))
      fx(MonacoThemes.setTheme("spec-dark"))
      val registration = fx(MonacoLanguages.registerHoverProvider("scala", MonacoHoverProvider(p =>
        Future.successful(Some(MonacoHover(List("**" + p.value.take(5) + "** at " + p.position.line)))))))
      val guest = fx(new FakeGuest("spec-3", answering))
      val bridge = fx(Monaco.bridge(guest))
      fx(bridge.attach(guest))
      eventually()(bridge.isReady) shouldBe true
      val methods = fx(guest.methods)
      methods should contain allOf ("editor.defineTheme", "editor.setTheme", "languages.registerHoverProvider")
      methods.indexOf("editor.setTheme") should be > methods.indexOf("editor.defineTheme")
      // Monaco asks the provider: the text is fetched, as no editor here holds that version, and the answer sent back
      fx(guest.say("language.hover.request", obj("providerId" -> num(1000), "requestId" -> str("r0"), "uri" -> str("inmemory://x"))))
      val provider = fx(guest.calls.find(_._1 == "languages.registerHoverProvider").get._2("providerId").asInt)
      fx(guest.say("language.hover.request", obj("providerId" -> num(provider), "requestId" -> str("r1"),
        "uri" -> str("inmemory://model/1"), "languageId" -> str("scala"), "versionId" -> num(9), "line" -> num(3),
        "column" -> num(2))))
      eventually()(guest.calls.exists(c => c._1 == "languages.respond" && Wire.string(c._2, "requestId") == "r1")) shouldBe true
      val respond = fx(guest.calls.find(c => c._1 == "languages.respond" && Wire.string(c._2, "requestId") == "r1").get._2)
      respond("result")("contents").asVector.head("value").asString shouldBe "**fetch** at 3"
      // a provider gone answers nothing, so Monaco is not left waiting
      fx(guest.calls.exists(c => c._1 == "languages.respond" && Wire.string(c._2, "requestId") == "r0")) shouldBe true
      fx(registration.value.get.get.dispose())
      fx(bridge.close())
    }
  }

  "A controller" should {
    "keep the text edit by edit, tell the user's changes from its own, and run its actions and commands" in {
      val guest = fx(new FakeGuest("spec-4", answering))
      val bridge = fx(Monaco.bridge(guest))
      fx(bridge.attach(guest))
      eventually()(bridge.isReady) shouldBe true
      val controller = fx(MonacoController(initialValue = "hello", language = "scala"))
      var heard: List[String] = Nil
      var ran: List[String] = Nil
      fx(controller.onDidChangeContent.attach(t => heard = heard :+ t))
      fx(controller.addAction(MonacoAction("spec.action", "Spec action")(id => ran = ran :+ id)))
      val command = fx(controller.addCommand(MonacoKeyMod.CtrlCmd | MonacoKeyCode.KeyK)({ ran = ran :+ "command" }))
      val decorations = fx(controller.deltaDecorations(Nil, List(MonacoDecoration.lines(1, 2))))
      decorations.isCompleted shouldBe false   // waits for its editor
      fx(controller.createOptions("value").asString) shouldBe "hello"
      fx(controller.attach(bridge, obj("editorId" -> str("7"), "uri" -> str("inmemory://model/7"), "versionId" -> num(1),
        "value" -> str("hello"))))
      // what was given before the editor was made reaches it once it is
      eventually()(decorations.isCompleted) shouldBe true
      decorations.value.get.get shouldBe List("d1", "d2")
      fx(guest.methods.count(_ == "editor.addAction")) shouldBe 1   // given as it attached, once
      // the user types: an edit, not the text
      fx(guest.say("editor.contentChange", obj("editorId" -> str("7"), "versionId" -> num(2),
        "changes" -> arr(obj("rangeOffset" -> num(5), "rangeLength" -> num(0), "text" -> str(" world"))))))
      fx(controller.value()) shouldBe "hello world"
      fx(controller.dirty()) shouldBe true
      heard shouldBe List("hello world")
      // the app's own change is kept but not heard as the user's
      fx(guest.say("editor.contentChange", obj("editorId" -> str("7"), "versionId" -> num(3), "external" -> bool(true),
        "changes" -> arr(obj("rangeOffset" -> num(0), "rangeLength" -> num(5), "text" -> str("HELLO"))))))
      fx(controller.value()) shouldBe "HELLO world"
      heard shouldBe List("hello world")
      fx(controller.markSaved())
      fx(controller.dirty()) shouldBe false
      // another editor's events are not this one's
      fx(guest.say("editor.cursorChange", obj("editorId" -> str("8"), "line" -> num(9), "column" -> num(9))))
      fx(controller.position()) shouldBe None
      fx(guest.say("editor.cursorChange", obj("editorId" -> str("7"), "line" -> num(1), "column" -> num(3))))
      fx(controller.position()) shouldBe Some(MonacoPosition(1, 3))
      fx(guest.say("editor.actionInvoked", obj("editorId" -> str("7"), "actionId" -> str("spec.action"))))
      val id = command.value.get.get
      fx(guest.say("editor.commandInvoked", obj("editorId" -> str("7"), "commandId" -> str(id))))
      ran shouldBe List("spec.action", "command")
      // removed, a command is turned off in the guest
      fx(controller.removeCommand(id))
      fx(guest.calls.exists(c => c._1 == "editor.removeCommand" && Wire.string(c._2, "commandId") == id)) shouldBe true
      // detached, calls wait for the next editor; disposed, they fail
      fx(controller.detach())
      val later = fx(controller.revealLine(3))
      later.isCompleted shouldBe false
      fx(controller.dispose())
      later.value.get.isFailure shouldBe true
      fx(bridge.close())
    }

    "say what moved the cursor and the selection, and why, beside the plain position and selection" in {
      val guest = fx(new FakeGuest("spec-5", answering))
      val bridge = fx(Monaco.bridge(guest))
      fx(bridge.attach(guest))
      eventually()(bridge.isReady) shouldBe true
      val controller = fx(MonacoController(initialValue = "a\nb\nc\nd\ne"))
      fx(controller.attach(bridge, obj("editorId" -> str("9"), "uri" -> str("inmemory://model/9"), "versionId" -> num(1),
        "value" -> str("a\nb\nc\nd\ne"))))
      var heard: List[String] = Nil
      var cursors: List[MonacoCursorChange] = Nil
      var selections: List[MonacoSelectionChange] = Nil
      fx {
        controller.onDidChangeCursorPosition.attach(p => heard = heard :+ s"position $p")
        controller.onDidChangeCursor.attach { c =>
          heard = heard :+ s"cursor ${c.source}"
          cursors = cursors :+ c
        }
        controller.onDidChangeCursorSelection.attach(s => heard = heard :+ s"selection ${s.position}")
        controller.onDidChangeSelection.attach(s => selections = selections :+ s)
      }
      def moved(line: Int, column: Int, fields: (String, Json)*): Unit =
        fx(guest.say("editor.cursorChange", obj((List("editorId" -> str("9"), "line" -> num(line), "column" -> num(column)) ++ fields)*)))
      // a click, with a second cursor
      moved(3, 2, "secondaryPositions" -> arr(obj("line" -> num(5), "column" -> num(1))), "source" -> str("mouse"),
        "reason" -> num(3))
      cursors.last shouldBe MonacoCursorChange(MonacoPosition(3, 2), List(MonacoPosition(5, 1)), "mouse",
        MonacoCursorReason.Explicit)
      cursors.last.byUser shouldBe true
      heard shouldBe List("position MonacoPosition(3:2)", "cursor mouse")
      fx(controller.position()) shouldBe Some(MonacoPosition(3, 2))
      // typed; a jump within the file; the app's own move; another copy's edit moving it along; undone
      moved(3, 3, "source" -> str("keyboard"), "reason" -> num(0))
      moved(1, 1, "source" -> str("code.jump"), "reason" -> num(0))
      moved(2, 1, "source" -> str("api"), "reason" -> num(0))
      moved(4, 1, "source" -> str("modelChange"), "reason" -> num(2))
      moved(3, 3, "source" -> str("modelChange"), "reason" -> num(5))
      cursors.drop(1).map(c => (c.source, c.reason, c.byUser, c.isJump)) shouldBe List(
        ("keyboard", MonacoCursorReason.NotSet, true, false),
        ("code.jump", MonacoCursorReason.NotSet, true, true),
        ("api", MonacoCursorReason.NotSet, false, false),
        ("modelChange", MonacoCursorReason.RecoverFromMarkers, false, false),
        ("modelChange", MonacoCursorReason.Undo, true, false))
      cursors.drop(1).map(_.secondaryPositions) shouldBe List.fill(5)(Nil)
      // what does not say why is the app's own
      moved(5, 1)
      cursors.last shouldBe MonacoCursorChange(MonacoPosition(5, 1), Nil, "api", MonacoCursorReason.NotSet)
      // a selection: the plain one still read from the same message, the change with its second cursor and why
      val first = MonacoSelection.at(MonacoPosition(1, 1), MonacoPosition(2, 2))
      val second = MonacoSelection.collapsed(MonacoPosition(4, 1))
      fx(guest.say("editor.selectionChange", Wire.merge(first.json, obj("editorId" -> str("9"),
        "secondarySelections" -> arr(second.json), "source" -> str("editor.action.selectAll"), "reason" -> num(0)))))
      fx(controller.selection()) shouldBe Some(first)
      selections shouldBe List(MonacoSelectionChange(first, List(second), "editor.action.selectAll", MonacoCursorReason.NotSet))
      heard.last shouldBe "selection MonacoPosition(2:2)"
      fx(guest.say("editor.selectionChange", Wire.merge(second.json, obj("editorId" -> str("9"), "source" -> str("modelChange"),
        "reason" -> num(2)))))
      selections.last.byUser shouldBe false
      // another editor's are not this one's
      fx(guest.say("editor.cursorChange", obj("editorId" -> str("8"), "line" -> num(9), "column" -> num(9), "source" -> str("mouse"))))
      cursors should have size 7
      fx(controller.dispose())
      fx(bridge.close())
    }
  }

  "MonacoCursorReason" should {
    "be Monaco's CursorChangeReason by number, anything else NotSet" in {
      (0 to 7).map(MonacoCursorReason.fromWire) shouldBe Vector(MonacoCursorReason.NotSet, MonacoCursorReason.ContentFlush,
        MonacoCursorReason.RecoverFromMarkers, MonacoCursorReason.Explicit, MonacoCursorReason.Paste,
        MonacoCursorReason.Undo, MonacoCursorReason.Redo, MonacoCursorReason.NotSet)
      MonacoCursorChange.byUser("api", MonacoCursorReason.NotSet) shouldBe false
      MonacoCursorChange.byUser("modelChange", MonacoCursorReason.RecoverFromMarkers) shouldBe false
      MonacoCursorChange.byUser("modelChange", MonacoCursorReason.Undo) shouldBe true
      MonacoCursorChange.byUser("keyboard", MonacoCursorReason.ContentFlush) shouldBe false
      MonacoCursorChange.byUser("editor.action.revealDefinition", MonacoCursorReason.NotSet) shouldBe true
    }
  }
}

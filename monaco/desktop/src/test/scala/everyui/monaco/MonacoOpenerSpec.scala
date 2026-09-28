package everyui.monaco

import everyui.ui
import fabric.*

import scala.concurrent.Future

/** Other files: opening them (the app's openers, then the editor's own onOpenLocation) and giving Monaco their models
  * (the resolver), against a guest the spec plays and then in Monaco itself. */
class MonacoOpenerSpec extends MonacoFxSpec {
  "The bridge" should {
    "ask the openers, newest first, then the editor's controller, and answer with whether one took it" in {
      val guest = fx(new FakeGuest("opener-spec", (_, _) => Some(Null)))
      val bridge = fx(Monaco.bridge(guest))
      fx(bridge.attach(guest))
      eventually()(bridge.isReady) shouldBe true
      val controller = fx(MonacoController(initialValue = "x"))
      fx(controller.attach(bridge, obj("editorId" -> str("3"), "uri" -> str("inmemory://model/3"), "versionId" -> num(1),
        "value" -> str("x"))))
      def answer(id: String): Json = fx(guest.calls.filter(c => c._1 == "languages.respond" && Wire.string(c._2, "requestId") == id)
        .last._2.get("result").getOrElse(Null))
      val range = obj("startLine" -> num(4), "startColumn" -> num(2), "endLine" -> num(4), "endColumn" -> num(9))
      // nobody listening: not handled, so Monaco does what it does by itself
      fx(guest.say("editor.open.request", obj("requestId" -> str("o1"), "editorId" -> str("3"), "uri" -> str("file:///B.scala"),
        "range" -> range)))
      answer("o1").asBoolean shouldBe false
      // the editor's own channel
      var opened: List[MonacoLocation] = Nil
      fx(controller.onOpenLocation.attach(l => opened = opened :+ l))
      fx(guest.say("editor.open.request", obj("requestId" -> str("o2"), "editorId" -> str("3"), "uri" -> str("file:///B.scala"),
        "range" -> range)))
      answer("o2").asBoolean shouldBe true
      opened shouldBe List(MonacoLocation("file:///B.scala", MonacoRange(4, 2, 4, 9)))
      // an opener first: one that takes it keeps it from the channel
      var asked: List[String] = Nil
      val opener = fx(MonacoLanguages.registerEditorOpener((uri, _) => { asked = asked :+ uri; uri.endsWith("C.scala") }))
      fx(guest.say("editor.open.request", obj("requestId" -> str("o3"), "editorId" -> str("3"), "uri" -> str("file:///C.scala"))))
      answer("o3").asBoolean shouldBe true
      asked shouldBe List("file:///C.scala")
      opened should have size 1
      fx(opener.value.get.get.dispose())
      // the resolver: a file's text and language, or none
      val resolver = fx(MonacoLanguages.registerModelResolver(uri =>
        Future.successful(if (uri.endsWith("B.scala")) Some(MonacoModelSource("object B", "scala")) else None)))
      eventually()(resolver.isCompleted) shouldBe true
      fx(guest.methods.contains("models.setResolver")) shouldBe true
      fx(guest.say("models.resolve.request", obj("requestId" -> str("r1"), "uri" -> str("file:///B.scala"))))
      eventually()(guest.calls.exists(c => Wire.string(c._2, "requestId") == "r1")) shouldBe true
      answer("r1")("text").asString shouldBe "object B"
      fx(guest.say("models.resolve.request", obj("requestId" -> str("r2"), "uri" -> str("file:///Z.scala"))))
      eventually()(guest.calls.exists(c => Wire.string(c._2, "requestId") == "r2")) shouldBe true
      answer("r2").isNull shouldBe true
      fx(resolver.value.get.get.dispose())
      fx(bridge.close())
    }
  }

  "Monaco in a JavaFX web view" should {
    "go to a definition in a file the editor does not show: its model from the resolver, the app told to open it" in {
      val other = "file:///spec/Other.scala"
      val registrations = fx(Future.sequence(List(
        MonacoLanguages.registerDefinitionProvider("scala", MonacoDefinitionProvider(_ =>
          Future.successful(List(MonacoLocation(other, MonacoRange(2, 7, 2, 12)))))),
        MonacoLanguages.registerModelResolver(uri =>
          Future.successful(if (uri == other) Some(MonacoModelSource("object Other {\n  def thing = 1\n}\n", "scala")) else None))))(using
        scala.collection.BuildFrom.buildFromIterableOps, Monaco.uiThread))
      val controller = fx(MonacoController(initialValue = "object Here {\n  val x = Other.thing\n}\n", language = "scala"))
      var opened: List[MonacoLocation] = Nil
      fx(controller.onOpenLocation.attach(l => opened = opened :+ l))
      val editor = fx {
        val e = new MonacoEditor(controller)
        e.size.width @= 800.0
        e.size.height @= 400.0
        ui.children += e
        e
      }
      eventually(120.0)(controller.isAttached) shouldBe true
      fx(controller.setPosition(MonacoPosition(2, 18)))
      fx(controller.trigger("editor.action.revealDefinition"))
      eventually(15.0)(opened.nonEmpty) shouldBe true
      // Monaco asks to open at the definition's start
      opened.head shouldBe MonacoLocation(other, MonacoRange(2, 7, 2, 7))
      fx(registrations.value.foreach(_.foreach(_.foreach(_.dispose()))))
      fx(editor.dispose())
      fx(controller.dispose())
    }
  }
}

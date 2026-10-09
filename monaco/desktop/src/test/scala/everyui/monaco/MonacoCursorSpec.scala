package everyui.monaco

import everyui.ui

import scala.concurrent.Future

/** What moved the cursor, as Monaco itself says it (JavaFX's web views): the app's own move, its edit, a jump within the
  * file (which the editor makes alone, never asking the app's openers), and on a shared model an edit made in the
  * other editor, relayed into this one's copy, which is never this one's user's move. */
class MonacoCursorSpec extends MonacoFxSpec {
  private def done[T](f: Future[T]): T = {
    eventually(10.0)(f.isCompleted) shouldBe true
    f.value.get.get
  }

  private def editor(controller: MonacoController, left: Double): MonacoEditor = fx {
    val e = new MonacoEditor(controller)
    e.size.width @= 600.0
    e.size.height @= 400.0
    e.position.left @= left
    ui.children += e
    e
  }

  "Monaco in a JavaFX web view" should {
    "say a jump within the file, the app's own moves and its edits, each as such" in {
      val text = "object Here {\n  def thing = 1\n  val x = thing\n}\n"
      var asked: List[String] = Nil
      val registrations = fx(Future.sequence(List(
        MonacoLanguages.registerDefinitionProvider("scala", MonacoDefinitionProvider(p =>
          Future.successful(List(MonacoLocation(p.uri, MonacoRange(2, 7, 2, 12)))))),
        MonacoLanguages.registerEditorOpener((uri, _) => { asked = asked :+ uri; true })))(using
        scala.collection.BuildFrom.buildFromIterableOps, Monaco.uiThread))
      try {
        val controller = fx(MonacoController(initialValue = text, language = "scala"))
        var opened: List[MonacoLocation] = Nil
        var changes: List[MonacoCursorChange] = Nil
        var selections: List[MonacoSelectionChange] = Nil
        fx {
          controller.onOpenLocation.attach(l => opened = opened :+ l)
          controller.onDidChangeCursor.attach(c => changes = changes :+ c)
          controller.onDidChangeSelection.attach(s => selections = selections :+ s)
        }
        val shown = editor(controller, 0.0)
        eventually(120.0)(controller.isAttached) shouldBe true
        done(fx(controller.setPosition(MonacoPosition(3, 12))))
        eventually(10.0)(changes.exists(_.position == MonacoPosition(3, 12))) shouldBe true
        changes.last.source shouldBe "api"
        changes.last.byUser shouldBe false
        // go to definition within the file: Monaco's own, as a jump; neither the opener nor the editor's channel asked
        fx(controller.trigger("editor.action.revealDefinition"))
        eventually(15.0)(changes.exists(_.isJump)) shouldBe true
        val jump = changes.find(_.isJump).get
        jump.position shouldBe MonacoPosition(2, 7)
        jump.source shouldBe "code.jump"
        jump.reason shouldBe MonacoCursorReason.NotSet
        jump.byUser shouldBe true
        eventually(10.0)(selections.exists(_.isJump)) shouldBe true
        selections.find(_.isJump).get.selection.position shouldBe MonacoPosition(2, 7)
        asked shouldBe Nil
        opened shouldBe Nil
        // the app's move after it is the app's again
        done(fx(controller.setPosition(MonacoPosition(1, 1))))
        eventually(10.0)(changes.exists(_.position == MonacoPosition(1, 1))) shouldBe true
        withClue(changes.map(c => s"${c.position} ${c.source} ${c.reason}").mkString(", ") + ": ") {
          changes.filter(_.position == MonacoPosition(1, 1)).map(_.source) shouldBe List("api")
        }
        // the app's edit before the cursor carries it along, as any edit made elsewhere does
        done(fx(controller.setPosition(MonacoPosition(3, 5))))
        val atEdit = fx(changes.size)
        done(fx(controller.executeEdits(List(MonacoRange(1, 1, 1, 1) -> "// top\n"))))
        eventually(10.0)(changes.size > atEdit) shouldBe true
        changes.last.position shouldBe MonacoPosition(4, 5)
        changes.last.source shouldBe "modelChange"
        changes.last.reason shouldBe MonacoCursorReason.RecoverFromMarkers
        changes.last.byUser shouldBe false
        fx(shown.dispose())
        fx(controller.dispose())
      } finally fx(registrations.value.foreach(_.foreach(_.foreach(_.dispose()))))
    }

    "jump within the file whatever its uri holds (an @, a space, a %, a #, words not English, a Windows drive)" in {
      val uris = List("file:///@0/ide/src/main/scala/A.scala", "file:///my dir/A b.scala", "file:///100%25/A.scala",
        "file:///a/A.scala#part", "file:///über/Straße.scala", "file:///C:/Users/x/A.scala", "inmemory://@1/A.scala")
      val text = "object A {\n  def thing = 1\n  val x = thing\n}\n"
      var asked: List[String] = Nil
      val registrations = fx(Future.sequence(List(
        MonacoLanguages.registerDefinitionProvider("scala", MonacoDefinitionProvider(p =>
          Future.successful(List(MonacoLocation(p.uri, MonacoRange(2, 7, 2, 12)))))),
        MonacoLanguages.registerEditorOpener((uri, _) => { asked = asked :+ uri; true })))(using
        scala.collection.BuildFrom.buildFromIterableOps, Monaco.uiThread))
      try {
        val models = fx(uris.map(u => MonacoModels.open(u, text, "scala")))
        val controller = fx(MonacoController.forModel(models.head))
        var changes: List[MonacoCursorChange] = Nil
        fx(controller.onDidChangeCursor.attach(c => changes = changes :+ c))
        val shown = editor(controller, 0.0)
        eventually(120.0)(controller.isAttached) shouldBe true
        models.foreach { m =>
          withClue(m.uri + ": ") {
            done(fx(controller.switchToModel(m.uri)))
            eventually(10.0)(controller.uri() == m.uri) shouldBe true
            done(fx(controller.setPosition(MonacoPosition(3, 12))))
            eventually(10.0)(changes.lastOption.exists(_.position == MonacoPosition(3, 12))) shouldBe true
            fx(controller.trigger("editor.action.revealDefinition"))
            eventually(15.0)(changes.lastOption.exists(_.isJump)) shouldBe true
            changes.last.position shouldBe MonacoPosition(2, 7)
            done(fx(controller.getPosition())) shouldBe Some(MonacoPosition(2, 7))
          }
        }
        asked shouldBe Nil
        // the same with the editor's own model, made by createModel
        val own = "file:///@0/own/B.scala"
        done(fx(controller.createModel(text, "scala", Some(own))))
        done(fx(controller.switchToModel(own)))
        done(fx(controller.setPosition(MonacoPosition(3, 12))))
        eventually(10.0)(changes.lastOption.exists(_.position == MonacoPosition(3, 12))) shouldBe true
        fx(controller.trigger("editor.action.revealDefinition"))
        eventually(15.0)(changes.lastOption.exists(_.isJump)) shouldBe true
        changes.last.position shouldBe MonacoPosition(2, 7)
        asked shouldBe Nil
        fx(shown.dispose())
        fx(controller.dispose())
        fx(uris.foreach(MonacoModels.close))
      } finally fx(registrations.value.foreach(_.foreach(_.foreach(_.dispose()))))
    }

    "say an edit made in another editor on a shared model, relayed here, as the model's change" in {
      val text = "object A {\n  val x = 1\n  val y = 2\n}\n"
      val model = fx(MonacoModels.open("file:///cursor/A.scala", text, "scala"))
      val (one, two) = fx(MonacoController.forModel(model) -> MonacoController.forModel(model))
      val left = editor(one, 0.0)
      val right = editor(two, 620.0)
      eventually(120.0)(one.isAttached && two.isAttached) shouldBe true
      var changes: List[MonacoCursorChange] = Nil
      fx(two.onDidChangeCursor.attach(c => changes = changes :+ c))
      done(fx(two.setPosition(MonacoPosition(3, 5))))
      eventually(10.0)(changes.exists(_.position == MonacoPosition(3, 5))) shouldBe true
      val at = fx(changes.size)
      // typed in the first; the second's cursor, after it, moves down a line
      done(fx(one.executeEdits(List(MonacoRange(1, 1, 1, 1) -> "// shared\n"))))
      eventually(10.0)(changes.size > at) shouldBe true
      val relayed = changes.drop(at)
      relayed.map(_.position).last shouldBe MonacoPosition(4, 5)
      relayed.foreach { c =>
        c.source shouldBe "modelChange"
        c.reason shouldBe MonacoCursorReason.RecoverFromMarkers
        c.byUser shouldBe false
      }
      fx(left.dispose())
      fx(right.dispose())
      fx(one.dispose())
      fx(two.dispose())
      fx(MonacoModels.close(model.uri))
    }
  }
}

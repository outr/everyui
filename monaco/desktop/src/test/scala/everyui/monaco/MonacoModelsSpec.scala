package everyui.monaco

import fabric.*

/** The model registry against guests the spec plays: copies made as editors come to show a model, its text and dirty
  * state kept once for every editor, its life, and edits relayed between copies where each editor has a Monaco of its
  * own. */
class MonacoModelsSpec extends MonacoFxSpec {
  private def answering(outOfStep: () => Boolean = () => false)(method: String, args: Json): Option[Json] = method match {
    case "bridge.init" => Some(obj("version" -> str(Monaco.version)))
    case "models.open" => Some(obj("uri" -> args("uri"), "versionId" -> num(1), "value" -> args("value")))
    case "models.applyEdits" => Some(obj("applied" -> bool(!outOfStep())))
    case _ => Some(Null)
  }

  private def editorOn(guest: FakeGuest, editorId: String): (MonacoBridge, MonacoController) = {
    val bridge = fx(Monaco.bridge(guest))
    fx(bridge.attach(guest))
    eventually()(bridge.isReady) shouldBe true
    val controller = fx(MonacoController(initialValue = "own"))
    fx(controller.attach(bridge, obj("editorId" -> str(editorId), "uri" -> str("inmemory://model/" + editorId),
      "versionId" -> num(1), "value" -> str("own"))))
    bridge -> controller
  }

  private def shows(guest: FakeGuest, editorId: String, uri: String, value: String): Unit =
    fx(guest.say("editor.modelChange", obj("editorId" -> str(editorId), "uri" -> str(uri), "versionId" -> num(1),
      "value" -> str(value))))

  private def typed(guest: FakeGuest, uri: String, offset: Int, text: String, extra: (String, Json)*): Unit =
    fx(guest.say("model.contentChange", obj((List("uri" -> str(uri), "versionId" -> num(2),
      "changes" -> arr(obj("rangeOffset" -> num(offset), "rangeLength" -> num(0), "text" -> str(text)))) ++ extra)*)))

  "The model registry" should {
    "open a model once, copy it into a Monaco as an editor comes to show it, share its dirty state, and keep it while shown" in {
      val guest = fx(new FakeGuest("models-1", answering()))
      val (bridge, controller) = editorOn(guest, "1")
      val uri = "file:///spec/A.scala"
      val model = fx(MonacoModels.open(uri, "alpha", "scala"))
      fx(MonacoModels.open(uri, "other text")) should be theSameInstanceAs model
      fx(model.value()) shouldBe "alpha"
      // made in the Monaco already loaded, at once, from the text given
      eventually()(guest.calls.exists(c => c._1 == "models.open" && Wire.string(c._2, "uri") == uri)) shouldBe true
      fx(guest.calls.find(_._1 == "models.open").get._2("value").asString) shouldBe "alpha"
      val switched = fx(controller.switchToModel(uri))
      eventually()(guest.methods.contains("editor.setModel")) shouldBe true
      fx(guest.methods.count(_ == "models.open")) shouldBe 1   // not made twice
      shows(guest, "1", uri, "alpha")
      eventually()(switched.isCompleted) shouldBe true
      fx(controller.shownModel()) shouldBe Some(model)
      // an edit in any editor is the model's: its text, and dirty for every editor on it
      typed(guest, uri, 5, "!")
      fx(model.value()) shouldBe "alpha!"
      fx(model.dirty()) shouldBe true
      fx(controller.dirty()) shouldBe true
      fx(MonacoModels.dirty(uri)) shouldBe true
      fx(controller.markSaved())
      fx(model.dirty()) shouldBe false
      fx(model.saved()) shouldBe "alpha!"
      // the app lets go, but an editor still shows it: kept
      var gone: List[String] = Nil
      fx(MonacoModels.onDidDispose.attach(u => gone = gone :+ u))
      fx(MonacoModels.close(uri))
      fx(MonacoModels.get(uri)) shouldBe Some(model)
      fx(guest.methods.contains("models.close")) shouldBe false
      // the editor moves away: it goes, in Monaco too
      shows(guest, "1", "inmemory://model/1", "own")
      fx(MonacoModels.get(uri)) shouldBe None
      fx(model.isDisposed) shouldBe true
      gone shouldBe List(uri)
      fx(guest.calls.exists(c => c._1 == "models.close" && Wire.string(c._2, "uri") == uri)) shouldBe true
      fx(controller.shownModel()) shouldBe None
      fx(controller.dispose())
      fx(bridge.close())
    }

    "start an editor on a model, and let a model no editor shows go as the app closes it" in {
      val guest = fx(new FakeGuest("models-2", answering()))
      val bridge = fx(Monaco.bridge(guest))
      fx(bridge.attach(guest))
      eventually()(bridge.isReady) shouldBe true
      val model = fx(MonacoModels.open("file:///spec/B.txt", "bee"))
      val controller = fx(MonacoController.forModel(model))
      fx(controller.value()) shouldBe "bee"
      fx(controller.startModel) shouldBe Some(model)
      fx(controller.attach(bridge, obj("editorId" -> str("5"), "uri" -> str(model.uri), "versionId" -> num(1),
        "value" -> str("bee"))))
      fx(controller.shownModel()) shouldBe Some(model)
      // detached (its editor gone), nothing shows it, but the app still holds it
      fx(controller.detach())
      fx(MonacoModels.get(model.uri)) shouldBe Some(model)
      fx(MonacoModels.close(model.uri))
      fx(MonacoModels.get(model.uri)) shouldBe None
      fx(controller.dispose())
      fx(bridge.close())
    }

    "copy an edit made in one Monaco into the others, and give a copy out of step the whole text" in {
      var outOfStep = false
      val left = fx(new FakeGuest("models-3", answering()))
      val right = fx(new FakeGuest("models-4", answering(() => outOfStep)))
      val (leftBridge, leftEditor) = editorOn(left, "1")
      val (rightBridge, rightEditor) = editorOn(right, "1")
      val uri = "file:///spec/C.scala"
      val model = fx(MonacoModels.open(uri, "hello", "scala"))
      fx(leftEditor.switchToModel(uri))
      fx(rightEditor.switchToModel(uri))
      eventually()(left.methods.contains("editor.setModel") && right.methods.contains("editor.setModel")) shouldBe true
      shows(left, "1", uri, "hello")
      shows(right, "1", uri, "hello")
      // typed on the left: kept, and made on the right as an edit of the text it had
      typed(left, uri, 5, " world")
      fx(model.value()) shouldBe "hello world"
      val relay = fx(right.calls.filter(_._1 == "models.applyEdits").last._2)
      relay("length").asInt shouldBe 5
      relay("changes").asVector.head("text").asString shouldBe " world"
      fx(left.methods.contains("models.applyEdits")) shouldBe false
      // the right's own report of the copied edit is not taken again
      typed(right, uri, 5, " world", "relayed" -> bool(true))
      fx(model.value()) shouldBe "hello world"
      // an undo on the right is an edit like any other there, copied to the left
      fx(right.say("model.contentChange", obj("uri" -> str(uri), "versionId" -> num(4), "undo" -> bool(true),
        "changes" -> arr(obj("rangeOffset" -> num(5), "rangeLength" -> num(6), "text" -> str(""))))))
      fx(model.value()) shouldBe "hello"
      fx(model.dirty()) shouldBe false
      fx(left.calls.filter(_._1 == "models.applyEdits").last._2("length").asInt) shouldBe 11
      // a copy out of step is given the whole text
      outOfStep = true
      typed(left, uri, 0, "> ")
      eventually()(right.calls.exists(c => c._1 == "models.setValue" && Wire.string(c._2, "value") == "> hello")) shouldBe true
      fx(right.calls.filter(_._1 == "models.setValue").last._2("relayed").asBoolean) shouldBe true
      // the app's text goes to one copy, which tells the others; markers go to every copy
      val leftSets = fx(left.methods.count(_ == "models.setValue"))
      val rightSets = fx(right.methods.count(_ == "models.setValue"))
      fx(model.setValue("replaced"))
      eventually()(left.methods.count(_ == "models.setValue") + right.methods.count(_ == "models.setValue") == leftSets + rightSets + 1) shouldBe true
      fx(model.setMarkers(List(MonacoMarker(MonacoRange(1, 1, 1, 3), MonacoMarkerSeverity.Error, "no"))))
      eventually()(left.methods.contains("markers.set") && right.methods.contains("markers.set")) shouldBe true
      // one Monaco gone (its web view closed): its copy with it, the other kept
      fx(rightEditor.dispose())
      fx(rightBridge.close())
      fx(model.copies.size) shouldBe 1
      fx(leftEditor.dispose())
      fx(MonacoModels.close(uri))
      fx(MonacoModels.get(uri)) shouldBe None
      fx(leftBridge.close())
    }
  }
}

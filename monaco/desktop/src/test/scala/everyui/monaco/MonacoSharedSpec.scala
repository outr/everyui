package everyui.monaco

import everyui.ui

import scala.concurrent.Future

/** Two editors on one model, in Monaco itself (JavaFX's web views, a Monaco each, the model copied between them): an
  * edit in one seen in the other, undone from the other, and each editor's own cursor, kept as it moves to another
  * model and back. */
class MonacoSharedSpec extends MonacoFxSpec {
  private def done[T](f: Future[T]): T = {
    eventually(10.0)(f.isCompleted) shouldBe true
    f.value.get.get
  }

  "Two editors on one model" should {
    "share its text, undo and dirty state, and keep their own cursors" in {
      val text = "object A {\n  val x = 1\n  val y = 2\n}\n"
      val a = fx(MonacoModels.open("file:///shared/A.scala", text, "scala"))
      val b = fx(MonacoModels.open("file:///shared/B.scala", "object B\n", "scala"))
      val (left, right) = fx {
        val l = MonacoEditor.of(a)
        val r = MonacoEditor.of(a)
        List(l, r).zipWithIndex.foreach { case (e, i) =>
          e.size.width @= 600.0
          e.size.height @= 400.0
          e.position.left @= i * 620.0
          ui.children += e
        }
        l -> r
      }
      val (one, two) = (left.controller, right.controller)
      withClue("both editors made on the model: ") {
        eventually(120.0)(one.isAttached && two.isAttached) shouldBe true
      }
      fx(one.uri()) shouldBe a.uri
      fx(two.uri()) shouldBe a.uri
      fx(two.value()) shouldBe text
      // typed in one, seen in the other, and dirty for both
      done(fx(one.executeEdits(List(MonacoRange(1, 1, 1, 1) -> "// shared\n"))))
      eventually(10.0)(two.value().startsWith("// shared\n")) shouldBe true
      fx(a.value()) shouldBe "// shared\n" + text
      done(fx(two.getValue())) shouldBe "// shared\n" + text
      fx(one.dirty()) shouldBe true
      fx(two.dirty()) shouldBe true
      // undone from the other
      done(fx(two.undo()))
      eventually(10.0)(one.value() == text) shouldBe true
      done(fx(one.getValue())) shouldBe text
      fx(a.dirty()) shouldBe false
      // each its own cursor
      done(fx(one.setPosition(MonacoPosition(3, 5))))
      done(fx(two.setPosition(MonacoPosition(2, 3))))
      done(fx(one.getPosition())) shouldBe Some(MonacoPosition(3, 5))
      done(fx(two.getPosition())) shouldBe Some(MonacoPosition(2, 3))
      // to another model and back: its cursor where it was, the other editor's untouched
      done(fx(one.switchToModel(b.uri)))
      eventually(10.0)(one.uri() == b.uri && one.value() == "object B\n") shouldBe true
      fx(one.shownModel()) shouldBe Some(b)
      done(fx(one.switchToModel(a.uri)))
      eventually(10.0)(one.uri() == a.uri) shouldBe true
      done(fx(one.getPosition())) shouldBe Some(MonacoPosition(3, 5))
      done(fx(two.getPosition())) shouldBe Some(MonacoPosition(2, 3))
      // the editors gone and the app letting go, the models go
      fx(left.dispose())
      fx(right.dispose())
      fx(MonacoModels.close(a.uri))
      fx(MonacoModels.close(b.uri))
      fx(MonacoModels.all()) shouldBe empty
    }
  }
}

package everyui.monaco

import everyui.ui
import fabric.*

/** Monaco itself, in JavaFX's web view: loaded from the classpath through the embed server, a value set, and read
  * back from the editor, and an edit followed on this side. */
class MonacoSmokeSpec extends MonacoFxSpec {
  "Monaco in a JavaFX web view" should {
    "load, take a value, give it back, and report edits as edits" in {
      val controller = fx(MonacoController(initialValue = "object A", language = "scala"))
      val editor = fx {
        val e = new MonacoEditor(controller)
        e.size.width @= 800.0
        e.size.height @= 500.0
        ui.children += e
        e
      }
      withClue("Monaco loaded and the editor was made: ") {
        eventually(120.0)(controller.isAttached) shouldBe true
      }
      fx(controller.value()) shouldBe "object A"
      fx(controller.setValue("object Hello {\n  def x = 1\n}\n"))
      val read = fx(controller.getValue())
      eventually(10.0)(read.isCompleted) shouldBe true
      read.value.get.get shouldBe "object Hello {\n  def x = 1\n}\n"
      // an edit made in the editor comes back as an edit, and this side's copy matches the editor's own
      fx(controller.executeEdits(List(MonacoRange(2, 11, 2, 12) -> "42")))
      eventually(10.0)(controller.value().contains("def x = 42")) shouldBe true
      val again = fx(controller.getValue())
      eventually(10.0)(again.isCompleted) shouldBe true
      again.value.get.get shouldBe fx(controller.value())
      // a decoration and a marker are taken
      val ids = fx(controller.deltaDecorations(Nil, List(MonacoDecoration.lines(1, 1))))
      eventually(10.0)(ids.isCompleted) shouldBe true
      ids.value.get.get should have size 1
      fx(editor.dispose())
      fx(controller.dispose())
    }
  }
}

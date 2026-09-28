package everyui.monaco.gallery

import everyui.component.{Button, ButtonStyle}
import everyui.monaco.*
import everyui.monaco.Monaco.ui
import reactify.*

/** Markers (error, warning, info) and decorations: a lit line with a glyph in the margin, and a range with a hover. */
object DiagnosticsDemo {
  val code: String =
    """// Hover the squiggles to see messages.
      |// The blue dot in the margin is a decoration (glyphMarginClassName).
      |
      |void main() {
      |  final unusedVariable = 42;         // info: "unused"
      |  String name = null;                // error: null assigned to non-null
      |  var typo = "hello wrld";           // warning: spelling
      |  print(name);
      |  print(typo);
      |
      |  // The range below has an inline decoration class.
      |  final highlighted = 'this phrase is decorated';
      |  print(highlighted);
      |}
      |""".stripMargin

  def apply(demo: Demo): DemoPage = {
    MonacoThemes.addStyles("gallery-diagnostics", ".demo-info-line { background: rgba(88, 166, 255, 0.12); }")
    val controller = MonacoController(initialValue = code, language = "dart",
      options = MonacoEditorOptions(glyphMargin = Some(true), bracketPairColorization = Some(true)))
    val shown = Var(false)
    var decorations: List[String] = Nil

    def apply(): Unit = {
      controller.setModelMarkers(List(
        MonacoMarker(MonacoRange(5, 9, 5, 23), MonacoMarkerSeverity.Info, "Unused variable \"unusedVariable\".",
          source = Some("demo-analyzer"), code = Some("unused_local"), tags = List(MonacoMarkerTag.Unnecessary)),
        MonacoMarker(MonacoRange(6, 17, 6, 21), MonacoMarkerSeverity.Error, "Cannot assign null to non-nullable String.",
          source = Some("demo-analyzer"), code = Some("nullable_assignment")),
        MonacoMarker(MonacoRange(7, 15, 7, 29), MonacoMarkerSeverity.Warning, "Possible typo: \"wrld\", did you mean \"world\"?",
          source = Some("demo-spellcheck"))), owner = "demo")
      controller.deltaDecorations(decorations, List(
        MonacoDecoration(MonacoRange(5, 1, 5, 1), MonacoDecorationOptions(isWholeLine = Some(true),
          className = Some("demo-info-line"), glyphMarginClassName = Some("everyui-monaco-info-glyph"),
          glyphMarginHoverMessage = Some("An info line"), overviewRuler = Some(MonacoOverviewRuler("#58a6ff")))),
        MonacoDecoration.highlight(MonacoRange(12, 23, 12, 49), hover = Some("A whole-range decoration with a hover.")))
      ).foreach { ids =>
        decorations = ids
        shown @= true
      }
    }

    def clear(): Unit = {
      controller.clearModelMarkers("demo")
      controller.deltaDecorations(decorations, Nil).foreach { _ =>
        decorations = Nil
        shown @= false
      }
    }

    controller.ready.foreach(_ => apply())
    val toggle = new Button("Clear", () => if (shown()) clear() else apply(), ButtonStyle.Tonal)
    toggle.label := (if (shown()) "Clear" else "Apply")
    new DemoPage(demo, new MonacoEditor(controller), List(toggle), () => controller.dispose())
  }
}

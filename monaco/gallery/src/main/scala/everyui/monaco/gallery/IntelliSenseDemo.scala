package everyui.monaco.gallery

import everyui.component.{Button, ButtonStyle}
import everyui.monaco.*
import everyui.monaco.Monaco.ui

import scala.concurrent.Future

/** Completion and hover worked out by the app: each request goes to the app and back, answered asynchronously. */
object IntelliSenseDemo {
  val code: String =
    """// Try it:
      |//   Ctrl+Space: suggestions
      |//   Type `pr`, `Fu`, `for`: suggestions appear
      |//   Hover "print", "Future", "main", "Monaco": documentation appears
      |//
      |// Every answer is worked out by the app: MonacoLanguages carries the request to it and the answer back.
      |
      |import 'dart:async';
      |
      |Future<void> main() async {
      |  print('Hello from Monaco!');
      |  await Future<void>.delayed(const Duration(seconds: 1));
      |}
      |""".stripMargin

  val items: List[MonacoCompletionItem] = List(
    MonacoCompletionItem("print", MonacoCompletionKind.Function, "print(${1:value});", Some("void print(Object? value)"),
      Some("Writes the string form of `value` to stdout."), insertTextRules = MonacoInsertTextRule.InsertAsSnippet),
    MonacoCompletionItem("Future.delayed", MonacoCompletionKind.Constructor,
      "Future<void>.delayed(const Duration(milliseconds: ${1:100}));", Some("Future<T>.delayed(Duration, [FutureOr<T> Function()?])"),
      Some("A future that completes after the given duration."), insertTextRules = MonacoInsertTextRule.InsertAsSnippet),
    MonacoCompletionItem("for-loop", MonacoCompletionKind.Snippet, "for (var ${1:i} = 0; $1 < ${2:length}; $1++) {\n  $0\n}",
      Some("for loop"), Some("A classic C-style for loop."), insertTextRules = MonacoInsertTextRule.InsertAsSnippet),
    MonacoCompletionItem("for-in", MonacoCompletionKind.Snippet, "for (final ${1:item} in ${2:items}) {\n  $0\n}",
      Some("for-in loop"), insertTextRules = MonacoInsertTextRule.InsertAsSnippet),
    MonacoCompletionItem("everyui_monaco", MonacoCompletionKind.Module, "everyui_monaco", Some("package"),
      Some("This module: Monaco as an EveryUI component.")))

  val docs: Map[String, String] = Map(
    "print" -> "**print(value)**: writes the value to stdout.\n\n_From the demo's hover provider._",
    "Future" -> "**Future&lt;T&gt;**: a computation that may not have completed yet.",
    "main" -> "**main()**: where every Dart program starts.",
    "Monaco" -> "**Monaco Editor**: VS Code's editor, in EveryUI through `everyui-monaco`.")

  /** The word at `column` (1-based) of line `line` of `text`. */
  def wordAt(text: String, line: Int, column: Int): String = {
    val lines = text.split("\n", -1)
    val l = if (line - 1 < lines.length && line >= 1) lines(line - 1) else ""
    def word(i: Int): Boolean = i >= 0 && i < l.length && (l(i).isLetterOrDigit || l(i) == '_')
    var start = math.min(math.max(0, column - 1), l.length)
    var end = start
    while (word(start - 1)) start -= 1
    while (word(end)) end += 1
    l.substring(start, end)
  }

  def apply(demo: Demo): DemoPage = {
    val controller = MonacoController(initialValue = code, language = "dart",
      options = MonacoEditorOptions(quickSuggestions = Some(true), bracketPairColorization = Some(true)))
    val registrations = Future.sequence(List(
      MonacoLanguages.registerCompletionProvider("dart", MonacoCompletionProvider(".")(_ =>
        Future.successful(MonacoCompletionList(items)))),
      MonacoLanguages.registerHoverProvider("dart", MonacoHoverProvider(p =>
        Future.successful(docs.get(wordAt(p.value, p.position.line, p.position.column)).map(d => MonacoHover(List(d))))))))
    val trigger = new Button("Trigger", () => controller.trigger("editor.action.triggerSuggest"), ButtonStyle.Tonal)
    new DemoPage(demo, new MonacoEditor(controller), List(trigger), () => {
      registrations.foreach(_.foreach(_.dispose()))
      controller.dispose()
    })
  }
}

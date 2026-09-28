package everyui.monaco.gallery

import everyui.monaco.*

/** The same editor switched between languages, each with a sample of its own. */
object LanguagesDemo {
  val samples: List[(String, String)] = List(
    "dart" ->
      """import 'dart:async';
        |
        |Future<int> fibonacci(int n) async {
        |  if (n < 2) return n;
        |  return await fibonacci(n - 1) + await fibonacci(n - 2);
        |}
        |
        |void main() async {
        |  for (var i = 0; i < 10; i++) {
        |    print('fib($i) = ${await fibonacci(i)}');
        |  }
        |}
        |""".stripMargin,
    "javascript" ->
      """const fib = n => n < 2 ? n : fib(n - 1) + fib(n - 2);
        |
        |for (let i = 0; i < 10; i++) {
        |  console.log(`fib(${i}) = ${fib(i)}`);
        |}
        |""".stripMargin,
    "typescript" ->
      """function fibonacci(n: number): number {
        |  return n < 2 ? n : fibonacci(n - 1) + fibonacci(n - 2);
        |}
        |
        |[...Array(10).keys()].forEach((i) => {
        |  console.log(`fib(${i}) = ${fibonacci(i)}`);
        |});
        |""".stripMargin,
    "python" ->
      """def fibonacci(n: int) -> int:
        |    return n if n < 2 else fibonacci(n - 1) + fibonacci(n - 2)
        |
        |for i in range(10):
        |    print(f"fib({i}) = {fibonacci(i)}")
        |""".stripMargin,
    "json" ->
      """{
        |  "name": "everyui-monaco",
        |  "bundledMonaco": "0.57.0",
        |  "platforms": ["web", "android", "desktop"],
        |  "features": ["intellisense", "diff editor", "themes"],
        |  "nested": { "deep": { "value": 42 } }
        |}
        |""".stripMargin,
    "html" ->
      """<!DOCTYPE html>
        |<html>
        |  <head>
        |    <title>Hello</title>
        |    <style>
        |      body { font-family: system-ui; background: #111; color: #eee; }
        |      h1 { color: #58a6ff; }
        |    </style>
        |  </head>
        |  <body>
        |    <h1>Hello Monaco!</h1>
        |    <p>Rendered by EveryUI and everyui-monaco.</p>
        |  </body>
        |</html>
        |""".stripMargin,
    "markdown" ->
      """# everyui-monaco
        |
        |Monaco as an **EveryUI** component.
        |
        |- The web, Android and the desktop
        |- IntelliSense providers
        |- The diff editor
        |
        |```scala
        |val code = MonacoController(initialValue = "hello", language = "scala")
        |```
        |""".stripMargin,
    "sql" ->
      """-- Find top-selling products by category
        |WITH sales AS (
        |  SELECT p.category, p.name, SUM(oi.quantity * oi.unit_price) AS revenue
        |  FROM order_items oi
        |  INNER JOIN products p ON p.id = oi.product_id
        |  WHERE oi.created_at >= NOW() - INTERVAL '30 days'
        |  GROUP BY p.category, p.name
        |)
        |SELECT category, name, revenue FROM sales ORDER BY category, revenue DESC LIMIT 20;
        |""".stripMargin,
    "yaml" ->
      """name: everyui-monaco
        |version: 0.1.0
        |platforms:
        |  - web
        |  - android
        |  - desktop
        |monaco:
        |  version: 0.57.0
        |""".stripMargin,
    "css" ->
      """.monaco-editor {
        |  --accent: #58a6ff;
        |  --bg: #0d1117;
        |}
        |
        |.monaco-editor:hover {
        |  outline: 1px solid var(--accent);
        |  transition: outline 120ms ease-out;
        |}
        |""".stripMargin)

  def apply(demo: Demo): DemoPage = {
    val controller = MonacoController(initialValue = samples.head._2, language = samples.head._1)
    val editor = new MonacoEditor(controller)
    val picker = new Picker(samples.map(_._1), samples.head._1, 560.0)(language => {
      controller.setValue(samples.find(_._1 == language).map(_._2).getOrElse(""))
      controller.setLanguage(language)
    })
    new DemoPage(demo, editor, List(picker), () => controller.dispose())
  }
}

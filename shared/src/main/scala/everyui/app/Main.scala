package everyui.app

import everyui.example.ExampleApp

/** The single entry point both backends emit.
  *
  * Flip `demo` to choose what runs; the Roku compiler collects `@main` and
  * calls it from the generated scene, and Scala.js links it as the module
  * initializer, so there can only be one.
  */
@main def main(): Unit =
  // "animation" — youi's AnimationExample, in youi's example app.
  // "hello"     — the original seam proof plus the frame-rate probe.
  // "bench"     — reactify's per-operation cost.
  // "easings"   — every easing curve as numbers.
  val demo = "animation"
  if demo == "hello" then HelloWorld.run()
  else if demo == "bench" then ReactifyBench.run()
  else if demo == "easings" then EasingTable.run()
  else if demo == "youibench" then YouiBench.run()
  else ExampleApp.main()

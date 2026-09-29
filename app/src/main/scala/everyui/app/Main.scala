package everyui.app

import everyui.example.ExampleApp
import everyui.render.Platform

/** The single entry point every backend runs.
  *
  * `demo=<name>` on the launch (`?demo=` in the page URL, `--demo=` on the desktop and tvOS, `--es demo` on
  * Android, `demo.txt` in a Roku sideload — `Platform.launchArg`) picks what runs; the Roku compiler collects
  * `@main` and calls it from the generated scene, and Scala.js links it as the module initializer, so there can
  * only be one.
  *
  *   animation  youi's AnimationExample, in youi's example app (the default)
  *   durable    the network module's example: a durable socket to `socket=<url>` (tools/check.sh)
  *   input      the input model: focus, the arrows, Enter and the pointer (`probe=1` drives it itself)
  *   hello      the original seam proof plus the frame-rate probe
  *   bench      reactify's per-operation cost
  *   easings    every easing curve as numbers
  *   blur       blur as the screensaver draws it: a photo over its blurred copy, and a blur coming clear
  *   hidden     what is made or changed in a hidden panel has its size when the panel shows
  *   autofill   a sign-in and a contact form as autofill and password managers see them
  *   rtlscroll  a rail in a right-to-left language, as the web's own scroll holds it
  *   rows       a table of `n=` rows built and then grown a row at a time, timed
  *   picker     a filtering picker narrowed by segment chips (a model picker by provider); not on a Roku or an Apple TV
  *              (app/full: FullDemos)
  */
@main def main(): Unit = {
  val demo = Platform.launchArg("demo")
  if (demo == "hello") HelloWorld.run()
  else if (demo == "bench") ReactifyBench.run()
  else if (demo == "easings") EasingTable.run()
  else if (demo == "youibench") YouiBench.run()
  else if (demo == "durable") DurableDemo.run()
  else if (demo == "input") InputDemo.run()
  else if (demo == "widgets") WidgetsDemo.run()
  else if (demo == "blur") BlurDemo.run()
  else if (demo == "hidden") HiddenDemo.run()
  else if (demo == "autofill") AutofillDemo.run()
  else if (demo == "rtlscroll") RtlScrollDemo.run()
  else if (demo == "rows") RowsDemo.run()
  else if (!FullDemos.run(demo)) ExampleApp.main()
}

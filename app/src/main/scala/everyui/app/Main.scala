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
  *   composer   a chat's composer: suggestions, attachments, @-mentions and /commands, a reply faked as busy
  *   chat       a group room in a ChatView: streaming replies, tool calls, reasoning, 2000 earlier entries (`theme=light`,
  *              `rtl=1`, `offline=1`, `probe=1`)
  *   busy       the app at work (AppBusy): idle, busy, a named progress, round and round (`title=1`: in the title too)
  *   markdown   Markdown drawn by EveryUI: every kind of block, and the same document streamed in (`stream=1`, `at=`)
  *   richlist   a conversation list (RichList): 5000 in three sections, with unread counts, pins, a search and menus
  *              (`theme=light`, `collapsed=archive`)
  *   letterspacing Material's type scale drawn plain and letter-spaced, each over a bar as wide as it measures
  *   italic     an empty list's and a search's labels upright and slanted, a paragraph, buttons and a field slanted
  *   readonly   read-only fields beside an editable one: a share link, a note and a key to choose and copy but not change
  *   colors     the colour picker: on the page with alpha and presets, and in a dialog (`dialog=1`)
  *   picker     a filtering picker narrowed by segment chips (a model picker by provider); not on a Roku or an Apple TV
  *              (app/full: FullDemos)
  *   voice      a microphone with a level meter, played back, and a tone that Stop cuts off (`probe=1` runs it itself); not
  *              on a Roku or an Apple TV (app/full: FullDemos)
  *   platform   the platform's services: saving a file, the clipboard, passkeys (`rp=`), push notifications (`vapid=`),
  *              the location; not on a Roku or an Apple TV (app/full: FullDemos)
  *   zoom       a project map on a canvas that pans and zooms (ZoomCanvas): semantic zoom over 2,578 boxes, and a
  *              drawing whose routers are picked by a click; not on a Roku or an Apple TV (app/full: FullDemos)
  *   graph      a project's module dependencies on a GraphView: layered, a broken rule in red, planned modules dashed,
  *              laid out again as a module is added (`flow=down`, `big=<n>`); not on a Roku or an Apple TV
  *              (app/full: FullDemos)
  *   forcegraph words seen together on a GraphView laid out by forces: a few hundred, draggable, with words added and
  *              removed (`n=<words>`, `measure=1`); not on a Roku or an Apple TV (app/full: FullDemos)
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
  else if (demo == "composer") ComposerDemo.run()
  else if (demo == "chat") ChatDemo.run()
  else if (demo == "busy") BusyDemo.run()
  else if (demo == "colors") ColorsDemo.run()
  else if (demo == "markdown") MarkdownDemo.run()
  else if (demo == "richlist") RichListDemo.run()
  else if (demo == "letterspacing") LetterSpacingDemo.run()
  else if (demo == "italic") ItalicDemo.run()
  else if (demo == "readonly") ReadOnlyDemo.run()
  else if (!FullDemos.run(demo)) ExampleApp.main()
}

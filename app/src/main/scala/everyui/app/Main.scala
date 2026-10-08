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
  *              `rtl=1`, `offline=1`, `probe=1`, `author=log`)
  *   busy       the app at work (AppBusy): idle, busy, a named progress, round and round (`title=1`: in the title too)
  *   markdown   Markdown drawn by EveryUI: every kind of block, and the same document streamed in (`stream=1`, `at=`)
  *   logs       a pipeline's log in a LogPanel: ANSI colours, a stack trace, a progress bar drawn over itself, search,
  *              filters and level chips (`lines=`, `rate=`, `stream=0`, `wrap=1`, `theme=light`, `probe=1`), or the example
  *              server's log over a durable socket (`socket=ws://host:8731/logs`)
  *   richlist   a conversation list (RichList): 5000 in three sections, with unread counts, pins, a search and menus
  *              (`theme=light`, `collapsed=archive`), a few with an agent at work (`stop=<seconds>`)
  *   blocks     a sidebar's conversations as blocks (RichStyle.Blocks): groups, workspaces open and closed, unread
  *              counts, long names, drags between blocks, a button to draw it as a tree (`style=tree`, `theme=light`,
  *              `rtl=1`, `collapsed=<keys>`, `groups=<keys>`, `find=<words>`)
  *   scale      the size the app is drawn at (UiScale), turned up and down over words, rows, a field, buttons and menus,
  *              kept on the device (`scale=<factor>`, `theme=light`, `rtl=1`)
  *   letterspacing Material's type scale drawn plain and letter-spaced, each over a bar as wide as it measures
  *   italic     an empty list's and a search's labels upright and slanted, a paragraph, buttons and a field slanted
  *   readonly   read-only fields beside an editable one: a share link, a note and a key to choose and copy but not change
  *   typing     a field, a search box and a keyboard of the app's own typed into without the platform's keyboard (a Roku's
  *              phone app: ECP's Lit_ keys), each value logged (`probe=1` types itself)
  *   rtlwrap    paragraphs 560 wide that wrap in a right-to-left direction: an Arabic overview, the same cut to two lines,
  *              and one in English, each logging its laid-out size
  *   dispose    home, then a theatre guide shown once, then a calendar over home, which disposes of the guide: nothing
  *              that made it up is left reachable afterwards (DisposeSpec; `sgnodes all` on a Roku)
  *   textcut    words cut short to fit (maxWidth, maxLines) that were made before they were on the screen, and in a face
  *              still loading
  *   textmeasure wrapped words over a box as tall as their measured height: a question at 26 bold in 560 and sizes 18
  *              to 40 in 300 to 900, with blank lines, a long word and spaced lines (`page=1` to `page=3`)
  *   badge      list rows with long badges (a file's folder and counts), cut from the start to fit after the names at
  *              400 wide, whole where there is room, and cut in the middle and at the end (`rtl=1`)
  *   webview    a page of the web (WebView) that is pinged and answers: web/webview.html beside the web build, or
  *              `src=<url>` (the same page on another origin); an empty box where there is no engine to lend;
  *              `sandbox=none|scripts|<flags>` shows web/sandbox.html sandboxed, trying to reach the app
  *   colors     the colour picker: on the page with alpha and presets, and in a dialog (`dialog=1`)
  *   dates      the date pickers: a date field and calendar, a range field and range calendar with presets, and
  *              their dialogs (`dialog=date|range`, `theme=light`, `lang=ar`, `today=`, `min=`, `max=`)
  *   sliders    sliders, range sliders and switches: steps, divisions, value labels, vertical, out of use (`theme=light`,
  *              `rtl=1`)
  *   captions   the device's caption setting as it changes, over a video that follows it until a subtitle track is
  *              chosen (`src=<stream>`)
  *   tabs       tabs led by an avatar or an icon, and the ring around somebody at work, turning and stopped (`busy=0`,
  *              `theme=light`, `rtl=1`)
  *   tabgroups  tabs in groups split across and down, as an editor splits its files: menus, drags, a layout kept
  *              (`fresh=1`, `layout=<json>`, `theme=light`, `rtl=1`)
  *   offmain    a rail that glides with the focus, and a card changed off the thread that draws while the app is away:
  *              the rail glides as before once it is back (OffMainDemo)
  *   coach      a coach-mark tour: the screen dimmed but for each stop (Spotlight), the focus held there (Focus.confine),
  *              along a rail, OK held for a menu, OK to open, Play where only Back works, and Ask (`probe=1` walks it)
  *   quality    the quality level and the live measures behind it, a button for each level, and shadows, glides, motion
  *              and blur that each level draws differently (QualityDemo; `window=5`, `slow=2` and `level=Reduced`)
  *   launch     what the app was launched with, and each launch that reaches it once it runs (Platform.onLaunch): on a
  *              Roku, ECP's `/launch/dev?open=…` to a running or suspended channel, or `/input?open=…` (LaunchDemo)
  *   unwind     a form whose content is set once it is built, as NaboTV's last setup step: shown, or with `throw=deep` a
  *              throw at depth in the children change, told with its backtrace (UnwindDemo)
  *   audio      a looping track, a clip that takes the player from it and gives it back, and effects over both, the
  *              hand-offs logged ("[ui] audio: uiMedia2 demo-clip.wav takes the player from uiMedia1 demo-music.wav");
  *              `unfit=1` adds an effect a Roku cannot mix, reported as an error and played through the player (AudioDemo)
  *   treebadge  the same in trees; not on a Roku or an Apple TV (app/full: FullDemos)
  *   picker     a filtering picker narrowed by segment chips (a model picker by provider); not on a Roku or an Apple TV
  *              (app/full: FullDemos)
  *   voice      a microphone with a level meter, played back, and a tone that Stop cuts off (`probe=1` runs it itself); not
  *              on a Roku or an Apple TV (app/full: FullDemos)
  *   platform   the platform's services: saving a file, the clipboard, passkeys (`rp=`), push notifications (`vapid=`),
  *              the location; not on a Roku or an Apple TV (app/full: FullDemos)
  *   zoom       a project map on a canvas that pans and zooms (ZoomCanvas): semantic zoom over 2,578 boxes, and a
  *              drawing whose routers are picked by a click; not on a Roku or an Apple TV (app/full: FullDemos)
  *   map        a slippy map (MapView): survey sections as polygons, a road, pins and a thousand clustered wells over
  *              tiles drawn in the page (`tiles=<template>`, `cluster=0`); not on a Roku or an Apple TV
  *              (app/full: FullDemos)
  *   graph      a project's module dependencies on a GraphView: layered, a broken rule in red, planned modules dashed,
  *              laid out again as a module is added (`flow=down`, `big=<n>`); not on a Roku or an Apple TV
  *              (app/full: FullDemos)
  *   forcegraph words seen together on a GraphView laid out by forces: a few hundred, draggable, with words added and
  *              removed (`n=<words>`, `measure=1`); not on a Roku or an Apple TV (app/full: FullDemos)
  *   transfer   files and a server: a POST answered with a file, saved (`HttpFiles.save`) and read as bytes, and a
  *              multipart upload of a JSON field and a file, made or picked (`api=<origin>`); not on a Roku or an Apple TV
  *              (app/full: FullDemos)
  *   capture    pictures the app takes of itself (Snapshot): a panel of words, a picture, a chart and a list as a PNG,
  *              the window, and the chart at three times its size from its figures, shown and saved (`probe=1`,
  *              `scale=2`, `photo=<url>`, `theme=light`); not on a Roku or an Apple TV (app/full: FullDemos)
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
  else if (demo == "bubbles") BubbleDemo.run()
  else if (demo == "busy") BusyDemo.run()
  else if (demo == "colors") ColorsDemo.run()
  else if (demo == "dates") DatesDemo.run()
  else if (demo == "sliders") SlidersDemo.run()
  else if (demo == "markdown") MarkdownDemo.run()
  else if (demo == "logs") LogsDemo.run()
  else if (demo == "richlist") RichListDemo.run()
  else if (demo == "blocks") BlocksDemo.run()
  else if (demo == "scale") ScaleDemo.run()
  else if (demo == "letterspacing") LetterSpacingDemo.run()
  else if (demo == "italic") ItalicDemo.run()
  else if (demo == "readonly") ReadOnlyDemo.run()
  else if (demo == "typing") TypingDemo.run()
  else if (demo == "rtlwrap") RtlWrapDemo.run()
  else if (demo == "captions") CaptionsDemo.run()
  else if (demo == "dispose") DisposeDemo.run()
  else if (demo == "textcut") TextCutDemo.run()
  else if (demo == "textmeasure") TextMeasureDemo.run()
  else if (demo == "webview") WebViewDemo.run()
  else if (demo == "badge") BadgeDemo.run()
  else if (demo == "tabs") TabsDemo.run()
  else if (demo == "tabgroups") TabGroupsDemo.run()
  else if (demo == "offmain") OffMainDemo.run()
  else if (demo == "coach") CoachDemo.run()
  else if (demo == "quality") QualityDemo.run()
  else if (demo == "launch") LaunchDemo.run()
  else if (demo == "unwind") UnwindDemo.run()
  else if (demo == "audio") AudioDemo.run()
  else if (!FullDemos.run(demo)) ExampleApp.main()
}

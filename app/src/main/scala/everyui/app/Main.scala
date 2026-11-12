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
  * `overlaps=1` turns the overlap check on (`Overlaps.warn`) whatever runs.
  *
  *   animation  youi's AnimationExample, in youi's example app (the default)
  *   durable    the network module's example: a durable socket to `socket=<url>` (tools/check.sh)
  *   net        the app's own files (its translations, a file by its path, one it lacks) and HTTP and a WebSocket to
  *              EveryUI's `server/` (`server=http://host:8731`), each check logged (NetDemo)
  *   input      the input model: focus, the arrows, Enter and the pointer (`probe=1` drives it itself)
  *   hello      the original seam proof plus the frame-rate probe
  *   bench      reactify's per-operation cost
  *   easings    every easing curve as numbers
  *   blur       blur as the screensaver draws it: a photo over its blurred copy, and a blur coming clear
  *   hidden     what is made or changed in a hidden panel has its size when the panel shows
  *   autofill   a sign-in and a contact form as autofill and password managers see them
  *   rtlscroll  a rail in a right-to-left language, as the web's own scroll holds it
  *   rows       a table of `n=` rows built and then grown a row at a time, timed
  *   swipe      a page of cards a tap is heard on, with a rail across it: a finger swipe scrolls either, however it
  *              starts, and a tap lands; where each comes to rest and each tap logged
  *   composer   a chat's composer: suggestions, attachments, @-mentions and /commands, a reply faked as busy
  *   chat       a group room in a ChatView: streaming replies, tool calls, reasoning, 2000 earlier entries (`theme=light`,
  *              `rtl=1`, `offline=1`, `probe=1`, `author=log`, `queue=1` for a composer that queues while a reply comes)
  *   chatperf   how fast a long conversation opens: `n=` entries of a coding agent's transcript, built `at=` seconds in, timed
  *   leave      asking before the app is left (Platform.leaveGuard): `guard=on|off`, F and C set and clear a flag's guard,
  *              G adds a guard, R removes them, P moves within the app
  *   reparent   a focused field moved between containers, and a chat's composer between two conversations' views: Alt+M,
  *              Alt+T, Alt+F, Alt+C, Alt+K and Alt+L (ReparentDemo)
  *   fill       a filter that wraps over a list that fills the rest (Panel, `Component.flex`), the overlap check on; `bad=1`
  *              places the list by hand under a guessed height instead, which the check reports (FillDemo)
  *   kept       kept views put back: panels of tabs, paragraphs and a conversation switched between, each switch timed
  *              (`kept=<panels>`, `switches=`, `every=<ms>`, `narrow=1`, `perfCounters=1` for the counts; KeptDemo)
  *   busy       the app at work (AppBusy): idle, busy, a named progress, round and round (`title=1`: in the title too)
  *   spinner    a button swapped for a spinner while its work runs (AsyncPress), the work just past spinAfter
  *              (`work=<seconds>`, `probe=1`)
  *   markdown   Markdown drawn by EveryUI: every kind of block, and the same document streamed in (`stream=1`, `at=`)
  *   logs       a pipeline's log in a LogPanel: ANSI colours, a stack trace, a progress bar drawn over itself, search,
  *              filters and level chips (`lines=`, `rate=`, `stream=0`, `wrap=1`, `theme=light`, `probe=1`), or the example
  *              server's log over a durable socket (`socket=ws://host:8731/logs`)
  *   richlist   a conversation list (RichList): 5000 in three sections, with unread counts, pins, a search and menus
  *              (`theme=light`, `collapsed=archive`), a few with an agent at work (`stop=<seconds>`)
  *   avatars    people's statuses on their avatars (Avatar.statuses) at 18 to 96 px, by an initial and a picture, busy,
  *              a face's own, and on a conversation list's rows (`theme=light`, `rtl=1`, `cycle=<seconds>`)
  *   blocks     a sidebar's conversations as blocks (RichStyle.Blocks): groups, workspaces open and closed, unread
  *              counts, long names, drags between blocks, a button to draw it as a tree (`style=tree`, `theme=light`,
  *              `rtl=1`, `collapsed=<keys>`, `groups=<keys>`, `find=<words>`)
  *   sections   a day's groups of tasks as section cards (SectionCard), each header the heading of its tasks, two
  *              open and one closed, one with no header, under a compact card of background work with a spinner, Clear
  *              and a sub-section (`backdrop=<picture>`, `see=`, `cards=1` for separate cards, `work=0` closed,
  *              `theme=light`, `rtl=1`)
  *   memory     an assistant's memories as blocks that expand in place to their whole Markdown with Pin and Delete,
  *              one at a time or several (`several=1`, `style=tree`, `theme=light`, `rtl=1`, `open=<keys>`, `find=<words>`)
  *   scale      the size the app is drawn at (UiScale), turned up and down over words, rows, a field, buttons and menus,
  *              kept on the device (`scale=<factor>`, `theme=light`, `rtl=1`)
  *   menus      a menu whose entries lead with letter tiles, pictures large and small, one that will not load, icons,
  *              a tick and nothing, with details under them, their words lined up (`theme=light`, `rtl=1`)
  *   svg        SVG pictures in avatars and image views, from the app's files, a server (by .svg and by its bytes) and a
  *              data: address, and one that is no picture (`svghost=`, `theme=light`)
  *   pictures   pictures from a server, over HTTPS, a 4K one decoded at the size drawn, SVGs, avatars, a blur, a tint, a
  *              404, no host, and a box asked for six in a moment showing the last (`picturehost=`, `public=`)
  *   wrap       a paragraph wrapped at three widths and in a box, cut to two lines, centred, at its end, Arabic, code
  *              as written, and emoji, a flag, CJK and Arabic in one line
  *   completion fields completing what is typed: To and Cc addresses between separators, a city by the whole field, a
  *              box of lines' #tags, and the city in a dialog (`dialog=1`, `theme=light`, `rtl=1`)
  *   letterspacing Material's type scale drawn plain and letter-spaced, each over a bar as wide as it measures
  *   italic     an empty list's and a search's labels upright and slanted, a paragraph, buttons and a field slanted
  *   weights    weights 300 to 800 in the bundled Open Sans and in faces of it registered as an app's (`six=1`)
  *   later      Platform.later on a later turn, in order, and 10,000 steps asking for the next one, one deep at most
  *   drawer     a side drawer over a screen, opened from a bar's Menu, its rows made a turn later (`rebuild=1`)
  *   readonly   read-only fields beside an editable one: a share link, a note and a key to choose and copy but not change
  *   typing     a field, a search box and a keyboard of the app's own typed into without the platform's keyboard (a Roku's
  *              phone app: ECP's Lit_ keys), each value logged (`probe=1` types itself)
  *   keyboard   the platform's keyboard where a field has none of its own (a Roku, an Apple TV): an email address, a
  *              password, a hidden PIN of digits and a search box, each value logged (KeyboardDemo)
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
  *              `add=1` for a "+" after the last tab, `theme=light`, `rtl=1`)
  *   tabgroups  tabs in groups split across and down, as an editor splits its files: menus, drags, each group's
  *              "+", a layout kept (`fresh=1`, `layout=<json>`, `probe=1`, `add=0`, `theme=light`, `rtl=1`)
  *   sidebar    a workspace laid out by hand: a tool rail, a sidebar whose edge drags its width (ResizeHandle), a header,
  *              and an editor beside an inspector kept at a width in pixels (SplitPane's `length`), both keeping their
  *              widths as the window changes (`width=`, `inspector=`, `folded=1`, `theme=light`, `rtl=1`)
  *   panes      a sidebar as stacked panes (StackedPanes): Folders, Conversations and Log, folding to their headers,
  *              sharing the height by handles, with actions and what the app keeps shown live (`sidebar=1` for 280 wide,
  *              `theme=light`, `rtl=1`, `collapsed=<folders,chats,log>`, `weights=<w,w,w>`, `probe=1`)
  *   offmain    a rail that glides with the focus, and a card changed off the thread that draws while the app is away:
  *              the rail glides as before once it is back (OffMainDemo)
  *   coach      a coach-mark tour: the screen dimmed but for each stop (Spotlight), the focus held there (Focus.confine),
  *              along a rail, OK held for a menu, OK to open, Play where only Back works, and Ask (`probe=1` walks it)
  *   quality    the quality level and the live measures behind it, a button for each level, and shadows, glides, motion
  *              and blur that each level draws differently (QualityDemo; `window=5`, `slow=2` and `level=Reduced`)
  *   launch     what the app was launched with, and each launch that reaches it once it runs (Platform.onLaunch): on a
  *              Roku, ECP's `/launch/dev?open=…` to a running or suspended channel, or `/input?open=…` (LaunchDemo)
  *   device     what the platform says of the device (its kind, name, version, safe area, input), a button that counts
  *              its presses, and errors thrown on purpose from a press and from a timer, each logged as Errors hears
  *              it (DeviceDemo)
  *   unwind     a form whose content is set once it is built, as NaboTV's last setup step: shown, or with `throw=deep` a
  *              throw at depth in the children change, told with its backtrace (UnwindDemo)
  *   audio      a looping track, a clip that takes the player from it and gives it back, and effects over both, the
  *              hand-offs logged ("[ui] audio: uiMedia2 demo-clip.wav takes the player from uiMedia1 demo-music.wav");
  *              `unfit=1` adds an effect a Roku cannot mix, reported as an error and played through the player (AudioDemo)
  *   player     a video over local test media (an MKV with two sound tracks and two subtitles, an MP4, a sound alone):
  *              play, pause, seeks, tracks, rate, volume and each end; `script=1` drives it, `src=` plays others,
  *              `cycle=<seconds>` lets one go mid-playback and opens another, again and again (PlayerDemo)
  *   railperf   a Home cut down to a move of the focus along a rail: a hero of words over `rails=<k>` rails of `n=<cards>`
  *              ringed cards that glide with the focus; OK takes the rail apart and builds it again, or starts `video=`;
  *              each move and each frame of 50 ms or more logged, `rokuperf=1` for a Roku's own counts (RailPerfDemo)
  *   pageopen   a title's page of about 200 components (a hero, tabs, `rails=` rails of `n=` cards) opened on OK and
  *              timed, taken apart after Back; `auto=<opens>` opens and closes it by itself (PageOpenDemo)
  *   httpperf   `n=` requests to `url=` one after another and then all at once, each timed (`method=POST`, `body=`;
  *              `httplog=1` or `rokuperf=1` for where a Roku's time went) (HttpPerfDemo)
  *   treebadge  the same in trees; not on a Roku or an Apple TV (app/full: FullDemos)
  *   rowtips    a project's files in a tree, each with its whole path as its tip, shown as the pointer, the cursor or
  *              a finger rests on it (`n=<files>`, `menu=1`, `theme=light`, `rtl=1`); not on a Roku or an Apple TV
  *              (app/full: FullDemos)
  *   picker     a filtering picker narrowed by segment chips (a model picker by provider); not on a Roku or an Apple TV
  *              (app/full: FullDemos)
  *   voice      a microphone with a level meter, played back, and a tone that Stop cuts off (`probe=1` runs it itself); not
  *              on a Roku or an Apple TV (app/full: FullDemos)
  *   prejoin    a meeting's "ready to join?" page (the camera's picture, the microphone's level, their switches) that
  *              joins `livekit=` with `token=`, handing its tracks to the call; a browser only (app/full: PreJoinDemo)
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
  *   datatable  a DataTable of an app's rows: `n=` personas (500) sorted, filtered, chosen, copied and downloaded, and a
  *              small one (`theme=light`, `rtl=1`, `max=<pixels>`); not on a Roku or an Apple TV (app/full: FullDemos)
  *   rail       a rail of tiles 44 wide, no scrollbar and its ends faded, beside a list that fades too, over stripes
  *              (`theme=light`, `at=<pixels>`); not on a Roku or an Apple TV (app/full: FullDemos)
  *   quota      an admin console's usage in a RichList: meters whose colour turns as they fill, a subtitle beside
  *              its section's title or under it, sections with no count (`width=<pixels>`, `theme=light`,
  *              `style=blocks`); not on a Roku or an Apple TV (app/full: FullDemos)
  *   turns      a coding agent's conversation whose turn header names the turn at the top while its message is out of
  *              sight, with a file and a spinner, going back to it on a press (`theme=light`, `room=1`, `busy=0`,
  *              `at=<pixels>`); not on a Roku or an Apple TV (app/full: FullDemos)
  *   press      a drag over a scroll of buttons or a list that activates on a click presses nothing as it lifts, a
  *              tap does; badges hang past a plain column in a rail (`theme=light`); not on a Roku or an Apple TV
  *              (app/full: FullDemos)
  *   webboard   a web view in a rounded panel over a busy board, shown 3 s in and taken apart 30 s later, to measure
  *              what a page playing costs the app's frames and what is left after it goes (`page=<url>`, `at=`,
  *              `for=`, `web=0`, `tint=1`, `prewarm=1`); not on a Roku or an Apple TV (app/full: FullDemos)
  *   background what runs with the app closed (ExampleBackground): work scheduled once, at once and every 15 minutes,
  *              an ongoing notice whose Pause, Resume and Open run with the app closed (Open lands on
  *              /background/opened), the settings pages, the restarts heard, and pushes with buttons delivered in 3 s
  *              (Done, Approve and Deny run with the app closed) (`theme=light`); also at a launch to /background; not
  *              on a Roku or an Apple TV (app/full: FullDemos)
  */
@main def main(): Unit = {
  val demo = Platform.launchArg("demo")
  // any demo, with the overlap check on (`overlaps=1`)
  if (Platform.launchArg("overlaps") == "1") everyui.layout.Overlaps.warn @= true
  if (demo == "hello") HelloWorld.run()
  else if (demo == "bench") ReactifyBench.run()
  else if (demo == "easings") EasingTable.run()
  else if (demo == "youibench") YouiBench.run()
  else if (demo == "durable") DurableDemo.run()
  else if (demo == "net") NetDemo.run()
  else if (demo == "input") InputDemo.run()
  else if (demo == "widgets") WidgetsDemo.run()
  else if (demo == "blur") BlurDemo.run()
  else if (demo == "backdrop") BackdropDemo.run()
  else if (demo == "hidden") HiddenDemo.run()
  else if (demo == "autofill") AutofillDemo.run()
  else if (demo == "rtlscroll") RtlScrollDemo.run()
  else if (demo == "rows") RowsDemo.run()
  else if (demo == "swipe") SwipeDemo.run()
  else if (demo == "composer") ComposerDemo.run()
  else if (demo == "chat") ChatDemo.run()
  else if (demo == "chatperf") ChatPerfDemo.run()
  else if (demo == "bubbles") BubbleDemo.run()
  else if (demo == "parked") ParkedDemo.run()
  else if (demo == "busy") BusyDemo.run()
  else if (demo == "colors") ColorsDemo.run()
  else if (demo == "dates") DatesDemo.run()
  else if (demo == "sliders") SlidersDemo.run()
  else if (demo == "markdown") MarkdownDemo.run()
  else if (demo == "logs") LogsDemo.run()
  else if (demo == "richlist") RichListDemo.run()
  else if (demo == "avatars") AvatarsDemo.run()
  else if (demo == "blocks") BlocksDemo.run()
  else if (demo == "sections") SectionsDemo.run()
  else if (demo == "memory") MemoryDemo.run()
  else if (demo == "scale") ScaleDemo.run()
  else if (demo == "menus") MenusDemo.run()
  else if (demo == "svg") SvgDemo.run()
  else if (demo == "pictures") PicturesDemo.run()
  else if (demo == "wrap") TextWrapDemo.run()
  else if (demo == "completion") CompletionDemo.run()
  else if (demo == "narrow") NarrowDemo.run()
  else if (demo == "letterspacing") LetterSpacingDemo.run()
  else if (demo == "italic") ItalicDemo.run()
  else if (demo == "weights") FontWeightsDemo.run()
  else if (demo == "later") LaterDemo.run()
  else if (demo == "drawer") DrawerDemo.run()
  else if (demo == "readonly") ReadOnlyDemo.run()
  else if (demo == "typing") TypingDemo.run()
  else if (demo == "keyboard") KeyboardDemo.run()
  else if (demo == "railperf") RailPerfDemo.run()
  else if (demo == "httpperf") HttpPerfDemo.run()
  else if (demo == "pageopen") PageOpenDemo.run()
  else if (demo == "rtlwrap") RtlWrapDemo.run()
  else if (demo == "captions") CaptionsDemo.run()
  else if (demo == "dispose") DisposeDemo.run()
  else if (demo == "textcut") TextCutDemo.run()
  else if (demo == "spinner") SpinnerDemo.run()
  else if (demo == "textmeasure") TextMeasureDemo.run()
  else if (demo == "webview") WebViewDemo.run()
  else if (demo == "badge") BadgeDemo.run()
  else if (demo == "tabs") TabsDemo.run()
  else if (demo == "tabgroups") TabGroupsDemo.run()
  else if (demo == "panes") PanesDemo.run()
  else if (demo == "sidebar") SidebarDemo.run()
  else if (demo == "offmain") OffMainDemo.run()
  else if (demo == "coach") CoachDemo.run()
  else if (demo == "quality") QualityDemo.run()
  else if (demo == "launch") LaunchDemo.run()
  else if (demo == "device") DeviceDemo.run()
  else if (demo == "lifecycle") LifecycleDemo.run()
  else if (demo == "unwind") UnwindDemo.run()
  else if (demo == "confirm") ConfirmDemo.run()
  else if (demo == "audio") AudioDemo.run()
  else if (demo == "player") PlayerDemo.run()
  else if (demo == "leave") LeaveDemo.run()
  else if (demo == "reparent") ReparentDemo.run()
  else if (demo == "fill") FillDemo.run()
  else if (demo == "kept") KeptDemo.run()
  else if (!FullDemos.run(demo)) ExampleApp.main()
}

package everyui.component

import everyui.component.types.Prop
import everyui.render.{Nodes, RenderNode}

/** A page of the web, drawn in place: the platform's own engine showing `source`, told things in its own language
  * and answering back.
  *
  * Only where a platform has an engine to lend. A television's browser engine is the one thing that can show what is
  * only ever published as an embed — a YouTube video, which no API will hand over as a file — and it is the only
  * reason this exists: everything else the app draws itself. On a platform with no engine (a Roku, an Apple TV) it
  * is an empty box, and whoever wanted the page has to want something else instead (`Platform.webViews`).
  *
  * On the web the page is an iframe. One of the app's own origin is told things and answers as it would anywhere
  * else; one of another origin cannot be run in, and `run` posts `{everyui: "run", script}` to it instead, for it to
  * act on the few commands it knows, while what it posts to its parent is heard (CONSUMING.md, WebView).
  *
  * `bridge` is the name of the object the page calls to say something back: `<bridge>.postMessage("…")`. The page
  * and the app have to agree on it, and neither of them is this layer, so it is named by whoever builds the view. */
class WebView(bridge: String, supplied: RenderNode = null)
  extends Component(if (supplied != null) supplied else Nodes.web(bridge)) {
  onDispose(() => this.node.releaseWeb())

  /** The address showing. Setting it again goes there again. */
  lazy val source: Prop[String] = own(new Prop[String]("", url => this.node.setWebSource(url)))

  /** Who the page is told is asking, where the engine lets it be said: set before `source`, since it goes with the
    * loads that follow. A television's engine calls itself a television, and some pages treat one differently —
    * YouTube's embed refuses to play for one at all. Empty is the engine's own. */
  lazy val agent: Prop[String] = own(new Prop[String]("", a => this.node.setWebAgent(a)))

  /** The page kept apart from the app, as an iframe's `sandbox` keeps it: None (the default) is no sandbox at all;
    * `Some(flags)` allows only those of the iframe's flags it names ("allow-scripts", "allow-forms", "allow-popups",
    * "allow-same-origin"…), `Some(Set())` nothing. Set it before `source`: a change loads the page again, as an engine
    * only honours it as a page loads. A sandboxed page is neither told (`run`) nor heard (`hear`), unless the app
    * asks to hear it (`hearSandboxed`). What each engine honours of it: CONSUMING.md, WebView; where there is no
    * engine it is ignored, as the view is. */
  lazy val sandbox: Prop[Option[Set[String]]] = own(new Prop[Option[Set[String]]](None, s => this.node.setWebSandbox(s)))

  /** Whether nothing of its own is behind the page: off (the default), a page that draws no ground of its own shows on
    * black, as a video's letterbox wants; on, what is behind the view shows through wherever the page is transparent
    * (`html, body { background: transparent }`, its colour scheme the app's so the browser backs it with nothing
    * either): a page laid over the app's own backdrop. Where there is no engine it is ignored, as the view is. */
  lazy val clear: Prop[Boolean] = own(new Prop[Boolean](false, c => this.node.setWebClear(c)))

  /** Say something to the page. It is script, and it runs in the page's world, not this one's. */
  def run(script: String): Unit = this.node.runWebScript(script)

  /** Hear what the page says back through its bridge. One listener: the page has one thing to talk to. */
  def hear(f: String => Unit): Unit = this.node.onWebMessage(f)

  /** Hear a sandboxed page too, which is otherwise not heard: what it posts from this view's own frame alone, as a
    * string (an object as its JSON), and nothing lent it but that, run in it or posted to it but `ask`. Set it before
    * `source`, as on Android the bridge goes with the loads after it. The page says it as it can: through `<bridge>`
    * where its window has one (Android), else to `window.parent` (the web). CONSUMING.md, WebView. */
  def hearSandboxed(f: String => Unit): Unit = {
    this.node.setWebHearSandboxed(true)
    hear(f)
  }

  /** Hear what the frames inside the page post (the web's, where a page holds frames of its own), with each one's
    * origin: `f(origin, message)`. Anything at all in them may post, so check the origin before acting on it. */
  def hearFrames(f: (String, String) => Unit): Unit = this.node.onWebFrameMessage(Some(f))

  /** Ask the page something and hear its answer: `request` posted to it as `{everyui: "ask", session, id, request}`
    * whatever its origin, and answered by the page's `{everyui: "reply", session, id, reply}` with the same session
    * and id, `reply` a string (an object is heard as its JSON). The session is the page's showing now (a new one as
    * `source` is set), so an answer from a page no longer showing is never taken for one from this. None where the
    * page has not answered in `seconds`, or could not be asked (no engine, no page yet, a sandboxed one not heard). */
  def ask(request: String, seconds: Double = 10.0)(answered: Option[String] => Unit): Unit = {
    asked += 1
    val id = asked
    this.node.onWebReply((n, reply) => replied(n, Some(reply)))
    waiting = waiting + (id -> answered)
    if (this.node.askWeb(id, request)) everyui.render.Platform.after(seconds, () => replied(id, None))
    else replied(id, None)
  }

  private var asked: Int = 0
  private var waiting: Map[Int, Option[String] => Unit] = Map.empty

  private def replied(id: Int, reply: Option[String]): Unit = waiting.get(id).foreach { f =>
    waiting = waiting - id
    f(reply)
  }

  override def kind: String = "WebView"
}

object WebView {
  /** The browser engine started now, at a moment the app chooses, rather than as the first page shows: on Android the
    * first web view of a process loads Chromium on the main thread and holds every frame up for a second or more on
    * a television. Call it where a page is likely soon and a pause would not be seen (a game's lobby before its
    * trailers): the engine is loaded off the main thread, started, and one web view made ready, with its page
    * process, for the next WebView to take. Calling it again while one is ready does nothing. Where the platform has
    * no engine to warm (the web's iframes start with their page; a Roku, an Apple TV, the desktop) it does nothing. */
  def prewarm(): Unit = everyui.render.Platform.prewarmWeb()
}

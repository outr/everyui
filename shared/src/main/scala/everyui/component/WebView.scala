package everyui.component

import everyui.component.types.Prop
import everyui.render.{Nodes, RenderNode}

/** A page of the web, drawn in place: the platform's own engine showing `source`, told things in its own language
  * and answering back.
  *
  * Only where a platform has an engine to lend. A television's browser engine is the one thing that can show what is
  * only ever published as an embed — a YouTube video, which no API will hand over as a file — and it is the only
  * reason this exists: everything else the app draws itself. On a platform with no engine (a Roku, an Apple TV) it
  * is an empty box, and whoever wanted the page has to want something else instead.
  *
  * `bridge` is the name of the object the page calls to say something back: `<bridge>.postMessage("…")`. The page
  * and the app have to agree on it, and neither of them is this layer, so it is named by whoever builds the view. */
class WebView(bridge: String, node: RenderNode = null)
  extends Component(if (node != null) node else Nodes.web(bridge)) {
  /** The address showing. Setting it again goes there again. */
  lazy val source: Prop[String] = new Prop[String]("", url => this.node.setWebSource(url))

  /** Who the page is told is asking, where the engine lets it be said: set before `source`, since it goes with the
    * loads that follow. A television's engine calls itself a television, and some pages treat one differently —
    * YouTube's embed refuses to play for one at all. Empty is the engine's own. */
  lazy val agent: Prop[String] = new Prop[String]("", a => this.node.setWebAgent(a))

  /** Say something to the page. It is script, and it runs in the page's world, not this one's. */
  def run(script: String): Unit = node.runWebScript(script)

  /** Hear what the page says back through its bridge. One listener: the page has one thing to talk to. */
  def hear(f: String => Unit): Unit = node.onWebMessage(f)

  override def kind: String = "WebView"
}

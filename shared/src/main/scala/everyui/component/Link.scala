package everyui.component

import everyui.app.screen.ScreenManager
import everyui.render.{Nodes, Platform}
import everyui.component.types.Prop

/** Something to follow to `href`, holding whatever it shows in `children`: a path this app has a screen for is shown
  * in place, anything else is gone to (in a new tab with `newTab`). On the web it is a real `<a>`, so a middle click,
  * a modifier key, "copy link" and a new tab keep working as the browser has them; everywhere, a click, a tap or Enter
  * follows it. */
class Link(target: String = "", newTab: Boolean = false) extends Container(Nodes.link()) {
  override def kind: String = "Link(" + href() + ")"

  lazy val href: Prop[String] = own(new Prop[String](target, h => {
    node.setHref(h, newTab)
    sayWhere(h)
  }))
  // what the app says of where it goes (`Link.describe`), last given as its tooltip and description: an app's own
  // tooltip or description, set apart from it, is left as it is
  private var said = ""
  // a Prop writes only what changes, and the first address is where the link was made to go: without it here a
  // link made with its address had none on the page, and a crawler, a middle click or "copy link" had nowhere to go
  node.setHref(href(), newTab)
  sayWhere(href())

  private def sayWhere(h: String): Unit = {
    val words = Link.description(h).getOrElse("")
    if (words.nonEmpty || said.nonEmpty) {
      if (tooltip() == said) tooltip @= words
      if (accessibleDescription() == said) accessibleDescription @= words
      said = words
    }
  }

  focusable @= true
  tapSlop @= true
  event.click.attach { e =>
    e.consume()
    Link.follow(href(), newTab)
  }
}

object Link {
  // the app's answers to links of schemes of its own, by scheme (in lower case)
  private var handlers: Map[String, String => Boolean] = Map.empty

  /** Links of `scheme` (`"voidcraft-share"` for `voidcraft-share:<id>`) answered by the app: a link followed anywhere
    * (`follow`: a `Link`, a link in Markdown, a chat message, a table or a `RichText`) is handed to `handler` first,
    * and nothing else opens it where it answers true; where it answers false, the link is followed as it would have
    * been (a scheme no platform opens, then, goes nowhere). Such a link is drawn as a link and followed by a click, a
    * tap or Enter on every platform, the web too, though the browser would not open it; it is never offered as an
    * address to copy or open in a new tab. A scheme the platforms open (`https`) can be answered too, the handler
    * answering false for what it leaves to them. Handing over another handler for the same scheme replaces it;
    * the function returned takes it away.
    *
    * {{{
    *   Link.handle("voidcraft-share") { href => viewer.open(href.stripPrefix("voidcraft-share:")); true }
    *   // a message's "[README.html](voidcraft-share:f81c)" now opens the viewer
    * }}} */
  def handle(scheme: String)(handler: String => Boolean): () => Unit = {
    val key = scheme.toLowerCase.stripSuffix(":")
    handlers = handlers.updated(key, handler)
    () => if (handlers.get(key).exists(_ eq handler)) handlers = handlers - key
  }

  // what the app says of links of schemes it describes, by scheme (in lower case)
  private var describers: Map[String, String => Option[String]] = Map.empty

  /** What a link of `scheme` (`"file"` for `file:<path>`) says as the pointer rests on it and to a screen reader, as
    * `describer` words it for the link's `href` (`href => Some("Open " + href.stripPrefix("file:"))`), None for
    * nothing: words for a link the app answers itself (`handle`), which otherwise shows nothing on a hover. A run's
    * own `TextRun.title` (a Markdown link's `"title"`) comes first; it is asked as the runs are drawn, so it is given
    * before the words it describes are shown. On the web a run is drawn with the words as its `title` and its
    * `aria-description` (a description, not the link's name, which stays its words), and a `Link` with them as its
    * title and its description; on the desktop and Android they are the platform's own tooltip over the run or the
    * `Link`; a Markdown paragraph holding such links shows them in a `Tooltip` as the keys bring the focus to it, where
    * the focus shows tips (`Tooltip.onFocus`), and as the detail of each in its menu of links. A link of a scheme the
    * platforms open (`https`) is drawn as it was unless a describer is given for its scheme. Handing over another
    * describer for the same scheme replaces it; the function returned takes it away.
    *
    * {{{
    *   Link.describe("file")(href => Some("Open " + href.stripPrefix("file:") + " in a tab"))
    * }}} */
  def describe(scheme: String)(describer: String => Option[String]): () => Unit = {
    val key = scheme.toLowerCase.stripSuffix(":")
    describers = describers.updated(key, describer)
    () => if (describers.get(key).exists(_ eq describer)) describers = describers - key
  }

  /** What the app says of `href` (`describe`), where it describes its scheme and has words for it. */
  def description(href: String): Option[String] =
    if (describers.isEmpty) None
    else schemeOf(href).flatMap(describers.get).flatMap(_(href)).map(_.trim).filter(_.nonEmpty)

  /** `run` with what it says on a hover (`TextRun.title`): its own, or the app's for its link (`description`). */
  def titled(run: everyui.text.TextRun): everyui.text.TextRun =
    if (run.href.isEmpty || run.title.nonEmpty) run
    else description(run.href).fold(run)(words => run.copy(title = words))

  /** The scheme `href` starts with, in lower case (`"https"`, `"voidcraft-share"`), where it has one: none for a path. */
  def schemeOf(href: String): Option[String] = {
    val colon = href.indexOf(':')
    val valid = colon > 0 && href.charAt(0).isLetter && href.substring(0, colon).forall(c =>
      c.isLetterOrDigit || c == '+' || c == '-' || c == '.')
    if (valid) Some(href.substring(0, colon).toLowerCase) else None
  }

  /** Whether the app answers `href` (`handle`): a link of one of its own schemes. */
  def answers(href: String): Boolean = schemeOf(href).exists(handlers.contains)

  /** Whether `href` is the app's own rather than an address: a link of a scheme it answers that no platform opens, or
    * one the app is pressed for (`RichText.action`, a mention). Drawn and followed as a link, never offered as an
    * address. */
  def appOwn(href: String): Boolean = RichText.acts(href) || (answers(href) && !NavigationUrl.safe(href))

  /** Hand `href` to the app where it answers its scheme (`handle`); otherwise show its screen if this app has one, or
    * go there. */
  def follow(href: String, newTab: Boolean = false): Unit =
    if (schemeOf(href).flatMap(handlers.get).exists(_(href))) ()
    else if (!NavigationUrl.safe(href)) ()
    else if (newTab) Platform.openUrl(href)
    else if (!ScreenManager.navigate(href)) Platform.navigate(href)

  /** A link around `content`. */
  def apply(href: String, newTab: Boolean = false)(content: Component*): Link = {
    val link = new Link(href, newTab)
    link.children ++= content
    link.size.width := content.map(c => c.position.left() + c.size.width()).foldLeft(0.0)(math.max)
    link.size.height := content.map(c => c.position.top() + c.size.height()).foldLeft(0.0)(math.max)
    link
  }
}

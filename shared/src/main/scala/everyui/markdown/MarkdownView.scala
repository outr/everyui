package everyui.markdown

import everyui.Color
import everyui.component.{Component, Container, Link}
import everyui.component.types.TextDirection
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** Markdown drawn with EveryUI's own components, so it looks the same on every platform: headings on the style's
  * scale, paragraphs with emphasis, code and links, code blocks coloured for their language and scrolled sideways,
  * quotes, lists and task lists, tables that scroll sideways, rules, and pictures. Raw HTML shows as the text it is.
  *
  * It lays itself out to the width it is given (`size.width`) and is as tall as its blocks, so it can sit in a chat's
  * bubble or a measured list row (`VirtualList(measuredRows = true)`). `content` can change at any time, and grow in place
  * while an answer streams in: only the last block is parsed again (`MarkdownStream`), the blocks before it keep
  * their components, and a paragraph or a code block that grows is drawn again in place.
  *
  * A link's click (or Enter on a paragraph that holds links, a remote's OK) goes to `onLink`, which by default shows a
  * path of this app in place and opens anything else (`Link.follow`). A picture's relative URL is found against
  * `base` (by default the platform's origin, `Platform.origin()`: the page's on the web). The colours are the theme's:
  * the link colour, the code wash and the quote bar are `Theme.extra` "markdown.link", "markdown.code" and
  * "markdown.quote" where the palette names them, else the accent, the raised colour and the border.
  *
  * {{{
  *   val answer = new MarkdownView(style = MarkdownStyle.compact, onLink = url => openInBrowser(url))
  *   answer.size.width := bubble.size.width() - 24.0
  *   answer.content := message().text        // grows token by token
  * }}}
  *
  * `direction` is which way its words run (`TextDirection.Auto` for words the app did not write: each paragraph, list
  * and quote then reads the way its own letters do); code always reads left to right. With `measures`, each paragraph
  * also measures its lines on one line, out of sight, so that `natural` says how wide it would be drawn on its own, as
  * a chat's bubble needs to be as narrow as a short reply. With `breaks`, a line break in the text is a line break
  * where Markdown would join the lines, as a chat's messages have it.
  *
  * On a Roku and an Apple TV, which draw no styled runs, words keep the paragraph's own style. */
class MarkdownView(initial: String = "", val style: MarkdownStyle = MarkdownStyle(),
                   onLink: String => Unit = MarkdownView.follow, base: => String = Platform.origin(),
                   direction: TextDirection = TextDirection.Inherit, measures: Boolean = false,
                   breaks: Boolean = false) extends Container {
  /** The Markdown it shows. */
  val content: Var[String] = own(Var(initial))
  /** Whether its tables sort, filter, copy and export (`everyui.table.DataTable`), where the platform has them (not a
    * Roku or an Apple TV); off, each is drawn as it is. It starts as `MarkdownView.richTables` says, for every view an
    * app makes; changed, the view is drawn again. */
  val richTables: Var[Boolean] = own(Var(MarkdownView.richTables.get))
  /** How its tables are drawn (`TableLook`): by default a header band, every second row striped and rounded corners,
    * in the theme's colours. It starts as `MarkdownView.tableLook` says, for every view an app makes; changed, the
    * view is drawn again. */
  val tableLook: Var[TableLook] = own(Var(MarkdownView.tableLook.get))
  /** The names "@" and a name is drawn as a mention of (`Mentions`: in the mention colour on a faint wash, not in code
    * or a link): a chat's people, say. Changed, its paragraphs follow in place. */
  val mentions: Var[Set[String]] = own(Var(Set.empty[String]))
  /** What a press on a mention does (a click, a tap, or Enter on its paragraph), with the name as `mentions` has it and
    * the words it was pressed in, to show something by: none, and a mention is only drawn, not pressed. */
  val onMention: Var[Option[(String, Component) => Unit]] = own(Var(None))
  private var stream = new MarkdownStream
  // (a link that is the app's to answer, which only a mention is here, never followed as an address)
  private val context = MarkdownContext(style, href => if (!everyui.component.RichText.acts(href)) onLink(href),
    () => base, direction = direction, measures = measures, breaks = breaks, richTables = () => richTables.get,
    tableLook = () => tableLook.get, mentions = () => mentions(), pressesMentions = () => onMention().nonEmpty,
    onMention = (name, at) => onMention.get.foreach(_(name, at)))
  private var drawn: Vector[MdBlock] = Vector.empty

  layout @= Some(VerticalLayout(style.spacing))
  content.attach(show)
  show(initial)
  // every block drawn again, its tables the other way or drawn anew
  richTables.attach(_ => redraw())
  tableLook.attach(_ => redraw())

  private def redraw(): Unit = {
    val gone = children()
    stream = new MarkdownStream
    drawn = Vector.empty
    children @= Vector.empty
    gone.foreach(_.dispose())
    show(content())
  }

  /** How wide it would be drawn on its own, where it `measures`: its widest line (a list's and a quote's past their
    * indent), or none where a block takes all the width it is given (code, a table, a rule, a picture), as every block
    * does where it does not measure. */
  val natural: Val[Option[Double]] = own(Val(
    if (!measures) None else MarkdownBlocks.widest(children().map(MarkdownBlocks.natural))))

  /** The blocks it shows, as parsed. */
  def blocks: Vector[MdBlock] = stream.blocks

  /** `text` shown: the blocks that did not change keep their components, a paragraph or code block that grew takes
    * its new content in place, and the rest are made. */
  private def show(text: String): Unit = {
    val from = stream.update(text)
    val now = stream.blocks
    val views = children()
    var kept: Vector[Component] = views.take(from)
    var gone: Vector[Component] = Vector.empty
    var i = from
    while (i < now.size) {
      val was = if (i < drawn.size && i < views.size) Some(views(i)) else None
      if (was.nonEmpty && (drawn(i) == now(i) || MarkdownBlocks.update(was.get, drawn(i), now(i)))) kept = kept :+ was.get
      else {
        was.foreach(v => gone = gone :+ v)
        kept = kept :+ MarkdownBlocks.make(now(i), context, size.width())
      }
      i += 1
    }
    var j = math.max(now.size, from)
    while (j < views.size) {
      gone = gone :+ views(j)
      j += 1
    }
    drawn = now
    children @= kept
    gone.foreach(_.dispose())
  }

  override def kind: String = "MarkdownView"
}

object MarkdownView {
  /** Whether a view made from now on starts with tables that sort, filter, copy and export (its `richTables`). */
  val richTables: Var[Boolean] = Var(true)
  /** How a view made from now on draws its tables (its `tableLook`). */
  val tableLook: Var[TableLook] = Var(TableLook())

  /** Markdown `text` laid out `width` wide. */
  def apply(text: String, width: Double, style: MarkdownStyle = MarkdownStyle()): MarkdownView = {
    val view = new MarkdownView(text, style)
    view.size.width @= width
    view
  }

  /** What a link does unless the app says: a path of this app shown in place, anything else opened. */
  def follow(href: String): Unit = Link.follow(href, newTab = !everyui.component.RichText.inApp(href))

  def link(): Color = Theme.extra("markdown.link", Theme.accent())
  def codeBackground(): Color = Theme.extra("markdown.code", Theme.raised())
  /** A mention's colour, and the faint wash behind it (`mentions`): `Theme.extra` "markdown.mention" and
    * "markdown.mentionWash" where the palette names them, else the accent, and the accent faintly. */
  def mention(): Color = Theme.extra("markdown.mention", Theme.accent())
  def mentionWash(): Color = Theme.extra("markdown.mentionWash", Theme.accent().withAlpha(0.14))
  def quoteBar(): Color = Theme.extra("markdown.quote", Theme.border())

  /** `url` found against `base`: a URL with a scheme (or a `data:` one) as it is; `//host/path` in the base's scheme;
    * `/path` on the base's origin; any other path under the base (its origin, where it is only one). No base leaves
    * it as it is. */
  def resolve(url: String, base: String): String =
    if (url.indexOf("://") >= 0 || url.startsWith("data:") || url.startsWith("mailto:") || base.isEmpty) url
    else {
      val root = if (base.endsWith("/")) base.substring(0, base.length - 1) else base
      val scheme = root.indexOf("://")
      val slash = if (scheme < 0) -1 else root.indexOf("/", scheme + 3)
      val origin = if (slash < 0) root else root.substring(0, slash)
      if (url.startsWith("//")) (if (scheme < 0) "https:" else root.substring(0, scheme + 1)) + url
      else if (url.startsWith("/")) origin + url
      else root + "/" + url
    }
}

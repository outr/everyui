package everyui.markdown

import everyui.Color
import everyui.component.{Component, Container, Link}
import everyui.component.types.TextDirection
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
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

  def link(): Color = Theme.extra("markdown.link", Theme.accentText())
  /** How whole the code wash is drawn, the app's to say: `None` (the default), worked out as `codeOver` says, by the
    * theme's surfaces and the ground a block is on, its words kept at `codeContrast`; or `Some(a)`, the wash drawn `a`
    * whole (held to 0 to 1) behind every code block, terminal's block and code span, whatever it is on and whatever
    * the theme's surfaces are, even where its words then read at less than `codeContrast`, as the app chose:
    * `Some(1.0)` keeps it whole however see-through the surfaces are, `Some(0.5)` lets half a picture through. A
    * terminal's colours are still made to read on it (`codeReadOn`), as far as they can over a white picture and a
    * black one alike; the highlighted code's are the theme's, as they always are. Read as it is drawn, so a change
    * redraws every code wash shown. */
  val codeOpacity: Var[Option[Double]] = Var(None)
  /** The contrast every colour of the code's words keeps on the wash, over a white picture and a black one, where its
    * opacity is worked out (`codeOpacity` `None`) while the theme's surfaces let a picture through: 4.5 to 1, the
    * default (WCAG's AA for words), held to 1 to 21. An app that wants more of its picture through its code lowers it,
    * one that wants it easier to read raises it: at 3 to 1 a code span lets about 0.2 of a picture through in the
    * framework's dark palette and 0.26 in its light (0.09 and 0.1 at 4.5), and a block in a bubble at 0.8 is drawn 0.5
    * whole (0.57 to 0.59 at 4.5), a block still letting through no more than half what the bubble does; a terminal's
    * colours are drawn as at 4.5 whatever it is (`codeReadOn`). It changes nothing while the surfaces are whole, nor
    * where `codeOpacity` is given. Read as it is drawn, so a change redraws every code wash shown. */
  val codeContrast: Var[Double] = Var(4.5)

  // `codeOpacity`, held to 0 to 1, none where it is not a number (NaN, the one value not equal to itself)
  private def exact(): Option[Double] = codeOpacity().filter(a => a == a).map(a => math.max(0.0, math.min(1.0, a)))
  // `codeContrast`, held to 1 to 21, 4.5 where it is not a number
  private def floor(): Double = {
    val f = codeContrast()
    if (f != f) 4.5 else math.max(1.0, math.min(21.0, f))
  }

  /** The code wash behind a code span, and behind anything with nothing known under it: `codeOver` a ground that
    * lets all of what is behind it through. Whole while the theme's surfaces are (`Theme.surfaceOpacity` 1, the
    * default) and the app gives no `codeOpacity`, so an opaque theme's code is drawn exactly as it always was. */
  def codeBackground(): Color = codeOver(Color.Clear)

  /** The code wash on `ground`, the fill of the bubble or card it is in: `codeColour` drawn as whole as the app's
    * `codeOpacity` where it gives one; else `codeColour`, whole while the theme's surfaces are, or where the ground
    * lets nothing through; otherwise see-through enough that the block lets half as much of a picture behind through
    * as the ground around it does (one in a bubble at 0.8 about 0.1 of it), so it shows through the code a little
    * less than through the bubble, but never more than `codeLetThrough`, and only as much as leaves every colour of
    * the code's words reading at `codeContrast` (4.5 to 1) on the wash, the ground's own colour and a white picture or
    * a black one behind them, the worst there could be (one's own bubble, a wash of the accent, keeps a little more of
    * the code's ground). */
  def codeOver(ground: Color): Color = {
    val c = codeColour()
    val set = exact()
    val through = 1.0 - ground.alpha
    if (set.nonEmpty) { if (set.get >= 1.0) c else c.withAlpha(c.alpha * set.get) }
    else if (Theme.surfaceOpacity() >= 1.0 || through <= 0.0) c
    else {
      codeLetThrough()
      val a = washes.getOrElse(ground, {
        var a = 1.0 - math.min(through / 2.0, letThrough) / through
        while (a < 1.0 && !reads(c, a, ground, floorOf)) a = math.min(1.0, a + 0.01)
        washes = washes + (ground -> a)
        a
      })
      if (a >= 1.0) c else c.withAlpha(c.alpha * a)
    }
  }

  /** The code wash's own colour: `Theme.extra` "markdown.code" where the palette names it, else the raised colour. */
  def codeColour(): Color = Theme.extra("markdown.code", Theme.raised())

  /** The most of what is behind a code block that may show through it (0 to 0.5), worked out for the palette and
    * `codeContrast`: the most at which every colour a code block's words are drawn in (the text, muted for comments,
    * the words' tones of the accent and the states, the code and link colours an app names) keeps that contrast (4.5
    * to 1 unless the app says) on the wash over a white picture and over a black one: 0.09 for the framework's dark
    * palette and 0.1 for its light at 4.5, muted the first to fall short; none where a colour falls short on the wash
    * alone. */
  def codeLetThrough(): Double = {
    val p = Theme.palette()
    val f = floor()
    if (!(letThroughOf eq p)) {
      letThroughOf = p
      var named: List[Color] = Nil
      p.extras.foreach(e => if (e._1.startsWith("code.")) named = e._2 :: named)
      words = List(Theme.text(), Theme.muted(), Theme.accentText(), Theme.successText(), Theme.warningText(),
        Theme.dangerText(), Theme.infoText(), link()) ++ named
      legibleThrough = most(4.5)
      floorOf = -1.0
    }
    if (f != floorOf) {
      floorOf = f
      washes = Map.empty
      letThrough = if (f == 4.5) legibleThrough else most(f)
    }
    letThrough
  }
  private var letThroughOf: Palette = null
  private var floorOf: Double = 4.5
  private var letThrough: Double = 0.0
  // `codeLetThrough` at 4.5 to 1, the contrast `LogTheme.legible` takes a terminal's colours to
  private var legibleThrough: Double = 0.0
  private var words: List[Color] = Nil
  private var washes: Map[Color, Double] = Map.empty

  // the most of a picture that may show through the wash with every colour of the code's words at `f` to 1 over it
  private def most(f: Double): Double = {
    val c = codeColour()
    var t = 0.0
    while (t < 0.5 && reads(c, 1.0 - (t + 0.005), Color.Clear, f)) t += 0.005
    t
  }

  // whether every colour of the code's words reads at `f` to 1 on wash `c` drawn `a` whole over `ground`, over white
  // and black, the wash's alpha taken as it is drawn (to 1 part in 255)
  private def reads(c: Color, a: Double, ground: Color, f: Double): Boolean =
    List(Color.White, Color.Black).forall { behind =>
      val under = ground.withAlpha(1.0).blend(behind, 1.0 - ground.alpha)
      val shown = c.withAlpha(1.0).blend(under, 1.0 - Color.channel(a) / 255.0)
      words.forall(w => everyui.access.Access.contrast(w, shown) >= f)
    }

  /** The ground a terminal's colours are made to read on (`LogTheme.legible`, at 4.5 to 1) in a code block:
    * `codeColour` while the wash is whole (the theme's surfaces whole and no `codeOpacity`, or a `codeOpacity` of
    * 1), as it always was; otherwise the wash with the worst picture through it (white behind a dark wash, black
    * behind a light one), as much as shows through it (`codeLetThrough`, or all `codeOpacity` leaves), but no more
    * than `codeLetThrough` at 4.5 to 1 whatever `codeContrast` is: past that no colour reads at 4.5 over a white
    * picture and a black one alike, and taking them further towards the one would lose them over the other (at 3 to
    * 1 they would all be taken to white), so they are drawn as at 4.5. */
  def codeReadOn(): Color = {
    val c = codeColour()
    val set = exact()
    if (set.exists(_ >= 1.0) || (set.isEmpty && Theme.surfaceOpacity() >= 1.0)) c
    else {
      val dark = 0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue <= 0.5
      val shows = codeLetThrough()
      val share = math.min(if (set.isEmpty) shows else 1.0 - set.get, legibleThrough)
      c.withAlpha(1.0).blend(if (dark) Color.White else Color.Black, share)
    }
  }
  /** A mention's colour, and the faint wash behind it (`mentions`): `Theme.extra` "markdown.mention" and
    * "markdown.mentionWash" where the palette names them, else the accent, and the accent faintly. */
  def mention(): Color = Theme.extra("markdown.mention", Theme.accentText())
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

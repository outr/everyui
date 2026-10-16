package everyui.render

import everyui.markdown.{MarkdownHtml, MarkdownPlain, MdBlock, MdInline, MdItem}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** HTML and text on the clipboard together, and the page's selection copied so.
  *
  * `write` puts both flavours through `navigator.clipboard.write`, falling back to `writeText` where the browser will
  * not write HTML, and to a copy command (both flavours set on its `copy` event) where there is no clipboard API.
  *
  * Once a component says what it is when copied (`Component.copyAs`, the element's `data-copy`), a copy of a selection
  * that touches such elements (Ctrl or Command+C, the browser's Copy) is made here rather than by the browser, which
  * would carry every inline style of the page: the selection is read back into Markdown's blocks — a paragraph, a
  * heading, a list and its items, code, a quote, a rule, a table's rows and cells, from the elements' tags, and the
  * bold, slant, strikethrough, code and links of their words from their runs (`data-run`, `<a href>`) — cut to the
  * words selected where it starts and ends, and written as `MarkdownHtml` and `MarkdownPlain` write them. A selection
  * inside one paragraph, heading or cell copies as those words alone; one inside a code block as code; one across a
  * list's items, a table's rows or a quote's blocks within them. Words in nothing tagged go as a paragraph of their
  * own; what is hidden, unselectable or tagged "none" is left out. */
object RichCopy {
  private var listening = false
  // while `write` copies through a command of its own, the selection's copy keeps out of it
  private var writing = false

  /** The selection's copy made here from now on. */
  def listen(): Unit = if (!listening) {
    listening = true
    dom.document.addEventListener("copy", (e: dom.ClipboardEvent) => if (!writing) selection().foreach { copied =>
      e.clipboardData.setData("text/html", copied._1)
      e.clipboardData.setData("text/plain", copied._2)
      e.preventDefault()
    })
  }

  /** `html` and `text` on the clipboard. */
  def write(html: String, text: String): Unit = {
    val clipboard = dom.window.navigator.asInstanceOf[js.Dynamic].clipboard
    val item = js.Dynamic.global.ClipboardItem
    if (js.isUndefined(clipboard)) command(html, text)
    else if (js.isUndefined(item) || js.isUndefined(clipboard.write)) plain(clipboard, html, text)
    else {
      def blob(content: String, kind: String): js.Dynamic =
        js.Dynamic.newInstance(js.Dynamic.global.Blob)(js.Array(content), js.Dynamic.literal(`type` = kind))
      val data = js.Dynamic.newInstance(item)(js.Dynamic.literal("text/html" -> blob(html, "text/html"),
        "text/plain" -> blob(text, "text/plain")))
      val failed: js.Function1[js.Any, Unit] = _ => plain(clipboard, html, text)
      clipboard.write(js.Array(data)).applyDynamic("catch")(failed)
    }
  }

  // the text alone, or both through a command where the browser will not have even that
  private def plain(clipboard: js.Dynamic, html: String, text: String): Unit = {
    val failed: js.Function1[js.Any, Unit] = _ => command(html, text)
    clipboard.writeText(text).applyDynamic("catch")(failed)
  }

  private def command(markup: String, text: String): Unit = {
    val set: js.Function1[dom.ClipboardEvent, Unit] = e => {
      e.clipboardData.setData("text/html", markup)
      e.clipboardData.setData("text/plain", text)
      e.preventDefault()
    }
    // a selection to copy, for a browser that copies nothing without one
    val area = dom.document.createElement("textarea").asInstanceOf[html.TextArea]
    area.value = text
    dom.document.body.appendChild(area)
    area.select()
    dom.document.addEventListener("copy", set)
    writing = true
    try dom.document.execCommand("copy")
    finally {
      writing = false
      dom.document.removeEventListener("copy", set)
      dom.document.body.removeChild(area)
    }
  }

  /** The selection as HTML and as text, where it touches elements that say how they copy; else None. */
  def selection(): Option[(String, String)] = {
    val selected = dom.window.getSelection()
    if (selected == null || selected.rangeCount == 0 || selected.isCollapsed) None
    else {
      val range = selected.getRangeAt(0)
      val common = range.commonAncestorContainer match {
        case e: dom.Element => e
        case n => n.parentNode.asInstanceOf[dom.Element]
      }
      if (common == null || !tagged(common, range)) None
      else {
        val found = blocks(common, range)
        if (found.isEmpty) None else Some((MarkdownHtml.blocks(found), MarkdownPlain.blocks(found)))
      }
    }
  }

  private def tag(n: dom.Node): String = n match {
    case e: dom.Element => Option(e.getAttribute("data-copy")).getOrElse("")
    case _ => ""
  }

  // the nearest element at or over `n` that says how it copies
  private def marked(n: dom.Element): Option[dom.Element] =
    Option(n.closest("[data-copy]")).filter(e => tag(e) != "none")

  // whether the selection is in, or runs across, something that says how it copies
  private def tagged(common: dom.Element, range: dom.Range): Boolean = marked(common).nonEmpty || {
    val inside = common.querySelectorAll("[data-copy]")
    (0 until inside.length).exists(i => reaches(range, inside(i)))
  }

  // the blocks of the selection, from where it lies: in one paragraph, heading or cell its words alone; in a code
  // block code; in a list, a quote or a table within it
  private def blocks(common: dom.Element, range: dom.Range): List[MdBlock] = marked(common) match {
    case Some(m) =>
      val t = tag(m)
      if (t == "p" || t == "td" || t == "th" || heading(t)) paragraph(words(m, range, t))
      else if (t == "li") flow(m, range)
      else if (t == "tr") Option(m.closest("[data-copy=table]")).map(table => block(table, range)).getOrElse(Nil)
      else block(m, range)
    case None => block(common, range)
  }

  // what `n` holds of the selection
  private def block(n: dom.Node, range: dom.Range): List[MdBlock] = n match {
    case t: dom.Text => if (reaches(range, t)) paragraph(List(MdInline.Text(cut(t, range)))) else Nil
    case _ if !taken(n, range) => Nil
    case e: dom.Element =>
      val t = tag(e)
      if (t == "p") paragraph(words(e, range, t))
      else if (heading(t)) {
        val content = words(e, range, t)
        if (content.isEmpty) Nil else List(MdBlock.Heading(t.charAt(1) - '0', content))
      }
      else if (t == "pre") {
        val code = text(e, range)
        if (code.isEmpty) Nil else List(MdBlock.CodeBlock("", code))
      }
      else if (t == "blockquote") {
        val inside = flow(e, range)
        if (inside.isEmpty) Nil else List(MdBlock.Quote(inside))
      }
      else if (t == "ul" || t.startsWith("ol")) list(e, t, range)
      else if (t == "hr") List(MdBlock.Rule)
      else if (t == "table") table(e, range)
      else if (t == "li") flow(e, range)
      else if (t == "tr" || t == "td" || t == "th") paragraph(words(e, range, t))
      else if (e.querySelector("[data-copy]") != null) flow(e, range)
      else paragraph(words(e, range, ""))
    case _ => Nil
  }

  private def flow(e: dom.Node, range: dom.Range): List[MdBlock] = children(e).flatMap(c => block(c, range))

  private def paragraph(content: List[MdInline]): List[MdBlock] =
    if (MdInline.plain(content).trim.isEmpty) Nil else List(MdBlock.Paragraph(content))

  // a list's items the selection reaches, numbered from the first of them
  private def list(e: dom.Element, t: String, range: dom.Range): List[MdBlock] = {
    val all = under(e, "li")
    val items = all.filter(li => taken(li, range))
    val first = all.indexWhere(li => taken(li, range))
    val start = (if (t.length > 3) t.substring(3).toIntOption.getOrElse(1) else 1) + math.max(0, first)
    val made = items.map(li => MdItem(flow(li, range))).filter(_.blocks.nonEmpty)
    if (made.isEmpty) Nil else List(MdBlock.BulletList(t.startsWith("ol"), start, tight = true, made))
  }

  // a table's rows the selection reaches, its header first where it is one of them
  private def table(e: dom.Element, range: dom.Range): List[MdBlock] = {
    val rows = under(e, "tr").filter(r => taken(r, range)).map { r =>
      val cells = under(r, "td", "th").filter(c => taken(c, range))
      (cells.forall(c => tag(c) == "th"), cells.map(c => words(c.asInstanceOf[dom.Element], range, tag(c))))
    }.filter(_._2.nonEmpty)
    if (rows.isEmpty) Nil
    else if (rows.head._1) List(MdBlock.TableBlock(Nil, rows.head._2, rows.tail.map(_._2)))
    else List(MdBlock.TableBlock(Nil, Nil, rows.map(_._2)))
  }

  // the elements tagged `t` (or `or`) under `e`, not looking inside them
  private def under(e: dom.Node, t: String, or: String = ""): List[dom.Element] = children(e).flatMap {
    case c: dom.Element if tag(c) == t || (or.nonEmpty && tag(c) == or) => List(c)
    case c => if (tag(c) == "none") Nil else under(c, t, or)
  }

  private def heading(t: String): Boolean = t.length == 2 && t.charAt(0) == 'h' && t.charAt(1).isDigit

  private def children(n: dom.Node): List[dom.Node] = {
    val all = n.childNodes
    (0 until all.length).toList.map(i => all(i))
  }

  // whether the selection reaches `n`, and it shows and is not left out
  private def taken(n: dom.Node, range: dom.Range): Boolean = reaches(range, n) && (n match {
    case e: html.Element => tag(e) != "none" && e.style.display != "none"
    case _ => true
  })

  // the part of `t` the selection holds: none of it where its words cannot be selected (a button's), as the nearest
  // `user-select` over it says
  private def cut(t: dom.Text, range: dom.Range): String = {
    val s = t.data
    val from = if (range.startContainer == t) range.startOffset else 0
    val to = if (range.endContainer == t) range.endOffset else s.length
    if (to > from && selectable(t)) s.substring(from, to) else ""
  }

  private def selectable(t: dom.Node): Boolean = {
    var at = t.parentNode
    var said = ""
    while (said.isEmpty && at != null) {
      at match {
        case e: html.Element => said = e.style.getPropertyValue("user-select")
        case _ => ()
      }
      at = at.parentNode
    }
    said != "none"
  }

  // the code's selected text, its elements' lines apart
  private def text(n: dom.Node, range: dom.Range): String = {
    val out = new StringBuilder
    def walk(at: dom.Node): Unit = at match {
      case t: dom.Text => if (reaches(range, t)) out.append(cut(t, range))
      case e: html.Element if taken(e, range) =>
        if (out.nonEmpty && !isRun(e) && out.last != '\n') out.append('\n')
        children(e).foreach(walk)
      case _ => ()
    }
    children(n).foreach(walk)
    MarkdownPlain.trimEnd(out.toString)
  }

  private def isRun(e: dom.Element): Boolean = e.tagName == "SPAN" || e.tagName == "A"

  // whether `range` takes in any of `n` (the DOM's Range.intersectsNode)
  private def reaches(range: dom.Range, n: dom.Node): Boolean =
    range.asInstanceOf[js.Dynamic].intersectsNode(n).asInstanceOf[Boolean]

  // the words of a paragraph, a heading or a cell (`t`) the selection holds, with their marks: its lines (elements of
  // their own) a line apart, or a space where it is no block of words (`t` empty)
  private def words(e: dom.Element, range: dom.Range, t: String): List[MdInline] = {
    // the marks a heading's or a header's own weight would give every word
    val bold = !(heading(t) || t == "th")
    var pieces: List[(String, String, String)] = Nil
    def walk(at: dom.Node, marks: String, href: String): Unit = at match {
      case x: dom.Text => if (reaches(range, x)) {
        val s = cut(x, range)
        if (s.nonEmpty) pieces = (s, marks, href) :: pieces
      }
      case el: html.Element if taken(el, range) =>
        if (pieces.nonEmpty && !isRun(el) && pieces.head._2 != "\n") pieces = ("", "\n", "") :: pieces
        val run = Option(el.getAttribute("data-run")).getOrElse("")
        val link = if (el.tagName == "A") Option(el.getAttribute("href")).getOrElse("") else href
        children(el).foreach(c => walk(c, marks + (if (bold) run else run.replace("b", "")), link))
      case _ => ()
    }
    children(e).foreach(c => walk(c, "", ""))
    // the line breaks at either end gone, and words beside each other with the same marks made one
    var ordered: List[(String, String, String)] = Nil
    pieces.dropWhile(_._2 == "\n").reverse.dropWhile(_._2 == "\n").foreach { p =>
      if (ordered.nonEmpty && p._2 != "\n" && ordered.head._2 == p._2 && ordered.head._3 == p._3)
        ordered = (ordered.head._1 + p._1, p._2, p._3) :: ordered.tail
      else ordered = p :: ordered
    }
    ordered = ordered.reverse
    var out: List[MdInline] = Nil
    var linked: List[MdInline] = Nil
    var linking = ""
    def endLink(): Unit = {
      if (linked.nonEmpty) out = MdInline.Hyperlink(linking, "", linked.reverse) :: out
      linked = Nil
    }
    ordered.foreach { p =>
      if (p._2 == "\n") {
        endLink()
        out = (if (t.isEmpty) MdInline.Text(" ") else MdInline.HardBreak) :: out
      }
      else {
        val piece = styled(p._1, p._2)
        if (p._3 != linking) endLink()
        linking = p._3
        if (p._3.isEmpty) out = piece :: out else linked = piece :: linked
      }
    }
    endLink()
    out.reverse
  }

  // `s` with what its marks say: code, struck, slanted, bold
  private def styled(s: String, marks: String): MdInline = {
    val base: MdInline = if (marks.contains("c")) MdInline.CodeSpan(s) else MdInline.Text(s)
    val struck = if (marks.contains("s")) MdInline.Strike(List(base)) else base
    val slanted = if (marks.contains("i")) MdInline.Emphasis(List(struck)) else struck
    if (marks.contains("b")) MdInline.Strong(List(slanted)) else slanted
  }
}

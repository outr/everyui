package everyui.render

import everyui.rich.{BlockKind, Mark, RichBlock, RichDoc, RichImage, RichInline, RichInput, RichLook, RichPos,
  RichRange}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.typedarray.*

/** A rich document's editing surface on the web (`Nodes.richEditor`, `everyui.rich.RichTextEditor`): an editable
  * element (`contenteditable`) the browser lends its caret, selection, input methods, spell check and dictation, whose
  * every change is turned back before it lands (`beforeinput`) and said instead (`RichInput`), the editor drawing the
  * document again from its model. Only an input method's composition is let land as it goes, since a browser cannot
  * be stopped there, and is said whole as it ends; the document is drawn again then.
  *
  * Each block is an element of its own (`data-b` its index), each run a `span` of its marks' classes, a picture among
  * the words an `img`, a line break a "\n" kept by `white-space: pre-wrap`: so a place in the document is its block and
  * a count of characters (a picture one) across the block's text, both ways. A rule and a picture of its own are not
  * editable. A list item's marker, and a numbered item's number, are drawn by CSS, outside the text. A block a draw
  * finds unchanged keeps its element. Paste and drop are read as HTML and text, or as pictures' bytes. */
class WebRichEditor(val outer: html.Element) {
  val content: html.Div = dom.document.createElement("div").asInstanceOf[html.Div]
  private var hearing: RichInput => Unit = _ => ()
  private var heightHeard: Double => Unit = _ => ()
  private var drawn: Vector[RichBlock] = Vector.empty
  private var pending: Option[(RichDoc, RichRange)] = None
  private var composing: Option[RichRange] = None
  private var placing = false
  private var editable = true
  // a suggestion drawn after the caret, not the document's (`setGhost`): where, its lines, and its element
  private var ghost: Option[(RichPos, List[String])] = None
  private var ghostEl: Option[dom.Element] = None

  WebRichEditor.style()
  outer.classList.add("everyui-rich-scroll")
  content.classList.add("everyui-rich")
  content.setAttribute("contenteditable", "true")
  content.setAttribute("spellcheck", "true")
  content.setAttribute("role", "textbox")
  content.setAttribute("aria-multiline", "true")
  // the keys are the editor's, as an embed's are: only the app's shortcuts are taken from it (Focus.key)
  outer.setAttribute("data-everyui-embed", "rich")
  outer.appendChild(content)

  content.addEventListener("beforeinput", (e: dom.InputEvent) => input(e))
  content.addEventListener("compositionstart", (_: dom.Event) => composing = Some(selection()))
  content.addEventListener("compositionend", (e: dom.CompositionEvent) => {
    val at = composing.getOrElse(selection())
    composing = None
    val words = Option(e.data).getOrElse("")
    // what the composition left in the element is not the document's: drawn again from the model
    drawn = Vector.empty
    if (words.nonEmpty) hearing(RichInput.Typed(at, words))
    else pending.foreach { case (d, s) => draw(d, s) }
  })
  content.addEventListener("paste", (e: dom.ClipboardEvent) => {
    e.preventDefault()
    if (editable) carried(e.clipboardData.asInstanceOf[js.Dynamic], selection())
  })
  content.addEventListener("drop", (e: dom.DragEvent) => {
    e.preventDefault()
    if (editable) carried(e.dataTransfer.asInstanceOf[js.Dynamic], pointAt(e.clientX, e.clientY).getOrElse(selection()))
  })
  content.addEventListener("keydown", (e: dom.KeyboardEvent) => keys(e))
  content.addEventListener("click", (e: dom.MouseEvent) => {
    val target = e.target.asInstanceOf[dom.Element]
    val link = if (target == null) null else target.closest("[data-href]")
    if (link != null && (e.ctrlKey || e.metaKey || !editable)) {
      e.preventDefault()
      hearing(RichInput.LinkPressed(link.getAttribute("data-href")))
    }
  })
  dom.document.addEventListener("selectionchange", (_: dom.Event) =>
    if (!placing && composing.isEmpty && dom.document.activeElement == content)
      hearing(RichInput.Selected(selection())))
  locally {
    val observer = js.Dynamic.global.ResizeObserver
    if (!js.isUndefined(observer)) {
      val measure: js.Function1[js.Any, Unit] = _ => heightHeard(content.offsetHeight.toDouble)
      js.Dynamic.newInstance(observer)(measure).observe(content)
    }
  }

  def hear(f: RichInput => Unit): Unit = hearing = f
  def heights(f: Double => Unit): Unit = {
    heightHeard = f
    f(content.offsetHeight.toDouble)
  }

  /** How it is drawn, what shows while it is empty, and whether it can be edited. */
  def look(look: RichLook, placeholder: String, readOnly: Boolean): Unit = {
    editable = !readOnly
    content.setAttribute("contenteditable", if (readOnly) "false" else "true")
    content.setAttribute("aria-readonly", if (readOnly) "true" else "false")
    content.setAttribute("data-placeholder", placeholder)
    val s = content.style
    s.setProperty("--rich-size", look.size.toString + "px")
    s.setProperty("--rich-family", if (look.family.isEmpty) "'Open Sans'" else RenderNode.families(look.family))
    s.setProperty("--rich-text", look.text.toRGBA)
    s.setProperty("--rich-muted", look.muted.toRGBA)
    s.setProperty("--rich-accent", look.accent.toRGBA)
    s.setProperty("--rich-code", look.codeBackground.toRGBA)
    s.setProperty("--rich-bar", look.quoteBar.toRGBA)
    s.setProperty("--rich-selection", look.selection.toRGBA)
  }

  /** The document drawn, and `selection` put in it where it has the focus; while a composition is going on, kept for
    * its end. */
  def draw(doc: RichDoc, selection: RichRange): Unit =
    if (composing.nonEmpty) pending = Some(doc -> selection)
    else {
      pending = None
      render(doc)
      drawGhost()
      if (dom.document.activeElement == content) place(selection)
    }

  /** The page's focus put in it: once the key being pressed has done what it does (RenderNode.afterKey), so a dialog's
    * Enter that closes it and hands the focus back here does not then split a block. */
  def focus(selection: RichRange): Unit = RenderNode.afterKey { () =>
    content.focus()
    place(selection)
  }

  /** A suggestion drawn dimmed after `at`, its first line in the line there and the rest under it, none of it the
    * document's: the caret, the selection and the places in the document pass it by, it takes no pointer, and a screen
    * reader is told of it by the editor rather than reading it. No lines takes it away. */
  def setGhost(at: RichPos, lines: List[String]): Unit = {
    ghost = if (lines.isEmpty) None else Some(at -> lines)
    drawGhost()
    if (dom.document.activeElement == content && composing.isEmpty) place(selection())
  }

  private def drawGhost(): Unit = {
    ghostEl.foreach { g =>
      val parent = g.parentNode
      if (parent != null) {
        parent.removeChild(g)
        parent.normalize()
      }
    }
    ghostEl = None
    if (composing.isEmpty) ghost.foreach { case (at, lines) =>
      val g = dom.document.createElement("span").asInstanceOf[html.Span]
      g.className = "ghost"
      g.setAttribute("data-ghost", "1")
      g.setAttribute("contenteditable", "false")
      g.setAttribute("aria-hidden", "true")
      lines.zipWithIndex.foreach { case (line, i) =>
        val part = dom.document.createElement("span").asInstanceOf[html.Span]
        if (i > 0) part.className = "gl"
        part.textContent = line
        g.appendChild(part)
      }
      point(at) match {
        case (t: dom.Text, offset) =>
          val after = if (offset < t.length) t.splitText(offset) else t.nextSibling
          t.parentNode.insertBefore(g, after)
        case (parent, index) =>
          val kids = parent.childNodes
          parent.insertBefore(g, if (index < kids.length) kids(index) else null)
      }
      ghostEl = Some(g)
    }
  }

  // ---- drawing ----

  private def render(doc: RichDoc): Unit = {
    val blocks = doc.blocks
    val kept = content.children
    // numbered items count on through a list, each depth its own count
    var numbers = Vector.empty[Int]
    blocks.indices.foreach { i =>
      val b = blocks(i)
      BlockKind.depth(b.kind) match {
        case Some(d) =>
          numbers = numbers.take(d + 1).padTo(d + 1, 0)
          if (b.kind.isInstanceOf[BlockKind.Numbered]) numbers = numbers.updated(d, numbers(d) + 1)
          else numbers = numbers.updated(d, 0)
        case None => numbers = Vector.empty
      }
      val number = BlockKind.depth(b.kind).map(d => numbers(d)).getOrElse(0)
      val same = i < drawn.length && drawn(i) == b && i < kept.length &&
        kept(i).getAttribute("data-n") == number.toString
      if (!same) {
        val made = block(b, number)
        if (i < kept.length) content.replaceChild(made, kept(i)) else content.appendChild(made)
      }
      kept(i).setAttribute("data-b", i.toString)
    }
    while (content.children.length > blocks.length) content.removeChild(content.lastElementChild)
    drawn = blocks
    if (doc.isEmpty) content.setAttribute("data-empty", "true") else content.removeAttribute("data-empty")
  }

  private def block(b: RichBlock, number: Int): html.Element = {
    def made(tag: String, kind: String): html.Element = {
      val el = dom.document.createElement(tag).asInstanceOf[html.Element]
      el.className = "rb " + kind
      el
    }
    val el = b.kind match {
      case BlockKind.Paragraph => made("div", "p")
      case BlockKind.Heading(level) => made("div", "h" + math.max(1, math.min(3, level)).toString)
      case BlockKind.Bullet(depth) =>
        val li = made("div", "li")
        li.style.setProperty("--depth", depth.toString)
        li.setAttribute("data-marker", if (depth % 3 == 0) "•" else if (depth % 3 == 1) "◦" else "▪")
        li
      case BlockKind.Numbered(depth) =>
        val li = made("div", "li")
        li.style.setProperty("--depth", depth.toString)
        li.setAttribute("data-marker", number.toString + ".")
        li
      case BlockKind.Quote => made("div", "q")
      case BlockKind.Code(_) => made("div", "code")
      case BlockKind.Rule =>
        val r = made("div", "rule")
        r.setAttribute("contenteditable", "false")
        r.appendChild(dom.document.createElement("hr"))
        r
      case BlockKind.Picture(image) =>
        val p = made("div", "pic")
        p.setAttribute("contenteditable", "false")
        p.appendChild(picture(image))
        p
    }
    el.setAttribute("data-n", number.toString)
    if (BlockKind.holdsWords(b.kind)) {
      b.inlines.foreach {
        case RichInline.Text(t, marks) => el.appendChild(run(t, marks))
        case RichInline.Image(image, _) =>
          val img = picture(image)
          img.setAttribute("data-i", "1")
          el.appendChild(img)
      }
      // a block with nothing in it, or ending in a line break, needs a line of its own to put the caret on
      if (b.length == 0 || b.text.endsWith("\n")) {
        val br = dom.document.createElement("br")
        br.setAttribute("data-end", "1")
        el.appendChild(br)
      }
    }
    el
  }

  private def run(text: String, marks: Set[Mark]): dom.Node = {
    val span = dom.document.createElement("span").asInstanceOf[html.Span]
    val classes = marks.toList.collect {
      case Mark.Bold => "b"
      case Mark.Italic => "i"
      case Mark.Underline => "u"
      case Mark.Strike => "s"
      case Mark.Code => "c"
      case Mark.Link(_) => "a"
    }
    if (classes.nonEmpty) span.className = classes.mkString(" ")
    Mark.link(marks).foreach { href =>
      span.setAttribute("data-href", href)
      span.title = href
    }
    span.appendChild(dom.document.createTextNode(text))
    span
  }

  private def picture(image: RichImage): html.Image = {
    val img = dom.document.createElement("img").asInstanceOf[html.Image]
    img.src = image.source
    img.alt = image.alt
    img.draggable = false
    image.width.foreach(w => img.style.width = w.toString + "px")
    image.height.foreach(h => img.style.height = h.toString + "px")
    img
  }

  // ---- places: the page's selection and the document's, each way ----

  /** The page's selection in the document's places, or the start where it is not in this editor. */
  def selection(): RichRange = {
    val s = dom.window.getSelection()
    if (s == null || s.rangeCount == 0) RichRange.at(RichPos(0, 0))
    else {
      val a = pos(s.anchorNode, s.anchorOffset)
      val f = pos(s.focusNode, s.focusOffset)
      (a, f) match {
        case (Some(x), Some(y)) => RichRange(x, y)
        case _ => RichRange.at(RichPos(0, 0))
      }
    }
  }

  // the document's place for a point of the page (a node and an offset into it), where it is in a block here
  private def pos(node: dom.Node, offset: Int): Option[RichPos] = {
    if (node == null || !content.contains(node)) None
    else if (node eq content) {
      val n = content.children.length
      if (n == 0) Some(RichPos(0, 0))
      else if (offset >= n) Some(RichPos(n - 1, length(content.children(n - 1))))
      else Some(RichPos(offset, 0))
    }
    else {
      val start: dom.Element = node match {
        case e: dom.Element => e
        case other => other.parentNode.asInstanceOf[dom.Element]
      }
      val blockEl = start.closest("[data-b]")
      if (blockEl == null) None
      else {
        val b = blockEl.getAttribute("data-b").toInt
        // what comes before the point in the block's own order
        var count = 0
        var found = false
        def walk(n: dom.Node): Unit = if (!found) {
          if (n eq node) node match {
            case t: dom.Text =>
              count += offset
              found = true
            case _ =>
              val kids = n.childNodes
              var k = 0
              while (k < math.min(offset, kids.length)) {
                count += lengthOf(kids(k))
                k += 1
              }
              found = true
          }
          else n match {
            case t: dom.Text => count += t.length
            case e: dom.Element if e.hasAttribute("data-ghost") => ()
            case e: dom.Element if e.hasAttribute("data-i") => count += 1
            case _ =>
              val kids = n.childNodes
              var k = 0
              while (k < kids.length && !found) {
                walk(kids(k))
                k += 1
              }
          }
        }
        walk(blockEl)
        Some(RichPos(b, count))
      }
    }
  }

  private def length(el: dom.Element): Int = lengthOf(el)

  private def lengthOf(n: dom.Node): Int = n match {
    case t: dom.Text => t.length
    case e: dom.Element if e.hasAttribute("data-ghost") => 0
    case e: dom.Element if e.hasAttribute("data-i") => 1
    case e: dom.Element if e.hasAttribute("data-end") => 0
    case _ =>
      var sum = 0
      val kids = n.childNodes
      var k = 0
      while (k < kids.length) {
        sum += lengthOf(kids(k))
        k += 1
      }
      sum
  }

  // the page's point for a place in the document
  private def point(p: RichPos): (dom.Node, Int) = {
    val blocks = content.children
    if (blocks.length == 0) (content, 0)
    else {
      val el = blocks(math.max(0, math.min(p.block, blocks.length - 1)))
      var left = p.offset
      var found: Option[(dom.Node, Int)] = None
      def walk(n: dom.Node): Unit = if (found.isEmpty) n match {
        case t: dom.Text =>
          if (left <= t.length) found = Some(t -> left) else left -= t.length
        case e: dom.Element if e.hasAttribute("data-i") =>
          val parent = e.parentNode
          val index = (0 until parent.childNodes.length).indexWhere(k => parent.childNodes(k) eq e)
          if (left == 0) found = Some(parent -> index)
          else {
            left -= 1
            if (left == 0) found = Some(parent -> (index + 1))
          }
        case e: dom.Element if e.hasAttribute("data-ghost") => ()
        case e: dom.Element if e.hasAttribute("data-end") => ()
        case _ =>
          val kids = n.childNodes
          var k = 0
          while (k < kids.length && found.isEmpty) {
            walk(kids(k))
            k += 1
          }
      }
      walk(el)
      found.getOrElse(el -> 0)
    }
  }

  private def place(range: RichRange): Unit = {
    val s = dom.window.getSelection()
    if (s != null) {
      placing = true
      try {
        val (an, ao) = point(range.anchor)
        val (fn, fo) = point(range.focus)
        s.asInstanceOf[js.Dynamic].setBaseAndExtent(an, ao, fn, fo)
      }
      catch { case _: Throwable => () }
      finally placing = false
    }
  }

  // the document's place under a point of the window, for a drop
  private def pointAt(x: Double, y: Double): Option[RichRange] = {
    val d = dom.document.asInstanceOf[js.Dynamic]
    if (js.typeOf(d.caretPositionFromPoint) == "function") {
      val c = d.caretPositionFromPoint(x, y)
      if (c == null) None else pos(c.offsetNode.asInstanceOf[dom.Node], c.offset.asInstanceOf[Int]).map(RichRange.at)
    }
    else if (js.typeOf(d.caretRangeFromPoint) == "function") {
      val r = d.caretRangeFromPoint(x, y)
      if (r == null) None
      else pos(r.startContainer.asInstanceOf[dom.Node], r.startOffset.asInstanceOf[Int]).map(RichRange.at)
    }
    else None
  }

  // a change the browser was about to make, said instead (but a composition's, which cannot be stopped)
  private def input(e: dom.InputEvent): Unit = {
    val kind = e.inputType
    if (kind == "insertCompositionText" || kind == "deleteCompositionText" || kind == "insertFromComposition") ()
    else {
      e.preventDefault()
      if (editable) {
        val target = targeted(e).getOrElse(selection())
        val data = Option(e.data).getOrElse("")
        kind match {
          case "insertText" => if (data.nonEmpty) hearing(RichInput.Typed(target, data))
          case "insertReplacementText" =>
            val words = Option(e.asInstanceOf[js.Dynamic].dataTransfer).filterNot(js.isUndefined)
              .map(t => t.asInstanceOf[js.Dynamic].getData("text/plain").asInstanceOf[String]).getOrElse(data)
            hearing(RichInput.Typed(target, words))
          case "insertParagraph" => hearing(RichInput.Split(target))
          case "insertLineBreak" => hearing(RichInput.LineBreak(target))
          case k if k.startsWith("delete") =>
            hearing(RichInput.Deleted(target, k.contains("Backward"), k.contains("Word")))
          case "historyUndo" => hearing(RichInput.Command("undo"))
          case "historyRedo" => hearing(RichInput.Command("redo"))
          case "formatBold" => hearing(RichInput.Command("bold"))
          case "formatItalic" => hearing(RichInput.Command("italic"))
          case "formatUnderline" => hearing(RichInput.Command("underline"))
          case _ => ()
        }
      }
    }
  }

  // where the browser says a change lands (a word deleted, a spelling suggestion), else the selection
  private def targeted(e: dom.InputEvent): Option[RichRange] = {
    val ranges = e.asInstanceOf[js.Dynamic].getTargetRanges
    if (js.typeOf(ranges) != "function") None
    else {
      val all = e.asInstanceOf[js.Dynamic].getTargetRanges().asInstanceOf[js.Array[js.Dynamic]]
      all.headOption.flatMap { r =>
        for {
          a <- pos(r.startContainer.asInstanceOf[dom.Node], r.startOffset.asInstanceOf[Int])
          b <- pos(r.endContainer.asInstanceOf[dom.Node], r.endOffset.asInstanceOf[Int])
        } yield RichRange(a, b)
      }
    }
  }

  // a paste's or a drop's words, or its pictures
  private def carried(data: js.Dynamic, at: RichRange): Unit = if (data != null && !js.isUndefined(data)) {
    val files = Option(data.files).filterNot(js.isUndefined).map(_.asInstanceOf[js.Array[dom.File]])
      .getOrElse(js.Array[dom.File]())
    val pictures = files.filter(_.`type`.startsWith("image/")).toList
    if (pictures.nonEmpty) {
      var read = List.empty[(Int, (String, Array[Byte]))]
      pictures.zipWithIndex.foreach { case (f, i) =>
        val bytes = f.asInstanceOf[js.Dynamic].arrayBuffer().asInstanceOf[js.Promise[ArrayBuffer]]
        bytes.`then`[Unit] { (b: ArrayBuffer) =>
          read = (i -> (f.`type` -> new Int8Array(b).toArray)) :: read
          if (read.length == pictures.length) hearing(RichInput.Pictures(at, read.sortBy(_._1).map(_._2)))
        }
      }
    }
    else {
      val htmlText = Option(data.getData("text/html")).filterNot(js.isUndefined).map(_.toString).getOrElse("")
      val text = Option(data.getData("text/plain")).filterNot(js.isUndefined).map(_.toString).getOrElse("")
      if (htmlText.nonEmpty || text.nonEmpty) hearing(RichInput.Pasted(at, htmlText, text))
    }
  }

  // the editor's own keys: the marks', undo's and redo's, and Tab, which the editor answers or hands back
  private def keys(e: dom.KeyboardEvent): Unit = {
    val primary = if (Platform.commandKey()) e.metaKey else e.ctrlKey
    val key = e.key.toLowerCase
    val command =
      if (primary && !e.altKey && key == "b") Some("bold")
      else if (primary && !e.altKey && key == "i") Some("italic")
      else if (primary && !e.altKey && key == "u") Some("underline")
      else if (primary && !e.altKey && key == "k") Some("link")
      else if (primary && !e.shiftKey && key == "z") Some("undo")
      else if (primary && ((e.shiftKey && key == "z") || key == "y")) Some("redo")
      else if (e.key == "Tab" && !primary && !e.altKey) Some(if (e.shiftKey) "outdent" else "indent")
      // a suggestion put away, and nothing else: the dialog the editor is in stays open
      else if (e.key == "Escape" && ghost.nonEmpty) Some("dismiss")
      else None
    command.foreach { c =>
      e.preventDefault()
      e.stopPropagation()
      hearing(RichInput.Command(c))
    }
  }
}

object WebRichEditor {
  private var styled = false

  // the editor's look, once for the page: each kind of block and mark, the list markers, the placeholder
  private def style(): Unit = if (!styled) {
    styled = true
    val css = dom.document.createElement("style")
    css.textContent =
      """.everyui-rich-scroll { overflow-y: auto; overflow-x: hidden; }
        |.everyui-rich .ghost { opacity: 0.45; pointer-events: none; user-select: none; -webkit-user-select: none; }
        |.everyui-rich .ghost .gl { display: block; }
        |.everyui-rich { outline: none; min-height: 100%; box-sizing: border-box; padding: 2px 0;
        |  color: var(--rich-text); font-family: var(--rich-family); font-size: var(--rich-size); line-height: 1.5;
        |  white-space: pre-wrap; overflow-wrap: anywhere; caret-color: var(--rich-accent); cursor: text; }
        |.everyui-rich ::selection { background: var(--rich-selection); }
        |.everyui-rich[data-empty]::before { content: attr(data-placeholder); color: var(--rich-muted);
        |  position: absolute; pointer-events: none; }
        |.everyui-rich .rb { margin: 0 0 0.5em 0; position: relative; }
        |.everyui-rich .h1 { font-size: 1.6em; font-weight: 700; line-height: 1.3; }
        |.everyui-rich .h2 { font-size: 1.3em; font-weight: 700; line-height: 1.3; }
        |.everyui-rich .h3 { font-size: 1.1em; font-weight: 700; }
        |.everyui-rich .li { margin: 0 0 0.2em 0; padding-left: calc(1.5em + var(--depth) * 1.5em); }
        |.everyui-rich .li::before { content: attr(data-marker); position: absolute; color: var(--rich-muted);
        |  left: calc(var(--depth) * 1.5em); width: 1.2em; text-align: right; }
        |.everyui-rich .q { border-left: 3px solid var(--rich-bar); padding-left: 0.8em; color: var(--rich-muted); }
        |.everyui-rich .code { font-family: 'Roboto Mono', monospace; font-size: 0.9em; background: var(--rich-code);
        |  padding: 0.5em 0.7em; border-radius: 6px; }
        |.everyui-rich .rule hr { border: none; border-top: 1px solid var(--rich-bar); margin: 0.6em 0; }
        |.everyui-rich img { max-width: 100%; vertical-align: text-bottom; }
        |.everyui-rich .pic img { display: block; border-radius: 6px; }
        |.everyui-rich .b { font-weight: 700; }
        |.everyui-rich .i { font-style: italic; }
        |.everyui-rich .u { text-decoration: underline; }
        |.everyui-rich .s { text-decoration: line-through; }
        |.everyui-rich .u.s { text-decoration: underline line-through; }
        |.everyui-rich .c { font-family: 'Roboto Mono', monospace; font-size: 0.9em; background: var(--rich-code);
        |  border-radius: 4px; padding: 0 0.2em; }
        |.everyui-rich .a { color: var(--rich-accent); text-decoration: underline; }
        |""".stripMargin
    dom.document.head.appendChild(css)
  }
}

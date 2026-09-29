package everyui.render

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** An element and all in it copied for a snapshot (`SnapshotAccess`), each with the style the page gives it written
  * on, what differs from a plain element's; and what is to be fetched for it (pictures, faces) noted as it goes. */
private[render] final class SnapshotCopy {
  /** An element, the attribute a picture's address goes in, and the address. */
  val pictures = js.Array[(dom.Element, String, String)]()
  /** An element, a style property of it naming a URL, and its value. */
  val urls = js.Array[(html.Element, String, String)]()
  /** The families the words are drawn in. */
  val families = js.Dictionary[Boolean]()
  /** Whether any words go beyond the first face of their list (emoji, other scripts): the fallbacks are carried too. */
  var wide = false
  /** Rules the style attribute cannot hold (a field's placeholder). */
  var rules = ""
  private val ids = js.Dictionary[Boolean]()
  private var made = 0

  def of(n: dom.Node, root: Boolean): Option[dom.Node] = n.nodeType match {
    case 3 => Some(n.cloneNode(false))
    case 1 =>
      val el = n.asInstanceOf[dom.Element]
      val tag = el.tagName.toUpperCase
      val computed = dom.window.getComputedStyle(el)
      if (!root && computed.display == "none") None
      else if (tag == "SCRIPT" || tag == "STYLE" || tag == "LINK" || tag == "NOSCRIPT" || tag == "TEMPLATE") None
      else if (el.namespaceURI != SnapshotAccess.xhtml) Some(svg(el, computed))
      else Some(element(el.asInstanceOf[html.Element], tag, computed))
    case _ => None
  }

  /** An SVG subtree as it is: its attributes say how it is drawn. Only its root takes the style it has in the page. */
  private def svg(el: dom.Element, computed: dom.CSSStyleDeclaration): dom.Node = {
    val clone = el.cloneNode(true).asInstanceOf[dom.Element]
    if (el.tagName.toLowerCase == "svg") styled(clone.asInstanceOf[html.Element], "svg", computed)
    clone
  }

  private def element(el: html.Element, tag: String, computed: dom.CSSStyleDeclaration): dom.Node = {
    val clone: html.Element = tag match {
      case "IMG" =>
        val img = dom.document.createElement("img").asInstanceOf[html.Element]
        val src = el.asInstanceOf[js.Dynamic].currentSrc.asInstanceOf[String]
        val url = if (src != null && src.nonEmpty) src else el.getAttribute("src")
        if (url != null && url.nonEmpty) pictures.push((img, "src", url))
        if (el.hasAttribute("alt")) img.setAttribute("alt", el.getAttribute("alt"))
        img
      case "VIDEO" | "CANVAS" =>
        val img = dom.document.createElement("img").asInstanceOf[html.Element]
        SnapshotAccess.frameOf(el).foreach(data => img.setAttribute("src", data))
        img
      case "IFRAME" | "EMBED" | "OBJECT" => dom.document.createElement("div").asInstanceOf[html.Element]
      case "SELECT" => el.cloneNode(true).asInstanceOf[html.Element]
      case _ =>
        val c = el.cloneNode(false).asInstanceOf[html.Element]
        c.removeAttribute("id")
        c
    }
    styled(clone, tag, computed)
    fields(el, clone, tag)
    if (tag != "IMG" && tag != "VIDEO" && tag != "CANVAS" && tag != "IFRAME" && tag != "EMBED" && tag != "OBJECT" &&
        tag != "TEXTAREA" && tag != "SELECT") {
      val scrolled = el.scrollTop != 0.0 || el.scrollLeft != 0.0
      val into: dom.Element =
        if (!scrolled) clone
        else {
          val inner = dom.document.createElement("div").asInstanceOf[html.Element]
          val px = SnapshotAccess.px
          inner.setAttribute("style", "position:absolute;left:" + px(-el.scrollLeft) + ";top:" + px(-el.scrollTop) +
            ";width:" + px(el.scrollWidth.toDouble) + ";height:" + px(el.scrollHeight.toDouble))
          clone.appendChild(inner)
          inner
        }
      var words = false
      var child = el.firstChild
      while (child != null) {
        if (child.nodeType == 3 && child.textContent.trim.nonEmpty) {
          words = true
          if (SnapshotAccess.beyondLatin(child.textContent)) wide = true
        }
        of(child, false).foreach(c => into.appendChild(c))
        child = child.nextSibling
      }
      if (words) family(computed)
    }
    else if (tag == "TEXTAREA" || tag == "SELECT") family(computed)
    clone
  }

  /** A field's value, which its element's attributes do not hold. */
  private def fields(el: html.Element, clone: html.Element, tag: String): Unit = tag match {
    case "INPUT" =>
      val input = el.asInstanceOf[html.Input]
      clone.setAttribute("value", input.value)
      if (input.checked) clone.setAttribute("checked", "") else clone.removeAttribute("checked")
      if (input.value.nonEmpty && SnapshotAccess.beyondLatin(input.value)) wide = true
      family(dom.window.getComputedStyle(el))
      placeholder(el, clone)
    case "TEXTAREA" =>
      clone.textContent = el.asInstanceOf[html.TextArea].value
      placeholder(el, clone)
    case "SELECT" =>
      val select = el.asInstanceOf[html.Select]
      val options = clone.getElementsByTagName("option")
      var i = 0
      while (i < options.length) {
        val option = options(i)
        if (i == select.selectedIndex) option.setAttribute("selected", "") else option.removeAttribute("selected")
        i += 1
      }
    case _ => ()
  }

  private def placeholder(el: html.Element, clone: html.Element): Unit = if (el.hasAttribute("placeholder")) {
    val shown = dom.window.getComputedStyle(el, "::placeholder")
    made += 1
    val name = "everyui-snapshot-" + made.toString
    clone.classList.add(name)
    rules += " ." + name + "::placeholder { color: " + shown.color + "; opacity: " + shown.opacity + "; }"
  }

  /** The style the browser works out for `el`, what differs from a plain element's written onto `clone`. Nothing
    * moves in a picture, so no transition or animation goes with it, and what scrolls draws no scroll bar. */
  private def styled(clone: html.Element, tag: String, computed: dom.CSSStyleDeclaration): Unit = {
    val plain = SnapshotAccess.defaults(tag)
    val out = new StringBuilder
    var i = 0
    while (i < computed.length) {
      val name = computed.item(i)
      val value = computed.getPropertyValue(name)
      if (!plain.get(name).contains(value) && !name.startsWith("transition") && !name.startsWith("animation") &&
          name != "will-change") {
        out.append(name).append(':').append(value).append(';')
        if (value.contains("url(")) references(name, value, clone)
      }
      i += 1
    }
    val overflow = computed.overflowX == "auto" || computed.overflowX == "scroll" || computed.overflowY == "auto" ||
      computed.overflowY == "scroll"
    if (overflow) out.append("overflow:hidden;")
    clone.setAttribute("style", out.toString)
  }

  /** A URL in a style: another element of the page (an SVG filter) copied in with it, and an address fetched. */
  private def references(name: String, value: String, clone: html.Element): Unit = {
    val all = SnapshotAccess.addresses(value)
    all.filter(_.startsWith("#")).foreach(url => ids(url.substring(1)) = true)
    if (all.exists(url => !url.startsWith("#") && !url.startsWith("data:"))) urls.push((clone, name, value))
  }

  /** The page's elements the copy points at by id, in an SVG of no size of their own. */
  def references(frame: dom.Element): Unit = if (ids.nonEmpty) {
    val holder = dom.document.createElementNS("http://www.w3.org/2000/svg", "svg")
    holder.setAttribute("width", "0")
    holder.setAttribute("height", "0")
    holder.setAttribute("style", "position:absolute")
    ids.keys.foreach { id =>
      val found = dom.document.getElementById(id)
      if (found != null) holder.appendChild(found.cloneNode(true))
    }
    frame.appendChild(holder)
  }

  private def family(computed: dom.CSSStyleDeclaration): Unit =
    computed.fontFamily.split(",").map(SnapshotAccess.unquoted).find(_.nonEmpty).foreach(f => families(f) = true)
}

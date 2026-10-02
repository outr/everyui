package everyui.seo

/** A `Doc` as semantic HTML: what the server writes into the page (`DocHtml.snapshot`) so that whatever reads it
  * without running the app — a crawler, an answer engine, the browser before the script arrives — has the page's
  * content, its headings and links real elements. The app removes it when it has drawn the page itself
  * (`Platform.drawn`). */
object DocHtml {
  private val void = Set("img", "br", "hr", "meta", "link")

  def render(doc: Doc): String = {
    val b = new StringBuilder
    write(doc, b)
    b.toString
  }

  /** The page's content, in the element the app replaces (`#everyui-snapshot`, inside the page's `#app`). It scrolls
    * itself, as the page around an app does not (`overflow: hidden` on `html`, `body` and `#app`), so a page longer
    * than the window can be read to its end before the app draws. */
  def snapshot(doc: Doc): String =
    "<div id=\"everyui-snapshot\" style=\"box-sizing:border-box;height:100%;overflow-y:auto\">" + render(doc) + "</div>"

  private def write(doc: Doc, b: StringBuilder): Unit =
    if (doc.tag.isEmpty) {
      b.append(Escape.html(doc.text))
      doc.children.foreach(write(_, b))
    } else {
      b.append('<').append(doc.tag)
      doc.attributes.foreach { case (k, v) => b.append(' ').append(k).append("=\"").append(Escape.attribute(v)).append('"') }
      b.append('>')
      if (!void.contains(doc.tag)) {
        b.append(Escape.html(doc.text))
        b.append(doc.html)
        doc.children.foreach(write(_, b))
        b.append("</").append(doc.tag).append('>')
      }
    }
}

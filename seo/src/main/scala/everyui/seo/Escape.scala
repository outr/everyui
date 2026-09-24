package everyui.seo

/** Text made safe for where it goes in a page. */
object Escape {
  /** For an element's content. */
  def html(s: String): String = {
    val b = new StringBuilder(s.length + 16)
    s.foreach {
      case '&' => b.append("&amp;")
      case '<' => b.append("&lt;")
      case '>' => b.append("&gt;")
      case c => b.append(c)
    }
    b.toString
  }

  /** For an attribute's value, in double quotes. */
  def attribute(s: String): String = html(s).replace("\"", "&quot;")

  /** For the inside of a `<script>`: no `</` can end it early. */
  def script(s: String): String = s.replace("</", "<\\/")
}

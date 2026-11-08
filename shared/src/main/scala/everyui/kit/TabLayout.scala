package everyui.kit

import everyui.store.Codec

/** How a `TabGroups` is laid out, to remember and restore (`TabGroups.tabLayout`, `restore`): a group, its `tabs` by their
  * ids and the one `selected`; or, with two `parts`, a split, one beside the other (one over the other where
  * `vertical`), the first given `ratio` of the room. A group keeps its own say on staying empty (`keepEmpty`,
  * `TabGroup.keepEmpty`) and the app's `data` about it (`TabGroup.data`: a name, a mark that it is pinned). Plain
  * fields only, so fabric derives it as it is (`given RW[TabLayout] = RW.gen`), and `TabLayout.codec` keeps it as JSON
  * with no fabric at all, for `Stored`:
  *
  * {{{
  *   val saved: Var[TabLayout] = Stored("editor", "layout", TabLayout())(using TabLayout.codec)
  *   groups.restore(saved(), file)
  *   groups.layoutChanged.attach(l => saved @= l)
  * }}} */
case class TabLayout(tabs: List[String] = Nil,
                     selected: String = "",
                     vertical: Boolean = false,
                     ratio: Double = 0.5,
                     parts: List[TabLayout] = Nil,
                     keepEmpty: Option[Boolean] = None,
                     data: Map[String, String] = Map.empty) {
  /** Whether this is one group, rather than a split. */
  def isGroup: Boolean = parts.isEmpty

  /** The groups in it, first to last. */
  def groups: List[TabLayout] = if (isGroup) List(this) else parts.flatMap(_.groups)

  /** As JSON: `{"tabs":["a"],"selected":"a","vertical":false,"ratio":0.5,"parts":[],"keepEmpty":null,"data":{}}`. */
  def json: String = "{\"tabs\":[" + tabs.map(TabLayout.quoted).mkString(",") + "],\"selected\":" +
    TabLayout.quoted(selected) + ",\"vertical\":" + (if (vertical) "true" else "false") + ",\"ratio\":" + ratio.toString +
    ",\"parts\":[" + parts.map(_.json).mkString(",") + "],\"keepEmpty\":" +
    keepEmpty.map(k => if (k) "true" else "false").getOrElse("null") + ",\"data\":{" +
    data.toList.sortBy(_._1).map(e => TabLayout.quoted(e._1) + ":" + TabLayout.quoted(e._2)).mkString(",") + "}}"
}

object TabLayout {
  /** A layout kept as JSON (`json`), read back by `parse`: what `Stored` needs. */
  lazy val codec: Codec[TabLayout] = Codec.from[TabLayout](_.json, parse)

  /** The layout `text` holds as JSON, or None where it holds none: a field it does not know is passed over. */
  def parse(text: String): Option[TabLayout] = {
    val reader = new TabLayoutReader(text)
    try {
      val l = reader.layout()
      if (reader.ended) Some(l) else None
    }
    catch { case _: Throwable => None }
  }

  private[kit] def quoted(s: String): String = {
    var out = "\""
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      out = out + (if (c == '"') "\\\"" else if (c == '\\') "\\\\" else if (c == '\n') "\\n" else if (c < ' ') " " else c.toString)
      i += 1
    }
    out + "\""
  }
}

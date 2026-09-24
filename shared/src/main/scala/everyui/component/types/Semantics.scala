package everyui.component.types

/** What a component is, for whatever reads the page rather than looking at it: a screen reader, a search engine, an
  * answer engine. `role` is the ARIA role ("heading", "main", "navigation", "banner", "contentinfo", "article",
  * "list", "listitem", "img"), `level` a heading's rank (1 for the page's title), and `label` its name when what it
  * shows does not say (an icon, a picture). Nothing, the default, is a component that is only drawn. */
case class Semantics(role: String = "", level: Int = 0, label: String = "")

object Semantics {
  /** Only drawn: nothing to say about it. */
  val Plain: Semantics = Semantics()

  /** A heading of rank `level`: 1 for the page's title, 2 for its sections, and so on. */
  def heading(level: Int): Semantics = Semantics("heading", math.max(1, math.min(6, level)))
  /** The page's own content, apart from what every page has around it. */
  val main: Semantics = Semantics("main")
  /** A set of links around the site. */
  def navigation(label: String = ""): Semantics = Semantics("navigation", label = label)
  /** The site's header. */
  val banner: Semantics = Semantics("banner")
  /** The site's footer. */
  val contentInfo: Semantics = Semantics("contentinfo")
  /** Something whole in itself: a post, a project, a card that stands alone. */
  val article: Semantics = Semantics("article")
  val list: Semantics = Semantics("list")
  val listItem: Semantics = Semantics("listitem")
  /** A picture made of other components, named `label`. */
  def image(label: String): Semantics = Semantics("img", label = label)
}

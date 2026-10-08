package everyui.component.types

/** What a component is, for whatever reads the page rather than looking at it: a screen reader, a search engine, an
  * answer engine. `role` is the ARIA role ("heading", "main", "navigation", "banner", "contentinfo", "article",
  * "list", "listitem", "img"), `level` a heading's rank (1 for the page's title), and `label` its name when what it
  * shows does not say (an icon, a picture). A control that sets a number (a slider) says where it stands (`range`),
  * one that is on or off (a switch) whether it is (`checked`), and one that shows or hides a part (a pane's header)
  * whether that part is open (`expanded`). Nothing, the default, is a component that is only drawn. */
case class Semantics(role: String = "", level: Int = 0, label: String = "", range: Option[SemanticRange] = None,
                     checked: Option[Boolean] = None, expanded: Option[Boolean] = None)

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
  /** Something pressed to act, named `label` where what it shows does not say (an icon); empty, it is read by its
    * words. */
  def button(label: String = ""): Semantics = Semantics("button", label = label)
  /** A control that sets a number, named `label`, where `range` says. */
  def slider(label: String, range: SemanticRange): Semantics = Semantics("slider", label = label, range = Some(range))
  /** A control that is on or off, named `label`. */
  def switch(label: String, on: Boolean): Semantics = Semantics("switch", label = label, checked = Some(on))
  /** A button that opens and closes a part, named `label`, saying whether the part is open. */
  def disclosure(label: String, open: Boolean): Semantics = Semantics("button", label = label, expanded = Some(open))
  /** A part of the page that stands out enough to be found by its name, `label` (a pane under its title). */
  def region(label: String): Semantics = Semantics("region", label = label)
}

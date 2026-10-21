package everyui.component

import everyui.event.Key
import everyui.focus.Focus
import everyui.render.{Nodes, Platform}
import reactify.*

/** Something the app does not draw itself, in a box the app lays out: a raw element or view the platform lends (the
  * web's own element, reached through the web backend's `node.el`), or, given a `site`, a web guest (a code editor, a
  * terminal) run from its files.
  *
  * The guest runs in the app's own page on the web, and in a web view of its own on Android and the desktop. Where
  * nothing can run it (a Roku, an Apple TV, a desktop built without JavaFX's web view) the `fallback` shows in its
  * place, and nothing is sent or heard.
  *
  *   - **Size.** It has none of its own: the container gives it one, and `onSize` tells the guest each new box.
  *   - **Focus in.** The app's focus arriving here (an arrow, Tab, `focus()`) gives the guest the platform's focus and
  *     calls `guestFocus(true)`, for the guest to put its caret where it belongs.
  *   - **Focus from the guest.** A click in the guest brings the app's focus here (`taken`), so something is always
  *     focused and nothing is found by position.
  *   - **Keys.** While the guest has the platform's focus, every key is its own (`everyui.keymap.Keymap.embed`, named
  *     by `name`), Escape included, but for the chords the app keeps.
  *   - **Focus out.** Only where stated: the guest hands a key back (`handBack`: an editor's Escape with no popup to
  *     close), or a remote's Back arrives, and the focus goes to `exit`, or, with none, on as that key would anyway
  *     (Back to the screen).
  *
  * A guest talks to the app in text, through `send` and `hear`; what the text says is between them. On the web every
  * embed of one site shares the page's one copy of the guest (`runtime` names it), so a guest keeps its embeds apart
  * itself (by `id`, the element each draws into); elsewhere each embed is a runtime of its own. */
class Embed(val name: String, val site: EmbedSite = null, fallback: => Component = null)
  extends Container(Nodes.embed(name, site)) {
  /** Whether the guest runs here, rather than the fallback showing. */
  val live: Boolean = site == null || Platform.embeds()

  /** Where the focus goes when the guest hands it out; none leaves the key to do what it does anywhere else. */
  val exit: Var[Option[Component]] = own(Var(None))

  /** What the guest does as the app's focus arrives here (true) or leaves (false). */
  var guestFocus: Boolean => Unit = _ => ()

  /** The id of the element the guest draws into (`everyuiEmbed.container(id)`). */
  def id: String = node.embedId()

  /** Which copy of the guest this embed shows: the same for every embed of a site on the web, its own elsewhere. */
  def runtime: String = node.embedRuntime()

  /** Say `message` to the guest. What is said before the guest has loaded waits for it. */
  def send(message: String): Unit = if (live) node.embedSend(message)

  /** Hear what the guest says (on the web, every embed of the site hears all of it). */
  def hear(f: String => Unit): Unit = if (live) node.onEmbedMessage(f)

  /** Its box, as it changes, once it has one: what the guest lays itself out to. */
  def onSize(f: (Double, Double) => Unit): Unit = {
    def tell(): Unit = if (size.width() > 0.0 && size.height() > 0.0) f(size.width(), size.height())
    size.width.attach(_ => tell())
    size.height.attach(_ => tell())
    tell()
  }

  /** The guest took the platform's focus itself (a click in it): the app's focus follows it here. */
  def taken(): Unit = if (!focused()) {
    fromGuest = true
    try focus()
    finally fromGuest = false
  }

  /** A key the guest had and does not want, pressed again for the app: to `exit` if it is Escape or Back and one is
    * stated, else as the key would go anywhere. Whether anything took it. */
  def handBack(key: String, ctrl: Boolean = false, meta: Boolean = false, alt: Boolean = false,
               shift: Boolean = false): Boolean = Focus.handBack(key, ctrl, meta, alt, shift)

  /** The focus to `exit`, if one is stated. */
  def leave(): Boolean = exit() match {
    case Some(target) =>
      target.focus()
      true
    case None => false
  }

  private var fromGuest = false

  if (live) {
    focusable @= true
    clickFocus @= true
    focused.attach { on =>
      node.focusEmbed(on)
      if (!fromGuest) guestFocus(on)
    }
    node.onEmbedFocus(on => if (on) taken())
    event.key.down.attach(e => if (e.key == Key.Escape || e.key == Key.Back) leave())
  }
  else if (fallback != null) {
    val shown = fallback
    shown.size.width := size.width()
    shown.size.height := size.height()
    children += shown
  }

  override def keepsKey(key: Key): Boolean = (key == Key.Escape || key == Key.Back) && exit().nonEmpty

  override def dispose(): Unit = {
    if (live) node.releaseEmbed()
    super.dispose()
  }

  override def takesTyping: Boolean = live

  override def kind: String = "Embed(" + name + ")"
}

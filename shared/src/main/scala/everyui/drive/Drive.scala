package everyui.drive

import everyui.component.{ImageView, MaterialIconView, TextInput, TextView}
import everyui.component.Component
import everyui.focus.Focus
import everyui.render.Platform
import everyui.theme.UiScale

/** A test's input to a running app, a line at a time, as a Roku's ECP drives a channel: what a platform's test
  * channel hears (the Apple backend's, `tools/apple-drive.sh`, in a build made to be driven) is answered here with what
  * to do and what to say, `<action>\t<reply>`, the platform doing the action through its own input, so a tap is a
  * touch the app hears as any other and a key a key.
  *
  * The lines, points being the screen's (a screenshot's pixels over the screen's scale):
  *   - `tap x y`, `hold x y [ms]` (a long press, 600 by default), `swipe x1 y1 x2 y2 [ms]` (300 by default)
  *   - `key name`: the web's names (`ArrowDown`, `Enter`, `Escape`) or `up`, `down`, `left`, `right`, `select` (`ok`,
  *     `enter`), `back` (`menu`), `escape`, `tab`, `space`, `backspace`, `play`
  *   - `type words`: into the text dialog that is up, or else a key for each character
  *   - `press label`: a tap on the middle of the component showing that is named so (`nameOf`: its semantics
  *     label, else its words), the same words first, then in any case, then a part of them; of those, what can take
  *     the focus before what cannot, and the smallest
  *   - `find label`: where that is, with no tap; `labels`: each focusable component showing, by name and where it is;
  *     `focused`: what has the focus; `ping`: the platform, the device and whether a remote drives it
  *
  * Actions are `tap x y`, `hold x y ms`, `swipe x1 y1 x2 y2 ms` in the app's units (inside the safe area, over the
  * size it is drawn at), `key name`, `type words`, and `say`, which does nothing. */
object Drive {
  /** Between an answer's action and its reply. (Built of a plain literal: an interpolated one's escapes are left to
    * the runtime, which the Roku and Swift emitters do not run.) */
  val Sep: String = "\t"

  /** The verbs whose numbers come first, and how many they want at least. */
  private val counts: Map[String, Int] = Map("tap" -> 2, "hold" -> 2, "swipe" -> 4)

  /** A line taken apart: its verb, its numbers, and the rest as words with any quotes round them taken off. */
  def parse(line: String): DriveCommand = {
    val trimmed = line.trim
    val space = trimmed.indexOf(' ')
    val verb = (if (space < 0) trimmed else trimmed.substring(0, space)).toLowerCase
    val rest = if (space < 0) "" else trimmed.substring(space + 1).trim
    if (counts.contains(verb)) {
      val parts = rest.split(' ').toVector.filter(_.nonEmpty)
      if (parts.forall(isNumber)) DriveCommand(verb, parts.map(_.toDouble), "")
      else DriveCommand(verb, Vector.empty, rest)
    } else DriveCommand(verb, Vector.empty, unquoted(rest))
  }

  private def isNumber(s: String): Boolean =
    try {
      s.toDouble
      true
    } catch { case _: NumberFormatException => false }

  private def unquoted(s: String): String = {
    val quoted = s.length >= 2 && ((s.head == '"' && s.last == '"') || (s.head == '\'' && s.last == '\''))
    if (quoted) s.substring(1, s.length - 1) else s
  }

  /** A key by the web's name, from its name or a short one. */
  def keyName(name: String): String = name.toLowerCase match {
    case "up" => "ArrowUp"
    case "down" => "ArrowDown"
    case "left" => "ArrowLeft"
    case "right" => "ArrowRight"
    case "select" | "ok" | "enter" | "return" => "Enter"
    case "back" | "menu" => "Back"
    case "escape" | "esc" => "Escape"
    case "tab" => "Tab"
    case "space" => " "
    case "backspace" => "Backspace"
    case "delete" => "Delete"
    case "play" | "pause" | "playpause" => "MediaPlayPause"
    case _ => name
  }

  /** What to do for `line` and what to say, `<action>\t<reply>`, the app's components under `root` (`ui`). */
  def answer(line: String): String = answer(parse(line), everyui.ui)

  def answer(c: DriveCommand, root: Component): String = {
    val n = c.numbers
    c.verb match {
      case "tap" if n.length >= 2 =>
        val (x, y) = inApp(n(0), n(1))
        s"tap ${num(x)} ${num(y)}${Sep}ok tap ${num(n(0))},${num(n(1))}"
      case "hold" if n.length >= 2 =>
        val (x, y) = inApp(n(0), n(1))
        val ms = if (n.length > 2) n(2) else 600.0
        s"hold ${num(x)} ${num(y)} ${num(ms)}${Sep}ok hold ${num(n(0))},${num(n(1))} for ${num(ms)} ms"
      case "swipe" if n.length >= 4 =>
        val (x1, y1) = inApp(n(0), n(1))
        val (x2, y2) = inApp(n(2), n(3))
        val ms = if (n.length > 4) n(4) else 300.0
        s"swipe ${num(x1)} ${num(y1)} ${num(x2)} ${num(y2)} ${num(ms)}${Sep}" +
          s"ok swipe ${num(n(0))},${num(n(1))} to ${num(n(2))},${num(n(3))} in ${num(ms)} ms"
      case "key" if c.words.nonEmpty =>
        val name = keyName(c.words)
        s"key $name${Sep}ok key $name"
      case "type" if c.words.nonEmpty => s"type ${c.words}${Sep}ok typed ${c.words.length} characters"
      case "press" if c.words.nonEmpty =>
        find(root, c.words) match {
          case Some(found) =>
            val (x, y) = middle(found)
            val (sx, sy) = onScreen(x, y)
            s"tap ${num(x)} ${num(y)}${Sep}ok pressed ${Focus.named(found)} at ${num(sx)},${num(sy)}"
          case None => s"say${Sep}error nothing showing is named ${c.words}"
        }
      case "find" if c.words.nonEmpty =>
        find(root, c.words) match {
          case Some(found) => s"say${Sep}ok ${where(found)}"
          case None => s"say${Sep}error nothing showing is named ${c.words}"
        }
      case "labels" =>
        val named = Focus.candidates(root).filter(shown(_, root)).map(f => nameOf(f) + "  " + where(f))
        s"say${Sep}ok ${named.length}" + named.map("\n" + _).mkString
      case "focused" =>
        s"say${Sep}ok " + Focus.focused().map(f => Focus.named(f) + "  " + where(f)).getOrElse("nothing")
      case "ping" =>
        s"say${Sep}ok ${Platform.kind} ${Platform.deviceName()} remote=${Platform.remoteNavigation()} " +
          s"touch=${Platform.touchSupport()} version=${Platform.appVersionName()} (${Platform.appVersion()})"
      case _ => s"say${Sep}error not a command: ${c.verb}"
    }
  }

  /** The component showing under `root` named `label` (see the object's description), if any. */
  def find(root: Component, label: String): Option[Component] = {
    val wanted = label.trim
    val lower = wanted.toLowerCase
    var best: Option[Component] = None
    // the best's rank: how closely its name matches, whether it takes the focus, and its area (smaller is better)
    var bestScore = 0
    var bestFocus = 0
    var bestArea = 0.0
    var pending: List[Component] = List(root)
    while (pending.nonEmpty) {
      val c = pending.head
      pending = pending.tail
      if (c.notHidden) {
        pending = c.childComponents.toList ++ pending
        val name = nameOf(c)
        val score = if (name == wanted) 3 else if (name.toLowerCase == lower) 2
          else if (lower.nonEmpty && name.toLowerCase.contains(lower)) 1 else 0
        if (score > 0 && shown(c, root)) {
          val focus = if (c.focusable()) 1 else 0
          val area = c.size.width() * c.size.height()
          val better = best.isEmpty || score > bestScore || (score == bestScore && (focus > bestFocus ||
            (focus == bestFocus && area < bestArea)))
          if (better) {
            best = Some(c)
            bestScore = score
            bestFocus = focus
            bestArea = area
          }
        }
      }
    }
    best
  }

  /** What `c` is called, as a person pointing at it would say: its semantics label, else its own words (a text's, a
    * picture's alt, a field's title), else the words shown in it, in the order they are held, up to 160 characters. */
  def nameOf(c: Component): String = {
    val own = ownWords(c)
    if (own.nonEmpty) own
    else {
      var words = ""
      var pending: List[Component] = c.childComponents.toList
      while (pending.nonEmpty && words.length < 160) {
        val at = pending.head
        pending = pending.tail
        if (at.notHidden) {
          val said = ownWords(at)
          if (said.nonEmpty) words = if (words.isEmpty) said else words + " " + said
          else pending = at.childComponents.toList ++ pending
        }
      }
      words.take(160)
    }
  }

  private def ownWords(c: Component): String = {
    val label = c.semantics().label.trim
    if (label.nonEmpty) label
    else c match {
      case _: MaterialIconView => ""
      case t: TextView => t.content().trim
      case i: ImageView => i.alt().trim
      case f: TextInput => f.title().trim
      case _ => ""
    }
  }

  /** Whether `c` is drawn: under `root`, and neither it nor anything round it hidden. */
  private def shown(c: Component, root: Component): Boolean = {
    var at: Option[Component] = Some(c)
    var ok = true
    var reached = false
    while (ok && at.nonEmpty) {
      val here = at.get
      if (!here.notHidden) ok = false
      if (here eq root) reached = true
      at = here.parent()
    }
    ok && reached
  }

  /** A screen's point (in points) in the app's units: inside the safe area, over the size the app is drawn at. */
  def inApp(x: Double, y: Double): (Double, Double) = {
    val safe = Platform.safeArea()
    (x / UiScale() - safe.left, y / UiScale() - safe.top)
  }

  /** The app's units as the screen's points. */
  def onScreen(x: Double, y: Double): (Double, Double) = {
    val safe = Platform.safeArea()
    ((x + safe.left) * UiScale(), (y + safe.top) * UiScale())
  }

  private def middle(c: Component): (Double, Double) =
    (c.absoluteLeft + c.size.width() / 2.0, c.absoluteTop + c.size.height() / 2.0)

  /** Where `c` is on the screen, in points: `x,y wxh`. */
  def where(c: Component): String = {
    val (x, y) = onScreen(c.absoluteLeft, c.absoluteTop)
    s"${num(x)},${num(y)} ${num(c.size.width() * UiScale())}x${num(c.size.height() * UiScale())}"
  }

  /** A number as a line says it: whole numbers without a fraction. */
  private def num(d: Double): String = {
    val rounded = math.round(d * 10.0) / 10.0
    if (rounded == math.floor(rounded)) rounded.toLong.toString else rounded.toString
  }
}

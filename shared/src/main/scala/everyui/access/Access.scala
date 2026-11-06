package everyui.access

import everyui.Color
import everyui.component.{Backdrop, Component, ImageView, MaterialIconView, TextInput, TextView}
import everyui.draw.Drawing
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A check of a screen against WCAG 2.2 AA, for while an app is made and for its tests, as `Overlaps` checks its
  * layout: everything shown under `root` is looked at, and what breaks a rule (`AccessRule`) is found, with the
  * component, how and where. `check()` returns the findings, so a test can ask for none on a screen; with `warn` on,
  * the screen is looked over after each layout pass and every second, and each finding is told once (`scribe.warn`):
  * {{{
  *   Access.warn @= true
  *   // WARN access: name: Container (12,80 32x32) has nothing a screen reader can say for it in Toolbar < ui$
  *   assert(Access.check().isEmpty)
  * }}}
  *
  * What it finds:
  *   - name: focusable or clicked, with no name: no `semantics` label, words of its own or inside it, alt or tooltip
  *     (`nameOf`, which is what a screen reader is given for it);
  *   - role: clicked, with no role (`roleOf`): a screen reader would not say it is a button, a link or a switch;
  *   - keyboard: clicked, but neither it nor anything holding it is `focusable`, so the keys never reach it;
  *   - target: clicked, smaller than 24 by 24, with another clicked thing within the 24 about its middle;
  *   - order: Tab moving the focus to something above the last in the same column, or before it in the same line;
  *   - contrast: words under 4.5:1 against what is drawn behind them (3:1 for large words, 24 points, or 18.66
  *     bold, and for an icon), unless out of use (`disabled`). Behind them is the theme's background under the
  *     background colours of what holds them, and of what lies under them in the same container; where a gradient
  *     or a picture is behind them it cannot be told, and nothing is said.
  *
  * A component that meets a rule in a way of its own is marked (`allow`). Off, as it is by default, `warn` costs
  * nothing. */
object Access {
  /** Whether what is found is reported as it is found. */
  val warn: Var[Boolean] = Var(false)

  /** `c`, marked as meeting `rules` in its own way (a target spaced by a margin the check cannot see, words over a
    * picture known to be dark), so they are not checked on it. */
  def allow[C <: Component](c: C, rules: AccessRule*): C = {
    c.accessAllowed = (c.accessAllowed ++ rules).distinct
    c
  }

  // read where layout passes end, without the cost of a Var's read
  private var on: Boolean = false
  private var ticking: Boolean = false
  private var soon: Boolean = false
  private var told: List[String] = Nil

  warn.attach { w =>
    on = w
    if (!w) told = Nil
    else {
      laidOut()
      if (!ticking) {
        ticking = true
        Platform.after(1.0, () => tick())
      }
    }
  }

  private def tick(): Unit = if (on) {
    report()
    Platform.after(1.0, () => tick())
  } else ticking = false

  /** A layout pass ended (`LayoutPass.flush`): the screen is looked over on a turn of its own, once however many
    * end. */
  private[everyui] def laidOut(): Unit = if (on && !soon) {
    soon = true
    Platform.later(() => {
      soon = false
      if (on) report()
    })
  }

  private def report(): Unit = {
    val now = check().map(_.toString)
    now.filterNot(told.contains).foreach(f => everyui.scribe.warn("access: " + f))
    told = now
  }

  /** Everything shown under `root` that breaks a rule, in the order of the screen. Walked with a list rather than by
    * recursion (a Roku's stack is small). */
  def check(root: Component = everyui.ui): List[AccessFinding] = {
    val shown = showing(root)
    // the root is the page, whose clicks are the empty space's, not a control's
    def acts(c: Component): Boolean = (c ne root) && c.clickHeard && !outOfUse(c)
    val targets = shown.filter(acts)
    var found = List.empty[AccessFinding]
    def add(rule: AccessRule, c: Component, detail: String): Unit =
      if (!c.accessAllowed.contains(rule)) found = AccessFinding(rule, c, detail, box(c)) :: found
    shown.foreach { c =>
      val acting = acts(c)
      if ((acting || c.focusable()) && nameOf(c).isEmpty)
        add(AccessRule.Name, c, "has nothing a screen reader can say for it")
      if (acting && !c.takesTyping && roleOf(c).isEmpty)
        add(AccessRule.Role, c, "is clicked, but nothing says what it is")
      if (acting && !self(c).exists(_.focusable())) add(AccessRule.Keyboard, c, "is clicked, but the keys never reach it")
      if (acting && (c.size.width() < 24.0 || c.size.height() < 24.0) && crowded(c, targets))
        add(AccessRule.Target, c, "is under 24x24 with another target within 24 of its middle")
      past(c, root).foreach(by => add(AccessRule.Reflow, c, "runs " + math.round(by).toString +
        " past the edge of a screen " + math.round(root.size.width()).toString + " across"))
      contrastOf(c).foreach { case (ratio, wanted, fg, bg) =>
        add(AccessRule.Contrast, c,
          tenths(ratio) + ":1, " + fg.toHex + " on " + bg.toHex + ", under " + tenths(wanted) + ":1")
      }
    }
    // the order Tab takes, against the reading order
    val order = everyui.focus.Focus.candidates(root).filter(c => shown.exists(_ eq c))
    order.indices.drop(1).foreach { i =>
      val (was, next) = (order(i - 1), order(i))
      if (backwards(was, next)) add(AccessRule.Order, next, "comes after " + was.kind + " " + box(was) +
        " on Tab, but before it in reading order")
    }
    found.reverse
  }

  // how far `c` runs past `root`'s left or right edge, where it is the first along its line of holders to and nothing
  // holding it cuts it (a scroll, a list): there, what runs past is cut, or scrolled to, not off the screen
  private def past(c: Component, root: Component): Option[Double] = {
    def over(x: Component): Double = {
      val left = x.absoluteLeft - root.absoluteLeft
      math.max(-left, left + x.size.width() - root.size.width())
    }
    val by = over(c)
    if ((c eq root) || by <= 0.5) None
    else {
      var at = c.parent()
      var cut = false
      var first = true
      while (at.nonEmpty && !(at.get eq root) && !cut) {
        if (at.get.clips) cut = true
        else if (first && over(at.get) > 0.5) cut = true
        first = false
        at = at.get.parent()
      }
      if (cut) None else Some(by)
    }
  }

  /** The check put where a test harness can read it, on the web: `window.everyuiAccess()`, the findings under `ui`
    * as lines (none: ""), and `window.everyuiAccessJson()`, the same as objects, each `{rule, criterion, component,
    * detail, where, left, top, width, height}`. Nothing is looked at until one is called, so it may be put there
    * always; elsewhere it does nothing. */
  def expose(): Unit = {
    Platform.expose("everyuiAccess", () => check().map(_.toString).mkString("\n"), json = false)
    Platform.expose("everyuiAccessJson", () => json(check()), json = true)
  }

  /** `found` as a JSON array, one object for each (`expose`). */
  def json(found: List[AccessFinding]): String = "[" + found.map { f =>
    val c = f.component
    def field(name: String, value: String): String = quoted(name) + ":" + quoted(value)
    def number(name: String, value: Double): String = quoted(name) + ":" + math.round(value).toString
    "{" + List(field("rule", f.rule.id), field("criterion", f.rule.criterion), field("component", c.kind),
      field("detail", f.detail), field("where", c.parent().map(Component.lineage).getOrElse("")),
      number("left", c.absoluteLeft), number("top", c.absoluteTop), number("width", c.size.width()),
      number("height", c.size.height())).mkString(",") + "}"
  }.mkString(",") + "]"

  // `s` as a JSON string
  private def quoted(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    .replace("\r", "\\r").replace("\t", "\\t") + "\""

  /** The name a screen reader is given for `c`: its `semantics` label; else its own words (a text's, a picture's alt,
    * a field's label); else the words of what it holds, in reading order (top to bottom, then across), each part
    * with a label of its own by that label, unless it is a whole made of parts (`wholes`: a list box); else its
    * tooltip (which, with words to name it, describes it). Empty where it has none. */
  def nameOf(c: Component): String = {
    val own = (labelOf(c) :: wordsOf(c) :: Nil).map(_.trim).find(_.nonEmpty)
    val held = if (wholes.contains(roleOf(c))) "" else inside(c)
    own.orElse(Some(held).filter(_.nonEmpty)).getOrElse(c.tooltip().trim)
  }

  /** Roles named only as they are given a name, never by the words they hold: a list box is named by its label, not
    * by its options, which a screen reader says one at a time. */
  val wholes: Set[String] = Set("listbox", "list", "tree", "treegrid", "grid", "table", "menu", "menubar", "tablist",
    "radiogroup", "toolbar", "region", "navigation", "main", "dialog", "log")

  // the words of what `c` holds, in reading order: each part with a label of its own by that label
  private def inside(c: Component): String = {
    var parts = List.empty[(Double, Double, String)]
    var pending: List[Component] = c.childComponents.toList
    var seen = 0
    while (pending.nonEmpty && seen < 400) {
      val at = pending.head
      pending = pending.tail
      seen += 1
      if (at.notHidden) {
        val said = (labelOf(at) :: wordsOf(at) :: Nil).map(_.trim).find(_.nonEmpty)
        said match {
          case Some(words) => parts = (at.absoluteTop, at.absoluteLeft, words) :: parts
          case None => pending = at.childComponents.toList ::: pending
        }
      }
    }
    // a line is what starts within 4 pixels of the same height
    parts.sortBy(p => (math.round(p._1 / 4.0), p._2)).map(_._3).mkString(" ").take(160).trim
  }

  /** What `c` is to a screen reader: its `semantics` role; a field of the platform's is a text box; an app's own
    * focusable, clicked component a button (`Component.impliedRole`). Empty where nothing says. */
  def roleOf(c: Component): String = {
    val said = c.semantics().role
    if (said.nonEmpty) said else if (c.takesTyping) "textbox" else c.impliedRole
  }

  /** The contrast of `a` against `b` (both opaque), from 1 to 21. */
  def contrast(a: Color, b: Color): Double = {
    val (x, y) = (luminance(a), luminance(b))
    (math.max(x, y) + 0.05) / (math.min(x, y) + 0.05)
  }

  private def labelOf(c: Component): String = c.semantics().label

  private def wordsOf(c: Component): String = c match {
    case _: MaterialIconView => ""
    case t: TextView => t.content()
    case i: ImageView => i.alt()
    case f: TextInput => f.title()
    case _ => ""
  }

  // what is shown under `root`, in the screen's order: not hidden, not faded out, with a size
  private def showing(root: Component): List[Component] = {
    var out = List.empty[Component]
    var pending: List[Component] = List(root)
    while (pending.nonEmpty) {
      val at = pending.head
      pending = pending.tail
      if (at.notHidden && at.opacity() > 0.0) {
        if (at.size.width() > 0.0 && at.size.height() > 0.0) out = at :: out
        pending = at.childComponents.toList ::: pending
      }
    }
    out.reverse
  }

  // `c` and what holds it, nearest first
  private def self(c: Component): List[Component] = c :: holders(c)
  private def holders(c: Component): List[Component] = {
    var out = List.empty[Component]
    var at = c.parent()
    while (at.nonEmpty) {
      out = at.get :: out
      at = at.get.parent()
    }
    out.reverse
  }

  private def outOfUse(c: Component): Boolean = self(c).exists(_.disabled())

  private def within(c: Component, of: Component): Boolean = holders(c).exists(_ eq of)

  // whether `next`, the stop after `was` on Tab, reads before it: wholly above it in the same column, or before it
  // in the same line (left of it, or right of it in a language read from the right)
  private def backwards(was: Component, next: Component): Boolean = {
    val (aLeft, aTop, aRight, aBottom) = edges(was)
    val (bLeft, bTop, bRight, bBottom) = edges(next)
    val column = bLeft < aRight && bRight > aLeft
    val line = bTop < aBottom && bBottom > aTop && !column
    val before = if (everyui.i18n.Direction.rtl()) bLeft >= aRight - 2.0 else bRight <= aLeft + 2.0
    (column && bBottom <= aTop + 2.0) || (line && before)
  }

  private def edges(c: Component): (Double, Double, Double, Double) =
    (c.absoluteLeft, c.absoluteTop, c.absoluteLeft + c.size.width(), c.absoluteTop + c.size.height())

  // another target within the 24 by 24 about `c`'s middle, other than what holds it or what it holds
  private def crowded(c: Component, targets: List[Component]): Boolean = {
    val x = c.absoluteLeft + c.size.width() / 2.0
    val y = c.absoluteTop + c.size.height() / 2.0
    targets.exists { t =>
      (t ne c) && !within(t, c) && !within(c, t) &&
        t.absoluteLeft < x + 12.0 && t.absoluteLeft + t.size.width() > x - 12.0 &&
        t.absoluteTop < y + 12.0 && t.absoluteTop + t.size.height() > y - 12.0
    }
  }

  // words', or an icon's, contrast, with what it should be, the colour drawn and what is behind: where it falls short
  private def contrastOf(c: Component): Option[(Double, Double, Color, Color)] = {
    val icon = c.isInstanceOf[MaterialIconView] || c.isInstanceOf[everyui.draw.VectorIcon]
    val words = c match {
      case _: MaterialIconView => true
      case t: TextView => t.content().trim.nonEmpty
      case _ => false
    }
    if (!(words || icon) || outOfUse(c)) None
    else behind(c).flatMap { bg =>
      val drawn = self(c).find(_.colored).map(_.color()).getOrElse(Theme.palette().text)
      val faded = self(c).foldLeft(1.0)((o, at) => o * at.opacity())
      val fg = over(drawn, drawn.alpha * faded, bg)
      val large = c match {
        case t: TextView if !icon =>
          val points = t.font.size()
          points >= 24.0 || (points >= 18.66 && t.font.weight().value >= 700)
        case _ => true
      }
      val wanted = if (icon || large) 3.0 else 4.5
      val ratio = contrast(fg, bg)
      if (ratio + 0.005 < wanted) Some((ratio, wanted, fg, bg)) else None
    }
  }

  // what is drawn behind `c`: the theme's background, under each holder's background and under whatever lies beneath
  // `c` in the same container and covers it; None where a gradient or a picture is in it
  private def behind(c: Component): Option[Color] = {
    var bg = Theme.palette().background.withAlpha(1.0)
    var known = true
    // from the root down to `c`
    val path = (holders(c).reverse :+ c).toVector
    val (left, top, right, bottom) = (c.absoluteLeft, c.absoluteTop, c.absoluteLeft + c.size.width(),
      c.absoluteTop + c.size.height())
    def layer(at: Component): Unit = {
      if (at.drawsGradient) known = false
      else if (at.drawsFill) bg = over(at.backgroundColor(), at.backgroundColor().alpha * at.opacity(), bg)
    }
    var i = 0
    while (known && i < path.length) {
      val at = path(i)
      if (at ne c) layer(at)
      if (i + 1 < path.length) {
        val next = path(i + 1)
        at.childComponents.takeWhile(_ ne next).foreach { s =>
          val covers = s.notHidden && s.absoluteLeft <= left && s.absoluteTop <= top &&
            s.absoluteLeft + s.size.width() >= right && s.absoluteTop + s.size.height() >= bottom
          if (covers) s match {
            case _: ImageView | _: Backdrop | _: Drawing => known = false
            case _ => layer(s)
          }
        }
      }
      else if (c.drawsFill || c.drawsGradient) layer(c)
      i += 1
    }
    if (known) Some(bg) else None
  }

  // `fg` at `alpha` over the opaque `bg`
  private def over(fg: Color, alpha: Double, bg: Color): Color = {
    val a = math.max(0.0, math.min(1.0, alpha))
    def mix(f: Double, b: Double): Double = f * a + b * (1.0 - a)
    Color.fromRGB(mix(fg.red, bg.red), mix(fg.green, bg.green), mix(fg.blue, bg.blue))
  }

  private def luminance(c: Color): Double = {
    def lin(v: Double): Double = if (v <= 0.03928) v / 12.92 else math.pow((v + 0.055) / 1.055, 2.4)
    0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
  }

  // to one place after the point
  private def tenths(d: Double): String = {
    val t = math.round(d * 10.0)
    (t / 10).toString + "." + (t % 10).toString
  }

  private def box(c: Component): String = "(" + math.round(c.absoluteLeft) + "," + math.round(c.absoluteTop) + " " +
    math.round(c.size.width()) + "x" + math.round(c.size.height()) + ")"
}

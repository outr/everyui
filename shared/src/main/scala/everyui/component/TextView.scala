package everyui.component

import everyui.{Color, Gradient}
import everyui.component.feature.FontFeature
import everyui.component.types.{Elide, Plain, Prop, Semantics, TextAlign, TextDirection, TextOverflow, WhiteSpace}
import everyui.render.{Nodes, Platform, RenderNode}
import everyui.theme.Theme
import reactify.Var

class TextView(node: RenderNode = Nodes.text()) extends Component(node) {
  // the app's `TextOverflow.default` as the view is made, which is what it does until it is told otherwise; and whether
  // `textOverflow` has been made (a look need not make it)
  private val overflowAtFirst: TextOverflow = TextOverflow.default()
  private var overflowMade = false
  lazy val content: Prop[String] = own(new Prop[String](node.getText(), _ => {
    followed = false
    fit(why = "content")
    everyui.access.LiveRegions.changed()
  }))
  /** The widest these words may be drawn: what will not fit on a line wraps onto another (`TextOverflow.Wrap`, the
    * default), or is cut short with an ellipsis (`TextOverflow.Ellipsis`), as a title beside a chip is, so whatever sits
    * after them keeps its place. 0 for no limit. */
  lazy val maxWidth: Var[Double] = own(Plain[Double](0.0))
  /** The most lines wrapped words may take: what will not fit is cut short at a word with an ellipsis, as a synopsis
    * under an episode's name is. 0 for no limit. */
  lazy val maxLines: Var[Int] = own(Plain[Int](0))
  /** Where words wider than `maxWidth` are cut: at the end, the default; at the start, for a path whose end says the
    * most ("…/backend/app"); or in the middle. Wrapped words cut to `maxLines` are cut at the end. */
  lazy val elide: Var[Elide] = own(Plain[Elide](Elide.End))
  /** Whether the words are drawn cut short to fit (`maxWidth`, `maxLines`, their box), as last measured. */
  lazy val elided: Var[Boolean] = own(Plain[Boolean](false))
  /** What the words do where they are wider than their box, the room what they are in gives them (a column's width
    * less its padding, a row's less what else is in it, the screen's), or than `maxWidth`: wrapped within it
    * (`TextOverflow.Wrap`, the default, `TextOverflow.default`), taking as many lines as they need; cut short with an
    * ellipsis where they are one line (`TextOverflow.Ellipsis`); or drawn whole past it (`TextOverflow.Visible`). Words
    * that fit are drawn just as they would be either way. Words given a width of their own (`size.width`) are kept
    * to it rather than to their box, a line wider than it wrapped within it or cut to it as the rest are, and words in
    * something sized to them (a row given no width, a button, a chip) are kept to the room that has. Cut short, they
    * are still read whole (`semantics`). */
  lazy val textOverflow: Prop[TextOverflow] = {
    overflowMade = true
    own(new Prop[TextOverflow](overflowAtFirst, _ => if (keepsToBox) {
      fit(why = "overflow")
      TextView.check(this)
    }))
  }
  private def overflow: TextOverflow = if (overflowMade) textOverflow() else overflowAtFirst
  /** A shadow behind the letters themselves, not the box: an edge that keeps words readable over a picture. None at
    * Minimal (`everyui.perf.Quality`). */
  lazy val textShadow: types.Prop[Option[everyui.Shadow]] = new types.Prop[Option[everyui.Shadow]](None, _ => {
    textShadowed = true
    drawTextShadow()
  })
  private var textShadowed = false
  private def drawTextShadow(): Unit = textShadow() match {
    case Some(sh) if everyui.perf.Quality.textEffects => node.setTextShadow(sh.x, sh.y, sh.blur, Component.css(sh.color))
    case _ => node.setTextShadow(0.0, 0.0, 0.0, "")
  }
  override private[everyui] def requality(): Unit = {
    super.requality()
    if (textShadowed) drawTextShadow()
  }
  /** A hard edge round the letters, where a shadow would only blur: words kept readable over bright footage, as the
    * Flutter screensaver edges them. Where a platform draws no outline, the words are drawn without one. */
  lazy val textOutline: types.Prop[Option[everyui.TextOutline]] = new types.Prop[Option[everyui.TextOutline]](None, o =>
    o match {
      case Some(line) => node.setTextOutline(line.width, Component.css(line.color))
      case None => node.setTextOutline(0.0, "")
    })
  val font: FontFeature = new FontFeature(this)
  /** Where the text sits in its box, by the reading direction: it matters once the box is wider than the words
    * (wrapped, or given a width). */
  lazy val align: Prop[TextAlign] = own(new Prop[TextAlign](TextAlign.Start, a => node.setTextAlign(TextView.side(a))))
  /** Which way the words run: the app's reading direction by default (`TextDirection.Inherit`), or the words' own
    * (`Auto`, the first letter with a direction deciding) for words the app did not write, a message or a name, so an
    * English one reads left to right in an Arabic app. The start of `align` is the words' start. */
  lazy val direction: Prop[TextDirection] =
    own(new Prop[TextDirection](TextDirection.Inherit, d => node.setTextDirection(d.value)))
  /** Whether a word too long for a line (a URL, a path, a hash) is broken where it must be, rather than running past
    * the box's edge. It matters for wrapped words alone. */
  lazy val wrapAnywhere: Prop[Boolean] =
    own(new Prop[Boolean](false, on => node.setWrapAnywhere(on), () => {
      // broken elsewhere, the same words take other room
      stale = true
      measure.trigger()
    }))
  /** The letters painted with a gradient instead of `color` (the web; elsewhere its first stop). */
  lazy val fill: Prop[Option[Gradient]] = own(new Prop[Option[Gradient]](None, g => g match {
    case Some(value) => node.setTextGradient(value.angle, value.encoded)
    case None => node.setTextGradient(0.0, "")
  }))

  // wrapped to a width that changes, the words take a different number of lines; given a width of their own, words
  // kept to their box are left to it, and a line is kept to that width (`ownRoom`)
  size.width.attach(_ => if (size.width.explicit) {
    if (boxed) fit(why = "width")
    else if (whiteSpace() != WhiteSpace.NoWrap) {
      if (maxLines() > 0) fit(why = "width") else measure.trigger()
    }
    else if (ownRoom > 0.0 || wrappedToFit || elided()) fit(why = "width")
  })
  /** The room a line of words given a width of their own has: that width, where nothing else holds them to less
    * (`maxWidth`, `maxLines`) and they are not let run past it (`TextOverflow.Visible`); 0 for none. Wider than it,
    * they wrap within it (the platform wraps words at a width of their own everywhere), or are cut short to it with an
    * ellipsis (`TextOverflow.Ellipsis`): a heading in a card the card has narrowed. */
  private def ownRoom: Double =
    if (size.width.explicit && whiteSpace() == WhiteSpace.NoWrap && keepsToBox && overflow != TextOverflow.Visible &&
      maxWidth() <= 0.0 && maxLines() <= 0) size.width() else 0.0
  // the cut is being made, whether it was made where the words could not yet be measured as they will be drawn (off
  // the page, or in a face still loading), and whether the page holds them to their room while it waits (setTextLimit)
  private var fitting = false
  private var unfit = false
  private var held = false
  private def limited: Boolean = maxWidth() > 0.0 || maxLines() > 0 || boxed
  // how wide they are whole, where they are cut to their box (`boxShrunk`): what they are cut by follows the width they
  // are drawn at, which the web reads a moment later
  private var boxWhole = 0.0
  override private[everyui] def boxShrunk: Double = math.max(0.0, boxWhole - size.width())
  // kept to their box: cut or wrapped to it (with the room it had then, and the width drawn before and after), about to
  // be measured against it, waiting for a look at it (TextView.check), and given up on where the box shrank with them
  private var boxed = false
  private var cutRoom = 0.0
  private var drawnBefore = 0.0
  private var drawnAfter = 0.0
  private var toBox = false
  private[component] var boxCheckWaits = false
  private var followed = false
  // the words whatever reads the page was given whole, as a name, while they were drawn cut short
  private var described = ""

  // wrapping words measured whole, on one line, and wrapped only at their box (`measureWhole`)
  private var wholeToBox = false
  /** Wrapping words measured whole, on one line, and wrapped only where they pass their box: a button's label
    * (`Button.labelLines`), in a row sized to it, which the web would otherwise wrap at that row, word by word. */
  // measured whole where they wrap at a limit of their own (`maxWidth`) or a box (`measureWhole`), with no width of
  // their own: what holds them, sized to them (a pill round a note), is no limit, though the web wraps them at it, and
  // words first measured in a pill not yet grown round them were wrapped at its width, a few letters wide, and kept so
  private def wholeNow(): Unit = node.setTextWhole((wholeToBox || maxWidth() > 0.0) && !size.width.explicit)
  private[everyui] def measureWhole(on: Boolean): Unit = if (on != wholeToBox) {
    wholeToBox = on
    wholeNow()
    stale = true
    measure.trigger()
  }

  /** Whether the words are kept to their box at all: a glyph's are not. */
  private[everyui] def keepsToBox: Boolean = true
  // the room the box gives the words (0 for none), as it is now, measured once what they are in has settled
  // (Container.roomOf). On the way to it the words are told which containers change it: a row they are in, whose other
  // children leave them less room as they grow; the box itself; and where the way stops short, what stops it
  // (something hidden, or the top of something not yet on the screen). Each of those has the words looked at again as
  // its width or its place on the screen changes, or as it is shown (Container.watchRoomOf); a column sized to them, or
  // a button, changes only as they do.
  private def roomNow: Double = {
    if (!TextView.looking(0)) TextView.look()
    val r = parent() match {
      case Some(p) =>
        if (overflow == TextOverflow.Visible || size.width.explicit) 0.0
        else if (!notHidden) {
          // hidden, they are looked at again as they are shown
          if (!watchingShown) {
            watchingShown = true
            visible.attach(_ => TextView.check(this))
          }
          0.0
        }
        else p.roomOf(this)
      case None => 0.0
    }
    if (!saw) watchesRoom(Nil)
    saw = false
    r
  }
  // the containers that change the words' room, as last found, and whether the look under way has found them
  private var watched: List[Container] = Nil
  private var saw = false
  private var watchingShown = false
  /** What changes the words' room, as found now (Container.roomOf): each container they were not watching is watched,
    * and each they no longer need let go. Mostly the same as before, which is seen without going through them twice. */
  private[everyui] def watchesRoom(now: List[Container]): Unit = {
    saw = true
    var a = now
    var b = watched
    while (a.nonEmpty && b.nonEmpty && (a.head eq b.head)) {
      a = a.tail
      b = b.tail
    }
    if (a.nonEmpty || b.nonEmpty) {
      watched.foreach(c => if (!now.exists(_ eq c)) c.unwatchRoomOf(this))
      now.foreach(c => if (!watched.exists(_ eq c)) c.watchRoomOf(this))
      watched = now
    }
  }
  // put in a container or taken out, and measured again, the words look at their box again
  override private[everyui] def containerChanged(): Unit = if (keepsToBox) TextView.check(this)
  override private[everyui] def widthRemeasured(): Unit = if (keepsToBox) TextView.check(this)
  // taken apart, the words are let go by what they watched on a turn of its own, all at once (Container.letGoLater): one
  // by one, a screen of them taken apart would go through its box's list once for each
  override def dispose(): Unit = {
    if (!takenApart) {
      watched.foreach(_.letGoLater())
      watched = Nil
      if (boxed) {
        boxed = false
        TextView.boxedCount(0) -= 1
      }
    }
    super.dispose()
  }

  /** Looked at once the turn is over (TextView.check), with what the words are in and their own size settled: words
    * that ran past their box are cut or wrapped to it, and words kept to it are fitted again as it changes. A box that
    * shrank by just as much as the words were cut follows them (a width bound to theirs that the box could not see):
    * they are drawn whole instead, until they change; and so are words fitted again and again in a moment, whatever
    * keeps changing their room as they are. */
  private[component] def keepToBox(): Unit = {
    val r = roomNow
    if (boxed) {
      if (r > 0.0 && r < cutRoom - 0.5 && math.abs((cutRoom - r) - (drawnBefore - drawnAfter)) < 0.5) followed = true
      if (r != cutRoom || followed) fitToBox()
    }
    else if (r > 0.0 && !followed && measured.width() > r + 0.5) {
      toBox = true
      fitToBox()
    }
  }
  // fitted to the box again: more than a few dozen times in a quarter of a second is a box that cannot settle
  private var lately = 0
  private var lastFit = 0.0
  private def fitToBox(): Unit = {
    val now = Platform.ticks()
    lately = if (now - lastFit > 250.0) 1 else lately + 1
    lastFit = now
    if (lately > 24) followed = true
    fit(why = "box")
  }

  maxWidth.attach { _ =>
    wholeNow()
    fit(why = "maxWidth")
  }
  maxLines.attach(_ => fit(why = "maxLines"))
  elide.attach(_ => if (maxWidth() > 0.0) fit(why = "elide"))
  // drawn in another face or size, the words are cut again: the cut was made in the one before
  measure.on(if (!fitting && limited) fit(why = "face"))

  // words that could not be measured when they were set are cut once they can be: as they join the page, whose sizes
  // are read then, or once their face has loaded; words not cut are measured again once it has
  override private[everyui] def beforeRead(): Unit =
    if (node.textMeasurable(refit) && unfit) fit(announce = false, why = "unmeasured")
  private val refit: () => Unit = () => if (!takenApart) {
    // a face loaded: the same words take other room
    stale = true
    if (unfit) fit(why = "loaded") else measure.trigger()
  }

  /** Draw the words, or as many of them as `maxWidth` or `maxLines` leaves room for with an ellipsis after. The
    * platform measures each try, so the cut is the one the words really take, and not a guess at how wide a letter
    * is. */
  private def fit(announce: Boolean = true, why: String): Unit = {
    fitting = true
    // what the words written meanwhile were written for, counted (`PerfCounters.textFor`)
    val counting = everyui.perf.PerfCounters.on
    val was = if (counting) everyui.perf.PerfCounters.textFor else ""
    if (counting) everyui.perf.PerfCounters.textFor = why
    try {
      // what is drawn decides the width: a cut that came from a narrower `maxWidth` changes nothing else
      if (cut() && announce) measure.trigger()
    } finally {
      fitting = false
      if (counting) everyui.perf.PerfCounters.textFor = was
    }
  }

  // what the last cut went by, and whether it has gone stale since, where something the key does not hold changed what
  // the words measure: a face loaded, the words measured whole, broken anywhere
  private var cutKey = ""
  private var stale = true

  /** The words cut to their room, as `fit` says; false where all it goes by is as at the last cut, the words drawn and
    * measured then, which nothing is done again for (a change of room that came to the same room, words set again). */
  private def cut(): Boolean = {
    val wanted = content()
    val limit = maxWidth()
    val lines = if (maxLines() > 0 && (whiteSpace() != WhiteSpace.NoWrap || wraps)) maxLines() else 0
    // the box's room, where the words were kept to it or ran past it as last measured (keepToBox)
    val box = if ((boxed || toBox) && !followed && !size.width.explicit) roomNow else 0.0
    val own = ownRoom
    // the width drawn before, which only words kept to their box look back at: read only then, since a read of words not
    // yet on the page lends them to it, and made the browser lay the page out for every label of a screen being built
    val before = if (box > 0.0 || boxed) measured.width() else 0.0
    val key = wanted + "|" + limit.toString + "|" + lines.toString + "|" + box.toString + "|" + own.toString + "|" +
      whiteSpace().value + "|" + overflow.toString + "|" + elide().toString + "|" +
      // a width of its own, which words that wrap wrap at, and how many times its face has changed
      (if (size.width.explicit) size.width().toString else "") + "|" + font.changes.toString
    val same = key == cutKey && !stale && !unfit
    everyui.perf.PerfCounters.fit(same)
    if (same) false
    else {
      cutKey = key
      stale = false
      drawCut(wanted, limit, lines, box, own, before)
      true
    }
  }

  // whether the words surely fit `room` on one line, by the platform's guess at their width (the web's, on a canvas),
  // with room to spare for what the guess misses: then nothing is read from the page, which, read just after the words
  // were written, made the browser lay the page out there and then. Where there is no guess, or it is near, they are
  // measured.
  private def surely(room: Double): Boolean = {
    val guess = node.textWidthGuess()
    guess >= 0.0 && guess * 1.08 + 2.0 < room
  }

  // the words last written to the node, so the same written again (a limit changed that cuts them as before, words
  // that fit either way) is not written: each wrote the page, and was read after as a change
  private var written: String = node.getText()
  private def write(words: String): Unit = if (words != written) {
    node.setText(words)
    written = words
  }

  private def drawCut(wanted: String, limit: Double, lines: Int, boxRoom: Double, own: Double, before: Double): Unit = {
    // words wrapped to fit their room before are put back as they were, to be measured whole again
    if (wrappedToFit) unwrap()
    write(wanted)
    val asked = limit > 0.0 || lines > 0
    unfit = (asked || boxRoom > 0.0 || own > 0.0) && !node.textMeasurable(refit)
    // measured whole, then held to the room it has; where it cannot be measured yet, only held
    if (held) node.setTextLimit(0.0, 0)
    // a word is broken across lines only where the box's room holds a few letters (`TextView.breakingRoom`): in less
    // (a link in a row too wide for its box, which leaves the words first in it a pixel) the words wrap between words
    // alone, each whole on its line, the room taken as their widest word, rather than one letter to a line
    val box = if (!unfit && boxRoom > 0.0 && boxRoom < TextView.breakingRoom * font.size() &&
      whiteSpace() != WhiteSpace.Pre && (wraps || whiteSpace() != WhiteSpace.NoWrap))
      math.max(boxRoom, widestWord(wanted)) else boxRoom
    held = asked
    if (unfit) {
      // (wrapping words wrap at the limit already, where they can be measured nothing more is known)
      if (held && wraps && limit > 0.0) wrapToFit()
      if (held) node.setTextLimit(limit, lines)
    }
    else {
      toBox = false
      // within the box, the words are drawn just as they are
      val was = boxed
      // (words that surely fit their box are whole in it, with nothing read: `surely`)
      val whole = if (box > 0.0 && !was && surely(box)) 0.0 else if (box > 0.0 || was) node.getWidth() else 0.0
      boxed = box > 0.0 && whole > box + 0.5
      if (boxed != was) TextView.boxedCount(0) += (if (boxed) 1 else -1)
      var single = limit
      var wrapAt = 0.0
      if (boxed) {
        cutRoom = boxRoom
        drawnBefore = before
        // words that wrap wrap within it, where the platform wraps them there; a line, and words it does not, are cut
        if (whiteSpace() != WhiteSpace.NoWrap && whiteSpace() != WhiteSpace.Pre) {
          node.setTextLimit(box, 0)
          if (node.getWidth() > box + 0.5) node.setTextLimit(0.0, 0) else wrapAt = box
        }
        if (wrapAt == 0.0) single = if (limit > 0.0 && limit < box) limit else box
      }
      // wrapped by default rather than cut (TextOverflow.Wrap): a line wider than its room, the box's or its `maxWidth`,
      // takes another, a word too long for one broken where it must be; `maxLines` still cuts what passes it
      if (wraps) {
        val room = if (single > 0.0) single else if (wrapAt > 0.0) wrapAt else if (limit > 0.0) limit else 0.0
        if (room > 0.0 && !surely(room) && node.getWidth() > room + 0.5) {
          wrapToFit()
          node.setTextLimit(room, 0)
          wrapAt = room
          single = 0.0
        }
        else if (room > 0.0 && wrapAt == 0.0) single = 0.0
      }
      cutTo(wanted, single, lines)
      if (own > 0.0 && !surely(own) && node.textWidth() > own + 0.5) {
        if (wraps) wrapToFit() else cutTo(wanted, own, 0, whole = true)
      }
      elided @= node.getText() != wanted
      val kept = if (wrapAt > 0.0 && (single == 0.0 || wrapAt < single)) wrapAt else single
      held = asked || boxed
      if (held) node.setTextLimit(kept, lines)
      if (boxed) drawnAfter = node.getWidth()
      // let go of their box, they are whole already, though the web reads the width they are drawn at a moment later
      boxWhole = if (boxed || was) whole else 0.0
      if (elided() || described.nonEmpty) describe(wanted)
    }
  }

  /** How wide the widest word of `wanted` is, measured as it is drawn, with a pixel to spare: the narrowest the words
    * can be wrapped to without breaking one. The words are written back after. */
  private def widestWord(wanted: String): Double = {
    var widest = 0.0
    var seen: List[String] = Nil
    var start = -1
    var i = 0
    while (i <= wanted.length) {
      val gap = i == wanted.length || wanted.charAt(i).isWhitespace
      if (gap && start >= 0) {
        val word = wanted.substring(start, i)
        // the same word measured once
        if (!seen.contains(word)) {
          seen = word :: seen
          write(word)
          val w = node.textWidth()
          if (w > widest) widest = w
        }
        start = -1
      }
      else if (!gap && start < 0) start = i
      i += 1
    }
    write(wanted)
    if (widest > 0.0) widest + 1.0 else 0.0
  }

  // whether words too wide wrap rather than end in an ellipsis (`TextOverflow.Wrap`): not words laid out as they are
  // written (`WhiteSpace.Pre`, code), which keep their lines, nor where the platform cannot wrap words at a width (an
  // Apple TV, for now), where they are cut as before rather than run past their room
  private def wraps: Boolean = overflow == TextOverflow.Wrap && whiteSpace() != WhiteSpace.Pre && node.wrapsToLimit()
  // whether the words are wrapped to fit their room, where they would otherwise be on one line
  private var wrappedToFit = false

  /** The words let wrap where they are on one line, and a word too long for a line broken, to fit their room. */
  private def wrapToFit(): Unit = if (!wrappedToFit) {
    wrappedToFit = true
    if (whiteSpace() == WhiteSpace.NoWrap) node.setWhiteSpace(WhiteSpace.Normal.value)
    node.setWrapAnywhere(true)
  }

  /** The words as they were set, before they were wrapped to fit. */
  private def unwrap(): Unit = {
    wrappedToFit = false
    node.setTextLimit(0.0, 0)
    node.setWhiteSpace(whiteSpace().value)
    node.setWrapAnywhere(wrapAnywhere())
  }

  // cut short, the words are named whole for whatever reads the page (`semantics`); a name the app gave them stands
  private def describe(wanted: String): Unit = {
    val ours = semantics() == Semantics(label = described)
    if (elided() && (ours || semantics() == Semantics.Plain)) {
      described = wanted
      semantics @= Semantics(label = wanted)
    }
    else if (!elided() && ours) {
      described = ""
      semantics @= Semantics.Plain
    }
  }

  // (`whole`: measured whole, as words given a width of their own are, where the node is as wide as that)
  private def cutTo(wanted: String, limit: Double, lines: Int, whole: Boolean = false): Unit = {
    def wide: Double = if (whole) node.textWidth() else node.getWidth()
    if (limit > 0.0 && !surely(limit) && wide > limit) {
      // the most letters kept, found in as many measures as halvings: a Roku measures each one
      val side = elide()
      var fewest = 0
      var most = wanted.length
      while (fewest < most) {
        val half = (fewest + most + 1) / 2
        write(TextView.kept(wanted, half, side))
        if (wide <= limit) fewest = half else most = half - 1
      }
      write(TextView.kept(wanted, fewest, side))
    }
    if (lines > 0 && wanted.nonEmpty) {
      // a line's height, from one letter: n lines are no taller than n of them, give or take half of one
      write(wanted.substring(0, 1))
      val one = node.getHeight()
      val tallest = one * lines + one / 2.0
      write(wanted)
      if (one > 0.0 && node.getHeight() > tallest) {
        var fewest = 0
        var most = wanted.length
        while (fewest < most) {
          val half = (fewest + most + 1) / 2
          write(wanted.substring(0, half) + "…")
          if (node.getHeight() <= tallest) fewest = half else most = half - 1
        }
        // back to the last whole word, where there is one
        val space = wanted.substring(0, fewest).lastIndexOf(' ')
        val cut = if (space > fewest / 2) space else fewest
        write(wanted.substring(0, cut).trim + "…")
      }
    }
  }

  override def kind: String = "TextView(" + content() + ")"
}

/** A line of text in one call: `TextView("Hello")`, `TextView.bound(name() + "!")`, `TextView.wrapped(long, 320.0)`. */
object TextView {
  // the text views to look at against their box once the turn is over (keepToBox): every one asked for in a turn, in
  // one look, after what they are in has settled (a box bound to the words' width has followed them by then)
  private val waiting: Array[List[TextView]] = Array(Nil)

  private[component] def check(view: TextView): Unit =
    // (words that have any, asked without reading a size: a read of words not on the page yet lends them to it, which
    // made the browser lay the page out for every label of a screen being built)
    if (!view.boxCheckWaits && !view.takenApart && (view.boxed || view.content().nonEmpty)) {
      view.boxCheckWaits = true
      val first = waiting(0).isEmpty
      waiting(0) = view :: waiting(0)
      if (first) Platform.afterTurn(() => {
        val views = waiting(0).reverse
        waiting(0) = Nil
        // one look: where each container's words lie in its box is worked out once for all of them (Container.space)
        look()
        looking(0) = true
        var left = views
        try while (left.nonEmpty) {
          val v = left.head
          left = left.tail
          v.boxCheckWaits = false
          if (!v.takenApart) v.keepToBox()
        }
        finally looking(0) = false
      })
    }
  /** Which look at the boxes this is, and whether one is under way (check). */
  private[component] val looks: Array[Int] = Array(0)
  /** The reading direction, as the look under way has it. */
  private[component] val lookRtl: Array[Boolean] = Array(false)
  private[component] def look(): Unit = {
    looks(0) += 1
    lookRtl(0) = everyui.i18n.Direction.rtl()
  }
  /** How many text views are cut or wrapped to their box now: while none is, what follows words in a row is the rest of
    * the row as it is drawn (Container.endOf). */
  private[component] val boxedCount: Array[Int] = Array(0)
  private val looking: Array[Boolean] = Array(false)

  /** The narrowest room, in the words' size (ems: four), at which words kept to their box have a word too long for a
    * line broken across lines: a URL or a path in a narrow column is broken where it must be, but words given less (the
    * first links in a row too wide for its box, left a pixel by what follows them) wrap between words alone, each word
    * whole on its line, and run past the room rather than stand one letter to a line. */
  val breakingRoom: Double = 4.0

  /** The seam's name for an alignment: "start", "center" or "end", resolved against the platform's direction. */
  def side(align: TextAlign): String =
    if (align == TextAlign.Center) "center" else if (align == TextAlign.End) "end" else "start"

  /** `words` cut to `letters` of them with an ellipsis where `side` says: "AppConn…", "…ection" or "App…ion". */
  def kept(words: String, letters: Int, side: Elide): String =
    if (side == Elide.Start) "…" + words.substring(words.length - letters, words.length)
    else if (side == Elide.Middle) {
      val front = (letters + 1) / 2
      words.substring(0, front) + "…" + words.substring(words.length - (letters - front), words.length)
    }
    else words.substring(0, letters) + "…"

  def apply(value: String, points: Double = 14.0, colour: => Color = Theme.text()): TextView = new TextView {
    content @= value
    font.size @= points
    color := colour
  }

  /** Bound to `value`, so it follows what it reads. */
  def bound(value: => String, points: Double = 14.0, colour: => Color = Theme.text()): TextView = new TextView {
    content := value
    font.size @= points
    color := colour
  }

  /** Bound to `value`, and out of the way — no height — while there is none. */
  def optional(value: => Option[String], points: Double = 14.0, colour: => Color = Theme.text()): TextView = {
    val view = bound(value.getOrElse(""), points, colour)
    view.size.height := (if (value.isEmpty) 0.0 else view.measured.height())
    view
  }

  /** Words that follow what they read, wrapped to `width` and aligned in it: a paragraph, a note under a form. */
  def paragraph(words: => String, width: Double, points: Double = 14.0, colour: => Color = Theme.text(),
                side: TextAlign = TextAlign.Start): TextView = new TextView {
    whiteSpace @= WhiteSpace.Normal
    size.width @= width
    font.size @= points
    color := colour
    align @= side
    content := words
  }

  /** Wrapped to `width`, as tall as it needs. */
  def wrapped(value: String, width: Double, points: Double = 14.0, colour: => Color = Theme.text()): TextView = new TextView {
    content @= value
    font.size @= points
    color := colour
    whiteSpace @= WhiteSpace.Normal
    size.width @= width
    measure.trigger()
  }

  /** One bound line that takes no room at all while it has nothing to say, so a column or a row it is in closes up
    * round it. The words are measured inside a box of their own (a size set on the words would be the size they are
    * measured at): `widest` cuts them short with an ellipsis (0 for no limit), and `leading` makes the line that many
    * times its size tall with the words in the middle of it (0 for the face's own). */
  def line(words: => String, points: Double, colour: => Color, weight: everyui.component.types.FontWeight =
             everyui.component.types.FontWeight.Normal, widest: => Double = 0.0, leading: Double = 0.0): Container = {
    val text = new TextView {
      content := words
      font.size @= points
      font.weight @= weight
      color := colour
      maxWidth := widest
    }
    val tall = points * leading
    text.position.top := (if (text.size.height() < tall) (tall - text.size.height()) / 2.0 else 0.0)
    new Container {
      size.width := (if (text.content().isEmpty) 0.0 else text.size.width())
      size.height := (if (text.content().isEmpty) 0.0 else math.max(tall, text.size.height()))
      children += text
    }
  }
}

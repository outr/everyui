package everyui.chat

import everyui.component.{Component, Container, TextView}
import everyui.component.types.{FontWeight, TextDirection, TextOverflow, WhiteSpace}
import everyui.event.Key
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.layout.{FlowLayout, VerticalLayout}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A message's answers (`ChatEntry.Message.replies`) under its bubble, as `MessageView` draws them: each group in the
  * order it first appears, its caption (the group's words, on one line) over its chips, which wrap within `widest`.
  * A press runs the reply's action once (the same chip pressed again within `QuickReplies.debounce` is the same press) and shows the
  * chip pressed and the rest of its group dimmed, kept on the message (`ChatEntry.Message.pressedReplies`) until its
  * replies change.
  *
  * The arrows go along the chips, Left and Right, and between the groups, Up and Down; out of the first and the last
  * they are `back`, `forward`, `up` and `down`, and Escape or Back is `out`, as the message says. */
class QuickReplies(m: ChatEntry.Message, list: List[QuickReply], widest: => Double, back: () => Unit,
                   forward: () => Unit, up: () => Unit, down: () => Unit, out: () => Unit) extends Container {
  private val tall: Boolean = QuickReplies.tall()
  // the groups in the order they first appear, each with its replies by their place in the list
  private val groups: List[Option[String]] = list.map(_.group).distinct
  private val chips: Vector[Vector[QuickReplyChip]] = groups.toVector.map { g =>
    list.zipWithIndex.filter(_._1.group == g).toVector.map { case (r, i) =>
      new QuickReplyChip(r, tall, widest, () => press(i))
    }
  }
  /** Every chip, group after group. */
  val all: Vector[QuickReplyChip] = chips.flatten
  // where each is in the list
  private val places: Vector[Int] = groups.toVector.flatMap(g => list.indices.filter(i => list(i).group == g))
  /** The first chip of the last group: what Up from under it comes to. */
  def bottom: Option[QuickReplyChip] = chips.lastOption.flatMap(_.headOption)

  // the chip last pressed, and when: pressed again at once, it is a double tap, and the same press
  private var last: Int = -1
  private var pressedAt: Double = -1.0e9
  private def press(i: Int): Unit = {
    val now = Platform.ticks()
    if (i != last || now - pressedAt >= QuickReplies.debounce) {
      last = i
      pressedAt = now
      val r = list(i)
      m.pressedReplies = Some((list, m.pressedNow(list) + (r.group -> i)))
      show()
      // (last: the app may take the replies away as it hears it, and this with them)
      r.action()
    }
  }
  // each chip pressed, or dimmed, as the message keeps them
  private def show(): Unit = {
    val chosen = m.pressedNow(list)
    all.zip(places).foreach { case (c, i) =>
      val at = chosen.get(c.reply.group)
      c.pressed @= at.contains(i)
      c.dimmed @= at.nonEmpty
    }
  }

  private val lines: Vector[Component] = chips.zip(groups).map { case (row, g) =>
    val flow = new Container {
      layout @= Some(FlowLayout(QuickReplies.gap, QuickReplies.gap))
      children ++= row
    }
    // on one line as wide as they are, else wrapped at the widest a bubble is
    flow.size.width := math.min(widest, row.foldLeft(-QuickReplies.gap)((w, c) => w + c.size.width() + QuickReplies.gap))
    val group = new Container {
      layout @= Some(VerticalLayout(QuickReplies.captionGap))
      children ++= g.map(caption).toVector :+ flow
    }
    group
  }
  private def caption(words: String): TextView = new TextView {
    content @= words
    direction @= TextDirection.Auto
    font.size @= 12.0
    font.weight @= FontWeight.SemiBold
    color := Theme.muted()
    whiteSpace @= WhiteSpace.NoWrap
    textOverflow @= TextOverflow.Ellipsis
    maxWidth := widest
    tooltip := (if (elided()) words else "")
  }

  // along the chips, back and on, and from group to group
  private val ahead: Key = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
  private val behind: Key = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
  all.zipWithIndex.foreach { case (c, i) =>
    if (i > 0) c.exitFocus(behind, all(i - 1)) else c.exitAction(behind, back)
    if (i < all.length - 1) c.exitFocus(ahead, all(i + 1)) else c.exitAction(ahead, forward)
    val g = chips.indexWhere(_.exists(_ eq c))
    if (g > 0) c.exitFocus(Key.ArrowUp, chips(g - 1).head) else c.exitAction(Key.ArrowUp, up)
    if (g < chips.length - 1) c.exitFocus(Key.ArrowDown, chips(g + 1).head) else c.exitAction(Key.ArrowDown, down)
    c.event.key.down.attach(e => if (e.key == Key.Escape || e.key == Key.Back) out())
  }

  /** Whether the focus is on one of its chips. */
  def holdsFocus: Boolean = Focus.focused().exists(f => Focus.within(f, this))

  layout @= Some(VerticalLayout(QuickReplies.groupGap))
  children ++= lines
  show()

  override def kind: String = "QuickReplies(" + m.id + ")"
}

object QuickReplies {
  /** Space between chips, along a line and between lines. */
  val gap: Double = 8.0
  /** Space under a caption, and between groups. */
  val captionGap: Double = 4.0
  val groupGap: Double = 10.0
  /** How soon after a press another is the same press (a double tap), in milliseconds. */
  val debounce: Double = 400.0

  /** About how tall the chips for `list` are, before they are drawn (`Transcript.estimate`): a line of chips to each
    * group, and a caption over each that has one. */
  def estimate(list: List[QuickReply]): Double = if (list.isEmpty) 0.0 else {
    val groups = list.map(_.group).distinct
    MessageView.repliesGap + groups.size * ((if (tall()) QuickReplyChip.tallHeight else QuickReplyChip.height) + groupGap) +
      groups.count(_.nonEmpty) * 18.0
  }

  /** Whether chips are a thumb's height: a touch screen, a finger used last, or a remote across a room. */
  def tall(): Boolean = Composer.touchScreen() || everyui.event.Pointer.finger || Platform.remoteNavigation()
}

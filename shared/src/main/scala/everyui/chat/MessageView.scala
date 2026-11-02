package everyui.chat

import everyui.Color
import everyui.component.{Component, Container, Row, TextView}
import everyui.component.types.{FontWeight, TextDirection}
import everyui.kit.Spinner
import everyui.list.ListRow
import everyui.theme.Theme
import reactify.*

/** A message in the transcript, `width` wide: a bubble of its words (`render` makes them), at the start edge under the
  * author's avatar and name, or at the end edge for the person using the app. A message that `leads` its group has the
  * avatar, the name and when it was said (`now` keeps "5m" current); the rest of the group sit close under it. Under
  * the bubble go the app's `meta` line and, while the words are still arriving, a small spinner. When it was said has
  * its date too unless the transcript names the days (`dated`). The words can be selected and copied
  * (`Component.selectable`). A click on the avatar or the name is `pressAuthor`, under the hand where it `answers`.
  *
  * Where the transcript shows reactions (`reacting`), the message's (`ChatEntry.Message.reactions`) go under the bubble
  * as small pills, an emoji and how many, the person's own lit, who reacted in each one's tip: a click on one adds the
  * person's or takes it away (`react`), and a "+" after them opens a picker for another (`addReaction`).
  *
  * On a phone (`width` under `Transcript.narrow`) everybody else's avatar goes small at the start of the name's line,
  * and the bubble under them takes all the width but the side margins. */
class MessageView(m: ChatEntry.Message, leads: Boolean, width: => Double, now: Val[Long],
                  render: (Val[String], Val[Double]) => Component, dated: Boolean = true,
                  pressAuthor: () => Unit = () => (), answers: => Boolean = false,
                  reacting: => Boolean = false, react: (String, Boolean) => Unit = (_: String, _: Boolean) => (),
                  addReaction: Component => Unit = (_: Component) => ()) extends Container {
  private val top: Double = if (leads) Transcript.groupGap else Transcript.lineGap

  private val name: TextView = new TextView {
    content @= m.author.name
    direction @= TextDirection.Auto
    font.size @= 12.5
    font.weight @= FontWeight.SemiBold
    color := Theme.text()
  }
  private val when: TextView = new TextView {
    content := m.time.map(t => ChatTime.relative(t, now(), dated)).getOrElse("")
    direction @= TextDirection.Auto
    font.size @= 11.5
    color := Theme.muted()
  }
  m.time.foreach(t => when.tooltip @= everyui.util.Format.time(t))
  private val header: Container = if (m.self) Row(8.0, middle = true)(when) else Row(8.0, middle = true)(name, when)
  header.visible @= leads

  private val words: Component = render(own(Val(if (m.text().isEmpty && m.streaming()) "…" else m.text())),
    own(Val(Transcript.widest(width) - Transcript.padX * 2.0)))
  // there to be read and copied: a drag selects them, on into the next message
  words.selectable @= true
  private[chat] val bubble: Container = new Container {
    size.width := math.max(Transcript.padX * 2.0 + 8.0, words.size.width() + Transcript.padX * 2.0)
    size.height := words.size.height() + Transcript.padY * 2.0
    radius @= 14.0
    backgroundColor := MessageView.fill(m.self)
    children += words
  }
  words.position.left := ListRow.along(Transcript.padX, words.size.width(), bubble.size.width())
  words.position.top @= Transcript.padY

  private val spinner: Spinner = new Spinner(12.0, () => Theme.muted())
  spinner.size.width := (if (m.streaming()) 12.0 else 0.0)
  spinner.visible := m.streaming()
  private val meta: TextView = new TextView {
    content := m.meta().getOrElse("")
    direction @= TextDirection.Auto
    font.size @= 11.5
    color := Theme.muted()
    maxWidth := Transcript.widest(width)
  }
  private val footer: Container = Row(6.0, middle = true)(spinner, meta)

  // the reactions under the bubble, made while the transcript shows them: on one line as wide as they are, or wrapped
  // at the bubble's widest
  private val reacted: Container = new Container {
    layout @= Some(everyui.layout.FlowLayout(4.0, 4.0))
  }
  private val reactionsShown: Val[Boolean] = own(Val(reacting && m.reactions().nonEmpty))
  private def pills(): Unit = if (reacting) {
    val made: Vector[Component] = m.reactions().toVector.map(pill) :+ adder()
    reacted.children.replaceMaking(made)
  }
  private def pill(r: ChatReaction): Component = new Container {
    // one of the app's own emoji (its `:name:`) as its picture
    private val glyph: Component = everyui.emoji.CustomEmoji.of(r.emoji)
      .map(e => new everyui.emoji.CustomEmojiView(e, 17.0)).getOrElse(new TextView {
        content @= r.emoji
        font.size @= 14.0
      })
    private val count = new TextView {
      content @= r.count.toString
      font.size @= 12.0
      font.weight @= (if (r.mine) FontWeight.SemiBold else FontWeight.Normal)
      color := (if (r.mine) Theme.accent() else Theme.muted())
    }
    private val line = Row(4.0, middle = true)(glyph, count)
    size.width := line.size.width() + 16.0
    size.height @= MessageView.pillHeight
    radius @= MessageView.pillHeight / 2.0
    borderWidth @= 1.0
    borderColor := (if (r.mine) Theme.accent() else Theme.border())
    backgroundColor := (if (r.mine) Theme.accent().withAlpha(0.18) else if (event.pointer.over()) Theme.controlLit()
      else Theme.control())
    tooltip @= r.who.mkString(", ")
    semantics @= everyui.component.types.Semantics.button(r.emoji + " " + r.count.toString)
    cursor @= "pointer"
    line.position.left @= 8.0
    line.position.middle := size.middle()
    children += line
    event.click.attach { e =>
      e.consume()
      react(r.emoji, !r.mine)
    }
  }
  // the "+" after them, for another
  private def adder(): Component = new Container {
    private val glyph = new everyui.component.MaterialIconView(everyui.font.MaterialIcon.AddReaction, 15.0)
    glyph.color := Theme.muted()
    size.width @= MessageView.pillHeight + 8.0
    size.height @= MessageView.pillHeight
    radius @= MessageView.pillHeight / 2.0
    borderWidth @= 1.0
    borderColor := Theme.border()
    backgroundColor := (if (event.pointer.over()) Theme.controlLit() else everyui.Color.Clear)
    tooltip @= everyui.emoji.EmojiWords.addReaction
    semantics @= everyui.component.types.Semantics.button(everyui.emoji.EmojiWords.addReaction)
    cursor @= "pointer"
    glyph.position.center := size.center()
    glyph.position.middle := size.middle()
    children += glyph
    private val self = this
    event.click.attach { e =>
      e.consume()
      addReaction(self)
    }
  }
  // on one line where they fit, else wrapped at the widest a bubble is
  private val reactedAcross: Val[Double] = own(Val(math.min(Transcript.widest(width),
    reacted.children().foldLeft(0.0)((w, c) => w + c.size.width() + 4.0))))
  reacted.size.width := reactedAcross()
  reacted.visible := reactionsShown()
  m.reactions.attach(_ => pills())
  // (shown once the transcript shows them, made then)
  private val reacts: Val[Boolean] = own(Val(reacting))
  reacts.attach(_ => pills())
  pills()
  footer.visible := m.streaming() || m.meta().nonEmpty

  // whether it is drawn as on a phone: the avatar small beside the name, the bubble the width less the margins
  private val narrow: Val[Boolean] = own(Val(width < Transcript.narrow))
  // whether the small avatar shares the header's line
  private val faceInLine: Val[Boolean] = own(Val(narrow() && !m.self && leads))

  // along the row: the start edge, past the avatar where it has its own room, for everybody else's; the end edge for
  // one's own
  private def at(length: Double): Double =
    if (m.self) ListRow.along(width - Transcript.side - length, length, width)
    else ListRow.along(Transcript.indent(width), length, width)

  // the header's line: as tall as the small avatar where it is in it
  private val lineHeight: Val[Double] = own(Val(if (faceInLine()) math.max(header.size.height(), Transcript.smallAvatar)
    else header.size.height()))
  private val headerHeight: Val[Double] = own(Val(if (leads) lineHeight() + 3.0 else 0.0))
  header.position.left := (if (!faceInLine()) at(header.size.width())
    else ListRow.along(Transcript.side + Transcript.smallAvatar + Transcript.smallGap, header.size.width(), width))
  header.position.top := top + (lineHeight() - header.size.height()) / 2.0
  bubble.position.left := at(bubble.size.width())
  bubble.position.top := top + headerHeight()
  footer.position.left := at(footer.size.width())
  reacted.position.left := at(reacted.size.width())
  reacted.position.top := bubble.position.top() + bubble.size.height() + 4.0
  footer.position.top := (if (reactionsShown()) reacted.position.top() + reacted.size.height() + 3.0
    else bubble.position.top() + bubble.size.height() + 3.0)

  // the avatar, where the message leads its group: large in its own room, or small beside the name on a phone, made
  // again as the width crosses from one to the other
  private var face: Option[Avatar] = None
  private def faced(small: Boolean): Unit = if (!m.self && leads) {
    face.foreach { f =>
      children -= f
      f.dispose()
    }
    val d = if (small) Transcript.smallAvatar else Transcript.avatar
    val made = new Avatar(m.author, d)
    made.position.left := ListRow.along(Transcript.side, d, width)
    made.position.top := (if (small) top + (lineHeight() - d) / 2.0 else top)
    MessageView.pressable(made, pressAuthor, answers)
    children += made
    face = Some(made)
  }
  faced(narrow())
  narrow.attach(small => faced(small))
  if (!m.self) MessageView.pressable(name, pressAuthor, answers)
  size.width := width
  size.height := footer.position.top() + (if (footer.visible()) footer.size.height() + 2.0 else -3.0)
  children ++= List(header, bubble, reacted, footer)

  override def kind: String = "MessageView(" + m.id + ")"
}

object MessageView {
  /** `c` pressed is `press`, the click kept from the row under it, and under the hand while it `answers`. */
  def pressable(c: Component, press: () => Unit, answers: => Boolean): Unit = {
    c.event.click.attach { e =>
      e.consume()
      if (answers) press()
    }
    // (the arrow where it does nothing: a click listened for alone gives the hand)
    c.cursor := (if (answers) "pointer" else "default")
  }

  /** How much of everybody else's bubble is drawn, 0 to 1: all of it (1, the default), or less where the app shows a
    * picture behind its conversations and wants it through their bubbles as through one's own, about 0.8 keeping the
    * words on them easy to read. The words, and the code and tables in a message, are drawn as they are. */
  val othersOpacity: Var[Double] = Var(1.0)

  /** How tall a reaction's pill is. */
  val pillHeight: Double = 24.0

  /** A bubble's colour: one's own a wash of the accent, everybody else's the control colour (`othersOpacity` of it);
    * the text colour reads on either, in a light theme and a dark one. */
  def fill(self: Boolean): Color = if (self) Theme.accent().withAlpha(0.2) else {
    val c = Theme.control()
    val o = math.max(0.0, math.min(1.0, othersOpacity()))
    if (o >= 1.0) c else c.withAlpha(c.alpha * o)
  }
}

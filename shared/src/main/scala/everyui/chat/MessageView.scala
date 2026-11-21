package everyui.chat

import everyui.Color
import everyui.component.{Button, ButtonStyle, Component, Container, Row, TextView}
import everyui.component.types.{FontWeight, TextDirection}
import everyui.event.{Key, KeyEvent}
import everyui.focus.Focus
import everyui.i18n.Direction
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
  * A message that asks something of the person (`ChatEntry.Message.attention`) has its bubble edged and washed in the
  * warning tone, its words, where it has any, under the bubble; one set aside, the edge dimmed. Neither moves the
  * bubble. Asking while it shows, it is said (`announce`). The app's own actions on it (`ChatEntry.Message.actions`) go
  * under everything else as small buttons with an icon and words, made only while it has some: the arrows go along
  * them, Up, Down, Escape and Back to `out`, and on past the last is `onward`. Neither is listened for until the app
  * makes it; `kept` is told as what it asks is first made, for what reads it out.
  *
  * Its answers (`ChatEntry.Message.replies`) go under the bubble and the words saying it asks, over the rest, as chips
  * (`QuickReplies`), made only while it has some: the forward arrow comes to them after those words and before the
  * actions, Up and Down go between the message, the chips and the actions.
  *
  * Its highlighted passages (`ChatEntry.Message.highlights`) are marked in its words where they are drawn as Markdown
  * (`MessageMarkdown`). Where it asks too, the words under the bubble count them ("2 questions for you") where they
  * are the default's, and a press on them (a click, Enter, a remote's OK) brings the next into view (`reveal`), from
  * the first again after the last, and says it; the forward arrow on the message comes to them before its actions.
  *
  * On a phone (`width` under `Transcript.narrow`) everybody else's avatar goes small at the start of the name's line,
  * and the bubble under them takes all the width but the side margins. */
class MessageView(m: ChatEntry.Message, leads: Boolean, width: => Double, now: Val[Long],
                  render: (Val[String], Val[Double]) => Component, dated: Boolean = true,
                  pressAuthor: () => Unit = () => (), answers: => Boolean = false,
                  reacting: => Boolean = false, react: (String, Boolean) => Unit = (_: String, _: Boolean) => (),
                  addReaction: Component => Unit = (_: Component) => (), announce: String => Unit = (_: String) => (),
                  out: Option[Component] = None, onward: () => Unit = () => (),
                  kept: () => Unit = () => (), reveal: Component => Unit = (_: Component) => (),
                  regroup: () => Unit = () => ()) extends Container {
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

  private val meta: TextView = new TextView {
    content := m.meta().getOrElse("")
    direction @= TextDirection.Auto
    font.size @= 11.5
    color := Theme.muted()
    maxWidth := Transcript.widest(width)
  }
  private val footer: Container = Row(6.0, middle = true)(meta)
  // the small spinner before the meta line while the words are still arriving, made only then: a message done with
  // keeps none, nor its turning
  private var spinner: Option[Spinner] = None
  private def spin(on: Boolean): Unit =
    if (on && spinner.isEmpty) {
      val made = new Spinner(12.0, () => Theme.muted())
      spinner = Some(made)
      footer.children @= made +: footer.children()
    }
    else if (!on) spinner.foreach { s =>
      spinner = None
      footer.children -= s
      s.dispose()
    }
  follow(m.streaming, spin)
  spin(m.streaming())
  /** The spinner under the bubble, while the words are still arriving. */
  private[chat] def spinning: Option[Spinner] = spinner

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
      color := (if (r.mine) Theme.accentText() else Theme.muted())
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

  // ---- what it asks, and the app's actions: nothing made or bound for a message with neither ----

  // the edge and the wash, bound the first time it asks anything, and the words under the bubble, made the first time
  // it asks with some and kept, hidden, once it does not
  private var edged = false
  private var note: Option[Container] = None
  private var noteWords: Option[TextView] = None
  private def attend(a: Option[Attention]): Unit = {
    if (a.nonEmpty && !edged) {
      edged = true
      paint()
    }
    if (MessageView.label(a).nonEmpty && note.isEmpty) noted()
    told(a)
  }
  // the words under the bubble as they stand, counting the highlights found where they are the default's, and whether
  // a press on them brings those into view
  private var jumps = false
  private def told(a: Option[Attention]): Unit = {
    val n = found.size
    val said = MessageView.label(a).map(w => MessageView.counted(w, n))
    jumps = said.nonEmpty && n > 0
    noteWords.foreach(_.content @= said.getOrElse(""))
    note.foreach { made =>
      made.visible @= said.nonEmpty
      made.focusable @= jumps
      made.cursor @= (if (jumps) "pointer" else "default")
      made.semantics @= (if (jumps) everyui.component.types.Semantics.button(said.get) else
        everyui.component.types.Semantics.Plain)
    }
  }
  private def noted(): Unit = {
    val glyph = new everyui.component.MaterialIconView(everyui.font.MaterialIcon.HelpOutline, 14.0)
    glyph.color := Theme.warningText()
    val said = new TextView {
      direction @= TextDirection.Auto
      font.size @= 11.5
      font.weight @= FontWeight.SemiBold
      color := Theme.warningText()
      maxWidth := Transcript.widest(width) - 18.0
    }
    // pressed (a click, Enter, OK) while there are highlights to go to; the keys out of it as out of the actions
    val made = new Container {
      override def keepsKey(key: Key): Boolean = key == Key.Enter && jumps
    }
    made.layout @= Some(everyui.layout.HorizontalLayout(4.0, true))
    made.children ++= List(glyph, said)
    made.radius @= 4.0
    made.backgroundColor := (if (made.focusShown()) Theme.controlLit() else Color.Clear)
    made.event.click.attach { e =>
      if (jumps) {
        e.consume()
        jump()
      }
    }
    made.event.key.down.attach { e =>
      if (e.key == Key.Enter && jumps) jump()
      else if (e.key == Key.Escape || e.key == Key.Back) out.foreach(_.focus())
    }
    val forward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
    val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
    made.exitAction(forward, () => replyChips.headOption.orElse(actionButtons.headOption).map(_.focus())
      .getOrElse(onward()))
    // (down onto the answers under it, where it has some)
    made.exitAction(Key.ArrowDown, () => replyChips.headOption.orElse(out).foreach(_.focus()))
    out.foreach { o =>
      List(back, Key.ArrowUp).foreach(k => made.exitAction(k, () => o.focus()))
    }
    made.position.left := at(made.size.width())
    made.position.top := bubble.position.top() + bubble.size.height() + 4.0
    note = Some(made)
    noteWords = Some(said)
    children += made
    place()
  }
  // the room the words under the bubble take while they show
  private def noteRoom: Double = note.filter(_.visible()).map(_.size.height() + 4.0).getOrElse(0.0)
  // what follows under the bubble (the answers, then the reactions and the meta line) moved down by what is over it:
  // bound again as the words or the answers are made
  private def place(): Unit = {
    replying.foreach(r => r.position.top := bubble.position.top() + bubble.size.height() + noteRoom +
      MessageView.repliesGap)
    def room: Double = noteRoom + replying.map(_.size.height() + MessageView.repliesGap + 2.0).getOrElse(0.0)
    reacted.position.top := bubble.position.top() + bubble.size.height() + room + 4.0
    footer.position.top := (if (reactionsShown()) reacted.position.top() + reacted.size.height() + 3.0
      else bubble.position.top() + bubble.size.height() + room + 3.0)
  }

  // ---- its answers (ChatEntry.Message.replies): nothing made or bound for a message with none ----

  // the chips under the bubble, made while it has some, and made again (they alone) as they change
  private var replying: Option[QuickReplies] = None
  private def replied(list: List[QuickReply]): Unit = if (list.nonEmpty || replying.nonEmpty) {
    // (the focus on one of them going, back to `out`: something must keep it, on a remote above all)
    val held = replying.exists(_.holdsFocus)
    replying.foreach { r =>
      children -= r
      r.dispose()
    }
    replying = if (list.isEmpty) None else Some(answersFor(list))
    replying.foreach(r => children += r)
    place()
    if (held) out.foreach(_.focus())
  }
  private def answersFor(list: List[QuickReply]): QuickReplies = {
    val r = new QuickReplies(m, list, Transcript.widest(width),
      back = () => jumper.orElse(out).foreach(_.focus()),
      forward = () => actionButtons.headOption.map(_.focus()).getOrElse(onward()),
      up = () => out.foreach(_.focus()),
      down = () => actionButtons.headOption.orElse(out).foreach(_.focus()),
      out = () => out.foreach(_.focus()))
    r.position.left := at(r.size.width())
    r
  }
  /** Its answers' chips, group after group, while it has some. */
  private[chat] def replyChips: Vector[QuickReplyChip] = replying.map(_.all).getOrElse(Vector.empty)
  /** Its answers under the bubble, while it has some. */
  private[chat] def quickReplies: Option[QuickReplies] = replying
  // (said as it comes to ask while it shows, not as it is drawn asking)
  private def heard(a: Option[Attention]): Unit = {
    attend(a)
    a match {
      case Some(Attention.Asking(_)) => announce(m.author.name + ", " + Attention.said(a))
      case _ => ()
    }
  }
  /** The words under the bubble saying what it asks, where they have been made. */
  private[chat] def asking: Option[Container] = note
  /** The words under the bubble, where a press on them brings its highlights into view. */
  private[chat] def jumper: Option[Container] = note.filter(_ => jumps)

  // the bubble's fill and edge, bound again as what it asks or where it goes is first made, to follow both: what it asks
  // first, then where it goes
  private def paint(): Unit = {
    def asked: Option[Attention] = if (m.attentionKept) m.attention() else None
    def going: Option[MessageDirection] = if (m.directionKept) m.direction() else None
    bubble.backgroundColor := MessageView.fillOf(m.self, asked, going)
    // (an edge drawn inside the box: the words stay where they are)
    bubble.borderWidth := MessageView.edgeWidthOf(asked, going)
    bubble.borderColor := MessageView.edgeOf(asked, going)
  }

  // ---- where it goes or came from: nothing made or bound for a message with neither ----

  // the line over its words ("To everyui", the other party's face), made the first time it has a direction and kept,
  // hidden, once it has none
  private var heading: Option[Container] = None
  private def direct(d: Option[MessageDirection]): Unit = if (d.nonEmpty && heading.isEmpty) {
    val arrow = new everyui.component.MaterialIconView(everyui.font.MaterialIcon.Redo, 14.0)
    arrow.icon := MessageView.arrowOf(m.direction())
    arrow.color := m.direction().map(MessageView.toneText).getOrElse(Theme.muted())
    val face = new Avatar(m.direction().map(MessageDirection.faceOf).getOrElse(m.author), MessageView.faceSize)
    face.statusDot @= false
    val said = new TextView {
      content := m.direction().map(MessageDirection.shown).getOrElse("")
      direction @= TextDirection.Auto
      font.size @= 11.5
      font.weight @= FontWeight.SemiBold
      color := m.direction().map(MessageView.toneText).getOrElse(Theme.muted())
      maxWidth := Transcript.widest(width) - Transcript.padX * 2.0 - 44.0
    }
    val line = Row(5.0, middle = true)(arrow, face, said)
    line.visible := m.direction().nonEmpty
    line.position.left := ListRow.along(Transcript.padX, line.size.width(), bubble.size.width())
    line.position.top @= Transcript.padY
    heading = Some(line)
    bubble.children += line
    def over: Double = if (line.visible()) line.size.height() + 4.0 else 0.0
    words.position.top := Transcript.padY + over
    bubble.size.width := math.max(Transcript.padX * 2.0 + 8.0,
      math.max(words.size.width(), if (line.visible()) line.size.width() else 0.0) + Transcript.padX * 2.0)
    bubble.size.height := words.size.height() + over + Transcript.padY * 2.0
    paint()
  }
  // (a change of direction may join it to its neighbours or part it from them: the transcript groups them again)
  private def turned(d: Option[MessageDirection]): Unit = {
    direct(d)
    regroup()
  }
  /** The line over its words saying where it goes or came from, where it has been made. */
  private[chat] def directed: Option[Container] = heading

  // ---- its highlights: nothing made or bound for a message with none ----

  // the Markdown its words are drawn in, which marks them: a message drawn otherwise marks none
  private def markdown: Option[everyui.markdown.MarkdownView] = words match {
    case w: MessageMarkdown => Some(w.view)
    case _ => None
  }
  private def mark(list: Vector[MessageHighlight]): Unit = markdown.foreach { v =>
    val now = list.map(_.passage)
    if (v.passages() != now) v.passages @= now
  }
  // the highlights found in its words, by their index, once there are any
  private def found: Vector[Int] = if (!m.highlightsKept) Vector.empty
    else markdown.map(_.passagesFound().zipWithIndex.collect { case (true, i) => i }).getOrElse(Vector.empty)
  // the one last brought into view
  private var shown = -1

  /** The next of its highlights found brought into view, from the first after the last, and said. */
  private[chat] def jump(): Unit = markdown.foreach { v =>
    val all = found
    if (all.nonEmpty) {
      shown = all.find(_ > shown).getOrElse(all.head)
      v.passageAt(shown).foreach(reveal)
      m.highlightsNow.lift(shown).foreach(h => announce(h.said))
    }
  }

  // the app's actions under the rest, made while it has some, and made again as they change
  private var acting: Option[Container] = None
  private def acts(list: List[MessageAction]): Unit = {
    // (the focus on one going, back to `out`: an action that took itself away, Ignore)
    val held = acting.exists(r => Focus.focused().exists(f => Focus.within(f, r)))
    acting.foreach { r =>
      children -= r
      r.dispose()
    }
    val was = acting.nonEmpty
    acting = if (list.isEmpty) None else Some(actionRow(list))
    acting match {
      case Some(r) =>
        children += r
        size.height := ends + r.size.height() + 4.0
      case None => if (was) size.height := ends
    }
    if (held) out.foreach(_.focus())
  }
  private def actionRow(list: List[MessageAction]): Container = {
    val forward = if (Direction.rtl()) Key.ArrowLeft else Key.ArrowRight
    val back = if (Direction.rtl()) Key.ArrowRight else Key.ArrowLeft
    val buttons = list.map { a =>
      new Button(a.label, () => {
        a.action()
        if (a.done.nonEmpty) announce(a.done)
      }, ButtonStyle.Text, Some(a.icon), 11.5) {
        override def keepsEvent(e: KeyEvent): Boolean =
          e.key == Key.Escape || e.key == Key.Back || super.keepsEvent(e)
      }
    }.toVector
    buttons.zipWithIndex.foreach { case (b, i) =>
      out.foreach { o =>
        // (the list itself, not the nearest of what it holds; up onto the answers over them, where it has some)
        b.exitAction(Key.ArrowUp, () => replying.flatMap(_.bottom).getOrElse(o).focus())
        b.exitAction(Key.ArrowDown, () => o.focus())
        b.event.key.down.attach(e => if (e.key == Key.Escape || e.key == Key.Back) o.focus())
      }
      if (i > 0) b.exitFocus(back, buttons(i - 1))
      else out.foreach(o => b.exitAction(back, () => replyChips.lastOption.orElse(jumper).getOrElse(o).focus()))
      if (i < buttons.length - 1) b.exitFocus(forward, buttons(i + 1))
      else b.exitAction(forward, onward)
    }
    val row = Row(4.0, middle = true)()
    row.children ++= buttons
    // the glyph of the first in line with the bubble's edge, the buttons' padding past it
    val pad = (if (m.self) 1.0 else -1.0) * (if (Direction.rtl()) -1.0 else 1.0) * 11.5 * 1.1
    row.position.left := at(row.size.width()) + pad
    row.position.top := ends + 2.0
    row
  }
  /** The app's actions under the bubble, in order, while it has some. */
  private[chat] def actionButtons: Vector[Component] = acting.map(_.children().toVector).getOrElse(Vector.empty)

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
  // the foot of what it draws, under which the app's actions go
  private def ends: Double = footer.position.top() + (if (footer.visible()) footer.size.height() + 2.0 else -3.0)

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
  size.height := ends
  children ++= List(header, bubble, reacted, footer)
  // (after the rest: the actions come last to the focus too)
  attend(m.attentionNow)
  replied(m.repliesNow)
  acts(m.actionsNow)
  direct(m.directionNow)
  // what it asks and its actions followed once the app has made them (`ChatEntry.Message.attention`, `actions`):
  // until then nothing listens for them
  private var hearing = false
  private var acted = false
  private var marking = false
  private var going = false
  private var answering = false
  private def watch(): Unit = {
    if (!marking && m.highlightsKept) {
      marking = true
      follow(m.highlights, mark)
      mark(m.highlightsNow)
      markdown.foreach(v => follow(v.passagesFound, (_: Vector[Boolean]) => told(m.attentionNow)))
      told(m.attentionNow)
      kept()
    }
    if (!hearing && m.attentionKept) {
      hearing = true
      follow(m.attention, heard)
      kept()
    }
    if (!acted && m.actionsKept) {
      acted = true
      follow(m.actions, acts)
    }
    if (!going && m.directionKept) {
      going = true
      follow(m.direction, turned)
      kept()
    }
    if (!answering && m.repliesKept) {
      answering = true
      follow(m.replies, replied)
    }
  }
  private val watcher: () => Unit = () => watch()
  m.made = watcher :: m.made
  onDispose(() => m.made = m.made.filterNot(_ eq watcher))
  watch()

  override def kind: String = "MessageView(" + m.id + ")"
}

object MessageView {
  /** The words under a bubble that asks (`Attention.Asking`), where it has some. */
  def label(attention: Option[Attention]): Option[String] = attention match {
    case Some(Attention.Asking(label)) => label.filter(_.nonEmpty)
    case _ => None
  }

  /** `label` counting `n` highlights where there are several and it is the default's ("2 questions for you"); else as
    * it is, an app's own words being the app's. */
  def counted(label: String, n: Int): String =
    if (n >= 2 && label == ChatWords.questionForYou) ChatWords.questionsForYou(n) else label

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
    * words on them easy to read. It is the theme's `Theme.surfaceOpacity`, the same `Var` under the name a chat first
    * had it by, so the code blocks in a message follow it too (`MarkdownView.codeOver`, a little more opaque);
    * the words, and the tables in a message, are drawn as they are. */
  val othersOpacity: Var[Double] = Theme.surfaceOpacity

  /** How tall a reaction's pill is. */
  val pillHeight: Double = 24.0
  /** Space between the bubble (or the words under it) and its answers' chips (`ChatEntry.Message.replies`). */
  val repliesGap: Double = 6.0

  /** A bubble's colour: one's own a wash of the accent, everybody else's the control colour (`othersOpacity` of it);
    * the text colour reads on either, in a light theme and a dark one. */
  def fill(self: Boolean): Color = if (self) Theme.accent().withAlpha(0.2) else {
    val c = Theme.control()
    val o = math.max(0.0, math.min(1.0, othersOpacity()))
    if (o >= 1.0) c else c.withAlpha(c.alpha * o)
  }

  /** A bubble's colour as it asks something of the person (`Attention.Asking`): its own with a wash of the warning
    * tone, as opaque as it was; as it was otherwise. */
  def fillFor(self: Boolean, attention: Option[Attention]): Color = attention match {
    case Some(Attention.Asking(_)) =>
      val c = fill(self)
      c.blend(Theme.warning().withAlpha(c.alpha), 0.12)
    case _ => fill(self)
  }

  /** The edge of a bubble asking something of the person: the warning tone, dimmed once it is set aside; none on any
    * other, as before. */
  def edge(attention: Option[Attention]): Color = attention match {
    case Some(Attention.Asking(_)) => Theme.warning()
    case Some(Attention.SetAside) => Theme.warning().withAlpha(0.4)
    case None => Color.Clear
  }
  def edgeWidth(attention: Option[Attention]): Double = attention match {
    case Some(Attention.Asking(_)) => 1.5
    case Some(Attention.SetAside) => 1.0
    case None => 0.0
  }

  /** How large the other party's face is in the line over a directed message's words. */
  val faceSize: Double = 16.0

  /** The tone of a message sent elsewhere (the accent) or come from elsewhere (the theme's info): its edge, and a
    * little of it in its bubble. */
  def tone(direction: MessageDirection): Color = direction match {
    case MessageDirection.To(_, _) => Theme.accent()
    case MessageDirection.From(_, _) => Theme.info()
  }
  /** The tone's words, which read on a bubble in a light theme and a dark one. */
  def toneText(direction: MessageDirection): Color = direction match {
    case MessageDirection.To(_, _) => Theme.accentText()
    case MessageDirection.From(_, _) => Theme.infoText()
  }
  /** The arrow before the words: out (↪) for one sent elsewhere, back (↩) for one come from elsewhere. */
  def arrowOf(direction: Option[MessageDirection]): everyui.font.MaterialIcon = direction match {
    case Some(MessageDirection.From(_, _)) => everyui.font.MaterialIcon.Undo
    case _ => everyui.font.MaterialIcon.Redo
  }

  /** A bubble's colour as it asks (`fillFor`), else with a little of its direction's tone, as opaque as it was. */
  def fillOf(self: Boolean, attention: Option[Attention], direction: Option[MessageDirection]): Color =
    (attention, direction) match {
      case (Some(Attention.Asking(_)), _) | (_, None) => fillFor(self, attention)
      case (_, Some(d)) =>
        val c = fill(self)
        c.blend(tone(d).withAlpha(c.alpha), 0.1)
    }
  /** A bubble's edge: what it asks first (`edge`), else its direction's tone, muted. */
  def edgeOf(attention: Option[Attention], direction: Option[MessageDirection]): Color =
    if (attention.nonEmpty) edge(attention) else direction.map(d => tone(d).withAlpha(0.55)).getOrElse(Color.Clear)
  def edgeWidthOf(attention: Option[Attention], direction: Option[MessageDirection]): Double =
    if (attention.nonEmpty) edgeWidth(attention) else if (direction.nonEmpty) 1.0 else 0.0
}

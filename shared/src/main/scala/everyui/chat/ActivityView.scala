package everyui.chat

import everyui.component.{Component, Container, MaterialIconView, Row, TextView}
import everyui.component.types.{FontWeight, Semantics, TextDirection, WhiteSpace}
import everyui.font.MaterialIcon
import everyui.kit.{BusyRing, Spinner}
import everyui.list.ListRow
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A tool call in the transcript, `width` wide: one line in a quiet box (a spinner while it runs, a tick or a cross once
  * it has, what it does, and a chevron where there is more), which a click (or Enter, from the transcript) opens onto
  * its details. `details` hands over what the entry's `details` made, the first time it is opened: the transcript keeps
  * it, so it is not made again, and takes it back (`release`) before this is taken apart.
  *
  * Where the entry has a `lead`, it goes first in place of the status mark: somebody's avatar, a ring turning around it
  * while the call runs (`Avatar.busy`), or an icon with a spinner on its corner while it runs; once it has, a tick or a
  * cross on the corner. A `body` goes under the summary, drawn by `render` (the transcript's way with a message's
  * words), selectable, cut to `lines` lines and shown whole while the line is open. With neither, it is the line
  * alone. */
class ActivityView(a: ChatEntry.Activity, width: => Double, details: () => Option[Component],
                   render: (Val[String], Val[Double]) => Component = MessageMarkdown.render,
                   lines: => Int = 3) extends Container {
  private val widest: Val[Double] = own(Val(Transcript.widest(width)))
  private val running: Val[Boolean] = own(Val(a.status() == ActivityStatus.Running))

  private val spinner: Spinner = new Spinner(14.0, () => Theme.accent())
  spinner.visible := a.status() == ActivityStatus.Running
  private val glyph: MaterialIconView = new MaterialIconView(MaterialIcon.CheckCircle, 16.0)
  glyph.icon := ActivityView.mark(a.status())
  glyph.color := ActivityView.tint(a.status())
  glyph.visible := a.status() != ActivityStatus.Running
  private val status: Container = new Container {
    size.width @= 16.0
    size.height @= 16.0
    children ++= List(spinner, glyph)
  }
  spinner.position.left @= 1.0
  spinner.position.top @= 1.0

  // what leads the line in place of the status mark, where the entry has a lead
  private val leading: Var[Option[Component]] = own(Var(None))
  private val markWidth: Val[Double] = own(Val(if (leading().isEmpty) 16.0 else ActivityView.room))

  private val summary: TextView = new TextView {
    content := a.summary()
    direction @= TextDirection.Auto
    font.size @= 13.0
    color := Theme.text()
    maxWidth := widest() - Transcript.padX * 2.0 - markWidth() - 18.0 - 16.0
  }
  private val chevron: MaterialIconView = new MaterialIconView(MaterialIcon.ExpandMore, 18.0)
  chevron.icon := (if (a.expanded()) MaterialIcon.ExpandLess else MaterialIcon.ExpandMore)
  chevron.color := Theme.muted()
  chevron.visible := a.opens
  chevron.size.width := (if (a.opens) 18.0 else 0.0)
  private val line: Container = Row(8.0, middle = true)(status, summary, chevron)

  private def lead(l: ActivityLead): Unit = {
    val was = leading()
    val made = l match {
      case ActivityLead.Person(who) => Some(person(who))
      case ActivityLead.Icon(icon) => Some(symbol(icon))
      case _ => None
    }
    leading @= made
    line.children @= Vector(made.getOrElse(status), summary, chevron)
    was.foreach(_.dispose())
  }

  // somebody's face in the ring's room, the ring turning while it runs and the status on its corner once it has
  private def person(who: Participant): Component = {
    val face = new Avatar(who, ActivityView.face)
    face.busy := running()
    val inset = BusyRing.outset(ActivityView.face)
    face.position.left @= inset
    face.position.top @= inset
    val corner = badge()
    corner.visible := !running()
    slot(face, corner)
  }

  // an icon, with a spinner on its corner while it runs and the status once it has
  private def symbol(icon: MaterialIcon): Component = {
    val view = new MaterialIconView(icon, ActivityView.icon)
    view.color := Theme.muted()
    view.position.left @= (ActivityView.room - ActivityView.icon) / 2.0
    view.position.top @= (ActivityView.room - ActivityView.icon) / 2.0
    val turning = new Spinner(ActivityView.badge - 4.0, () => Theme.accent())
    turning.visible := running()
    turning.position.left @= 2.0
    turning.position.top @= 2.0
    slot(view, badge(turning))
  }

  // the status on a lead's corner (the end edge, at the bottom), cut out of what is under it by the box's colour
  private def badge(also: Component*): Container = {
    val mark = new MaterialIconView(MaterialIcon.CheckCircle, ActivityView.badge - 2.0)
    mark.icon := ActivityView.mark(a.status())
    mark.color := ActivityView.tint(a.status())
    mark.visible := !running()
    mark.position.left @= 1.0
    mark.position.top @= 1.0
    new Container {
      size.width @= ActivityView.badge
      size.height @= ActivityView.badge
      radius @= ActivityView.badge / 2.0
      backgroundColor := Theme.surface()
      semantics := Semantics.image(ActivityView.said(a.status()))
      children ++= (mark +: also)
    }
  }

  private def slot(main: Component, corner: Container): Container = {
    corner.position.left := ListRow.along(ActivityView.room - ActivityView.badge, ActivityView.badge, ActivityView.room)
    corner.position.top @= ActivityView.room - ActivityView.badge
    new Container {
      size.width @= ActivityView.room
      size.height @= ActivityView.room
      children ++= List(main, corner)
    }
  }

  // the body under the summary, made the first time there is one: its words, and one line drawn the same way, out of
  // sight, which says how tall a line is
  private val hasBody: Val[Boolean] = own(Val(a.body().nonEmpty))
  private val bodyWidest: Val[Double] = own(Val(math.max(1.0, widest() - Transcript.padX * 2.0 - markWidth() - 8.0)))
  private val bodyWidth: Var[Double] = own(Var(0.0))
  private val bodyHeight: Var[Double] = own(Var(0.0))
  private val lineHeight: Var[Double] = own(Var(0.0))
  private val clip: Container = new Container
  private var words: Option[Component] = None
  private def write(): Unit = if (words.isEmpty && hasBody()) {
    val made = render(own(Val(a.body())), own(Val(bodyWidest())))
    // there to be read and copied, as a message's words are
    made.selectable @= true
    val one = render(own(Val("X")), own(Val(bodyWidest())))
    one.visible @= false
    clip.children ++= List(one, made)
    bodyWidth := made.size.width()
    bodyHeight := made.size.height()
    lineHeight := one.size.height()
    words = Some(made)
  }
  hasBody.attach(_ => write())
  write()

  private val cuts: Val[Boolean] = own(Val(hasBody() && lineHeight() > 0.0 &&
    bodyHeight() > lineHeight() * math.max(1, lines) + 0.5))
  cuts.attach(c => a.cut @= c)
  a.cut @= cuts()

  private val holder: Container = new Container
  private var shown: Option[Component] = None
  private val open: Val[Boolean] = own(Val(a.expanded() && a.opens))

  clip.size.width := bodyWidth()
  clip.size.height := (if (!hasBody()) 0.0 else if (open() || !cuts()) bodyHeight()
    else lineHeight() * math.max(1, lines))
  clip.size.width.attach(_ => clip.node.setClip(clip.size.width(), clip.size.height()))
  clip.size.height.attach(_ => clip.node.setClip(clip.size.width(), clip.size.height()))
  clip.node.setClip(clip.size.width(), clip.size.height())
  // the line and the body under its summary
  private val inner: Val[Double] = own(Val(if (!hasBody()) line.size.height()
    else math.max(line.size.height(), summary.position.top() + summary.size.height() + 2.0 + clip.size.height())))

  private val box: Container = new Container {
    size.width := (if (open()) widest() else math.min(widest(), math.max(line.size.width(),
      if (hasBody()) markWidth() + 8.0 + clip.size.width() else 0.0) + Transcript.padX * 2.0))
    size.height := inner() + 12.0 + (if (open()) holder.size.height() + 8.0 else 0.0)
    radius @= 10.0
    borderWidth @= 1.0
    borderColor := Theme.border()
    backgroundColor := Theme.surface()
    cursor := (if (a.opens) "pointer" else "")
    children := (if (hasBody()) Vector(line, clip, holder) else Vector(line, holder))
  }
  line.position.left := ListRow.along(Transcript.padX, line.size.width(), box.size.width())
  line.position.top @= 6.0
  clip.position.left := ListRow.along(Transcript.padX + markWidth() + 8.0, clip.size.width(), box.size.width())
  clip.position.top := 6.0 + summary.position.top() + summary.size.height() + 2.0
  holder.size.width := box.size.width() - Transcript.padX * 2.0
  holder.position.left @= Transcript.padX
  holder.position.top := inner() + 12.0
  holder.visible := open()
  holder.size.height := holder.children().headOption.map(_.size.height()).getOrElse(0.0)
  // what is wider than the box is cut at its edge
  holder.size.width.attach(_ => holder.node.setClip(holder.size.width(), holder.size.height()))
  holder.size.height.attach(_ => holder.node.setClip(holder.size.width(), holder.size.height()))
  box.event.click.attach { e =>
    // (a drag that selected the body's words is not a click that opens or closes it)
    if (a.opens && !(hasBody() && Platform.selectsText() && Platform.selectedText().nonEmpty)) {
      e.consume()
      a.expanded @= !a.expanded()
    }
  }

  private def place(on: Boolean): Unit = if (on && shown.isEmpty) {
    shown = details()
    shown.foreach { c =>
      c.parent().foreach {
        case p: Container => p.children -= c
        case _ => ()
      }
      holder.children += c
    }
  }
  open.attach(on => place(on))
  place(open())

  a.lead.attach(lead)
  a.lead() match {
    case ActivityLead.Person(_) | ActivityLead.Icon(_) => lead(a.lead())
    case _ => ()
  }
  onDispose(() => if (leading().nonEmpty) status.dispose())

  box.position.left := ListRow.along(Transcript.indent(width), box.size.width(), width)
  box.position.top @= Transcript.lineGap + 2.0
  size.width := width
  size.height := box.size.height() + Transcript.lineGap * 2.0 + 2.0
  children += box

  /** The details put back where the transcript keeps them, so taking this apart does not take them with it. */
  def release(): Unit = shown.foreach { c =>
    holder.children -= c
    shown = None
  }

  /** What leads the line in place of the status mark, where the entry has a lead. */
  def leadView: Option[Component] = leading()

  /** The body's words as drawn, where there is a body. */
  def bodyView: Option[Component] = words

  /** How tall the body shows: cut to `lines` lines, or whole. */
  def bodyShown: Double = clip.size.height()

  override def kind: String = "ActivityView(" + a.id + ")"
}

object ActivityView {
  /** How big a lead's avatar is, the room a lead takes (the avatar and its ring), an icon in it, and the status badge
    * on its corner. */
  val face: Double = 24.0
  val room: Double = face + BusyRing.outset(face) * 2.0
  val icon: Double = 20.0
  val badge: Double = 14.0

  /** Input and output as plain text under small headings, wrapped to the width it is given (its parent's). */
  def textDetails(input: String, output: String): Component = {
    def heading(words: String): TextView = new TextView {
      content @= words
      font.size @= 11.0
      font.weight @= FontWeight.SemiBold
      color := Theme.muted()
    }
    def body(words: String): TextView = new TextView {
      content @= words
      font.size @= 12.5
      whiteSpace @= WhiteSpace.PreWrap
      direction @= TextDirection.Auto
      wrapAnywhere @= true
      color := Theme.text()
    }
    val in = body(input)
    val out = body(output)
    val column = everyui.component.Column(4.0)(heading(ChatWords.input), in, heading(ChatWords.output), out)
    val box = new Container {
      size.width := parent().map(_.size.width()).getOrElse(320.0)
      size.height := column.size.height()
      children += column
    }
    in.size.width := box.size.width()
    out.size.width := box.size.width()
    box
  }

  /** How a status is read out. */
  def said(status: ActivityStatus): String = status match {
    case ActivityStatus.Running => ChatWords.running
    case ActivityStatus.Succeeded => ChatWords.succeeded
    case ActivityStatus.Failed => ChatWords.failed
  }

  /** The mark for a status that has settled, and its colour: a tick in the success colour, or a cross in the danger
    * colour. */
  def mark(status: ActivityStatus): MaterialIcon =
    if (status == ActivityStatus.Failed) MaterialIcon.Error else MaterialIcon.CheckCircle
  def tint(status: ActivityStatus): everyui.Color =
    if (status == ActivityStatus.Failed) Theme.danger() else Theme.success()
}

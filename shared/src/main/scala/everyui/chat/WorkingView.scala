package everyui.chat

import everyui.component.{Button, ButtonStyle, Container, IconButton, Row, TextView}
import everyui.component.types.{FontWeight, TextDirection, WhiteSpace}
import everyui.font.MaterialIcon
import everyui.list.ListRow
import everyui.theme.Theme
import reactify.*

/** Somebody at work, as a row at the end of the transcript (`ChatView.workingInline`), `width` wide, laid out as
  * everybody else's messages are: their avatar, a ring turning around it, and their name, with how long they have been
  * at it (live, from `now`); under them what they are doing, wrapped in a quiet box, cut to `lines` lines with a Show
  * more that opens it whole (and Show less again); and after the box, where the work can be stopped, a control that
  * cancels it. On a phone (`width` under `Transcript.narrow`) the avatar goes small at the start of the name's line,
  * and the box takes all the width but the side margins and the control's room. A click on the avatar or the name is
  * `pressAuthor`, under the hand where it `answers`. */
class WorkingView(slot: WorkingSlot, width: => Double, now: Val[Long], lines: => Int,
                  pressAuthor: () => Unit = () => (), answers: => Boolean = false) extends Container {
  private val top: Double = Transcript.groupGap

  private val name: TextView = new TextView {
    content := slot.working().who.name
    direction @= TextDirection.Auto
    font.size @= 12.5
    font.weight @= FontWeight.SemiBold
    color := Theme.text()
  }
  private val took: TextView = new TextView {
    content := ChatTime.span(now() - slot.working().since)
    font.size @= 11.5
    color := Theme.muted()
  }
  private val header: Container = Row(8.0, middle = true)(name, took)

  // whether the work can be stopped: the control after the box, and the room kept for it
  private val stops: Val[Boolean] = own(Val(slot.working().cancel.nonEmpty))
  /** The control that cancels the work, shown where it can be (`Working.cancel`). */
  val cancel: IconButton = new IconButton(MaterialIcon.Close, ChatWords.cancel,
    () => slot.working().cancel.foreach(stop => stop()))
  cancel.visible := stops()

  private val widest: Val[Double] = own(Val(Transcript.widest(width)))
  private val room: Val[Double] = own(Val(math.max(40.0, widest() - Transcript.padX * 2.0 -
    (if (stops()) cancel.size.width() + WorkingView.gap else 0.0))))

  // the words on one line each, out of sight: how wide they would be, so a short one makes a narrow box; and one line,
  // which says how tall a line is
  private val whole: TextView = new TextView {
    content := slot.working().what
    font.size @= WorkingView.points
    whiteSpace @= WhiteSpace.Pre
    visible @= false
  }
  private val one: TextView = new TextView {
    content @= "X"
    font.size @= WorkingView.points
    visible @= false
  }
  /** What they are doing, wrapped to the box, cut to `lines` lines until it is opened. */
  val words: TextView = new TextView {
    content := slot.working().what
    font.size @= WorkingView.points
    whiteSpace @= WhiteSpace.PreWrap
    direction @= TextDirection.Auto
    wrapAnywhere @= true
    color := Theme.text()
    maxLines := (if (slot.expanded()) 0 else math.max(1, lines))
  }
  words.size.width := math.min(room(), math.ceil(whole.size.width()) + 1.0)

  /** Show more while the words are cut short, Show less while they are open whole. */
  val more: Button = new Button(ChatWords.showMore, () => slot.expanded @= !slot.expanded(), ButtonStyle.Text,
    points = 12.0)
  more.label := (if (slot.expanded()) ChatWords.showLess else ChatWords.showMore)
  more.visible := slot.expanded() || words.elided()

  private val box: Container = new Container {
    size.width := math.max(words.size.width(), if (more.visible()) more.size.width() - WorkingView.moreInset else 0.0) +
      Transcript.padX * 2.0
    size.height := words.size.height() + Transcript.padY * 2.0 + (if (more.visible()) more.size.height() else 0.0)
    radius @= 10.0
    borderWidth @= 1.0
    borderColor := Theme.border()
    backgroundColor := Theme.surface()
    children ++= List(whole, one, words, more)
  }
  words.position.left := ListRow.along(Transcript.padX, words.size.width(), box.size.width())
  words.position.top @= Transcript.padY
  // the button's words in line with the box's (it has its own padding)
  more.position.left := ListRow.along(Transcript.padX - WorkingView.moreInset, more.size.width(), box.size.width())
  more.position.top := Transcript.padY + words.size.height()

  // whether it is drawn as on a phone: the avatar small beside the name
  private val narrow: Val[Boolean] = own(Val(width < Transcript.narrow))
  private val lineHeight: Val[Double] = own(Val(if (narrow()) math.max(header.size.height(), Transcript.smallAvatar)
    else header.size.height()))

  header.position.left := (if (!narrow()) ListRow.along(Transcript.indent(width), header.size.width(), width)
    else ListRow.along(Transcript.side + Transcript.smallAvatar + Transcript.smallGap, header.size.width(), width))
  header.position.top := top + (lineHeight() - header.size.height()) / 2.0
  box.position.left := ListRow.along(Transcript.indent(width), box.size.width(), width)
  box.position.top := top + lineHeight() + 3.0
  // after the box's end edge, level with its first line
  cancel.position.left := ListRow.along(Transcript.indent(width) + box.size.width() + WorkingView.gap,
    cancel.size.width(), width)
  cancel.position.top := box.position.top() + Transcript.padY + one.size.height() / 2.0 - cancel.size.height() / 2.0

  // the avatar: large in its own room, or small beside the name on a phone, made again as the width crosses
  private var face: Option[Avatar] = None
  private def faced(small: Boolean): Unit = {
    face.foreach { f =>
      children -= f
      f.dispose()
    }
    val d = if (small) Transcript.smallAvatar else Transcript.avatar
    val made = new Avatar(slot.working().who, d)
    made.busy @= true
    made.position.left := ListRow.along(Transcript.side, d, width)
    made.position.top := (if (small) top + (lineHeight() - d) / 2.0 else top)
    MessageView.pressable(made, pressAuthor, answers)
    children += made
    face = Some(made)
  }
  faced(narrow())
  narrow.attach(small => faced(small))
  MessageView.pressable(name, pressAuthor, answers)

  size.width := width
  size.height := math.max(box.position.top() + box.size.height(),
    if (stops()) cancel.position.top() + cancel.size.height() else 0.0) + Transcript.lineGap
  children ++= List(header, box, cancel)

  /** The avatar, as it is drawn now. */
  def avatar: Option[Avatar] = face

  override def kind: String = "WorkingView(" + slot.working().id + ")"
}

object WorkingView {
  /** The words' size, and the space between the box and the control that cancels. */
  val points: Double = 13.0
  val gap: Double = 4.0
  // how far Show more is moved out toward the box's edge, its words then about in line with the box's (a text button
  // pads its words by 1.1 times their size, 13.2 at 12 points), its lit pill just inside the box's edge
  private[chat] val moreInset: Double = 11.0
}

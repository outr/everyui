package everyui.chat

import everyui.{Color, Shadow}
import everyui.component.{Container, MaterialIconView, TextView}
import everyui.component.types.{Semantics, TextDirection, TextOverflow}
import everyui.font.MaterialIcon
import everyui.kit.Mirror
import everyui.render.Platform
import everyui.theme.Theme
import everyui.tooltip.Tooltip
import reactify.*

/** A transcript's turn header (`Transcript.turnHeader`): one quiet line over the transcript's top edge naming the turn
  * the entries at the top belong to, while the entry that starts it is scrolled out of sight above. It shows the turn's
  * words (`TurnHeader.line`) after a small upward arrow, muted and cut short to fit, then a paperclip and the name of
  * a file where the turn has one, and a small spinner at the end while the turn is still running. Its tip (the pointer
  * resting on it, or a finger held on it) shows the words whole. A click, a tap, or Enter or OK with the focus on it
  * calls `press`, which takes the transcript to the entry; a screen reader hears it as a link, "Jump to message: …".
  * It is drawn over the transcript, so nothing moves as it comes and goes. */
class TurnHeaderView(press: () => Unit) extends Container {
  /** What it shows: None hides it. */
  val header: Var[Option[TurnHeader]] = own(Var(None))

  private val arrow: MaterialIconView = new MaterialIconView(MaterialIcon.ArrowUpward, TurnHeader.glyph)
  /** The turn's words, on one line and cut short to fit. */
  val words: TextView = new TextView {
    content := header().map(_.line).getOrElse("")
    // the way its own words run, whichever way the app reads
    direction @= TextDirection.Auto
    font.size @= 13.0
    color := Theme.muted()
    textOverflow @= TextOverflow.Ellipsis
  }
  private val clip: MaterialIconView = new MaterialIconView(MaterialIcon.AttachFile, TurnHeader.glyph)
  /** The name of the file that went with the turn, cut short past `TurnHeader.fileShare` of the width. */
  val file: TextView = new TextView {
    content := header().flatMap(_.attachment).getOrElse("")
    direction @= TextDirection.Auto
    font.size @= 12.5
    color := Theme.muted()
    textOverflow @= TextOverflow.Ellipsis
  }
  /** The small spinner at the end, while the turn is busy: it turns, and stands still at half strength where less
    * motion is asked for (`everyui.motion.ReduceMotion`). */
  val spinner: MaterialIconView = new MaterialIconView(MaterialIcon.Autorenew, TurnHeader.glyph)

  /** Whether a file's name shows, and whether the spinner does. */
  val filed: Val[Boolean] = own(Val(header().exists(_.attachment.exists(_.nonEmpty))))
  val busy: Val[Boolean] = own(Val(header().exists(_.busy())))
  /** The words whole, as the tip shows them and a screen reader hears them. */
  val said: Val[String] = own(Val(header().map(_.said).getOrElse("")))

  visible := header().nonEmpty
  size.height @= TurnHeader.height
  radius @= 8.0
  backgroundColor := (if (focusShown() || event.pointer.over()) Theme.controlLit() else Theme.raised())
  borderWidth := (if (focusShown()) 2.0 else 1.0)
  borderColor := (if (focusShown()) Theme.accent() else Theme.border())
  shadow @= Some(Shadow(0.0, 2.0, 8.0, Color.Black.withAlpha(0.18)))
  focusable @= true
  cursor @= "pointer"
  tapSlop @= true
  tooltip := said()
  // (and, while the turn runs, that it does)
  semantics := Semantics("link", label = ChatWords.jumpToMessage(said()) +
    (if (busy()) ", " + ChatWords.running else ""))

  arrow.color := Theme.muted()
  clip.color := Theme.muted()
  clip.visible := filed()
  file.visible := filed()
  file.maxWidth := math.max(24.0, size.width() * TurnHeader.fileShare)
  spinner.color := Theme.muted()
  spinner.visible := busy()
  spinner.busy := busy()
  // the words take what the arrow, the file and the spinner leave
  words.maxWidth := math.max(16.0, size.width() - TurnHeader.padX * 2.0 - TurnHeader.glyph - TurnHeader.gap -
    (if (filed()) TurnHeader.gap + TurnHeader.glyph + 2.0 + file.size.width() else 0.0) -
    (if (busy()) TurnHeader.gap + TurnHeader.glyph else 0.0))
  // from the start: the arrow, the words, the paperclip and the file's name after them; the spinner at the end
  private val wordsAt: () => Double = () => TurnHeader.padX + TurnHeader.glyph + TurnHeader.gap
  private val clipAt: () => Double = () => wordsAt() + words.size.width() + TurnHeader.gap
  Mirror.place(arrow, () => size.width(), () => TurnHeader.padX)
  Mirror.place(words, () => size.width(), wordsAt)
  Mirror.place(clip, () => size.width(), clipAt)
  Mirror.place(file, () => size.width(), () => clipAt() + TurnHeader.glyph + 2.0)
  Mirror.place(spinner, () => size.width(), () => size.width() - TurnHeader.padX - TurnHeader.glyph)
  List(arrow, words, clip, file, spinner).foreach(c => c.position.middle := size.middle())
  children ++= List(arrow, words, clip, file, spinner)

  event.click.attach { e =>
    e.consume()
    Tooltip.hideFor(this)
    press()
  }
  // a finger held on it shows the words whole, for a moment, as a list's row's tip does
  private var holds = 0
  event.pointer.menu.attach { e =>
    if (e.finger) {
      e.consume()
      Tooltip.askFor(this, () => Some(said()).filter(_.nonEmpty), now = true)
      holds += 1
      val mine = holds
      Platform.after(TurnHeaderView.held,
        () => if (mine == holds && Tooltip.shownFor.exists(_ eq this)) Tooltip.hideFor(this))
    }
  }

  override def kind: String = "TurnHeaderView"
}

object TurnHeaderView {
  /** How long the tip a finger held up stays, in seconds, as Android's own does. */
  val held: Double = 2.5
}

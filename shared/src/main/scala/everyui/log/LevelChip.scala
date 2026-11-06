package everyui.log

import everyui.Color
import everyui.component.{Container, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.theme.Theme
import reactify.*

/** A level's chip in a `LogPanel`: shown (`on`, the level's colour) or hidden (faint); a click or OK turns it over. */
class LevelChip(val level: LogLevel, on: => Boolean, turn: () => Unit) extends Container {
  private val shows: Val[Boolean] = own(Val(on))
  private val words = new TextView {
    content @= LogLevel.name(level)
    font.family @= LogView.face
    font.size @= 11.0
    font.weight @= FontWeight.SemiBold
    color := (if (shows()) Theme.readable(LevelChip.colour(level)) else Theme.muted().withAlpha(0.7))
    position.left @= 8.0
  }
  words.position.top := (size.height() - words.size.height()) / 2.0

  focusable @= true
  size.width := words.size.width() + 16.0
  size.height @= 24.0
  radius @= 12.0
  borderWidth @= 1.0
  borderColor := (if (shows()) LevelChip.colour(level).withAlpha(0.6) else Theme.border())
  backgroundColor := (if (shows()) LevelChip.colour(level).withAlpha(if (focusShown()) 0.3 else 0.14)
    else if (focusShown() || event.pointer.over()) Theme.controlLit() else Color.Clear)
  semantics := Semantics.switch(LogLevel.name(level), shows())
  tapSlop @= true
  children += words
  event.click.attach { e =>
    e.consume()
    turn()
  }

  override def kind: String = "LevelChip(" + LogLevel.name(level) + ")"
}

object LevelChip {
  /** A level's colour: errors the theme's danger, warnings its warning, information its info, debugging its
    * success, tracing muted. */
  def colour(level: LogLevel): Color = level match {
    case LogLevel.Error => Theme.danger()
    case LogLevel.Warn => Theme.warning()
    case LogLevel.Info => Theme.info()
    case LogLevel.Debug => Theme.success()
    case LogLevel.Trace => Theme.muted()
  }
}

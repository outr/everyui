package everyui.call

import everyui.component.ProgressBar
import everyui.component.types.{SemanticRange, Semantics}
import reactify.*

/** How loud a microphone hears the room, as a bar filled from its start: `level` (0 to 1, read reactively) from a
  * [[CallPreview]] (`MicLevel(preview)`) or a `MicMeter`, for a "check your microphone" step. Its colours are a
  * `ProgressBar`'s (`fill`, `track`). A screen reader hears it as a meter of the microphone's level, in percent.
  *
  * {{{
  *   val meter = MicLevel(preview)
  *   val checking = new MicLevel(MediaDevices.meter().level(), 160.0)
  * }}} */
class MicLevel(level: => Double, width: Double = 120.0, height: Double = 6.0) extends ProgressBar(width, height) {
  fraction := level
  semantics := {
    val percent = math.round(math.max(0.0, math.min(1.0, fraction())) * 100.0).toDouble
    Semantics("meter", label = CallWords.micLevel,
      range = Some(SemanticRange(percent, 0.0, 100.0, percent.toInt.toString + "%")))
  }

  override def kind: String = "MicLevel"
}

object MicLevel {
  /** The level of `preview`'s microphone. */
  def apply(preview: CallPreview, width: Double = 120.0, height: Double = 6.0): MicLevel =
    new MicLevel(preview.level(), width, height)
}

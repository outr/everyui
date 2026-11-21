package everyui.app

import everyui.component.AudioPlayer
import everyui.render.Platform
import everyui.ui

/** One AudioPlayer whose source is changed again before it has opened, as NaboTV's lobby music was 0.9 s in: through
  * the addresses in `src=` (comma separated) in turn, every `every=` seconds (0.3), `flips=` times (20), then let go
  * (`source` ""), and looked at for `rest=` seconds more (10). Logs `audio flip <n> <file>` for each change, `audio
  * flip event <file> <event>` for each event, and `audio flip done errors=<n>` at the end: no error belongs to a source
  * that played. On Android, `mediaReleaseMs=` and `mediaReleaseStall=` (ExoMedia) make each release time out, and the
  * log says each player let go kept silent and released later. */
object AudioFlipDemo {
  def run(): Unit = {
    val sources = Platform.launchArg("src").split(',').toList.map(_.trim).filter(_.nonEmpty)
    val every = Platform.launchArg("every").toDoubleOption.getOrElse(0.3)
    val flips = Platform.launchArg("flips").toIntOption.getOrElse(20)
    val rest = Platform.launchArg("rest").toDoubleOption.getOrElse(10.0)
    val player = new AudioPlayer
    var errors = 0
    player.events { e =>
      if (e == "error") errors += 1
      Platform.log("audio flip event " + player.source().split('/').last + " " + e)
    }
    ui.children += player
    def flip(n: Int): Unit =
      if (n >= flips || sources.isEmpty) {
        player.source @= ""
        Platform.log("audio flip let go")
        Platform.after(rest, () => Platform.log("audio flip done errors=" + errors.toString))
      }
      else {
        val next = sources(n % sources.size)
        Platform.log("audio flip " + n.toString + " " + next.split('/').last)
        player.source @= next
        Platform.after(every, () => flip(n + 1))
      }
    flip(0)
  }
}

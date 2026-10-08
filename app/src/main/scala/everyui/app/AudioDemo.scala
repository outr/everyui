package everyui.app

import everyui.audio.PlayerStack
import everyui.component.{AudioPlayer, Container, TextView}
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.ui

/** Sound that takes turns, as NaboTV's trivia makes it: a looping track; a clip that takes the player from it (on a
  * Roku, which plays one Audio node at a time, the track stops where it was); a cheer and a groan over both as effects,
  * which take nothing; the clip stopped, and the track back where it was; the clip again, and the track stopped while
  * it waits, so the clip's end leaves nothing to come back. The players are laid out in a column, as a screen lays out
  * a clip in its panel, and nothing of the layout is written to a Roku's Audio node. Each step is logged with who has
  * the player and who waits ("[ui] audio demo: …"), and on a Roku each hand-off ("[ui] audio: … takes the player from
  * …", "… back from …"). With `unfit=1`, an effect from an address, which a Roku cannot mix, is reported ("[error]
  * audio: …") and played through the player. */
object AudioDemo {
  private val words = new TextView
  // the players laid out under the words, as a screen lays out a clip in its panel
  private val column = new Container

  def run(): Unit = {
    column.layout @= Some(VerticalLayout(12.0))
    column.position.left @= 40.0
    column.position.top @= 40.0
    column.children += words
    ui.children += column
    val music = player(false)
    val clip = player(false)
    val cheer = player(true)
    val groan = player(true)
    music.loop @= true
    music.source @= sound("demo-music.wav")
    step("music playing")
    Platform.after(1.0, () => {
      clip.source @= sound("demo-clip.wav")
      step("clip playing")
    })
    Platform.after(1.5, () => {
      cheer.source @= sound("demo-cheer.wav")
      step("cheer over both")
    })
    Platform.after(2.0, () => {
      groan.source @= sound("demo-groan.wav")
      step("groan over both")
      if (Platform.launchArg("unfit") == "1") {
        val far = player(true)
        far.source @= "https://everyui.com/sounds/cheer.mp3"
        step("an effect from an address")
      }
    })
    Platform.after(3.0, () => {
      clip.playing @= false
      step("clip stopped")
    })
    Platform.after(4.0, () => {
      clip.playing @= true
      step("clip again")
    })
    Platform.after(4.5, () => {
      music.playing @= false
      step("music stopped while it waits")
    })
    Platform.after(5.0, () => {
      clip.playing @= false
      step("clip stopped, nothing to come back")
    })
  }

  private def player(effect: Boolean): AudioPlayer = {
    val p = new AudioPlayer
    p.effect @= effect
    p.events(e => step(p.source().split('/').last + " " + e))
    column.children += p
    p
  }

  private def sound(file: String): String = Platform.resolvePath("/sounds/" + file)

  private def step(what: String): Unit = {
    val line = what + " | player: " + PlayerStack.device.describe()
    words.content @= line
    Platform.log("audio demo: " + line)
  }
}

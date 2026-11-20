package everyui.app

import everyui.Color
import everyui.component.{Component, Container, MaterialIconView, TextView}
import everyui.component.types.Semantics
import everyui.font.MaterialIcon
import everyui.motion.{Keyframe, Track}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui

/** Motions taken away mid-cycle, each node left as it has itself rather than where its motion stood: a busy Sync glyph
  * turned into a done tick (it should stand upright), a box turning from its own tilt of 18°, one growing from its own
  * half size, one fading from its own 0.8, one sliding from where it is placed, and one whose turn is replaced by a
  * fade (it should stand at no tilt, fading). `stop=<seconds>` takes them away (2 by default, in the second of
  * their 1.3 s cycles), logged "motionstop taken away"; `again=<seconds>` sets them going again that long
  * after, and so on, round and round. Each box is named for a page reader (`motion-turn`, `motion-scale`,
  * `motion-fade`, `motion-slide`, `motion-swap`, `motion-tick`), for OldChromeCheck to find; their turns a moment after
  * are logged ("motionstop turns: tick 0.0, turn 0.314.., swap 0.0"), on a Roku read from the SceneGraph node. */
object MotionStopDemo {
  private val turn = Track(Track.Rotation, 1.3, List(Keyframe(0.0, 0.0), Keyframe(1.0, 2.0 * math.Pi)))
  private val grow = Track(Track.Scale, 1.3, List(Keyframe(0.0, 1.0, 1.0), Keyframe(1.0, 1.6, 1.6)))
  private val fade = Track(Track.Opacity, 1.3, List(Keyframe(0.0, 1.0), Keyframe(1.0, 0.1)))
  private val slide = Track(Track.Position, 1.3, List(Keyframe(0.0, 0.0, 0.0), Keyframe(1.0, 120.0, 60.0)))

  private val tick = new MaterialIconView(MaterialIcon.Sync, 96.0)
  private val turning = box("motion-turn", 0)
  private val growing = box("motion-scale", 1)
  private val fading = box("motion-fade", 2)
  private val sliding = box("motion-slide", 3)
  private val swapped = box("motion-swap", 4)
  private val said = new TextView

  def run(): Unit = {
    said.position.left @= 40.0
    said.position.top @= 40.0
    said.color := Theme.text()
    tick.color := Theme.text()
    tick.position.left @= 50.0
    tick.position.top @= 150.0
    tick.semantics @= Semantics.image("motion-tick")
    turning.rotation @= 0.05
    growing.scale @= 0.5
    fading.opacity @= 0.8
    ui.children ++= List(said, tick, turning, growing, fading, sliding, swapped)
    start()
  }

  // a box 120 across with a bar along its top, to see which way up it is, the `at`th after the tick in rows of three,
  // its name under it
  private def box(name: String, at: Int): Container = {
    val c = new Container {
      position.left @= 40.0 + ((at + 1) % 3) * 220.0
      position.top @= 140.0 + ((at + 1) / 3) * 280.0
      size.width @= 120.0
      size.height @= 120.0
      backgroundColor @= Color.fromHex("#3f51b5")
      semantics @= Semantics.image(name)
      children += new Container {
        size.width @= 120.0
        size.height @= 20.0
        backgroundColor @= Color.fromHex("#ffc107")
      }
    }
    val label = new TextView
    label.content @= name.stripPrefix("motion-")
    label.color := Theme.text()
    label.position.left @= 40.0 + ((at + 1) % 3) * 220.0
    label.position.top @= 330.0 + ((at + 1) / 3) * 280.0
    ui.children += label
    c
  }

  private def start(): Unit = {
    tick.icon @= MaterialIcon.Sync
    tick.busy @= true
    turning.motion @= List(turn)
    growing.motion @= List(grow)
    fading.motion @= List(fade)
    sliding.motion @= List(slide)
    swapped.motion @= List(turn)
    said.content @= "Moving"
    Platform.after(Platform.launchArg("stop").toDoubleOption.getOrElse(2.0), () => stop())
  }

  private def stop(): Unit = {
    tick.icon @= MaterialIcon.TaskAlt
    tick.busy @= false
    List[Component](turning, growing, fading, sliding).foreach(_.motion @= Nil)
    swapped.motion @= List(fade)
    said.content @= "Taken away: each as it has itself"
    Platform.log("motionstop taken away")
    // the turns as the platform has them a moment on (on a Roku, the SceneGraph field itself)
    Platform.after(0.3, () => Platform.log("motionstop turns: tick " + tick.node.getRotation().toString + ", turn " +
      turning.node.getRotation().toString + ", swap " + swapped.node.getRotation().toString))
    Platform.launchArg("again").toDoubleOption.foreach(s => Platform.after(s, () => start()))
  }
}

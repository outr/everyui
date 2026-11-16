package everyui.memory

import everyui.component.Component
import everyui.render.Platform
import everyui.ui

import scala.scalajs.js as sjs

/** The page the web's memory check measures (`MemoryCheck`, in webcheck): it puts a case on the screen as the check
  * asks, through `window.everyuiMemory`:
  *
  *  - `cases()`: the cases, a line each of name, unit and step, apart by tabs;
  *  - `start(name)`: the case's rig on the screen;
  *  - `add(n)`: `n` more of its units;
  *  - `tick()`: its clock moved on a second, where it shows one, and whether it does;
  *  - `live()`: `Component.liveNow`;
  *  - `dispose()`: the rig off the screen and taken apart. */
object MemoryPage {
  private var rig: Option[MemoryRig] = None

  // the cases, a line each
  private def listed: String = MemoryCases.all.map(c => c.name + "\t" + c.unit + "\t" + c.step.toString).mkString("\n")

  def main(args: Array[String]): Unit = {
    val api = sjs.Dynamic.literal(
      cases = (() => listed): sjs.Function0[String],
      start = ((name: String) => MemoryCases.all.find(_.name == name).foreach { c =>
        val made = c.rig()
        ui.children += made.root
        rig = Some(made)
      }): sjs.Function1[String, Unit],
      add = ((n: Int) => rig.foreach(_.add(n))): sjs.Function1[Int, Unit],
      tick = (() => rig.exists(_.tick())): sjs.Function0[Boolean],
      live = (() => Component.liveNow): sjs.Function0[Int],
      dispose = (() => rig.foreach { r =>
        ui.children -= r.root
        r.root.dispose()
        rig = None
      }): sjs.Function0[Unit]
    )
    sjs.Dynamic.global.window.updateDynamic("everyuiMemory")(api)
    Platform.log("memory page ready")
  }
}

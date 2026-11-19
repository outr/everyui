package everyui.memory

import everyui.component.{Component, MediaSupport}
import everyui.render.WebMedia
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
  *  - `media()`: what holds a player: the page's elements with a source, hls.js's players (`WebMedia.held`), and
  *    `MediaSupport.held`;
  *  - `dispose()`: the rig off the screen and taken apart. */
object MemoryPage {
  private var rig: Option[MemoryRig] = None

  // a second of quiet as a WAV, a `blob:` of the page's own, for the VideoView case to play
  private def wav(): String = {
    val rate = 8000
    val bytes = new sjs.typedarray.DataView(new sjs.typedarray.ArrayBuffer(44 + rate * 2))
    def text(at: Int, s: String): Unit = s.indices.foreach(i => bytes.setUint8(at + i, s.charAt(i).toShort))
    text(0, "RIFF")
    bytes.setUint32(4, 36 + rate * 2, true)
    text(8, "WAVEfmt ")
    bytes.setUint32(16, 16, true)
    bytes.setUint16(20, 1, true)
    bytes.setUint16(22, 1, true)
    bytes.setUint32(24, rate, true)
    bytes.setUint32(28, rate * 2, true)
    bytes.setUint16(32, 2, true)
    bytes.setUint16(34, 16, true)
    text(36, "data")
    bytes.setUint32(40, rate * 2, true)
    val blob = sjs.Dynamic.newInstance(sjs.Dynamic.global.Blob)(sjs.Array(bytes.buffer),
      sjs.Dynamic.literal(`type` = "audio/wav"))
    sjs.Dynamic.global.URL.createObjectURL(blob).asInstanceOf[String]
  }

  // the cases, a line each
  private def listed: String = MemoryCases.all.map(c => c.name + "\t" + c.unit + "\t" + c.step.toString).mkString("\n")

  def main(args: Array[String]): Unit = {
    MemoryCases.clip = wav()
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
      media = (() => sjs.Array(WebMedia.held._1, WebMedia.held._2, MediaSupport.held)): sjs.Function0[sjs.Array[Int]],
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

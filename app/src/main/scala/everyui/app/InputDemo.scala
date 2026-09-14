package everyui.app

import everyui.Color
import everyui.component.{Container, TextView}
import everyui.focus.Focus
import everyui.render.Platform
import everyui.ui
import reactify.*

/** The input model: three focusable boxes. An arrow moves the focus (the focused box is blue), Enter or a click
  * reports a click, the pointer's entering and leaving are logged. With `probe=1` the demo drives the shared focus
  * layer itself and prints one line to compare across platforms:
  *
  *   [ui] input ok focus=a focus=b click=b focus=a
  */
object InputDemo:
  private var log: List[String] = Nil

  def run(): Unit =
    val boxes = List("a", "b", "c").zipWithIndex.map { case (name, i) => box(name, 100.0 + i * 220.0, 100.0) }
    ui.children ++= boxes
    boxes.head.focus()
    if Platform.launchArg("probe") == "1" then
      List("ArrowRight", "Enter", "ArrowLeft", "ArrowDown").foreach { key =>
        Focus.key(key, true)
        Focus.key(key, false)
      }
      Platform.log("input ok " + log.reverse.mkString(" "))

  private def note(entry: String): Unit =
    log = entry :: log
    Platform.log(entry)

  private def box(name: String, left: Double, top: Double): Container = new Container:
    position.left @= left
    position.top @= top
    size.width @= 180.0
    size.height @= 120.0
    radius @= 12.0
    focusable @= true
    backgroundColor := (if focused() then Color.fromHex("1565c0") else Color.fromHex("616161"))
    children += new TextView:
      content @= name
      font.size @= 40.0
      color @= Color.White
      position.left @= 20.0
      position.top @= 20.0
    focused.attach(f => if f then note("focus=" + name))
    event.click.attach(_ => note("click=" + name))
    event.pointer.enter.attach(_ => Platform.log("enter " + name))
    event.pointer.leave.attach(_ => Platform.log("leave " + name))

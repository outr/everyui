package everyui.app

import everyui.component.TextView
import everyui.keymap.{Chord, Keymap}
import everyui.render.Platform
import everyui.ui
import reactify.*

/** Asking before the app is left (`Platform.leaveGuard`): `guard=on` holds a guard that always answers true, `guard=off`
  * one that always answers false, and anything else none; a flag's guard (`leaveGuardWhile`) is held throughout, set by
  * F and cleared by C. G adds a guard answering true, R removes every guard but the flag's, and P moves within the app
  * (`Platform.pushPath`). Each change is logged ("[ui] leave armed true") and written on the screen. */
object LeaveDemo {
  private val words = new TextView
  private val flag: Var[Boolean] = Var(false)
  private var removers: List[() => Unit] = Nil
  private var pushed = 0

  def run(): Unit = {
    words.position.left @= 40.0
    words.position.top @= 40.0
    ui.children += words
    Platform.launchArg("guard") match {
      case "on" => removers = List(Platform.leaveGuard(() => true))
      case "off" => removers = List(Platform.leaveGuard(() => false))
      case _ => ()
    }
    Platform.leaveGuardWhile(flag)
    Keymap.global.add("leave.flag", "Set the flag", Chord("f")) {
      flag @= true
      said("flag set")
    }
    Keymap.global.add("leave.clear", "Clear the flag", Chord("c")) {
      flag @= false
      said("flag cleared")
    }
    Keymap.global.add("leave.guard", "Add a guard", Chord("g")) {
      removers = Platform.leaveGuard(() => true) :: removers
      said("guard added")
    }
    Keymap.global.add("leave.remove", "Remove the guards", Chord("r")) {
      removers.foreach(r => r())
      removers = Nil
      said("guards removed")
    }
    Keymap.global.add("leave.push", "Move within the app", Chord("p")) {
      pushed += 1
      Platform.pushPath("/leave/" + pushed.toString)
      said("pushed " + Platform.path())
    }
    said("started")
  }

  private def said(what: String): Unit = {
    val armed = LeaveGuards.armed()
    words.content @= what + ": guarded " + LeaveGuards.guarded().toString + ", armed " + armed.toString
    Platform.log("leave " + what)
    Platform.log("leave armed " + armed.toString)
  }
}

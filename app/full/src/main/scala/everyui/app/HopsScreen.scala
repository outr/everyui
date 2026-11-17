package everyui.app

import everyui.Color
import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Component, Container, TextView}
import everyui.layout.VerticalLayout
import everyui.net.*
import everyui.network.onScreen
import everyui.render.Platform
import reactify.*

import scala.concurrent.duration.*

/** The hops demo's one screen: a line saying the step and the thread it was made on, and a column of at most twelve
  * chips, the newest added at the end, the oldest taken away, the column turned over every tenth step. */
class HopsScreen extends ScrollScreen {
  override def manager: ScreenManager = HopsDemo
  override def title: String = "Hops"
  override def path: URLPath = path"/hops"

  private val steps = Option(Platform.launchArg("n")).filter(_.nonEmpty).map(_.toInt).getOrElse(200)
  private val drawing = Platform.launchArg("onscreen") == "1"

  private val said = new TextView {
    content @= "waiting"
    color @= Color.White
    focusable @= true
  }
  private val column = new Container {
    size.width @= 240.0
    layout @= Some(VerticalLayout(6.0))
  }

  // each chip with its number, for the log
  private var numbered = Map.empty[Container, Int]

  private def chip(n: Int): Container = {
    val made = new Container {
      size.width @= 240.0
      size.height @= 40.0
      backgroundColor @= (if (n % 2 == 0) Color.Blue else Color.Gray)
      children += new TextView {
        content @= "chip " + n
        color @= Color.White
        position.left @= 12.0
        position.top @= 4.0
      }
    }
    numbered += made -> n
    made
  }

  override def defaultFocus(): Component = said

  private def change(n: Int): Unit = {
    said.content @= "step " + n + " on " + Thread.currentThread.getName
    column.children += chip(n)
    if (column.children.length > 12) column.children -= column.children().head
    if (n % 10 == 9) column.children @= column.children().reverse
    if (n % 20 == 0 || n == steps)
      Platform.log("hops " + n + " on " + Thread.currentThread.getName + ": " +
        column.children().map(c => numbered.collectFirst { case (k, v) if k eq c => v }.getOrElse(-1)).mkString(" "))
  }

  private def step(n: Int): rapid.Task[Unit] = rapid.Task.sleep(150.millis).flatMap { _ =>
    val made = if (drawing) rapid.Task.unit.onScreen(_ => change(n)) else rapid.Task(change(n))
    made.flatMap(_ => if (n < steps) step(n + 1) else rapid.Task.unit)
  }

  override def createUI(): Task[Unit] = Task {
    body.children += said
    body.children += column
    // a step that throws is said, rather than ending the loop unheard
    step(1).handleError(t => rapid.Task(Platform.logError("hops: a step failed", t))).startUnit()
  }
}

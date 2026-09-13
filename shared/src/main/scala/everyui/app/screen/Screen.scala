package everyui.app.screen

import everyui.async.Task
import everyui.task.TaskSupport

/** youi's `Screen`, reduced to its lifecycle (init, load, activate) and its task support. */
trait Screen extends TaskSupport:
  def title: String = ""

  protected def init(): Task[Unit] = Task.unit
  protected def load(): Task[Unit] = Task.unit
  protected def activate(): Task[Unit] = Task.unit
  protected def deactivate(): Task[Unit] = Task.unit

object Screen:
  def init(screen: Screen): Task[Unit] = screen.init()
  def load(screen: Screen): Task[Unit] = screen.load()
  def activate(screen: Screen): Task[Unit] = screen.activate()
  def deactivate(screen: Screen): Task[Unit] = screen.deactivate()

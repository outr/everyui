package everyui.render

import everyui.Permission
import everyui.push.PushAnswer

/** Push notifications (`everyui.push.Push`). A desktop app has no push service to be woken by, and what it hears
  * while it runs comes over its own connection to its server; so there is no push here: `supported` is false, the
  * permission is refused, and a subscription answers at once as unsupported. */
object PushAccess {
  def supported(): Boolean = false

  def listen(): Unit = ()

  def permission(done: Permission => Unit): Unit = done(Permission.Refused)

  def permit(done: Boolean => Unit): Unit = done(false)

  def subscribe(key: String, done: PushAnswer => Unit): Unit = done(PushAnswer.PushUnsupported)

  def unsubscribe(done: () => Unit): Unit = done()
}

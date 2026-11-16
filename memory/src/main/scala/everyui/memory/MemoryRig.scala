package everyui.memory

import everyui.component.Component

/** One memory case on the screen (`MemoryCase`): `root` is put on it, and `add` puts `n` more of the case's units in it
  * (components, a transcript's messages, a tab group's tabs). `tick` moves its clock on a second, where it shows one
  * (how long a call has run), and says whether it does: what a second costs the page, which should be words changed in
  * place, nothing made. */
trait MemoryRig {
  def root: Component
  def add(n: Int): Unit
  def tick(): Boolean = false
}

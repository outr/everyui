package everyui.memory

import everyui.component.Component

/** One memory case on the screen (`MemoryCase`): `root` is put on it, and `add` puts `n` more of the case's units in it
  * (components, a transcript's messages, a tab group's tabs). */
trait MemoryRig {
  def root: Component
  def add(n: Int): Unit
}

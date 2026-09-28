package everyui.tree

/** Nodes ticked or unticked by the person (a click on a box, Space, OK): the keys they asked for, before what is under
  * and above them follows. */
case class CheckToggle(keys: Set[String], checked: Boolean)

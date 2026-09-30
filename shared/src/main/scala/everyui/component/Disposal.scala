package everyui.component

/** A `Component.disposeGradually` under way, for its console lines: what it is taking apart, how many components, when
  * it started (`Platform.ticks`) and how many turns it has taken. */
private[component] case class Disposal(what: String, count: Int, started: Double, turns: Int)

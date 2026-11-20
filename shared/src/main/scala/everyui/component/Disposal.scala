package everyui.component

/** A `Component.disposeGradually` under way, for its console lines: what it is taking apart, how many components, when
  * it started (`Platform.ticks`), how many turns it has taken and how long its slices took, in milliseconds (on a slow
  * device, the turns between them, a screen opening meanwhile, are most of the time from its start to its end). */
private[component] case class Disposal(what: String, count: Int, started: Double, turns: Int, busy: Double)

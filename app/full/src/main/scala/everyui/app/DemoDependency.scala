package everyui.app

/** One module depending on another, in the graph demo: planned, or breaking the project's rules. */
case class DemoDependency(from: String, to: String, planned: Boolean = false, violation: Boolean = false)

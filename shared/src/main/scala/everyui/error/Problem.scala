package everyui.error

/** Something that went wrong in the app: `where` it happened (an event on a component, a frame, "uncaught" for what the
  * platform caught), what it said, and its stack as the platform prints one ("" where it has none). */
case class Problem(where: String, message: String, stack: String)

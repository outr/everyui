package everyui.render

/** Where a change taken to the drawing thread (`Hop`) was made, said once for each place: the node's method, and the
  * first frame of the app's own below it, the first outside EveryUI, reactify, rapid, Scala and the platform. Where
  * there is none (EveryUI's own example app is in `everyui`), the frames below the method are said instead.
  * In a shrunk build the names are the shrinker's; retrace them with the build's mapping. */
object HopSite {
  private val library = List("everyui.", "reactify.", "rapid.", "scala.", "sourcecode.", "java.", "javax.", "jdk.",
    "sun.", "com.sun.", "android.", "androidx.", "dalvik.", "com.android.", "kotlin.")
  private val seen = java.util.Collections.synchronizedSet(new java.util.HashSet[String])
  // a bound on the places remembered, past which nothing more is said
  private val most = 500

  private def thread: String = {
    val name = Thread.currentThread.getName
    if (name.isEmpty) "a virtual thread" else name
  }

  /** Said for the change being made now, unless its place has been. `to` names the drawing thread. */
  def said(to: String): Unit = {
    val frames = new Throwable().getStackTrace
    val node = frames.indexWhere(f => f.getClassName == "everyui.render.RenderNode" || f.getClassName == "everyui.render.Platform$")
    val method = if (node < 0) "a change" else frames(node).getClassName.stripPrefix("everyui.render.").stripSuffix("$") +
      "." + frames(node).getMethodName
    val below = frames.drop(math.max(0, node + 1)).filterNot(_.getClassName.startsWith("everyui.render.Hop"))
    val place = below.find(f => !library.exists(f.getClassName.startsWith)) match {
      case Some(app) => "from " + app.toString
      case None => "from " + below.filter(_.getClassName.startsWith("everyui.")).take(8).map(_.toString).mkString(" < ")
    }
    if (seen.size < most && seen.add(place))
      Platform.log("hop: " + method + " made on " + thread + ", off " + to + ", taken there; " +
        place)
  }
}

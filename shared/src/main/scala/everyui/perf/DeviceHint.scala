package everyui.perf

/** What the device says of itself at launch, for reading a slow session against (`Perf.device`): its model, its
  * graphics (a Roku's "opengl" or "directfb", Android's OpenGL ES version), its memory in MB and how much of it an app
  * may have, its cores, and whether it calls itself low on memory. None where the platform does not say. */
case class DeviceHint(model: Option[String] = None,
                      graphicsPlatform: Option[String] = None,
                      ramMb: Option[Int] = None,
                      appMemoryMb: Option[Int] = None,
                      cores: Option[Int] = None,
                      lowRam: Option[Boolean] = None) {
  /** In a few words, for a log line: `4802X opengl 2048MB app 512MB 4 cores`. */
  def line: String = {
    val parts: List[Option[String]] = List(
      model,
      graphicsPlatform,
      ramMb.map(m => m.toString + "MB"),
      appMemoryMb.map(m => "app " + m.toString + "MB"),
      cores.map(c => c.toString + " cores"),
      if (lowRam.getOrElse(false)) Some("low-ram") else None)
    parts.filter(_.nonEmpty).map(_.get).mkString(" ")
  }
}

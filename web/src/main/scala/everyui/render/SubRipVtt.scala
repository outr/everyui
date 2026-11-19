package everyui.render

/** A subtitle file as WebVTT, which is all a browser's `<track>` reads, plain Scala (so the desktop's specs read it as
  * the page does): WebVTT as it is, SubRip (`.srt`) turned into it (a header, the cues' numbers dropped, the comma
  * before the milliseconds a full stop, a byte-order mark and Windows' line ends taken out). None for anything else
  * (SubStation, a picture format), which a browser cannot show. */
object SubRipVtt {
  def apply(text: String): Option[String] = {
    val clean = text.stripPrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
    if (clean.trim.startsWith("WEBVTT")) Some(clean)
    else if (!clean.contains("-->") || clean.contains("[Script Info]")) None
    else {
      val out = new StringBuilder("WEBVTT\n\n")
      val lines = clean.split("\n", -1)
      lines.indices.foreach { i =>
        val line = lines(i)
        // a cue's number is the line before its timing; a cue that says a number is kept
        val number = line.trim.nonEmpty && line.trim.forall(_.isDigit) && i + 1 < lines.length &&
          lines(i + 1).contains("-->")
        if (line.contains("-->")) out.append(line.replace(',', '.')).append('\n')
        else if (!number) out.append(line).append('\n')
      }
      Some(out.toString)
    }
  }
}

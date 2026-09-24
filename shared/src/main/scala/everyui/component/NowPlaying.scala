package everyui.component

/** What is playing, as the platform's own now-playing shows it — a television's playing card, an assistant asked what
  * this is, a phone's lock screen: its name, who made it, what it is from, a picture of it, and how many seconds it
  * runs (0 where that is not known). An empty `title` is nothing to show. */
case class NowPlaying(title: String = "", artist: String = "", album: String = "", art: String = "",
                      seconds: Double = 0.0)

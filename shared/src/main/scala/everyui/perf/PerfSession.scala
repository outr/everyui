package everyui.perf

/** How this run of the app has gone so far (`Perf.summaries`, `Perf.session`), for the app to send where a device's
  * level is decided: plain fields only, so fabric derives it as it is (`given RW[PerfSession] = RW.gen`), on the Roku
  * too. The same `sessionId` from launch to exit, each summary an update of the one before; `at` when it was made (ms
  * since 1970). Whatever was not measured is None, never 0.
  *
  *  - `keyLatencyMsP50`, `keyLatencyMsP95`: from a key to the frame drawn after it was handled (`keys` of them);
  *  - `frameDropPct`: the share of `frames` that came `QualityPolicy.dropMs` or more after the one before;
  *  - `updateMsP95`, `frameMsP95`: the app's own update in a frame, and the time from frame to frame;
  *  - `memoryLevel`: "normal", "low" or "critical", where the platform says (a Roku's general memory level, Android's
  *    low-memory state), and `memoryUsedPct` of what the app may have;
  *  - the device as it said at launch (`DeviceHint`): `platform`, `model`, `graphicsPlatform`, `ramMb`;
  *  - `levelGiven`, the level `Quality.start` was given, and `levelRun`, the one it runs at now, lower where the
  *    session `steppedDown` for itself;
  *  - `evidence`: in a line, for people, what the session saw, and why it stepped down where it did. */
case class PerfSession(sessionId: String,
                       at: Long,
                       sessionSeconds: Option[Int] = None,
                       keyLatencyMsP50: Option[Int] = None,
                       keyLatencyMsP95: Option[Int] = None,
                       frameDropPct: Option[Int] = None,
                       memoryLevel: Option[String] = None,
                       evidence: Option[String] = None,
                       keys: Option[Int] = None,
                       frames: Option[Int] = None,
                       updateMsP95: Option[Int] = None,
                       frameMsP95: Option[Int] = None,
                       memoryUsedPct: Option[Int] = None,
                       platform: Option[String] = None,
                       model: Option[String] = None,
                       graphicsPlatform: Option[String] = None,
                       ramMb: Option[Int] = None,
                       levelGiven: Option[String] = None,
                       levelRun: Option[String] = None,
                       steppedDown: Option[Boolean] = None)

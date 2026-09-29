package everyui.app

/** The demos of what only the web, the desktop and Android run (lists, pickers, voice), which the Roku and tvOS are not
  * compiled with (`app/native` has their `FullDemos`, which runs none). */
object FullDemos {
  /** Run the demo named `demo`, if it is one of these. Whether it was. */
  def run(demo: String): Boolean =
    if (demo == "picker") {
      PickerDemo.run()
      true
    }
    else if (demo == "voice") {
      VoiceDemo.run()
      true
    }
    else false
}

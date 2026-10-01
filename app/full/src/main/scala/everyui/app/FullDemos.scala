package everyui.app

/** The demos of what only the web, the desktop and Android run (lists, pickers, voice, the platform's services), which the Roku and tvOS are not
  * compiled with (`app/native` has their `FullDemos`, which runs none). */
object FullDemos {
  /** Run the demo named `demo`, if it is one of these. Whether it was. */
  def run(demo: String): Boolean =
    if (demo == "treebadge") {
      TreeBadgeDemo.run()
      true
    }
    else if (demo == "picker") {
      PickerDemo.run()
      true
    }
    else if (demo == "voice") {
      VoiceDemo.run()
      true
    }
    else if (demo == "pagedtable") {
      PagedTableDemo.run()
      true
    }
    else if (demo == "zoom") {
      ZoomDemo.run()
      true
    }
    else if (demo == "map") {
      MapDemo.run()
      true
    }
    else if (demo == "graph") {
      GraphDemo.run()
      true
    }
    else if (demo == "forcegraph") {
      ForceGraphDemo.run()
      true
    }
    else if (demo == "platform") {
      PlatformDemo.run()
      true
    }
    else if (demo == "video") {
      VideoDemo.run()
      true
    }
    else if (demo == "transfer") {
      TransferDemo.run()
      true
    }
    else if (demo == "capture") {
      CaptureDemo.run()
      true
    }
    else false
}

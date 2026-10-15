package everyui.render

import everyui.push.Badge

/** A count on a Mac's Dock icon (`Platform.setBadge`), through `java.awt.Taskbar`, and nothing on another system or
  * without a screen (a headless run): Windows and Linux taskbars have no text badge to set. */
object DockBadge {
  private lazy val taskbar: Option[java.awt.Taskbar] =
    try {
      val mac = System.getProperty("os.name", "").toLowerCase.contains("mac")
      if (mac && !java.awt.GraphicsEnvironment.isHeadless && java.awt.Taskbar.isTaskbarSupported) {
        Some(java.awt.Taskbar.getTaskbar).filter(_.isSupported(java.awt.Taskbar.Feature.ICON_BADGE_TEXT))
      } else None
    } catch { case _: Exception | _: LinkageError => None }

  def set(count: Int): Unit = taskbar.foreach { t =>
    val text = Badge.text(count)
    try t.setIconBadge(if (text.isEmpty) null else text) catch { case _: Exception => () }
  }
}

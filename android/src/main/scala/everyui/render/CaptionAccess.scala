package everyui.render

import android.content.Context
import android.os.{Handler, Looper}
import android.view.accessibility.CaptioningManager
import everyui.text.{CaptionMode, CaptionPreference}

/** The device's caption setting (`everyui.text.Captions`) from its CaptioningManager (Settings, Accessibility,
  * Captions): on or off, and the language chosen there, where one is (none is "the app's own"). A change is heard
  * through its listener, on the thread that draws. */
object CaptionAccess {
  private def manager: Option[CaptioningManager] =
    try Option(Host.activity.getSystemService(Context.CAPTIONING_SERVICE).asInstanceOf[CaptioningManager])
    catch { case _: Throwable => None }

  def supported(): Boolean = manager.nonEmpty

  // the viewer's to change, in the device's own settings
  def writable(): Boolean = false

  def setDevice(mode: CaptionMode): Boolean = false

  def device(): Option[CaptionPreference] = manager.map { m =>
    val language = Option(m.getLocale).map(_.getLanguage).filter(_.nonEmpty)
    CaptionPreference(if (m.isEnabled) CaptionMode.CaptionsOn else CaptionMode.CaptionsOff, language)
  }

  private lazy val main = new Handler(Looper.getMainLooper)

  def listen(changed: () => Unit): () => Unit = manager match {
    case Some(m) =>
      val listener = new CaptioningManager.CaptioningChangeListener {
        override def onEnabledChanged(enabled: Boolean): Unit = main.post(() => changed())
        override def onLocaleChanged(locale: java.util.Locale): Unit = main.post(() => changed())
      }
      m.addCaptioningChangeListener(listener)
      () => m.removeCaptioningChangeListener(listener)
    case None => () => ()
  }
}

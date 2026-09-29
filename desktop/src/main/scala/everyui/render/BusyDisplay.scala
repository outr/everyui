package everyui.render

import everyui.app.Busy

import java.awt.Taskbar
import java.awt.Taskbar.Feature
import java.util.concurrent.Executors

/** The app at work (`everyui.app.AppBusy`), on its taskbar or dock entry, through `java.awt.Taskbar` where the
  * system has one for the whole app: macOS fills the dock icon with the progress and badges it ("…" while there is
  * none); Linux does both where a Unity launcher is there to show it (libunity, as KDE's task manager and Ubuntu's
  * dock read it; a number only, so no badge); Windows only has a window's entry, and a JavaFX stage is not an AWT
  * window, so nothing. Where nothing shows, `supported` is false and only the title (`AppBusy.inTitle`) says it.
  *
  * AWT is reached on a thread of its own, never JavaFX's, and anything it throws (no display, a native image built
  * without it) leaves the taskbar alone. */
object BusyDisplay {
  private lazy val taskbar: Option[Taskbar] =
    try if (Taskbar.isTaskbarSupported) Some(Taskbar.getTaskbar) else None
    catch {
      case _: Throwable => None
    }
  private lazy val progress: Boolean = has(Feature.PROGRESS_VALUE)
  private lazy val badge: Boolean = has(Feature.ICON_BADGE_TEXT)
  private lazy val awt = Executors.newSingleThreadExecutor { (r: Runnable) =>
    val t = new Thread(r, "everyui-taskbar")
    t.setDaemon(true)
    t
  }

  private var prefix = ""

  private def has(feature: Feature): Boolean = taskbar.exists(t => try t.isSupported(feature) catch {
    case _: Throwable => false
  })

  def supported(): Boolean = progress || badge

  def permitted(): Boolean = true

  def permit(done: Boolean => Unit): Unit = done(true)

  /** `still` (AppBusy.stillAfter) is the web's: a taskbar's progress shows no orbit. */
  def show(busy: Option[Busy], inTitle: Boolean, still: Option[Double]): Unit = {
    awt.execute(() => taskbar.foreach { t =>
      try {
        if (progress) t.setProgressValue(busy.flatMap(_.percent).getOrElse(-1))
        // the badge says what the fill cannot: that it is working with no progress, or the progress where no fill shows
        val words = busy.flatMap(b => b.percent match {
          case Some(p) => if (progress) None else Some(p.toString + "%")
          case None => Some("…")
        })
        if (badge) t.setIconBadge(words.orNull)
      } catch {
        case _: Throwable => ()
      }
    })
    retitle(busy, inTitle)
  }

  /** Report what this machine's taskbar offers, for a check: "taskbar supported=… progress=… badge=…". */
  def describe(): String =
    "taskbar supported=" + taskbar.nonEmpty + " progress=" + progress + " badge=" + badge +
      " number=" + has(Feature.ICON_BADGE_NUMBER) + " window-progress=" + has(Feature.PROGRESS_VALUE_WINDOW)

  private def retitle(busy: Option[Busy], inTitle: Boolean): Unit =
    Option(Host.scene).flatMap(s => Option(s.getWindow)).foreach {
      case stage: javafx.stage.Stage =>
        val base = Busy.untitled(Option(stage.getTitle).getOrElse(""), prefix)
        prefix = if (inTitle) Busy.titlePrefix(busy) else ""
        if (stage.getTitle != prefix + base) stage.setTitle(prefix + base)
      case _ => ()
    }
}

package everyui.tray

import java.io.File

/** A D-Bus session bus of a spec's own (`dbus-daemon` on a socket in the scratch directory), so nothing it does
  * reaches the desktop's: its socket's path, and stopped with `stop()`. */
class PrivateBus {
  private val dir = new File("/data/storage/everyui-scratch/tray")
  dir.mkdirs()
  val path: String = new File(dir, "bus-" + ProcessHandle.current().pid() + "-" + System.nanoTime()).getPath
  private val process: Process = new ProcessBuilder("dbus-daemon", "--config-file=/usr/share/dbus-1/session.conf",
    "--nofork", "--nopidfile", "--address=unix:path=" + path).redirectErrorStream(true).start()

  // until its socket is there to connect to
  private val until = System.currentTimeMillis() + 5000L
  while (!new File(path).exists && System.currentTimeMillis() < until) Thread.sleep(10L)

  /** A connection to it, its messages handled on the thread that reads them. */
  def connect(): DBusConnection = DBusConnection.open(path, f => f()).getOrElse(throw new IllegalStateException(
    "no connection to the private bus at " + path))

  def stop(): Unit = {
    process.destroy()
    process.waitFor()
    new File(path).delete()
  }
}

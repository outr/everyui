package everyui.render

/** What a guest's page calls on the desktop (`EveryUIEmbedHost`, FxWebGuest): what it says, and what it logs. The
  * page calls it on the JavaFX thread. */
class FxEmbedHost(said: String => Unit, logged: String => Unit) {
  def postMessage(message: String): Unit = said(message)
  def log(line: String): Unit = logged(line)
}

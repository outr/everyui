package everyui.tray

/** A D-Bus message: a method call (`DBusMessage.Call`), its return or error, or a signal, with its header's fields
  * (empty where it has none) and its body's values, typed by `signature`. */
case class DBusMessage(kind: Int, serial: Int, path: String = "", interface: String = "", member: String = "",
                       errorName: String = "", replySerial: Int = 0, destination: String = "", sender: String = "",
                       signature: String = "", body: List[Any] = Nil, noReply: Boolean = false)

object DBusMessage {
  val Call: Int = 1
  val Return: Int = 2
  val Error: Int = 3
  val Signal: Int = 4
}

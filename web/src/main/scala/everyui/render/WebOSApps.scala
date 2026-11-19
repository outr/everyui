package everyui.render

import everyui.apps.AppLink

/** The Application Manager's calls on an LG TV (`luna://com.webos.applicationManager`), as plain Scala, so the
  * desktop's specs read them as the TV does: what each call is sent (`launch`, `loadStatus`) and what its answer says
  * (`launched`, `exists`). `WebOSTv` makes the calls. */
object WebOSApps {
  val service: String = "luna://com.webos.applicationManager"

  /** `launch`'s parameters: the app's id and its `params`, `link.json` where it has one, else `link.params` as strings. */
  def launch(link: AppLink): String =
    "{\"id\":" + quote(link.id) + ",\"params\":" + (if (link.json.trim.nonEmpty) link.json.trim else strings(link.params)) + "}"

  /** `getAppLoadStatus`'s parameters (webOS 4 and later). */
  def loadStatus(id: String): String = "{\"appId\":" + quote(id) + "}"

  /** Whether `launch`'s answer took the launch. */
  def launched(reply: String): Boolean = LaunchParams.parse(reply).get("returnValue").contains("true")

  /** Whether `getAppLoadStatus`'s answer has the app, None where it failed (a webOS before 4, a call refused). */
  def exists(reply: String): Option[Boolean] = {
    val fields = LaunchParams.parse(reply)
    if (fields.get("returnValue").contains("true")) fields.get("exist").map(_ == "true") else None
  }

  private def strings(params: Map[String, String]): String =
    params.toList.map { case (k, v) => quote(k) + ":" + quote(v) }.mkString("{", ",", "}")

  /** `s` as a JSON string. */
  def quote(s: String): String = {
    val out = new StringBuilder("\"")
    s.foreach {
      case '"' => out.append("\\\"")
      case '\\' => out.append("\\\\")
      case '\n' => out.append("\\n")
      case '\r' => out.append("\\r")
      case '\t' => out.append("\\t")
      case c if c < ' ' =>
        val hex = "0123456789abcdef"
        out.append("\\u00").append(hex.charAt(c.toInt / 16)).append(hex.charAt(c.toInt % 16))
      case c => out.append(c)
    }
    out.append('"').toString
  }
}

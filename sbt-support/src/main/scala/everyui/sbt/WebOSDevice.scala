package everyui.sbt

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** A TV in Developer Mode as plain SSH reaches it (`WebOSSsh`): its address, port and user, its key (the encrypted PEM
  * `ares-novacom --getkey` fetched) and the key's passphrase, where it has one. Given outright, or read from LG's CLI's
  * own list (`ares`). */
case class WebOSDevice(host: String, port: Int = 9922, user: String = "prisoner", key: File,
                       passphrase: Option[String] = None) {
  override def toString: String = s"$user@$host:$port (key $key)"
}

object WebOSDevice {
  /** The device `name` as `ares-setup-device` set it up: from `~/.webos/tv/novacom-devices.json` (the TV profile's),
    * else `~/.webos/ose/novacom-devices.json`; its key `~/.ssh/<privateKey.openSsh>`, else `~/.ssh/<name>_webos`, where
    * `ares-novacom --getkey` puts it. Fails where there is no such device. */
  def ares(name: String, home: File = new File(sys.props("user.home"))): WebOSDevice = {
    val lists = Seq("tv", "ose").map(p => new File(home, s".webos/$p/novacom-devices.json")).filter(_.isFile)
    val found = lists.iterator.flatMap(f => devices(new String(Files.readAllBytes(f.toPath), UTF_8)))
      .find(_.get("name").contains(name))
      .getOrElse(sys.error(s"webOS: no device \"$name\" in ${lists.mkString(" or ")}: set it up with " +
        "ares-setup-device"))
    def text(field: String): Option[String] = found.get(field).collect { case s: String if s.nonEmpty => s }
    val keyName = found.get("privateKey").collect { case m: Map[?, ?] => m.asInstanceOf[Map[String, Any]] }
      .flatMap(_.get("openSsh")).collect { case s: String if s.nonEmpty => s }.getOrElse(name + "_webos")
    WebOSDevice(
      host = text("host").getOrElse(sys.error(s"webOS: device \"$name\" has no host")),
      port = found.get("port").map(p => p.toString.toDouble.toInt).getOrElse(9922),
      user = text("username").getOrElse("prisoner"),
      key = new File(home, ".ssh/" + keyName),
      passphrase = text("passphrase"))
  }

  /** The devices of a `novacom-devices.json`, each its fields. */
  def devices(json: String): List[Map[String, Any]] = new Reader(json).value() match {
    case list: List[?] => list.collect { case m: Map[?, ?] => m.asInstanceOf[Map[String, Any]] }
    case _ => Nil
  }

  // JSON as Scala: an object a Map, an array a List, a string, a number a Double, true, false, null
  private final class Reader(s: String) {
    private var at = 0
    private def space(): Unit = while (at < s.length && s.charAt(at).isWhitespace) at += 1
    private def expect(c: Char): Unit = {
      space()
      if (at >= s.length || s.charAt(at) != c) sys.error(s"webOS: the device list is not JSON (at $at)")
      at += 1
    }
    def value(): Any = {
      space()
      s.charAt(at) match {
        case '{' =>
          at += 1
          var fields = Map.empty[String, Any]
          space()
          if (s.charAt(at) == '}') at += 1
          else {
            var more = true
            while (more) {
              space()
              val k = string()
              expect(':')
              fields += k -> value()
              space()
              more = s.charAt(at) == ','
              at += 1
            }
          }
          fields
        case '[' =>
          at += 1
          var items = List.empty[Any]
          space()
          if (s.charAt(at) == ']') at += 1
          else {
            var more = true
            while (more) {
              items ::= value()
              space()
              more = s.charAt(at) == ','
              at += 1
            }
          }
          items.reverse
        case '"' => string()
        case 't' => at += 4; true
        case 'f' => at += 5; false
        case 'n' => at += 4; null
        case _ =>
          val start = at
          while (at < s.length && "+-0123456789.eE".indexOf(s.charAt(at)) >= 0) at += 1
          s.substring(start, at).toDouble
      }
    }
    private def string(): String = {
      expect('"')
      val out = new StringBuilder
      while (s.charAt(at) != '"') {
        if (s.charAt(at) == '\\') {
          at += 1
          s.charAt(at) match {
            case 'n' => out.append('\n')
            case 't' => out.append('\t')
            case 'r' => out.append('\r')
            case 'b' => out.append('\b')
            case 'f' => out.append('\f')
            case 'u' => out.append(Integer.parseInt(s.substring(at + 1, at + 5), 16).toChar); at += 4
            case c => out.append(c)
          }
        }
        else out.append(s.charAt(at))
        at += 1
      }
      at += 1
      out.toString
    }
  }
}

package everyui.push

/** A push notification, as the server sends it: JSON `{"title": "...", "body": "...", "path": "/runs/42"}` on the
  * web (the Push API's payload), the same keys in an FCM data message on Android. `path` is where in the app a tap on
  * it goes, the app's own path (`ScreenManager.go`); None stays where the app is.
  *
  * `tag` groups it with the others of the same tag (a conversation's): shown, it takes the place of the one with that
  * tag the system shows, rather than another beside it, and `Push.close` closes it. `renotify` (`"renotify": true`)
  * has such a replacement sound or buzz again; without it, a replacement changes what shows quietly.
  *
  * A push of `{"close": "tag"}` alone takes away what shows with that tag, the person having read it elsewhere, and
  * shows nothing: on Android at once, in silence, and in a browser through the app's service worker, with no window of
  * the app open. A browser holds a push that shows nothing against the site: Chrome lets one pass now and then and
  * otherwise shows a notice of its own, Firefox allows a few and then ends the subscription, Safari ends it after a
  * few. So a server sends a browser one only where it sent a notification with that tag that is not yet closed, and
  * none to Safari's (an endpoint on `push.apple.com`), where the page closes its own as they are read (`Push.close`);
  * CONSUMING.md has the rest.
  *
  * `group` stacks it with the others of that group (a mail account's) under the group's summary, a push with
  * `summary` (`"groupSummary": "true"`) whose `lines` (a JSON array of text, the newest first) list what is in it; on
  * the web, which stacks nothing, it is the tag of one that has none, so the group shows as one. `silent` posts it
  * with no sound or buzz. `channel` is the Android channel it is posted into (`PushChannel`: "channel",
  * "channelName", "channelGroup", "channelGroupName", "channelImportance"), `PushChannel.default` where it names none.
  *
  * `actions` are its buttons (`PushButton`, JSON text under "actions"), which the person may choose instead of
  * tapping it (`Push.acted`); `data` is the rest of what the server sent with it, by key. */
case class PushMessage(title: String, body: String, path: Option[String], tag: Option[String] = None,
                       renotify: Boolean = false, actions: Vector[PushButton] = Vector.empty,
                       data: Map[String, String] = Map.empty, group: Option[String] = None, summary: Boolean = false,
                       lines: Vector[String] = Vector.empty, silent: Boolean = false,
                       channel: Option[PushChannel] = None) {
  /** The tag it is shown under: its own, or for a group's summary the group's, so that a summary takes the place of
    * the group's last and a close of the group (`{"close": group}`) takes it away. */
  def shownTag: Option[String] = tag.orElse(if (summary) group else None)

  /** The keys and values it was sent with, which `PushMessage.of` reads back as it is. */
  def fields: Map[String, String] = {
    val c = channel.getOrElse(PushChannel("", ""))
    data ++ List("title" -> title, "body" -> body, "path" -> path.getOrElse(""), "tag" -> tag.getOrElse(""),
      "renotify" -> (if (renotify) "true" else ""), "actions" -> (if (actions.isEmpty) "" else PushButton.json(actions)),
      "group" -> group.getOrElse(""), "groupSummary" -> (if (summary) "true" else ""),
      "lines" -> (if (lines.isEmpty) "" else lines.map(PushJson.quote).mkString("[", ",", "]")),
      "silent" -> (if (silent) "true" else ""), "channel" -> c.id, "channelName" -> c.name,
      "channelGroup" -> c.group, "channelGroupName" -> c.groupName,
      "channelImportance" -> (if (c.importance == NoticeImportance.Default) "" else c.importance.word))
      .filter((_, v) => v.nonEmpty)
  }
}

object PushMessage {
  /** The keys a push's data says what it is with; the rest are its `data`. */
  val keys: Set[String] = Set("title", "body", "path", "tag", "renotify", "close", "actions", "group", "groupSummary",
    "lines", "silent", "channel", "channelName", "channelGroup", "channelGroupName", "channelImportance")

  /** A push from its data as the server sent it, every value text (an FCM data message's): its title, body, path,
    * tag, renotify ("true"), its buttons (`PushButton.parse` of "actions"), its group, whether it is the group's
    * summary ("groupSummary": "true") and its lines (a JSON array), silent ("true"), its channel (where "channel"
    * names one: its name "channelName", the id where it has none, "channelGroup" with its "channelGroupName", and
    * its importance "channelImportance", `NoticeImportance.named`: default where it names none it knows),
    * and the rest as `data`, less what the push service adds itself (keys from "google." and "gcm."). */
  def of(data: Map[String, String]): PushMessage = {
    val text = (k: String) => data.getOrElse(k, "")
    val some = (k: String) => Some(text(k)).filter(_.nonEmpty)
    val group = text("channelGroup")
    PushMessage(text("title"), text("body"), some("path"), some("tag"), text("renotify") == "true",
      PushButton.parse(text("actions")),
      data.filter((k, _) => !keys.contains(k) && !k.startsWith("google.") && !k.startsWith("gcm.")),
      some("group"), text("groupSummary") == "true", lines(text("lines")), text("silent") == "true",
      some("channel").map(id => PushChannel(id, some("channelName").getOrElse(id), group,
        some("channelGroupName").getOrElse(group), NoticeImportance.named(text("channelImportance")))))
  }

  /** The lines of a summary: a JSON array's text, in order; none where it is not one. */
  private def lines(text: String): Vector[String] = PushJson.parse(text) match {
    case Some(items: Vector[?]) => items.collect {
      case s: String => s
      case d: Double => if (d == math.floor(d)) d.toLong.toString else d.toString
    }
    case _ => Vector.empty
  }
}

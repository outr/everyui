package everyui.push

import everyui.Color
import everyui.font.MaterialIcon

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
  * `format` is how its body is written: Plain, or Markdown (`"format": "markdown"`), which Android styles and a
  * browser shows as plain words (`PushText`); a Markdown push's title and `lines` are always plain words, never styled.
  * `shownTitle`, `shownBody` and `shownLines` are what a platform that shows no styles shows.
  *
  * `actions` are its buttons (`PushButton`, JSON text under "actions"), which the person may choose instead of
  * tapping it (`Push.acted`); `data` is the rest of what the server sent with it, by key.
  *
  * How Android shows it, so the kinds of push an app sends are told apart at a glance: `noticeIcon` names the Material
  * icon of its small icon by the font's name (`"noticeIcon": "mail"`, `MaterialIcon.named`; `shownIcon`), the app's
  * `everyui_notice` where it names none the font has; `noticeColor` (`"noticeColor": "#1E88E5"`, or `#AARRGGBB`;
  * `colorOf`) is the colour Android tints it with, none where it is no such colour; `noticePicture` is a picture's URL
  * shown large beside its words (`"noticePicture": "https://..."`), fetched on Android only over http or https
  * (`fetchable`) and shown without it where it does not come in time, and on the web the notification's icon where
  * the push does not name one under "icon". Where it has no picture, its icon is drawn large instead, white on a
  * circle of its colour (`LargeIcon`), where Android 16 shows its kind in the shade. A group's summary that names no
  * icon looks as the latest of its group (`lookingAs`). A plain "icon", "color" or "largeIcon" is the app's own data,
  * unread.
  *
  * A push with neither title nor body, or with `"dataOnly": "true"`, is no notification but a data push
  * (`PushMessage.isData`), never shown: its keys go to the app's `BackgroundApp.onPush`, or `Push.data`. */
case class PushMessage(title: String, body: String, path: Option[String], tag: Option[String] = None,
                       renotify: Boolean = false, actions: Vector[PushButton] = Vector.empty,
                       data: Map[String, String] = Map.empty, group: Option[String] = None, summary: Boolean = false,
                       lines: Vector[String] = Vector.empty, silent: Boolean = false,
                       channel: Option[PushChannel] = None, format: PushFormat = PushFormat.Plain,
                       noticeIcon: Option[String] = None, noticeColor: Option[Color] = None,
                       noticePicture: Option[String] = None) {
  /** The Material icon `noticeIcon` names, None where it names none the font has. */
  def shownIcon: Option[MaterialIcon] = noticeIcon.flatMap(MaterialIcon.named)

  /** `noticePicture` where it is one to fetch: an http or https URL. */
  def fetchedPicture: Option[String] = noticePicture.filter(PushMessage.fetchable)

  /** The tag it is shown under: its own, or for a group's summary the group's, so that a summary takes the place of
    * the group's last and a close of the group (`{"close": group}`) takes it away. */
  def shownTag: Option[String] = tag.orElse(if (summary) group else None)

  /** How it looks beside `child`, the latest of its group: a group's summary that names no icon of its own has its
    * child's icon, and its colour where it names none, so the stack shows its kind (Android draws a summary as it
    * draws the others); anything else looks as it is. */
  def lookingAs(child: PushMessage): PushMessage =
    if (!summary || noticeIcon.nonEmpty) this
    else copy(noticeIcon = child.noticeIcon, noticeColor = noticeColor.orElse(child.noticeColor))

  /** Its title as it is shown: plain words where it is Markdown (`PushText.plain`), as it is where not. */
  def shownTitle: String = shown(title)

  /** Its body as a platform that shows no styles shows it: plain words where it is Markdown, as it is where not. */
  def shownBody: String = shown(body)

  /** Its summary's lines as they are shown: plain words where it is Markdown, as they are where not. */
  def shownLines: Vector[String] = lines.map(shown)

  private def shown(text: String): String = if (format == PushFormat.Markdown) PushText.plain(text) else text

  /** The keys and values it was sent with, which `PushMessage.of` reads back as it is. */
  def fields: Map[String, String] = {
    val c = channel.getOrElse(PushChannel("", ""))
    data ++ List("title" -> title, "body" -> body, "path" -> path.getOrElse(""), "tag" -> tag.getOrElse(""),
      "renotify" -> (if (renotify) "true" else ""), "actions" -> (if (actions.isEmpty) "" else PushButton.json(actions)),
      "group" -> group.getOrElse(""), "groupSummary" -> (if (summary) "true" else ""),
      "lines" -> (if (lines.isEmpty) "" else lines.map(PushJson.quote).mkString("[", ",", "]")),
      "silent" -> (if (silent) "true" else ""), "channel" -> c.id, "channelName" -> c.name,
      "channelGroup" -> c.group, "channelGroupName" -> c.groupName,
      "channelImportance" -> (if (c.importance == NoticeImportance.Default) "" else c.importance.word),
      "format" -> (if (format == PushFormat.Plain) "" else format.word), "noticeIcon" -> noticeIcon.getOrElse(""),
      "noticeColor" -> noticeColor.map(PushMessage.hex).getOrElse(""), "noticePicture" -> noticePicture.getOrElse(""))
      .filter((_, v) => v.nonEmpty)
  }
}

object PushMessage {
  /** The key that makes a push a data push whatever else it says (`"dataOnly": "true"`): `isData`. */
  val dataOnlyKey: String = "dataOnly"

  /** Whether a push, its keys as sent, is a data push, never shown: one with `dataOnly` true, or one with neither a
    * title nor a body that is not a close alone (a close with keys of its own closes, then is a data push too). The
    * app hears it in `BackgroundApp.onPush`, or `Push.data` while it is in front. `silent` is no part of it: a silent
    * push is shown, with no sound. */
  def isData(sent: Map[String, String]): Boolean = {
    val text = (k: String) => sent.getOrElse(k, "")
    text(dataOnlyKey) == "true" ||
      (text("title").isEmpty && text("body").isEmpty && (text("close").isEmpty || dataOf(sent).nonEmpty))
  }

  /** What a data push hands the app: its keys and values as sent, less `dataOnly`, `close` and what the push service
    * adds itself (keys from "google." and "gcm."). */
  def dataOf(sent: Map[String, String]): Map[String, String] =
    sent.filter((k, _) => k != dataOnlyKey && k != "close" && !k.startsWith("google.") && !k.startsWith("gcm."))

  /** The keys a push's data says what it is with; the rest are its `data`. */
  val keys: Set[String] = Set("title", "body", "path", "tag", "renotify", "close", "actions", "group", "groupSummary",
    "lines", "silent", "channel", "channelName", "channelGroup", "channelGroupName", "channelImportance",
    "format", "noticeIcon", "noticeColor", "noticePicture", dataOnlyKey)

  /** A push from its data as the server sent it, every value text (an FCM data message's): its title, body, path,
    * tag, renotify ("true"), its buttons (`PushButton.parse` of "actions"), its group, whether it is the group's
    * summary ("groupSummary": "true") and its lines (a JSON array), silent ("true"), its channel (where "channel"
    * names one: its name "channelName", the id where it has none, "channelGroup" with its "channelGroupName", and
    * its importance "channelImportance", `NoticeImportance.named`: default where it names none it knows),
    * its format ("format": "markdown", `PushFormat.named`: plain where it names none it knows), how Android shows it
    * ("noticeIcon", "noticeColor" where it is a colour, `colorOf`, and "noticePicture"),
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
        some("channelGroupName").getOrElse(group), NoticeImportance.named(text("channelImportance")))),
      PushFormat.named(text("format")), some("noticeIcon").map(_.trim).filter(_.nonEmpty),
      colorOf(text("noticeColor")), some("noticePicture").map(_.trim).filter(_.nonEmpty))
  }

  /** The colour a push's "noticeColor" names: `#RRGGBB`, or `#AARRGGBB` as Android writes a colour with its alpha
    * first, in either case and around spaces; None for anything else. */
  def colorOf(text: String): Option[Color] = {
    val t = text.trim.toLowerCase
    val digits = t.drop(1)
    if (!t.startsWith("#") || (digits.length != 6 && digits.length != 8) || digits.exists(c => hexDigits.indexOf(c) < 0))
      None
    else {
      val argb = if (digits.length == 6) "ff" + digits else digits
      val at = (i: Int) => hexDigits.indexOf(argb.charAt(i)) * 16 + hexDigits.indexOf(argb.charAt(i + 1))
      Some(new Color(at(2), at(4), at(6), at(0)))
    }
  }

  /** `color` as "noticeColor" is sent: `#RRGGBB` where it is opaque, else `#AARRGGBB`. */
  def hex(color: Color): String =
    if (color.alphaInt == 255) color.toHex else "#" + color.alphaHex + color.redHex + color.greenHex + color.blueHex

  /** Whether `url` is a picture a device fetches for "noticePicture": an http or https URL with a host. */
  def fetchable(url: String): Boolean = {
    val t = url.trim.toLowerCase
    val rest = if (t.startsWith("https://")) t.drop(8) else if (t.startsWith("http://")) t.drop(7) else ""
    rest.nonEmpty && !rest.startsWith("/")
  }

  private val hexDigits = "0123456789abcdef"

  /** The lines of a summary: a JSON array's text, in order; none where it is not one. */
  private def lines(text: String): Vector[String] = PushJson.parse(text) match {
    case Some(items: Vector[?]) => items.collect {
      case s: String => s
      case d: Double => if (d == math.floor(d)) d.toLong.toString else d.toString
    }
    case _ => Vector.empty
  }
}

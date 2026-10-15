package everyui.app

import everyui.component.{Button, ButtonStyle, Column, Component, Container, ScrollContainer, TextInput, TextView}
import everyui.component.types.Autofill
import everyui.layout.FlowLayout
import everyui.location.{Location, LocationAnswer, LocationWatch}
import everyui.clipboard.Clipboard
import everyui.file.{Files, SaveResult}
import everyui.passkey.{PasskeyResult, Passkeys}
import everyui.push.{Notice, Push, PushAnswer}
import everyui.{Permission, PlatformKind}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** The platform's own services, one section each, with what each answered written under it and logged
  * (`[ui] platform save Saved notes.txt`): saving a file, as bytes or from a URL; the clipboard, copied to and read;
  * a passkey made and signed in with, by a click or from the username field's autofill, with options made here as a
  * server would (`rp=<domain>` names the site, which Android needs) and the credential's JSON logged whole; push
  * notifications, allowed, subscribed to with `vapid=<public key>` (a key of the demo's own otherwise, which nothing
  * can send with), and what arrives while open or is tapped; the app's own notices (tagged "demo", so each replaces the
  * last; one "while away" shows only while the app is out of sight) and its unread badge; where the device is, once and
  * followed 25 m at a time.
  * Each section says whether the platform has it.
  * `theme=light` shows the light palette. */
class PlatformDemo {
  private val width = math.min(720.0, ui.size.width() - 32.0)

  private val saved: Var[String] = Var("Nothing saved yet")
  private val pasted: Var[String] = Var("Nothing read yet")
  private val signedIn: Var[String] = Var("No passkey yet")
  private val autofills: Var[String] = Var("")
  private val pushed: Var[String] = Var("Not subscribed")
  private val allowed: Var[String] = Var("")
  private val noticed: Var[String] = Var("No notice shown yet")
  private val noticeAllowed: Var[String] = Var("")
  private var unread = 0
  private val located: Var[String] = Var("Not asked yet")
  private val followed: Var[String] = Var("")
  private var following: Option[LocationWatch] = None
  private val username = new TextInput("Username", width = 220.0) {
    autofill @= Some(Autofill.Username)
    passkey @= true
  }

  private val column = Column(20.0, padding = 16.0)(
    TextView("The platform", 22.0),
    section("Save a file", Files.savable, saved)(
      new Button("Save notes.txt", () => save(), ButtonStyle.Filled),
      new Button("Save a picture from its URL", () => saveUrl())),
    section("The clipboard", Clipboard.readable, pasted)(
      new Button("Copy a line", () => {
        Clipboard.copy("Copied from EveryUI")
        tell(pasted, "copy", "Copied \"Copied from EveryUI\"")
      }),
      new Button("Read the clipboard", () =>
        Clipboard.read().map(t => tell(pasted, "read", t.map(w => "Read \"" + w + "\"").getOrElse("Nothing to read"))).start(),
        ButtonStyle.Filled)),
    section("Passkeys", Passkeys.supported, signedIn, autofills, Some(username))(
      new Button("Create a passkey", () => Passkeys.create(creation()).map(r => keyed("create", r)).start()),
      new Button("Sign in with a passkey", () => Passkeys.get(request()).map(r => keyed("get", r)).start(),
        ButtonStyle.Filled),
      new Button("Offer in autofill", () => Passkeys.autofill(request()).map(r => keyed("autofill", r)).start())),
    section("Push notifications", Push.supported, pushed, allowed)(
      new Button("Allow notifications", () => Push.permit().map(_ => permission()).start()),
      new Button("Subscribe", () => Push.subscribe(vapid).map { a =>
        permission()
        a match {
          case PushAnswer.Subscribed(token) =>
            Platform.log("platform push token " + token.service + " " + token.value)
            tell(pushed, "push subscribe", "Subscribed (" + token.service + "): " + token.value.take(90) + "…")
          case other => tell(pushed, "push subscribe", other.toString)
        }
      }.start(), ButtonStyle.Filled),
      new Button("Unsubscribe", () => Push.unsubscribe().map(_ => tell(pushed, "push unsubscribe", "Unsubscribed")).start())),
    section("Notices and the unread badge", Platform.kind == PlatformKind.Web ||
      (Platform.kind == PlatformKind.Android && !Platform.remoteNavigation()), noticed, noticeAllowed)(
      new Button("Allow notices", () => Platform.askNotificationPermission()),
      new Button("Notice now", () => notice(onlyWhenHidden = false), ButtonStyle.Filled),
      new Button("Notice while away", () => notice(onlyWhenHidden = true)),
      new Button("One more unread", () => badge(unread + 1)),
      new Button("All read", () => badge(0))),
    section("Location", Location.supported, located, followed)(
      new Button("Where am I?", () => Location.current().map(a => tell(located, "location current", where(a))).start(),
        ButtonStyle.Filled),
      new Button("Follow, 25 m at a time", () => follow()),
      new Button("Stop following", () => {
        following.foreach(_.stop())
        following = None
        followed @= ""
        tell(located, "location stopped", "Stopped following")
      }))
  )
  Passkeys.autofillable.map(a => autofills @= (if (a) "offered in autofill too" else "not in autofill")).start()
  Push.received.attach(m => tell(pushed, "push received", "Arrived while open: " + m.title + ": " + m.body +
    m.path.map(p => " (" + p + ")").getOrElse("")))
  Push.opened.attach(m => tell(pushed, "push opened", "Tapped: " + m.title + m.path.map(p => " (" + p + ")").getOrElse("")))
  Push.tokens.attach(t => tell(pushed, "push renewed", "Renewed (" + t.service + "): " + t.value.take(90) + "…"))
  Platform.notificationPermission.attachAndFire(p => noticeAllowed @= "notices " + allowedWords(p))
  permission()

  private val scroll = new ScrollContainer {
    size.width := ui.size.width()
    size.height := ui.size.height()
  }
  scroll.content.children += column
  ui.children += scroll

  /** A section: its title, whether the platform has it (and anything more to say of that), a field above its buttons
    * where it has one, its buttons (wrapping on a narrow screen), and what it last answered. */
  private def section(title: String, supported: Boolean, answer: Var[String], note: Var[String] = Var(""),
                      field: Option[Component] = None)(buttons: Component*): Container = {
    val row = new Container {
      layout @= Some(FlowLayout(10.0, 10.0))
      size.width @= width
      children ++= buttons
    }
    val status = TextView.bound((if (supported) "Supported here" else "Not supported here") +
      (if (note().isEmpty) "" else " · " + note()), 12.0, Theme.muted())
    Column(8.0)((List(TextView(title, 16.0), status) ++ field.toList ++
      List(row, TextView.paragraph(answer(), width, 13.0, Theme.muted())))*)
  }

  private def tell(answer: Var[String], what: String, words: String): Unit = {
    answer @= words
    Platform.log("platform " + what + " " + words)
  }

  private def said(r: SaveResult): String = r match {
    case SaveResult.Saved(name) => "Saved " + name.getOrElse("(to the browser's downloads)")
    case SaveResult.Cancelled => "Cancelled"
    case SaveResult.SaveFailed(why) => "Not saved: " + why
  }

  private def keyed(what: String, r: PasskeyResult): Unit = r match {
    case PasskeyResult.Credential(json) =>
      Platform.log("platform passkey " + what + " json " + json)
      val id = "\"id\":\"([^\"]+)\"".r.findFirstMatchIn(json).map(_.group(1)).getOrElse("?")
      tell(signedIn, "passkey " + what, (if (what == "create") "Created" else "Signed in with") + " passkey " + id)
    case other => tell(signedIn, "passkey " + what, other.toString)
  }

  /** Options as a server's WebAuthn library writes them: a fresh challenge, this demo's user, ES256 or RS256, a
    * passkey the authenticator keeps (a discoverable credential). */
  private def creation(): String = {
    val name = if (username.value().isEmpty) "demo@everyui.com" else username.value()
    "{\"challenge\":\"" + random(32) + "\",\"rp\":{\"name\":\"EveryUI\"" + rp + "}," +
      "\"user\":{\"id\":\"" + userId + "\",\"name\":\"" + name + "\",\"displayName\":\"" + name + "\"}," +
      "\"pubKeyCredParams\":[{\"type\":\"public-key\",\"alg\":-7},{\"type\":\"public-key\",\"alg\":-257}]," +
      "\"authenticatorSelection\":{\"residentKey\":\"required\",\"userVerification\":\"preferred\"}," +
      "\"timeout\":60000,\"attestation\":\"none\"}"
  }

  private def request(): String =
    "{\"challenge\":\"" + random(32) + "\",\"userVerification\":\"preferred\",\"timeout\":60000" +
      (if (rpId.isEmpty) "" else ",\"rpId\":\"" + rpId + "\"") + "}"

  private val rpId = Platform.launchArg("rp")
  private def rp: String = if (rpId.isEmpty) "" else ",\"id\":\"" + rpId + "\""
  private val userId = random(16)

  private def random(count: Int): String = {
    val bytes = new Array[Byte](count)
    scala.util.Random.nextBytes(bytes)
    java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
  }

  private def where(a: LocationAnswer): String = a match {
    case LocationAnswer.Located(p) =>
      val round = (v: Double, places: Int) => BigDecimal(v).setScale(places, BigDecimal.RoundingMode.HALF_UP).toString
      round(p.latitude, 5) + ", " + round(p.longitude, 5) + " ±" + round(p.accuracy, 0) + " m" +
        p.altitude.map(v => ", " + round(v, 0) + " m up").getOrElse("") +
        p.heading.map(v => ", heading " + round(v, 0) + "°").getOrElse("") +
        p.speed.map(v => ", " + round(v, 1) + " m/s").getOrElse("")
    case other => other.toString
  }

  private def follow(): Unit = if (following.isEmpty) {
    var heard = 0
    followed @= "following"
    val watch = Location.watch(distance = 25.0) { a =>
      a match {
        case LocationAnswer.Located(_) => heard += 1
        case _ => ()
      }
      // a refusal ends the watch
      val ended = a == LocationAnswer.LocationDenied || a == LocationAnswer.LocationUnsupported
      if (ended) following = None
      followed @= (if (ended) "" else "following, " + heard.toString + (if (heard == 1) " position" else " positions"))
      tell(located, "location watch", where(a))
    }
    following = if (watch.running) Some(watch) else None
  }

  /** The app's own VAPID public key, from `vapid=`; otherwise one the demo made, whose private key nobody has. */
  private def vapid: String = {
    val asked = Platform.launchArg("vapid")
    if (asked.nonEmpty) asked else "BLSOXys1qjYrgzDKjeRKolQdpn2QJ4UhzRPnI7cir1pKrx84DS56fBO_hZ_73xIZhkbZQ6--RxCfdXpun26k2Qg"
  }

  private def permission(): Unit = Push.permission.map { p =>
    allowed @= "notifications " + (if (p == Permission.Granted) "allowed" else if (p == Permission.Refused) "refused"
      else "not asked for yet")
  }.start()

  private def allowedWords(p: Permission): String =
    if (p == Permission.Granted) "allowed" else if (p == Permission.Refused) "refused" else "not asked for yet"

  /** A notice tagged "demo", so each replaces the last; a click on it is said. */
  private def notice(onlyWhenHidden: Boolean): Unit = {
    val time = Platform.localDateTime(System.currentTimeMillis())
    Platform.showNotice(Notice("A notice from EveryUI", "Shown at " + time, tag = "demo", onlyWhenHidden = onlyWhenHidden),
      () => tell(noticed, "notice clicked", "Clicked the notice shown at " + time))
    tell(noticed, "notice shown", "Asked to show one at " + time + (if (onlyWhenHidden) ", if the app is out of sight" else ""))
  }

  private def badge(count: Int): Unit = {
    unread = count
    Platform.setBadge(count)
    tell(noticed, "badge", count.toString + " unread")
  }

  private def save(): Unit = {
    val words = "Notes from EveryUI\nsaved at " + Platform.localDateTime(System.currentTimeMillis()) + "\n"
    Files.save(words.getBytes("UTF-8"), "notes.txt").map(r => tell(saved, "save", said(r))).start()
  }

  /** The page's own logo on the web; elsewhere a picture on the web (`url=` for another). */
  private def saveUrl(): Unit = {
    val asked = Platform.launchArg("url")
    val url = if (asked.nonEmpty) asked else if (Platform.kind == PlatformKind.Web) "images/logo.png"
      else "https://www.scala-lang.org/resources/img/frontpage/scala-spiral.png"
    Files.saveUrl(url, url.substring(url.lastIndexOf("/") + 1)).map(r => tell(saved, "saveUrl", said(r))).start()
  }
}

object PlatformDemo {
  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    new PlatformDemo
  }
}

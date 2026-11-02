package everyui.call

import everyui.Permission
import everyui.render.{CallAccess, Platform}
import reactify.*

/** The microphones, cameras and speakers this device has, which of each a call uses, and whether the app may use them.
  * A choice is kept on the device (an id; None is the system's own), and a call in progress follows a new one at once.
  *
  * A browser lists the devices without their labels until the person has let the page use one: `ask` puts its prompt
  * up (best from a click, once the person knows what for), and the lists are filled again once it is answered, as
  * they are whenever a device is plugged in or taken out. Safari and Firefox on Android choose no speaker
  * (`speakersChosen` false, and `speakers` empty): sound goes where the system sends it.
  *
  * Only the web has them for now (`supported`); elsewhere the lists are empty and the access refused as unavailable.
  *
  * {{{
  *   MediaDevices.ask(microphone = true, camera = false)(why => why.foreach(r => Toast.show(r.toString)))
  *   val meter = MediaDevices.meter()
  *   bar.size.width := 200.0 * meter.level()
  *   ...
  *   meter.stop()
  * }}} */
object MediaDevices {
  private val listed: Var[Vector[MediaDevice]] = Var(Vector.empty)
  private val micAccess: Var[MediaPermission] =
    Var(if (CallAccess.supported()) MediaPermission.NotAsked else MediaPermission.refused(MediaRefusal.Unavailable))
  private val camAccess: Var[MediaPermission] = Var(micAccess())

  val microphones: Val[Vector[MediaDevice]] = Val(listed().filter(_.kind == DeviceKind.Microphone))
  val cameras: Val[Vector[MediaDevice]] = Val(listed().filter(_.kind == DeviceKind.Camera))
  val speakers: Val[Vector[MediaDevice]] = Val(listed().filter(_.kind == DeviceKind.Speaker))

  /** The microphone a call records from, by id; None is the system's own. */
  val microphone: Var[Option[String]] = kept("microphone")
  /** The camera a call sends, by id; None is the system's own. */
  val camera: Var[Option[String]] = kept("camera")
  /** Where a call's sound is played, by id; None is the system's own. */
  val speaker: Var[Option[String]] = kept("speaker")

  /** Whether the app may record from the microphone, and why not where it may not. */
  val microphoneAccess: Val[MediaPermission] = micAccess
  /** Whether the app may use the camera, and why not where it may not. */
  val cameraAccess: Val[MediaPermission] = camAccess

  /** Whether this platform lists devices and lets a call use them. */
  def supported: Boolean = CallAccess.supported()

  /** Whether a speaker can be chosen here, rather than sound going where the system sends it. */
  def speakersChosen: Boolean = CallAccess.speakersChosen()

  /** The devices listed again: the platform tells of one plugged in or taken out by itself, so this is for after
    * something it does not tell of. */
  def refresh(): Unit = CallAccess.devices(found => listed @= found)

  /** The platform's prompt for the microphone, the camera or both, answered with why not (None: given). The devices'
    * labels are listed once it is given. */
  def ask(microphone: Boolean = true, camera: Boolean = false)(done: Option[MediaRefusal] => Unit = _ => ()): Unit =
    CallAccess.ask(microphone, camera, chosen(DeviceKind.Microphone), chosen(DeviceKind.Camera), why => {
      why match {
        case None =>
          if (microphone) granted(DeviceKind.Microphone)
          if (camera) granted(DeviceKind.Camera)
        case Some(r) =>
          if (microphone) refused(DeviceKind.Microphone, r)
          if (camera) refused(DeviceKind.Camera, r)
      }
      done(why)
    })

  /** How loud the chosen microphone (or `device`) hears the room, until the meter is stopped. Asks for the microphone
    * where it has not been given. */
  def meter(device: Option[String] = microphone()): MicMeter = {
    val meter = new MicMeter
    meter.begin(CallAccess.meter(device, () => granted(DeviceKind.Microphone), meter.hear, why => {
      refused(DeviceKind.Microphone, why)
      meter.refuse(why)
    }))
    meter
  }

  /** The chosen device of `kind`, where one is chosen and still plugged in. */
  def chosen(kind: DeviceKind): Option[String] = {
    val choice = kind match {
      case DeviceKind.Microphone => microphone()
      case DeviceKind.Camera => camera()
      case DeviceKind.Speaker => speaker()
    }
    choice.filter(id => listed().isEmpty || listed().exists(d => d.kind == kind && d.id == id))
  }

  /** What a call or the platform found: the person let the app use a `kind` (the labels come with it). */
  private[everyui] def granted(kind: DeviceKind): Unit = {
    val was = access(kind)()
    access(kind) @= MediaPermission.Granted
    if (was.permission != Permission.Granted) refresh()
  }

  /** What a call or the platform found: a `kind` could not be had, for `why`. */
  private[everyui] def refused(kind: DeviceKind, why: MediaRefusal): Unit = access(kind) @= MediaPermission.refused(why)

  /** The permission as the platform reports it, where it changed by itself (in the site's settings). */
  private[everyui] def reported(kind: DeviceKind, permission: Permission): Unit = {
    val now = access(kind)()
    if (permission != now.permission) {
      access(kind) @= (permission match {
        case Permission.Refused => MediaPermission.refused(now.refusal.getOrElse(MediaRefusal.ByPerson))
        case other => MediaPermission(other)
      })
      if (permission == Permission.Granted) refresh()
    }
  }

  private def access(kind: DeviceKind): Var[MediaPermission] = if (kind == DeviceKind.Camera) camAccess else micAccess

  private def kept(name: String): Var[Option[String]] = {
    val key = "everyui.call." + name
    val v: Var[Option[String]] = Var(Option(Platform.setting(key)).filter(_.nonEmpty))
    v.attach(id => Platform.saveSetting(key, id.getOrElse("")))
    v
  }

  CallAccess.watch(() => refresh(), reported)
  refresh()
}

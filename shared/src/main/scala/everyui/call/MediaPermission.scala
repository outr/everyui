package everyui.call

import everyui.Permission

/** Whether the app may use the microphone or the camera (`MediaDevices.microphoneAccess`, `cameraAccess`): given,
  * refused, or not asked yet, and, once it was refused or failed, why (`refusal`). */
case class MediaPermission(permission: Permission, refusal: Option[MediaRefusal] = None)

object MediaPermission {
  val NotAsked: MediaPermission = MediaPermission(Permission.NotAsked)
  val Granted: MediaPermission = MediaPermission(Permission.Granted)

  /** Refused, for `why`. */
  def refused(why: MediaRefusal): MediaPermission = MediaPermission(Permission.Refused, Some(why))
}

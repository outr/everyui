package everyui.sbt

/** A file of a Roku channel's remote bundle (`RokuAssets.bundle`): its path in the package, the SHA-256 of its bytes
  * (hex) and the extension it is hosted under (`<hash>.<ext>`, `9.png` for a 9-patch, which SceneGraph knows by its
  * name), and its size. A font has the core face it is drawn in until it arrives (`standIn`, a path); a WAV given a
  * compressed form for the player has that form as `media`. */
final case class RokuRemoteFile(path: String, hash: String, ext: String, size: Long, standIn: String = "",
                                media: Option[RokuRemoteFile] = None) {
  /** Its name in the bundle and on the server. */
  def name: String = hash + "." + ext
}

package everyui

/** What a kind of platform is, apart from the enum, since the emitter does not carry an enum's companion. */
object PlatformKinds {
  /** On the web backend, in a browser or in a TV's web runtime (`WebOS`): what the DOM does, it does. */
  def browser(kind: PlatformKind): Boolean = kind == PlatformKind.Web || kind == PlatformKind.WebOS

  /** A TV whose remote has a pointer as well as its arrows (LG's Magic Remote): what the pointer comes onto takes the
    * focus while it shows (`everyui.focus.Focus.remotePointer`). */
  def pointerRemote(kind: PlatformKind): Boolean = kind == PlatformKind.WebOS
}

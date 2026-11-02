package everyui.call

/** One call as an engine carries it (`CallEngine.room`): what `Call` asks of it. It tells its `CallFeed` what comes
  * of each (the state, who is there, what this person sends) rather than answering, but for `send`, which answers why
  * a source could not be had. Everything is asked and told on the thread that draws. */
trait CallRoom {
  /** Join the room at `url` with `token` (minted by the app's server), recording from and playing to the `devices`
    * chosen (by kind; one not given is the system's own). */
  def connect(url: String, token: String, devices: Map[DeviceKind, String]): Unit

  /** Leave the room and let go of everything it holds: its devices, its tracks, its elements. */
  def leave(): Unit

  /** Start or stop sending `source` (the microphone, the camera or a screen), from `device` where one is chosen;
    * `done` hears why it could not be (None: it is done). Asked only while connected. */
  def send(source: TrackSource, on: Boolean, device: Option[String], done: Option[MediaRefusal] => Unit): Unit

  /** Record from, film with or play to another device of `kind` (None: the system's own), as the call goes on. */
  def device(kind: DeviceKind, id: Option[String]): Unit

  /** Play the others' sound (true) or keep it silent (false), every track of it. */
  def audible(on: Boolean): Unit

  /** Start the sound a browser held back until the page was used (`CallFeed.audioBlocked`): called from a click. */
  def startAudio(): Unit

  /** Draw `track` (a camera's or a screen's) in `into`, as `into.cover` and `into.mirror` say, until `hide`. */
  def show(track: CallTrack, into: CallVideo): Unit

  /** Stop drawing `track` in `into`. */
  def hide(track: CallTrack, into: CallVideo): Unit
}

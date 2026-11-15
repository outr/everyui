package everyui.call

/** How a call sends this person's camera (`CallSettings.quality`): `Auto`, as the engine adapts it to the connection,
  * `High` (720p) and `Standard` (360p) at most that size, or `AudioOnly`, no camera at all. `send` is what each
  * captures and publishes; `name` is how it is kept on the device. */
sealed abstract class VideoQuality {
  /** How it is kept on the device. */
  def name: String

  /** What the camera is captured and published at; None sends none. */
  def send: Option[VideoSend]
}

object VideoQuality {
  private val h180 = VideoLayer(320, 180, 160000, 20)
  private val h360 = VideoLayer(640, 360, 450000, 20)
  private val h720 = VideoLayer(1280, 720, 1700000, 30)

  /** Captured at 720p and published at three sizes (720p, 360p and 180p), each receiver given the one its connection
    * and its box take, and the size lowered before the frames where this person's connection or device cannot keep
    * up. LiveKit's own defaults. */
  case object Auto extends VideoQuality {
    def name: String = "auto"
    def send: Option[VideoSend] = Some(VideoSend(h720, List(h180, h360), keepResolution = false))
  }

  /** Captured and published at 720p, with 360p beside it for a receiver who cannot take more, and the size kept where
    * the connection or the device cannot keep up (fewer frames instead). */
  case object High extends VideoQuality {
    def name: String = "720p"
    def send: Option[VideoSend] = Some(VideoSend(h720, List(h360), keepResolution = true))
  }

  /** Captured and published at 360p (with 180p beside it): less of this person's upload and of the device's work. */
  case object Standard extends VideoQuality {
    def name: String = "360p"
    def send: Option[VideoSend] = Some(VideoSend(VideoLayer(640, 360, 500000, 30), List(h180), keepResolution = false))
  }

  /** No camera: none is opened in a preview or sent in a call while it is chosen. */
  case object AudioOnly extends VideoQuality {
    def name: String = "audio"
    def send: Option[VideoSend] = None
  }

  /** Every quality, in the order a settings dialog offers them. */
  val all: List[VideoQuality] = List(Auto, High, Standard, AudioOnly)

  /** The quality kept as `name`; `Auto` for any other. */
  def of(name: String): VideoQuality = all.find(_.name == name).getOrElse(Auto)
}

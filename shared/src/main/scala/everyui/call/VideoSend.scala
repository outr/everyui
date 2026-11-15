package everyui.call

/** How a [[VideoQuality]] sends the camera: captured at `top` and published at it, with the smaller `layers` (the
  * smallest first) published beside it for those whose connection or box takes less (simulcast; none, only `top`).
  * `keepResolution` keeps the picture's size when the connection or the device cannot keep up, dropping frames
  * rather than pixels, where otherwise the engine keeps the frames moving and lowers the size. */
case class VideoSend(top: VideoLayer, layers: List[VideoLayer], keepResolution: Boolean)

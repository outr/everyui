package everyui.call

/** One size a camera is sent at (a [[VideoSend]]'s): its picture's `width` and `height` in pixels, at most `maxBitrate`
  * bits a second and `frameRate` frames a second. */
case class VideoLayer(width: Int, height: Int, maxBitrate: Int, frameRate: Int)

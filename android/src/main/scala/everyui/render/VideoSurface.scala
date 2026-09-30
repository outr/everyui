package everyui.render

import android.content.Context
import android.graphics.Region
import android.view.SurfaceView

/** The `SurfaceView` a `direct` video is drawn into ([[ExoMedia]]), telling the window nothing of where it is.
  *
  * A `SurfaceView` marks its box as a hole in the app's window, and every view over it gives back its own box, so the
  * display can leave the hole out when it composes the window. Each box is measured at the view's own size, whatever
  * scales it on the screen, and everything in an EveryUI app is scaled by the density (EveryUIActivity): on a SHIELD
  * the hole was the picture's box at the real size ([[VideoFrame]]) and each button the half of it that its own size
  * covers, and the display cut the bar's buttons off where the hole claimed them. Without it the window is composed
  * whole over the picture, its pixels there see-through as the view clears them, as they are over a `TextureView`. */
final class VideoSurface(context: Context) extends SurfaceView(context) {
  override def gatherTransparentRegion(region: Region): Boolean = true
}

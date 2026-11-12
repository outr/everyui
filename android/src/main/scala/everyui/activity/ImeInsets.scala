package everyui.activity

import android.view.WindowInsets

/** The on-screen keyboard's height by Android 11's inset types, in an object of its own, touched only on 11 and later
  * (EveryUIActivity.keyboard): `WindowInsets.Type` is not there before it, and reached from the activity's own insets
  * listener it was a NoClassDefFoundError on the first layout of every app on Android 7 to 10. */
private[activity] object ImeInsets {
  /** How much of the window the keyboard covers, from its bottom; 0 while it is down. */
  def bottom(insets: WindowInsets): Int =
    if (insets.isVisible(WindowInsets.Type.ime())) insets.getInsets(WindowInsets.Type.ime()).bottom else 0
}

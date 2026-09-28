package everyui.monaco.gallery

import everyui.activity.EveryUIActivity

/** The gallery on Android: `sbt monacoAndroid/packageApk`, Monaco in a web view served from the APK. */
class GalleryActivity extends EveryUIActivity {
  override protected def run(): Unit = Gallery.main()
}

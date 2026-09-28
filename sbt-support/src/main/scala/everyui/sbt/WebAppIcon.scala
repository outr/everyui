package everyui.sbt

/** An icon of an installable web app: `src` next to the page, its `sizes` ("192x192"), and its `purpose`: "any", or
  * "maskable" for one drawn with room round it that the platform may cut to its own shape. */
case class WebAppIcon(src: String, sizes: String, purpose: String = "any", mime: String = "image/png")

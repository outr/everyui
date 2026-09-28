package everyui.seo

/** A reply for a `/.well-known/` address (`WellKnown`): `body` of `contentType` with `status` 200, or a redirect to
  * `location`. */
case class WellKnownAnswer(status: Int, contentType: String, body: String, location: String = "")

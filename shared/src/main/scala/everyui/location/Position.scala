package everyui.location

/** Where the device is: `latitude` and `longitude` in degrees, `accuracy` the radius in metres the true position is
  * likely within, and what the platform knows beside: `altitude` in metres above the WGS 84 ellipsoid, `heading` in
  * degrees clockwise from true north, `speed` in metres a second (each None where it does not know). `time` is when it
  * was fixed, in milliseconds since 1970. */
case class Position(latitude: Double, longitude: Double, accuracy: Double, altitude: Option[Double],
                    heading: Option[Double], speed: Option[Double], time: Long) {
  /** The distance to `other` in metres, near enough for the distances a watch filters by (the earth taken as a sphere,
    * and flat over the step between them). */
  def distanceTo(other: Position): Double = {
    val radians = math.Pi / 180.0
    val x = (other.longitude - longitude) * radians * math.cos((latitude + other.latitude) / 2.0 * radians)
    val y = (other.latitude - latitude) * radians
    math.sqrt(x * x + y * y) * 6371008.8
  }
}

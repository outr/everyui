package universal.ui

import universal.render.Platform

/** youi's easing library, ported.
  *
  * youi models an easing as `trait Easing { def calculate(progress: Double): Double }`
  * — a SAM. Here an easing IS the function, `Double => Double`: the cheapest
  * polymorphism on every backend. On the Roku a curve is a defunctionalised
  * closure, and calling it is one `RtInvoke` — a vtable of one method, with no
  * object-model dispatch. youi's call sites are unaffected:
  * `easing.calculate(p)` becomes `easing(p)`.
  *
  * The curves themselves are youi's verbatim (`youi/easing/Easing.scala`),
  * rewritten from the four-argument `(time, start, change, duration)` form into
  * the normalised 0..1 progress form, which is what every call site used
  * anyway (`wrap(f) = f(progress, 0.0, 1.0, 1.0)`).
  */
type Easing = Double => Double

/** A curve with youi's name for it, so the demo can label its rows. */
case class NamedEasing(name: String, easing: Easing)

object Easing:
  /** Backend-supplied because `scala.math` is not one thing across targets:
    * Scala.js has the real stdlib, while the Roku side reaches
    * BrightScript's intrinsics. Each backend's `Platform` supplies the four
    * functions the curves need. */
  val Pi: Double = 3.141592653589793
  private val DefaultOvershoot: Double = 1.70158

  // ── back ────────────────────────────────────────────────────────────────
  def backIn(overshoot: Double): Easing = t => t * t * ((overshoot + 1.0) * t - overshoot)
  def backOut(overshoot: Double): Easing = t =>
    val t1 = t - 1.0
    t1 * t1 * ((overshoot + 1.0) * t1 + overshoot) + 1.0
  def backInOut(overshoot: Double): Easing = t =>
    val s = overshoot * 1.525
    val t2 = t * 2.0
    if t2 < 1.0 then 0.5 * (t2 * t2 * ((s + 1.0) * t2 - s))
    else
      val t3 = t2 - 2.0
      0.5 * (t3 * t3 * ((s + 1.0) * t3 + s) + 2.0)

  // ── linear ──────────────────────────────────────────────────────────────
  val linear: Easing = t => t

  // ── bounce ──────────────────────────────────────────────────────────────
  val bounceOut: Easing = t =>
    if t < (1.0 / 2.75) then 7.5625 * t * t
    else if t < (2.0 / 2.75) then
      val t2 = t - (1.5 / 2.75)
      7.5625 * t2 * t2 + 0.75
    else if t < (2.5 / 2.75) then
      val t3 = t - (2.25 / 2.75)
      7.5625 * t3 * t3 + 0.9375
    else
      val t4 = t - (2.625 / 2.75)
      7.5625 * t4 * t4 + 0.984375
  val bounceIn: Easing = t => 1.0 - bounceOut(1.0 - t)
  val bounceInOut: Easing = t =>
    if t < 0.5 then bounceIn(t * 2.0) * 0.5
    else bounceOut(t * 2.0 - 1.0) * 0.5 + 0.5

  // ── circular ────────────────────────────────────────────────────────────
  val circularIn: Easing = t => -(Platform.sqrt(1.0 - t * t) - 1.0)
  val circularOut: Easing = t =>
    val t1 = t - 1.0
    Platform.sqrt(1.0 - t1 * t1)
  val circularInOut: Easing = t =>
    val t2 = t * 2.0
    if t2 < 1.0 then -0.5 * (Platform.sqrt(1.0 - t2 * t2) - 1.0)
    else
      val t3 = t2 - 2.0
      0.5 * (Platform.sqrt(1.0 - t3 * t3) + 1.0)

  // ── polynomial ──────────────────────────────────────────────────────────
  val cubicIn: Easing = t => t * t * t
  val cubicOut: Easing = t =>
    val t1 = t - 1.0
    t1 * t1 * t1 + 1.0
  val cubicInOut: Easing = t =>
    val t2 = t * 2.0
    if t2 < 1.0 then 0.5 * t2 * t2 * t2
    else
      val t3 = t2 - 2.0
      0.5 * (t3 * t3 * t3 + 2.0)

  val quadraticIn: Easing = t => t * t
  val quadraticOut: Easing = t => -t * (t - 2.0)
  val quadraticInOut: Easing = t =>
    val t2 = t * 2.0
    if t2 < 1.0 then 0.5 * t2 * t2
    else
      val t3 = t2 - 1.0
      -0.5 * (t3 * (t3 - 2.0) - 1.0)

  val quarticIn: Easing = t => t * t * t * t
  val quarticOut: Easing = t =>
    val t1 = t - 1.0
    -(t1 * t1 * t1 * t1 - 1.0)
  val quarticInOut: Easing = t =>
    val t2 = t * 2.0
    if t2 < 1.0 then 0.5 * t2 * t2 * t2 * t2
    else
      val t3 = t2 - 2.0
      -0.5 * (t3 * t3 * t3 * t3 - 2.0)

  val quinticIn: Easing = t => t * t * t * t * t
  val quinticOut: Easing = t =>
    val t1 = t - 1.0
    t1 * t1 * t1 * t1 * t1 + 1.0
  val quinticInOut: Easing = t =>
    val t2 = t * 2.0
    if t2 < 1.0 then 0.5 * t2 * t2 * t2 * t2 * t2
    else
      val t3 = t2 - 2.0
      0.5 * (t3 * t3 * t3 * t3 * t3 + 2.0)

  // ── elastic ─────────────────────────────────────────────────────────────
  val elasticIn: Easing = t =>
    if t <= 0.0 then 0.0
    else if t >= 1.0 then 1.0
    else
      val period = 0.3
      val s = period / 4.0
      val t1 = t - 1.0
      -(Platform.pow(2.0, 10.0 * t1) * Platform.sin((t1 - s) * (2.0 * Pi) / period))
  val elasticOut: Easing = t =>
    if t <= 0.0 then 0.0
    else if t >= 1.0 then 1.0
    else
      val period = 0.3
      val s = period / 4.0
      Platform.pow(2.0, -10.0 * t) * Platform.sin((t - s) * (2.0 * Pi) / period) + 1.0
  val elasticInOut: Easing = t =>
    if t <= 0.0 then 0.0
    else if t >= 1.0 then 1.0
    else
      val period = 0.3 * 1.5
      val s = period / 4.0
      val t2 = t * 2.0 - 1.0
      if t2 < 0.0 then -0.5 * (Platform.pow(2.0, 10.0 * t2) * Platform.sin((t2 - s) * (2.0 * Pi) / period))
      else Platform.pow(2.0, -10.0 * t2) * Platform.sin((t2 - s) * (2.0 * Pi) / period) * 0.5 + 1.0

  // ── exponential ─────────────────────────────────────────────────────────
  val exponentialIn: Easing = t => if t <= 0.0 then 0.0 else Platform.pow(2.0, 10.0 * (t - 1.0))
  val exponentialOut: Easing = t => if t >= 1.0 then 1.0 else -Platform.pow(2.0, -10.0 * t) + 1.0
  val exponentialInOut: Easing = t =>
    if t <= 0.0 then 0.0
    else if t >= 1.0 then 1.0
    else
      val t2 = t * 2.0
      if t2 < 1.0 then 0.5 * Platform.pow(2.0, 10.0 * (t2 - 1.0))
      else 0.5 * (-Platform.pow(2.0, -10.0 * (t2 - 1.0)) + 2.0)

  // ── sine ────────────────────────────────────────────────────────────────
  val sineIn: Easing = t => -Platform.cos(t * (Pi / 2.0)) + 1.0
  val sineOut: Easing = t => Platform.sin(t * (Pi / 2.0))
  val sineInOut: Easing = t => -0.5 * (Platform.cos(Pi * t) - 1.0)

  /** youi's `Easing.map`, kept in youi's own sort order (alphabetical by name)
    * so the demo's rows line up with the original screenshot. */
  val all: List[NamedEasing] = List(
    NamedEasing("BackIn", backIn(DefaultOvershoot)),
    NamedEasing("BackInOut", backInOut(DefaultOvershoot)),
    NamedEasing("BackOut", backOut(DefaultOvershoot)),
    NamedEasing("BounceIn", bounceIn),
    NamedEasing("BounceInOut", bounceInOut),
    NamedEasing("BounceOut", bounceOut),
    NamedEasing("CircularIn", circularIn),
    NamedEasing("CircularInOut", circularInOut),
    NamedEasing("CircularOut", circularOut),
    NamedEasing("CubicIn", cubicIn),
    NamedEasing("CubicInOut", cubicInOut),
    NamedEasing("CubicOut", cubicOut),
    NamedEasing("ElasticIn", elasticIn),
    NamedEasing("ElasticInOut", elasticInOut),
    NamedEasing("ElasticOut", elasticOut),
    NamedEasing("ExponentialIn", exponentialIn),
    NamedEasing("ExponentialInOut", exponentialInOut),
    NamedEasing("ExponentialOut", exponentialOut),
    NamedEasing("Linear", linear),
    NamedEasing("QuadraticIn", quadraticIn),
    NamedEasing("QuadraticInOut", quadraticInOut),
    NamedEasing("QuadraticOut", quadraticOut),
    NamedEasing("QuarticIn", quarticIn),
    NamedEasing("QuarticInOut", quarticInOut),
    NamedEasing("QuarticOut", quarticOut),
    NamedEasing("QuinticIn", quinticIn),
    NamedEasing("QuinticInOut", quinticInOut),
    NamedEasing("QuinticOut", quinticOut),
    NamedEasing("SineIn", sineIn),
    NamedEasing("SineInOut", sineInOut),
    NamedEasing("SineOut", sineOut)
  )

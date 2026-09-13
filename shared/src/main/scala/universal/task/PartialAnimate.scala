package universal.task

import universal.easing.Easing

import scala.concurrent.duration.*

case class PartialAnimate(get: () => Double,
                          apply: Double => Unit,
                          destination: () => Double):
  def from(value: => Double): PartialAnimate = copy(get = () => value)

  def in(duration: => FiniteDuration): AnimateIn = AnimateIn(get, apply, destination, () => duration, Easing.linear)

  def by(stepBy: Double): AnimateIn =
    val duration = () => ((math.abs(get() - destination()) * 1000.0) / stepBy).millis
    AnimateIn(get, apply, destination, duration, Easing.linear)

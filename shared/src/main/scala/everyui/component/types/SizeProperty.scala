package everyui.component.types

import reactify.{Val, Var}

/** youi's `SizeProperty`: follows the component's `measured` size until something sets it explicitly, after which
  * every value is written out to the node. */
class SizeProperty(measured: Val[Double], write: Double => Unit) extends Var[Double](measured()):
  private var explicitlySet = false

  attach { d =>
    if explicitlySet then write(d)
  }

  override def @=(value: Double): Unit =
    val first = !explicitlySet
    explicitlySet = true
    // the first explicit value must reach the node even if it equals the measured one
    if first && get == value then write(value) else super.@=(value)

  override def :=(value: => Double): Unit =
    val first = !explicitlySet
    explicitlySet = true
    val before = get
    super.:=(value)
    if first && get == before then write(get)

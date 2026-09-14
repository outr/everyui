package everyui.app

import reactify.*
import everyui.component.{Container, TextView}
import everyui.component.types.{PositionType, Prop}
import everyui.scribe
import everyui.easing.Easing
import everyui.task.*
import everyui.ui

import scala.concurrent.duration.*

/** Per-frame cost of youi's animation model, one layer at a time, so the Roku
  * frame rate of AnimationExample can be optimised where the time actually goes. */
object YouiBench {
  private class Frames extends TaskSupport

  private def label(container: Container, text: String): TextView = {
    val l = new TextView {
      content @= text
      font.size @= 24.0
      position.`type` @= PositionType.Absolute
    }
    container.children += l
    l
  }

  def run(): Unit = {
    val container = new Container
    ui.children += container
    val row = label(container, "Linear Example")
    var flip = false
    val base = ReactifyBench.time("(loop+call baseline)", 20000, 0.0, () => ())

    scribe.info("bench x reactions attached: " + row.position.x.reactions().size.toString)
    val noop = new Prop[Double](0.0, _ => ())
    ReactifyBench.time("youi Prop @= x, no-op setter", 1000, base, () => {
      flip = !flip
      noop @= (if (flip) 60.0 else 70.0)
    })
    val direct = new Prop[Double](0.0, d => row.node.setX(d))
    ReactifyBench.time("youi Prop @= x, setter writes node", 1000, base, () => {
      flip = !flip
      direct @= (if (flip) 60.0 else 70.0)
    })
    ReactifyBench.time("row.node.setX(x) direct", 2000, base, () => {
      flip = !flip
      row.node.setX(if (flip) 60.0 else 70.0)
    })
    ReactifyBench.time("row.position.x @= x", 1000, base, () => {
      flip = !flip
      row.position.x @= (if (flip) 60.0 else 70.0)
    })
    ReactifyBench.time("left @= x (Prop)", 1000, base, () => {
      flip = !flip
      row.position.left @= (if (flip) 60.0 else 70.0)
    })
    ReactifyBench.time("right := x (Dep)", 1000, base, () => {
      flip = !flip
      row.position.right := (if (flip) 600.0 else 700.0)
    })
    ReactifyBench.time("read container.size.width - 50", 4000, base, () => { container.size.width - 50.0; () })

    val step = row.position.right.to(1800.0).in(5.seconds).easing(Easing.linear)
    step.update(0.0, true)
    ReactifyBench.time("AnimateIn.update (right)", 1000, base, () => { step.update(0.000001, false); () })

    val loop = forever(sequential(
      row.position.right.to(1800.0).in(5.seconds).easing(Easing.linear), sleep(2.seconds),
      row.position.left.to(50.0).in(5.seconds).easing(Easing.linear), sleep(2.seconds)))
    loop.update(0.0, true)
    ReactifyBench.time("forever(sequential(..)).update", 1000, base, () => { loop.update(0.000001, false); () })

    val ch = Channel[Double]
    (0 until 32).toList.foreach(_ => ch.attach(_ => ()))
    ReactifyBench.time("delta fire, 32 no-op reactions", 500, base, () => ch @= 0.016)

    val idle = new Frames
    (0 until 32).toList.foreach(_ => forever(sleep(2.seconds)).start(idle))
    ReactifyBench.time("frame: 32 sleeping tasks", 200, base, () => idle.update(0.000001))

    val moving = new Frames
    (0 until 32).toList.foreach { i =>
      val l = label(container, "Row " + i.toString)
      forever(sequential(
        l.position.right.to(1800.0).in(5.seconds).easing(Easing.linear), sleep(2.seconds),
        l.position.left.to(50.0).in(5.seconds).easing(Easing.linear), sleep(2.seconds))).start(moving)
    }
    moving.update(0.0)
    ReactifyBench.time("frame: 32 moving rows", 20, base, () => moving.update(0.000001))
    scribe.info("bench done")
  }
}

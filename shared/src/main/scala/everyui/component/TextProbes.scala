package everyui.component

import everyui.render.Platform
import reactify.*

/** Words measured out of sight, once, for how wide they are, and what measured them let go: a paragraph's lines each on
  * one line (how narrow a chat's bubble may be), a table's cells on one line and a word to a line (how wide its
  * columns are). The probes (`RichText`s set as the words are drawn, so they measure as the words do) go in a container
  * of their own in `owner`, which takes no room and is not drawn, so they inherit what the words do; they are read with
  * the turn's other sizes where the platform reads sizes in a batch (the web), and once each has been read in the faces
  * its words are drawn in (`RenderNode.textMeasurable`) only the numbers are kept (`widths`) and they are taken apart,
  * on the next turn, what this one still has for them heard first. A twin kept beside each line for as long as it
  * showed cost as much again as the line. A probe whose face is still coming is kept until it has been read in it, as
  * it measures again once it has (`RichText`). */
private[everyui] class TextProbes(owner: Container) {
  /** How wide each probe last measured was, in the order they were given: none until every one has been read. */
  val widths: Var[Option[Vector[Double]]] = owner.own(Var[Option[Vector[Double]]](None))
  private var box: Option[Container] = None
  private var probes: Vector[RichText] = Vector.empty
  // (the probes being put in place: a platform that reads sizes as they are asked for reads them there)
  private var placing = false
  private val noted: () => Unit = () => ()

  /** The container the probes being measured are in, while there are some: whatever `owner` sets its children to
    * keeps it. */
  def held: Vector[Component] = box.toVector

  /** `made` measured in place of whatever was being measured; `fresh`, the widths there were forgotten until then (the
    * words are other lines now), else kept until these are read. */
  def measure(made: Vector[RichText], fresh: Boolean = false): Unit = {
    drop()
    if (fresh) widths @= None
    if (made.isEmpty) widths @= Some(Vector.empty)
    else if (!owner.takenApart) {
      val b = new Container {
        size.width @= 0.0
        size.height @= 0.0
        visible @= false
        copyAs @= "none"
      }
      probes = made
      box = Some(b)
      made.foreach { p =>
        p.measured.known.attach(_ => check())
        p.size.width.attach(_ => check())
      }
      placing = true
      try {
        b.children @= made
        owner.children += b
      } finally placing = false
      check()
    }
  }

  /** Whatever is being measured let go, unread. */
  def dispose(): Unit = drop()

  // every probe read, in its faces: the numbers kept, and the probes let go on the next turn. Not at once: a change
  // that runs through a long chain of values runs the rest flat once the stack unwinds (reactify), so the words, face
  // and size a probe was given may reach its node, and be read, after it already read once without them, still in this
  // turn; kept till the next, it is heard again then (an Android phone's chat had a message a letter wide, every
  // letter on a line of its own, from a probe read before its words were in it and let go at once)
  private def check(): Unit = if (!placing && probes.nonEmpty &&
    probes.forall(p => p.measured.known() && p.node.textMeasurable(noted))) {
    val read = probes.map(_.size.width())
    if (!widths().contains(read)) widths @= Some(read)
    if (!leaving.exists(b => box.exists(_ eq b))) {
      val those = box
      leaving = those
      Platform.later(() => if (box.exists(b => those.exists(_ eq b))) drop())
    }
  }
  // the probes' container to be let go on the next turn
  private var leaving: Option[Container] = None

  private def drop(): Unit = {
    val gone = box
    probes = Vector.empty
    box = None
    gone.foreach { b =>
      owner.children -= b
      b.dispose()
    }
  }
}

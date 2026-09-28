package everyui.tree

import reactify.*

import scala.collection.mutable

/** The nodes of a tree, by key, changed a node at a time: insert, update, remove and move one, or give a node all its
  * children (`setChildren`, as a lazy load does). A `Tree` showing it follows each change without being built again,
  * and keeps what is open, chosen and scrolled to. Changes made in `batch` are heard once.
  *
  * {{{
  *   val model = new TreeModel[File](_.path)
  *   model.setChildren(None, roots)
  *   model.insert(Some("/src"), 0, File("/src/Main.scala"))
  *   model.update(file.copy(status = "M"))
  *   model.move("/src/Main.scala", Some("/app"), 0)
  *   model.remove("/tmp")
  * }}} */
class TreeModel[T](val keyOf: T => String) {
  private val entries = mutable.HashMap.empty[String, TreeEntry[T]]
  private var top: Vector[String] = Vector.empty
  private var topLoaded = false
  private var batching = 0
  private var dirty = false

  /** Heard after each change, or once after a batch of them. */
  val changed: Channel[Unit] = Channel[Unit]

  def get(key: String): Option[T] = entries.get(key).map(_.value)
  def contains(key: String): Boolean = entries.contains(key)
  def entry(key: String): Option[TreeEntry[T]] = entries.get(key)
  def parentOf(key: String): Option[String] = entries.get(key).flatMap(_.parent)
  /** The keys of `parent`'s children, in order; the roots' for None. */
  def childKeys(parent: Option[String]): Vector[String] = parent match {
    case None => top
    case Some(key) => entries.get(key).map(_.children).getOrElse(Vector.empty)
  }
  def children(parent: Option[String]): Vector[T] = childKeys(parent).flatMap(get)
  def roots: Vector[T] = children(None)
  /** Whether `parent`'s children are known: given by `setChildren`, or the roots given any way. */
  def loaded(parent: Option[String]): Boolean = parent match {
    case None => topLoaded
    case Some(key) => entries.get(key).exists(_.loaded)
  }
  /** How many nodes there are. */
  def size: Int = entries.size
  /** The keys from the root down to `key`'s parent. */
  def ancestors(key: String): List[String] = {
    var found: List[String] = Nil
    var at = parentOf(key)
    while (at.nonEmpty) {
      found = at.get :: found
      at = parentOf(at.get)
    }
    found
  }

  /** `value` as `parent`'s child at `index` (at the end, past the last). One already here by its key moves there. */
  def insert(parent: Option[String], index: Int, value: T): Unit = changing {
    val key = keyOf(value)
    if (entries.contains(key)) detach(key)
    val entry = entries.getOrElseUpdate(key, new TreeEntry[T](value, parent))
    entry.value = value
    entry.parent = parent
    place(key, parent, index)
  }

  /** `value` as `parent`'s last child. */
  def add(parent: Option[String], value: T): Unit = insert(parent, Int.MaxValue, value)

  /** The node with `value`'s key given `value`, where it is. */
  def update(value: T): Unit = entries.get(keyOf(value)).foreach(e => changing(e.value = value))

  /** The node `key`, and everything under it, gone. */
  def remove(key: String): Unit = if (entries.contains(key)) changing {
    detach(key)
    forget(key)
  }

  /** The node `key` moved, with everything under it, to `parent` at `index` (as it is counted with the node gone from
    * where it was). */
  def move(key: String, parent: Option[String], index: Int): Unit = entries.get(key).foreach { e =>
    if (!parent.exists(p => p == key || ancestors(p).contains(key))) changing {
      detach(key)
      e.parent = parent
      place(key, parent, index)
    }
  }

  /** `parent`'s children, all of them, in order: a node already here by its key keeps what is under it, one no longer
    * among them goes with everything under it. */
  def setChildren(parent: Option[String], values: Seq[T]): Unit = changing {
    val keys = values.map(keyOf).toVector
    val named = keys.toSet
    childKeys(parent).filterNot(named.contains).foreach(forget)
    values.foreach { v =>
      val key = keyOf(v)
      if (entries.get(key).exists(_.parent != parent)) detach(key)
      val entry = entries.getOrElseUpdate(key, new TreeEntry[T](v, parent))
      entry.value = v
      entry.parent = parent
    }
    parent match {
      case None =>
        top = keys
        topLoaded = true
      case Some(p) => entries.get(p).foreach { e =>
        e.children = keys
        e.loaded = true
      }
    }
  }

  /** Every node gone. */
  def clear(): Unit = changing {
    entries.clear()
    top = Vector.empty
    topLoaded = false
  }

  /** `f`'s changes heard as one. */
  def batch(f: => Unit): Unit = {
    batching += 1
    try f finally {
      batching -= 1
      if (batching == 0 && dirty) {
        dirty = false
        changed @= (())
      }
    }
  }

  private def changing(f: => Unit): Unit = batch {
    f
    dirty = true
  }

  private def place(key: String, parent: Option[String], index: Int): Unit = {
    val siblings = childKeys(parent)
    val at = math.max(0, math.min(index, siblings.length))
    val next = siblings.patch(at, Vector(key), 0)
    parent match {
      case None => top = next
      case Some(p) => entries.get(p).foreach(_.children = next)
    }
  }

  // out of its parent's children, still held
  private def detach(key: String): Unit = entries.get(key).foreach { e =>
    e.parent match {
      case None => top = top.filterNot(_ == key)
      case Some(p) => entries.get(p).foreach(pe => pe.children = pe.children.filterNot(_ == key))
    }
  }

  // gone, with everything under it
  private def forget(key: String): Unit = {
    var pending = List(key)
    while (pending.nonEmpty) {
      val k = pending.head
      pending = entries.get(k).map(_.children.toList).getOrElse(Nil) ++ pending.tail
      entries.remove(k)
    }
    top = top.filterNot(_ == key)
  }
}

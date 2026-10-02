package everyui.kit

/** What is asked of a `TabTree`. (An object of its own rather than methods of the enum or its companion: the Roku's
  * compiler reads a method of an enum's case as one of its fields, and emits no companion of an enum.) */
object TabTrees {
  /** The groups in `t`, first to last. */
  def groups[K](t: TabTree[K]): List[TabGroup[K]] = t match {
    case TabTree.Leaf(g) => List(g)
    case TabTree.Split(_, _, a, b) => groups(a) ++ groups(b)
  }

  def has[K](t: TabTree[K], g: TabGroup[K]): Boolean = groups(t).exists(_ eq g)

  /** `t` with `g`'s leaf made `f` of it. */
  def replace[K](t: TabTree[K], g: TabGroup[K], f: TabTree[K] => TabTree[K]): TabTree[K] = t match {
    case TabTree.Leaf(x) => if (x eq g) f(t) else t
    case TabTree.Split(v, r, a, b) => TabTree.Split(v, r, replace(a, g, f), replace(b, g, f))
  }

  /** `t` without `g`, a split it leaves alone giving its place to the other part; None where `g` was all of it. */
  def without[K](t: TabTree[K], g: TabGroup[K]): Option[TabTree[K]] = t match {
    case TabTree.Leaf(x) => if (x eq g) None else Some(t)
    case TabTree.Split(v, r, a, b) => (without(a, g), without(b, g)) match {
      case (Some(x), Some(y)) => Some(TabTree.Split(v, r, x, y))
      case (Some(x), None) => Some(x)
      case (None, y) => y
    }
  }

  /** The group next to `g` along a split that way (`vertical`: below, else after it in the reading direction), or
    * before it where not `after`: the nearest of the other part's groups. */
  def neighbour[K](t: TabTree[K], g: TabGroup[K], vertical: Boolean, after: Boolean): Option[TabGroup[K]] = t match {
    case TabTree.Leaf(_) => None
    case TabTree.Split(v, _, a, b) =>
      if (has(a, g)) neighbour(a, g, vertical, after).orElse(if (v == vertical && after) groups(b).headOption else None)
      else if (has(b, g)) neighbour(b, g, vertical, after).orElse(if (v == vertical && !after) groups(a).lastOption else None)
      else None
  }

  /** The nearest group of the part beside `g` in the split it is directly in: where its tabs go as it is unsplit. */
  def sibling[K](t: TabTree[K], g: TabGroup[K]): Option[TabGroup[K]] = t match {
    case TabTree.Leaf(_) => None
    case TabTree.Split(_, _, a, b) => (a, b) match {
      case (TabTree.Leaf(x), _) if x eq g => groups(b).headOption
      case (_, TabTree.Leaf(y)) if y eq g => groups(a).lastOption
      case _ => if (has(a, g)) sibling(a, g) else sibling(b, g)
    }
  }
}

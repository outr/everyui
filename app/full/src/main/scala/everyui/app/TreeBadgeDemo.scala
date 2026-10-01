package everyui.app

import everyui.tree.{Tree, TreeModel, TreeRow}

/** BadgeDemo's changed files in trees: a tree's rows cut their badges as a list's do. */
object TreeBadgeDemo {
  def run(): Unit = BadgeDemo.show { (wide, side) =>
    val model = new TreeModel[BadgeChange](_.path)
    val tree = new Tree[BadgeChange](model, _.name)
    tree.decorate @= (c => BadgeDemo.decoration(c, side))
    model.setChildren(None, BadgeDemo.changes)
    tree.size.width := wide()
    tree.size.height @= 28.0 * BadgeDemo.changes.size
    (tree, i => tree.list.rowAt(i).map(_.asInstanceOf[TreeRow[BadgeChange]].label))
  }
}

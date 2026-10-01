package everyui.app

/** A changed file in BadgeDemo: its path and how many lines were added and removed. */
case class BadgeChange(path: String, added: Int, removed: Int) {
  def name: String = path.substring(path.lastIndexOf('/') + 1)
  def folder: String = path.substring(0, math.max(path.lastIndexOf('/'), 0))
}

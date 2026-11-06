package everyui.access

import everyui.Color
import everyui.render.Platform

/** What the pictures words lie on look like, for the contrast check (`Access`): each picture as a grid of cells, each
  * cell its average colour, read once a picture (`Platform.pictureCells`) and kept by its address. The first check
  * that meets a picture asks for it and judges words over it as over the worst it could be until it has come; one
  * that cannot be read (from another site that keeps its pixels to itself, or on a platform that reads none) is
  * judged that way for good. */
object PictureCells {
  /** The cells across and down a picture is read as. */
  val Across: Int = 32
  val Down: Int = 32

  // read, by address: the cells, or none where it could not be read; and those asked for and not yet come
  private var read: Map[String, Vector[Color]] = Map.empty
  private var asked: Set[String] = Set.empty

  /** The cells of the picture at `uri`, where they have been read: asked for, the first time, where not. */
  def of(uri: String): Option[Vector[Color]] = read.get(uri) match {
    case Some(cells) => if (cells.isEmpty) None else Some(cells)
    case None =>
      if (uri.nonEmpty && !asked.contains(uri)) {
        asked = asked + uri
        Platform.pictureCells(uri, Across, Down, values => {
          asked = asked - uri
          read = read + (uri -> values.map(v => Color.fromRGB(((v >> 16) & 0xff) / 255.0, ((v >> 8) & 0xff) / 255.0,
            (v & 0xff) / 255.0)).toVector)
          // the screen looked over again, now it is known
          Access.laidOut()
        })
      }
      None
  }

  /** The colours of the cells of `cells` under the part of the picture from `left` to `right` across and `top` to
    * `bottom` down, each a fraction of it (0 to 1), the lightest and the darkest among them: the grounds words there
    * read on least well. */
  def under(cells: Vector[Color], left: Double, top: Double, right: Double, bottom: Double): List[Color] = {
    def cell(f: Double, n: Int): Int = math.max(0, math.min(n - 1, math.floor(f * n).toInt))
    val (x0, x1) = (cell(left, Across), cell(math.max(left, right - 0.000001), Across))
    val (y0, y1) = (cell(top, Down), cell(math.max(top, bottom - 0.000001), Down))
    var covered = List.empty[Color]
    var y = y0
    while (y <= y1) {
      var x = x0
      while (x <= x1) {
        covered = cells(y * Across + x) :: covered
        x += 1
      }
      y += 1
    }
    def light(c: Color): Double = 0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue
    if (covered.isEmpty) Nil else List(covered.maxBy(light), covered.minBy(light)).distinct
  }
}

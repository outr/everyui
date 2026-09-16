package everyui.font

import everyui.render.Platform

object Faces {
  lazy val OpenSansRegular: Face = new Face("Open Sans", 400, "OpenSans-Regular.ttf", 2048.0, 2189.0, 600.0)
  lazy val OpenSansSemiBold: Face = new Face("Open Sans", 600, "OpenSans-SemiBold.ttf", 2048.0, 2189.0, 600.0)
  lazy val MaterialIcons: Face = new Face("Material Icons", 400, "MaterialIcons-Regular.ttf", 512.0, 512.0, 0.0)
  lazy val RobotoMono: Face = new Face("Roboto Mono", 400, "RobotoMono-Regular.ttf", 2048.0, 2146.0, 555.0)

  private var registered: Vector[Face] = Vector.empty

  /** An app's own face, its file beside the bundled ones in `fonts/`: from now on its family answers with the
    * registered weight nearest the one asked for. Register before `ScreenManager.run`, so the web waits for it. */
  def register(face: Face): Unit = {
    registered = registered :+ face
    Platform.registerFont(face)
  }

  /** The face for a family and CSS weight: a registered one, a bundled one, or Open Sans for a family neither has. */
  def apply(family: String, weight: Int): Face = {
    var nearest: Option[Face] = None
    var distance = 0
    registered.foreach { f =>
      val d = math.abs(f.weight - weight)
      // as CSS and Flutter choose: nearest wins, and between two as near, a weight above 500 takes the heavier
      // face and one below the lighter — so a semibold 600 in a family of 500 and 700 is drawn bold
      val better = nearest.forall(n => d < distance || (d == distance && (if (weight > 500) f.weight > n.weight else f.weight < n.weight)))
      if (f.family == family && better) {
        nearest = Some(f)
        distance = d
      }
    }
    nearest.getOrElse {
      if (family == "Material Icons") MaterialIcons
      else if (family == "Roboto Mono") RobotoMono
      else if (weight >= 600) OpenSansSemiBold
      else OpenSansRegular
    }
  }

  /** Every registered face, for a backend that loads them up front. */
  def all(): Vector[Face] = registered
}

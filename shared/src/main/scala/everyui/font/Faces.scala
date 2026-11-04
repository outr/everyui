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
      if (f.family == family && !f.italic && better) {
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

  /** The family's italic face nearest `weight`, where one is registered: what a platform that can draws a run marked
    * italic in, rather than slanting the upright letters itself. */
  def italic(family: String, weight: Int): Option[Face] = {
    var nearest: Option[Face] = None
    registered.foreach { f =>
      if (f.family == family && f.italic && nearest.forall(n => math.abs(f.weight - weight) < math.abs(n.weight - weight)))
        nearest = Some(f)
    }
    nearest
  }

  private var fallbacks: Vector[Face] = Vector.empty

  /** A face for what the others have nothing to draw with: colour emoji, which a television's own fonts may be years
    * out of date for (a Fire TV drew a dozen trivia emoji and two awards as empty boxes). Where a platform can, a
    * character none of an app's faces has is drawn in it; the line keeps its own face's height. */
  def fallback(face: Face): Unit = {
    fallbacks = fallbacks :+ face
    Platform.registerFont(face)
  }

  /** The fallback faces, first first. */
  def fallbackFaces(): Vector[Face] = fallbacks

  private var colorEmojis: Vector[Face] = Vector.empty

  /** A colour emoji face for where the system has none (a Linux machine without Noto Color Emoji drew boxes), last of
    * all: after the system's own, so it is drawn only where they are missing, and only where the platform can draw
    * it in colour (the web's `font-tech(color-COLRv1)`); anywhere else it is not registered at all, and whether it
    * was is the answer. Its file beside the bundled faces in `fonts/`. */
  def colorEmoji(face: Face): Boolean = {
    val drawn = Platform.registerColorEmoji(face)
    if (drawn) colorEmojis = colorEmojis :+ face
    drawn
  }

  /** The colour emoji faces registered and drawn, first first. */
  def colorEmojiFaces(): Vector[Face] = colorEmojis
}

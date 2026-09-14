package everyui.font


object Faces {
  lazy val OpenSansRegular: Face = new Face("Open Sans", 400, "OpenSans-Regular.ttf", 2048.0, 2189.0, 600.0)
  lazy val OpenSansSemiBold: Face = new Face("Open Sans", 600, "OpenSans-SemiBold.ttf", 2048.0, 2189.0, 600.0)
  lazy val MaterialIcons: Face = new Face("Material Icons", 400, "MaterialIcons-Regular.ttf", 512.0, 512.0, 0.0)
  lazy val RobotoMono: Face = new Face("Roboto Mono", 400, "RobotoMono-Regular.ttf", 2048.0, 2146.0, 555.0)

  /** The bundled face for a family and CSS weight — Open Sans for a family that is not bundled. */
  def apply(family: String, weight: Int): Face =
    if (family == "Material Icons") MaterialIcons
    else if (family == "Roboto Mono") RobotoMono
    else if (weight >= 600) OpenSansSemiBold
    else OpenSansRegular
}

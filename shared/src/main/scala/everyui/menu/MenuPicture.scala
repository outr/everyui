package everyui.menu

import everyui.Color

/** A picture an entry of a menu shows where its icon would be (`MenuItem.picture`), as a small rounded square: a
  * workspace's own icon, a person's face, a product's logo, or a letter on a colour where there is no picture. */
enum MenuPicture {
  /** The picture at `url`, loaded as the menu shows, the menu not waiting for it; until it has loaded, and where it
    * will not, `fallback` (a letter, say), else the entry's icon. */
  case Picture(url: String, fallback: Option[MenuPicture])
  /** `text` (a letter or two) on `colour`, in white or black, whichever reads on it. */
  case Letter(text: String, colour: Color)
}

object MenuPicture {
  /** The picture at `url`, and until it loads, or where it will not, `fallback`, else the entry's icon. */
  def image(url: String, fallback: Option[MenuPicture] = None): MenuPicture = Picture(url, fallback)

  /** `text` on `colour`: a workspace with no picture of its own, by its first letter, say. */
  def letter(text: String, colour: Color): MenuPicture = Letter(text, colour)
}

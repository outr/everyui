package everyui.chat

import everyui.component.*
import everyui.component.types.Semantics
import everyui.font.MaterialIcon
import everyui.image.Image
import everyui.theme.Theme
import reactify.*

/** A file waiting to go with a message, in a [[Composer]]: a small picture of it where it is one (else a file's
  * glyph), its name, its size, and a button that takes it off (`remove`). */
class AttachmentChip(val attachment: Attachment, removeLabel: String, remove: () => Unit, points: Double = 13.0)
  extends Container {
  private val side = points * 2.4
  private val tile = new Container {
    size.width @= side
    size.height @= side
    radius @= 6.0
    backgroundColor := Theme.control()
  }
  private val glyph = new MaterialIconView(if (attachment.image) MaterialIcon.Image else MaterialIcon.Description, side * 0.6)
  glyph.color := Theme.muted()
  glyph.position.center := tile.size.center()
  glyph.position.middle := tile.size.middle()
  tile.children += glyph
  attachment.preview.filter(_ => attachment.image).foreach { source =>
    Image(source).map { loaded =>
      if (loaded.width > 0.0 && loaded.height > 0.0) {
        val scale = math.min(side / loaded.width, side / loaded.height)
        val picture = new ImageView
        picture.sizedByPicture @= false
        picture.image @= loaded
        picture.alt @= attachment.name
        picture.size.width @= loaded.width * scale
        picture.size.height @= loaded.height * scale
        picture.position.left @= (side - loaded.width * scale) / 2.0
        picture.position.top @= (side - loaded.height * scale) / 2.0
        tile.children -= glyph
        tile.children += picture
      }
    }.startUnit()
  }
  private val name = new TextView {
    content @= attachment.name
    font.size @= points
    color := Theme.text()
    maxWidth @= points * 12.0
    // a chip is a line tall: its name cut short, and read whole in its tip
    textOverflow @= everyui.component.types.TextOverflow.Ellipsis
    tooltip := (if (elided()) attachment.name else "")
  }
  private val weight = new TextView {
    content @= Attachment.sizeText(attachment.size)
    font.size @= points * 0.85
    color := Theme.muted()
  }
  /** What takes it off the message. */
  val removal: IconButton = new IconButton(MaterialIcon.Close, removeLabel + " " + attachment.name, remove)
  private val words = Column(1.0)(name, weight)

  padding @= 4.0
  layout @= Some(everyui.layout.HorizontalLayout(points * 0.6, middle = true))
  radius @= 10.0
  backgroundColor := Theme.surface()
  borderColor := Theme.border()
  borderWidth @= 1.0
  semantics @= Semantics.listItem
  children ++= List(tile, words, removal)

  override def kind: String = "AttachmentChip"
}

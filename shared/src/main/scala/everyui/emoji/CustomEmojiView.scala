package everyui.emoji

import everyui.component.PictureSquare

/** One of the app's own emoji drawn as its picture (`CustomEmoji`), fitted in a square `side` pixels across, its name
  * between colons read out and shown on a hover: in a picker's cell, a reaction, a completion. */
class CustomEmojiView(val emoji: CustomEmoji, side: Double) extends PictureSquare(emoji.source, side, emoji.code) {
  override def kind: String = "CustomEmojiView(" + emoji.name + ")"
}

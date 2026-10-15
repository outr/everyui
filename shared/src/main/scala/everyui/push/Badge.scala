package everyui.push

/** What a badge says for a count (`Platform.setBadge`): the number up to 9, "9+" over it, and nothing for none. */
object Badge {
  def text(count: Int): String = if (count <= 0) "" else if (count > 9) "9+" else count.toString
}

package everyui.markdown

/** An item of a list: its blocks, and for a task list's item whether it is ticked (`Some(true)`), not ticked
  * (`Some(false)`), or not a task at all (`None`). */
case class MdItem(blocks: List[MdBlock], task: Option[Boolean] = None)

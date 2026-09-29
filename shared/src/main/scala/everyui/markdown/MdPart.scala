package everyui.markdown

import everyui.text.TextRun

/** A line of a paragraph as it is drawn (`MarkdownRuns`): its runs, or a picture standing on a line of its own. */
private[markdown] case class MdPart(runs: List[TextRun], image: Option[MdInline.Picture])

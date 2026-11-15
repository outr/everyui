package everyui.memory

/** A memory check's measures, a row per case and a column per measure, written as a Markdown table, which is both what
  * a baseline file holds and what a run prints:
  * {{{
  *   | case | unit | heap bytes | DOM nodes |
  *   |---|---|---:|---:|
  *   | TextView | view | 1830 | 1.00 |
  * }}} */
final case class MemoryTable(columns: Vector[String], rows: Vector[MemoryRow]) {
  def row(name: String): Option[MemoryRow] = rows.find(_.name == name)

  /** The table as Markdown, a byte measure as a whole number and a count to two places. */
  def markdown: String = {
    val head = "| case | unit | " + columns.mkString(" | ") + " |"
    val rule = "|---|---|" + columns.map(_ => "---:|").mkString
    val body = rows.map { r =>
      "| " + r.name + " | " + r.unit + " | " + r.values.indices.map(i => MemoryTable.show(columns(i), r.values(i)))
        .mkString(" | ") + " |"
    }
    (Vector(head, rule) ++ body).mkString("\n") + "\n"
  }

  /** Where this goes over `baseline` by more than the measure's `margin` (0.1 for a tenth) and its `slack` in the case
    * (what it may move by with nothing changed: a few bytes of a heap, nothing of a count), a line each saying where
    * and by how much. A case or measure the baseline lacks is not held to anything. */
  def over(baseline: MemoryTable, margin: String => Double, slack: (String, String) => Double): Vector[String] =
    rows.flatMap(r => baseline.row(r.name).toVector.flatMap(b => columns.indices.flatMap { i =>
      val at = b.values.indices.find(j => baseline.columns.lift(j).contains(columns(i)))
      at.flatMap { j =>
        val was = b.values(j)
        val now = r.values(i)
        val budget = was * (1.0 + margin(columns(i))) + slack(r.name, columns(i))
        if (now <= budget) None
        else Some(r.name + ": " + columns(i) + " per " + r.unit + " is " + MemoryTable.show(columns(i), now) +
          ", over its budget of " + MemoryTable.show(columns(i), budget) + " (baseline " +
          MemoryTable.show(columns(i), was) + ")")
      }
    }))
}

object MemoryTable {
  /** A measure as the table writes it: bytes whole, counts to two places. */
  def show(column: String, value: Double): String =
    if (column.endsWith("bytes")) math.round(value).toString
    else (math.round(value * 100.0) / 100.0).toString

  /** The table in Markdown `text` (as `markdown` writes it), lines outside it (a heading, notes) passed over. */
  def parse(text: String): MemoryTable = {
    val lines = text.split("\n").toVector.map(_.trim).filter(_.startsWith("|"))
    val cells = lines.map(_.stripPrefix("|").stripSuffix("|").split("\\|").toVector.map(_.trim))
      .filterNot(_.forall(c => c.nonEmpty && c.forall(ch => ch == '-' || ch == ':')))
    cells.headOption match {
      case Some(head) => MemoryTable(head.drop(2), cells.tail.map(c =>
        MemoryRow(c(0), c(1), c.drop(2).map(v => v.toDoubleOption.getOrElse(0.0)))))
      case None => MemoryTable(Vector.empty, Vector.empty)
    }
  }
}

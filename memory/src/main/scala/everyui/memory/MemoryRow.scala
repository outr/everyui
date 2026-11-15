package everyui.memory

/** A case's measures per unit (`MemoryTable`), in the table's columns' order. */
final case class MemoryRow(name: String, unit: String, values: Vector[Double])

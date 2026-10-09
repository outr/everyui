package everyui.table

/** What a `DataTable` column holds, which is how its header sorts it: words in the language's own order, numbers by
  * their value (thousands separators, a currency, a percentage or a unit around them), and dates by when they are. */
enum ColumnKind {
  case Text, Number, Date
}

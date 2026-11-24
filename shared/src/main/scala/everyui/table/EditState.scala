package everyui.table

/** An edit of a `DataTable`'s not yet applied, as the app marks a cell (`DataTable.cellStates`) or a row
  * (`DataTable.rowStates`): a value `Changed` (a cell typed over, or a row with one), a row `New` (put in, not yet
  * saved) or a row `Deleted` (to go once the edits are applied). The table tints a changed cell and a new row in the
  * theme's own colours, strikes a deleted row's words through and dims it, and tells a screen reader which. */
enum EditState {
  case Changed, New, Deleted
}

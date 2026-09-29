package everyui.paging

import everyui.table.Sorting

/** What a `PagedSource` asks its server for: `limit` rows from `offset`, sorted as `sort` says (the heading the table
  * is sorted by), and, where the server pages by cursor, the `cursor` the page before gave (none for the first). The
  * app's own filter is the app's to add: the source asks again from the start whenever it is told the query changed. */
case class PageRequest(offset: Int, limit: Int, sort: Option[Sorting], cursor: Option[String])

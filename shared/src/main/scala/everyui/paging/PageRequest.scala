package everyui.paging

import everyui.table.Sorting

/** What a `PagedSource` asks its server for: `limit` rows from `offset`, sorted as `sort` says (the heading the table
  * is sorted by), and, where the server pages by cursor, the `cursor` the page before gave (none for the first).
  *
  * `query` is the words the rows must match, where the source is searched through it (`PagedSource.query`, a table's
  * filter field): the server decides what a match is. The app's own filter is the app's to add: the source asks again
  * from the start whenever it is told the query changed.
  *
  * A request that takes a while (a scan through a file for the next page of matches) says how far it has got with
  * `progress` (0 to 1: "Searching… 38%"), and stops once `live` is false: the query or the sort changed since it was
  * asked, or the source was closed, and nothing it answers will be heard. */
case class PageRequest(offset: Int, limit: Int, sort: Option[Sorting], cursor: Option[String], query: String = "",
                       live: () => Boolean = () => true, progress: Double => Unit = (_: Double) => ()) {
  // the same page of the same query is the same request, whoever asked and whatever it is told: the functions are not
  // compared
  override def equals(other: Any): Boolean = other match {
    case r: PageRequest => offset == r.offset && limit == r.limit && sort == r.sort && cursor == r.cursor && query == r.query
    case _ => false
  }
  override def hashCode: Int = (offset, limit, sort, cursor, query).hashCode
  override def toString: String = "PageRequest(" + offset.toString + ", " + limit.toString + ", " + sort.toString + ", " +
    cursor.toString + ", " + query + ")"
}

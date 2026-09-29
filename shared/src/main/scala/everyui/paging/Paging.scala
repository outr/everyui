package everyui.paging

/** How a `PagedSource` reaches its rows. */
enum Paging {
  /** Any page by its offset, the server counting the rows (`Page.total`): the list is as long as the count from the
    * first answer on, and scrolling anywhere in it fetches what shows there. Without a count it grows as `InOrder`
    * does. */
  case Anywhere
  /** Page after page, each asked for with the cursor the one before it gave (`Page.next`), or by offset where the
    * server gives none: the list is as long as what has come, with a page of placeholders after it while there is
    * more, fetched as they are neared (load more). */
  case InOrder
}

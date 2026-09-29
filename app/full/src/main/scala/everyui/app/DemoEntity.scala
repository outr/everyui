package everyui.app

/** A row of the paged table demo's made-up server (`EntityServer`): an entity with its name, status, record count,
  * county, when it last changed (days since 1970) and whether it is one someone manages. */
case class DemoEntity(id: String, name: String, status: String, records: Int, county: String, updated: Int,
                      managed: Boolean)

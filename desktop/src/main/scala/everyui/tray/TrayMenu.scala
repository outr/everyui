package everyui.tray

import everyui.menu.MenuItem
import reactify.*

/** A tray icon's menu served over D-Bus as DBusMenu (`com.canonical.dbusmenu`, at `TrayMenu.Path`), which the panel
  * draws as its own menu: the app's `MenuItem`s, each numbered by where it is (0 the menu itself, then each entry and
  * its submenu's entries in order). Given new entries, it tells the panel what changed: only their words, ticks or
  * whether they can be chosen (a line of live numbers) as `ItemsPropertiesUpdated`, which a menu that is open shows
  * where it is; anything more (an entry come or gone, a submenu) as a new layout. A ticked entry with an `on` is told
  * again as `on` changes. Choosing one runs its action on the app's thread. */
class TrayMenu(bus: DBusConnection, rtl: => Boolean) {
  private var items: Vector[MenuItem] = Vector.empty
  // each entry's id's children, the menu's own (0) first
  private var children: Map[Int, Vector[Int]] = Map(0 -> Vector.empty)
  private var revision = 1
  // the properties each entry was last told with
  private var told: Map[Int, Map[String, DBusVariant]] = Map.empty
  // the ticks followed, each with the way to stop following it
  private var watching: List[() => Unit] = Nil

  /** The entries shown, the menu told of whatever changed. */
  def set(entries: List[MenuItem]): Unit = {
    val (flat, kids) = TrayMenu.flatten(entries)
    val sameShape = kids == children && flat.size == items.size &&
      flat.indices.forall(i => flat(i).separator == items(i).separator)
    items = flat
    children = kids
    watch()
    val now = (1 to items.size).map(id => id -> props(id)).toMap
    if (sameShape) {
      val changed = now.toList.filter { case (id, p) => !told.get(id).contains(p) }
      if (changed.nonEmpty) bus.signal(TrayMenu.Path, TrayMenu.Interface, "ItemsPropertiesUpdated", "a(ia{sv})a(ias)",
        List(changed.sortBy(_._1).map { case (id, p) => DBusStruct(List(id, p.toVector)) }.toVector, Vector.empty))
    }
    else {
      revision += 1
      bus.signal(TrayMenu.Path, TrayMenu.Interface, "LayoutUpdated", "ui", List(revision, 0))
    }
    told = now
  }

  // the `on` of each ticked entry followed, so a tick changed elsewhere shows in the menu
  private def watch(): Unit = {
    watching.foreach(_())
    watching = items.toList.flatMap(_.on.toList).map { v =>
      val r = v.attach(_ => set(current))
      () => v.reactions -= r
    }
  }

  private def current: List[MenuItem] = TrayMenu.rebuild(items, children)

  private def props(id: Int): Map[String, DBusVariant] = {
    if (id == 0) Map("children-display" -> DBusVariant("s", "submenu"))
    else {
      val m = items(id - 1)
      if (m.separator) Map("type" -> DBusVariant("s", "separator"))
      else {
        var p = Map[String, DBusVariant](
          // an underscore marks a mnemonic to DBusMenu: one of the app's own is doubled
          "label" -> DBusVariant("s", m.label.replace("_", "__")),
          "enabled" -> DBusVariant("b", m.enabled),
          "visible" -> DBusVariant("b", true))
        if (m.checked.nonEmpty || m.on.nonEmpty) {
          p = p + ("toggle-type" -> DBusVariant("s", "checkmark")) +
            ("toggle-state" -> DBusVariant("i", if (m.ticked) 1 else 0))
        }
        if (m.submenu.nonEmpty) p = p + ("children-display" -> DBusVariant("s", "submenu"))
        p
      }
    }
  }

  private def chosen(names: Seq[String], id: Int): Vector[(String, DBusVariant)] = {
    val p = props(id)
    (if (names.isEmpty) p else p.filter(e => names.contains(e._1))).toVector
  }

  private def layout(id: Int, depth: Int, names: Seq[String]): DBusStruct = {
    val kids = if (depth == 0) Vector.empty else children.getOrElse(id, Vector.empty)
      .map(k => DBusVariant("(ia{sv}av)", layout(k, depth - 1, names)))
    DBusStruct(List(id, chosen(names, id), kids))
  }

  /** What the panel asks of the menu, answered. */
  def answer(m: DBusMessage): Option[(String, List[Any])] = (m.interface, m.member) match {
    case (TrayMenu.Interface, "GetLayout") =>
      val parent = m.body.head.asInstanceOf[Int]
      val depth = m.body(1).asInstanceOf[Int]
      val names = m.body(2).asInstanceOf[Vector[String]]
      Some(("u(ia{sv}av)", List(revision, layout(parent, if (depth < 0) Int.MaxValue else depth, names))))
    case (TrayMenu.Interface, "GetGroupProperties") =>
      val ids = m.body.head.asInstanceOf[Vector[Int]]
      val names = m.body(1).asInstanceOf[Vector[String]]
      val known = (if (ids.isEmpty) 0 to items.size else ids).filter(id => id >= 0 && id <= items.size)
      Some(("a(ia{sv})", List(known.map(id => DBusStruct(List(id, chosen(names, id)))).toVector)))
    case (TrayMenu.Interface, "GetProperty") =>
      val id = m.body.head.asInstanceOf[Int]
      val name = m.body(1).toString
      props(id).get(name).map(v => ("v", List(v)))
    case (TrayMenu.Interface, "Event") =>
      event(m.body.head.asInstanceOf[Int], m.body(1).toString)
      Some(("", Nil))
    case (TrayMenu.Interface, "EventGroup") =>
      val events = m.body.head.asInstanceOf[Vector[DBusStruct]]
      events.foreach(e => event(e.fields.head.asInstanceOf[Int], e.fields(1).toString))
      Some(("ai", List(Vector.empty[Int])))
    case (TrayMenu.Interface, "AboutToShow") => Some(("b", List(false)))
    case (TrayMenu.Interface, "AboutToShowGroup") => Some(("aiai", List(Vector.empty[Int], Vector.empty[Int])))
    case (TrayMenu.Properties, "Get") => property(m.body(1).toString).map(v => ("v", List(v)))
    case (TrayMenu.Properties, "GetAll") =>
      Some(("a{sv}", List(TrayMenu.properties.flatMap(n => property(n).map(n -> _)).toVector)))
    case (TrayMenu.Introspectable, "Introspect") => Some(("s", List(TrayMenu.introspection)))
    case _ => None
  }

  private def property(name: String): Option[DBusVariant] = name match {
    case "Version" => Some(DBusVariant("u", 3))
    case "TextDirection" => Some(DBusVariant("s", if (rtl) "rtl" else "ltr"))
    case "Status" => Some(DBusVariant("s", "normal"))
    case "IconThemePath" => Some(DBusVariant("as", Vector.empty[String]))
    case _ => None
  }

  // an entry clicked: its action, where it does one
  private def event(id: Int, kind: String): Unit =
    if (kind == "clicked" && id >= 1 && id <= items.size) {
      val m = items(id - 1)
      if (m.choosable && m.submenu.isEmpty) m.action()
    }
}

object TrayMenu {
  val Path: String = "/MenuBar"
  val Interface: String = "com.canonical.dbusmenu"
  val Properties: String = "org.freedesktop.DBus.Properties"
  val Introspectable: String = "org.freedesktop.DBus.Introspectable"
  private val properties: List[String] = List("Version", "TextDirection", "Status", "IconThemePath")

  /** `entries` numbered in order, each submenu's after its entry: the entries by id - 1, and each id's children. */
  def flatten(entries: List[MenuItem]): (Vector[MenuItem], Map[Int, Vector[Int]]) = {
    var flat = Vector.empty[MenuItem]
    var kids = Map.empty[Int, Vector[Int]]
    def add(parent: Int, list: List[MenuItem]): Unit = {
      var mine = Vector.empty[Int]
      list.foreach { m =>
        flat = flat :+ m
        val id = flat.size
        mine = mine :+ id
        if (m.submenu.nonEmpty) add(id, m.submenu)
      }
      kids = kids + (parent -> mine)
    }
    add(0, entries)
    (flat, kids)
  }

  // the entries back from their numbering, with each one's submenu as it is now
  private def rebuild(flat: Vector[MenuItem], kids: Map[Int, Vector[Int]]): List[MenuItem] = {
    def under(id: Int): List[MenuItem] = kids.getOrElse(id, Vector.empty).toList.map { k =>
      val m = flat(k - 1)
      if (m.submenu.nonEmpty) m.copy(submenu = under(k)) else m
    }
    under(0)
  }

  private val introspection: String =
    """<!DOCTYPE node PUBLIC "-//freedesktop//DTD D-BUS Object Introspection 1.0//EN"
      | "http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd">
      |<node>
      | <interface name="com.canonical.dbusmenu">
      |  <property name="Version" type="u" access="read"/>
      |  <property name="TextDirection" type="s" access="read"/>
      |  <property name="Status" type="s" access="read"/>
      |  <property name="IconThemePath" type="as" access="read"/>
      |  <method name="GetLayout"><arg type="i" direction="in"/><arg type="i" direction="in"/>
      |   <arg type="as" direction="in"/><arg type="u" direction="out"/><arg type="(ia{sv}av)" direction="out"/>
      |  </method>
      |  <method name="GetGroupProperties"><arg type="ai" direction="in"/><arg type="as" direction="in"/>
      |   <arg type="a(ia{sv})" direction="out"/></method>
      |  <method name="GetProperty"><arg type="i" direction="in"/><arg type="s" direction="in"/>
      |   <arg type="v" direction="out"/></method>
      |  <method name="Event"><arg type="i" direction="in"/><arg type="s" direction="in"/><arg type="v" direction="in"/>
      |   <arg type="u" direction="in"/></method>
      |  <method name="EventGroup"><arg type="a(isvu)" direction="in"/><arg type="ai" direction="out"/></method>
      |  <method name="AboutToShow"><arg type="i" direction="in"/><arg type="b" direction="out"/></method>
      |  <method name="AboutToShowGroup"><arg type="ai" direction="in"/><arg type="ai" direction="out"/>
      |   <arg type="ai" direction="out"/></method>
      |  <signal name="ItemsPropertiesUpdated"><arg type="a(ia{sv})"/><arg type="a(ias)"/></signal>
      |  <signal name="LayoutUpdated"><arg type="u"/><arg type="i"/></signal>
      |  <signal name="ItemActivationRequested"><arg type="i"/><arg type="u"/></signal>
      | </interface>
      |</node>""".stripMargin
}

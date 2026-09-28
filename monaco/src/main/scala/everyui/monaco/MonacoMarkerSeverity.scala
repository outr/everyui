package everyui.monaco

/** How serious a marker is: Monaco's `MarkerSeverity`. */
enum MonacoMarkerSeverity(val wire: Int) {
  case Hint extends MonacoMarkerSeverity(1)
  case Info extends MonacoMarkerSeverity(2)
  case Warning extends MonacoMarkerSeverity(4)
  case Error extends MonacoMarkerSeverity(8)
}

object MonacoMarkerSeverity {
  def fromWire(value: Int): MonacoMarkerSeverity = values.find(_.wire == value).getOrElse(Info)
}

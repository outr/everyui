package everyui

/** A page of the platform's settings the app can open (`SystemSettings.open`): the app's own page; its
  * notifications; its location permission; the switch that lets the platform take away its permissions and stop its
  * background work after months unused; the device's location switch. */
enum SettingsPage {
  case App, AppNotifications, AppLocation, UnusedAppRestrictions, DeviceLocation
}

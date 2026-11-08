package everyui.background

/** The network scheduled work waits for: none, any connection, or one that is not metered (Wi-Fi, usually). */
enum WorkNetwork {
  case NotNeeded, Connected, Unmetered
}

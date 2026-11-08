package everyui.background

/** What must hold before scheduled work runs: the network it needs, the device charging, its battery not low, the
  * device idle (not in use for a while). Work waits until they all hold, and is stopped (and run again later) where
  * one stops holding while it runs. */
case class WorkConstraints(network: WorkNetwork = WorkNetwork.NotNeeded,
                           charging: Boolean = false,
                           batteryNotLow: Boolean = false,
                           idle: Boolean = false)

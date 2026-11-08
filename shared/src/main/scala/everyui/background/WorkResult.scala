package everyui.background

/** What a run of scheduled work came to (`BackgroundApp.onWork`): done; to be run again after its backoff (a server
  * that did not answer); or failed, and not run again (work scheduled once) or not until its next time (periodic). */
enum WorkResult {
  case Done, Retry, Failed
}

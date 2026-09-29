package everyui.chat

/** Where a tool call (`ChatEntry.Activity`) stands: under way, done, or gone wrong. */
enum ActivityStatus {
  case Running, Succeeded, Failed
}

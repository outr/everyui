package everyui.sbt

/** How far an EveryUI web app is linked (the sbt setting `everyuiWebStage`), for its JavaScript build and its
  * WebAssembly build alike. */
enum WebStage {
  /** `fullLinkJS`: the optimizer, optimized semantics and Scala.js's minifier (`everyuiWebMinify`). A release. */
  case Full
  /** `fastLinkJS`: links in a fraction of the time, into a larger, slower and unminified build. For
    * development, never a release. */
  case Fast
}

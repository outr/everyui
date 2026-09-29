// Scala.js for the published web backend and the network module's browser build (`web`, `networkJs`)
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")

// ASM for project/AndroidRuntime.scala, which rewrites Scala 3's VarHandle lazy vals for Android before API 33
libraryDependencies += "org.ow2.asm" % "asm-tree" % "9.10.1"

// Scala.js for the published web backend and the network module's browser build (`web`, `networkJs`)
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")

// The sbt-everyui plugin, from its sources: this build packages its own apps with the helpers it publishes
// (AndroidApk, AndroidRuntime, Aars, RokuChannel, AppleApp), rather than with a copy of them
Compile / unmanagedSourceDirectories ++= Seq("main", "plugin")
  .map(d => baseDirectory.value.getParentFile / "sbt-support" / "src" / d / "scala")

// ASM for AndroidRuntime, which rewrites Scala 3's VarHandle lazy vals for Android before API 33
libraryDependencies += "org.ow2.asm" % "asm-tree" % "9.10.1"

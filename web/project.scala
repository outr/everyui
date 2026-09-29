//> using scala 3.10.0-RC3
//> using platform js
//> using jsVersion 1.22.0
//> using jsModuleKind none
//> using jsEsVersionStr es2021
//> using dep org.scala-js::scalajs-dom::2.8.1
//> using dep com.outr::spice-client::1.10.9-SNAPSHOT4
// spice-core_sjs1 as published locally lists its dependencies by their JVM names, so the Scala.js builds of them
// are named here until spice publishes them right (reactify comes from source, below)
//> using dep org.typelevel::fabric-io::1.30.2-SNAPSHOT
//> using dep com.outr::rapid-core::2.9.9
//> using dep com.outr::scribe::3.19.1-SNAPSHOT
//> using dep com.outr::profig::3.7.1
// UUID.randomUUID (the durable client id) needs SecureRandom, which Scala.js keeps in its own library
//> using dep org.scala-js:scalajs-java-securerandom_sjs1_2.13:1.0.0
//> using file ../shared/src/main/scala
//> using file ../app/src/main/scala
//> using file ../app/full/src/main/scala
//> using file ../app/protocol/src/main/scala
//> using file ../network/src/main/scala
//> using file ../network/spice/src/main/scala
//> using file ../network/js/src/main/scala
//> using file ../../reactify/reactify/src/main/scala
//> using mainClass everyui.app.main

package universal.app

/** The single entry point both backends emit.
  *
  * Flip `Demo` to choose what runs; the Roku emitter collects `@main` and
  * calls it from the generated scene, and Scala.js links it as the module
  * initializer, so there can only be one.
  */
@main def main(): Unit =
  // "animation" — youi's AnimationExample: 31 easing curves + a spinning,
  //               bouncing image. The comparison demo.
  // "hello"     — the original seam proof plus the frame-rate probe.
  val demo = "animation"
  if demo == "hello" then HelloWorld.run()
  else if demo == "bench" then ReactifyBench.run()
  else if demo == "easings" then EasingTable.run()
  else AnimationExample.run()

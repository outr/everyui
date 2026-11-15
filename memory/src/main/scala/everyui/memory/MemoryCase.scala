package everyui.memory

/** What a memory check measures: `name`'s cost per `unit`, as the difference between the rig with `step` units and with
  * twice as many, so what the first of them sets up once (a font's measures, a theme's colours) is left out. */
final case class MemoryCase(name: String, unit: String, step: Int, rig: () => MemoryRig)

package reactifytest.lib

/** A library's class extending another and mixing in a trait, as everyui's Container is a Component (Kinds). */
class Pane(name: String) extends Widget(name) with Holder

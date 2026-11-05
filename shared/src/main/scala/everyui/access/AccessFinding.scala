package everyui.access

import everyui.component.Component

/** One thing `Access` found: which rule `component` breaks, how (`detail`), and where it is (`box`: left, top, width
  * by height on the screen). Its text is what `Access.warn` reports:
  * {{{
  *   contrast: TextView(Sent) (24,180 31x16) 2.9:1, #6b6b6b on #2a2a2a, under 4.5:1 in Row < ChatRow < VirtualList
  * }}} */
case class AccessFinding(rule: AccessRule, component: Component, detail: String, box: String) {
  override def toString: String = rule.id + ": " + component.kind + " " + box + " " + detail + " in " +
    component.parent().map(Component.lineage).getOrElse("nothing")
}

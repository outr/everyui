package everyui.list

/** How a `RichList` draws its sections. `Tree`, the default, as a tree: a header with a chevron before it over its
  * items, a group's header a level above its sections' and theirs set in under it. `Blocks` as a sidebar's panels: a
  * group's title a small caption over its sections, and each section a softly tinted block with rounded corners, its
  * header a bar across the top that opens and closes it (a small ▾ or ▸ at its end), its items under it, nothing set
  * in, and a gap between one block and the next. */
enum RichStyle {
  case Tree, Blocks
}

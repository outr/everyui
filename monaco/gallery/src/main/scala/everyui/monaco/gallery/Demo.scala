package everyui.monaco.gallery

import everyui.font.MaterialIcon

/** One page of the gallery: its name, what it shows, its icon, and how it is made. */
case class Demo(label: String, blurb: String, icon: MaterialIcon, make: Demo => DemoPage)

object Demo {
  lazy val all: List[Demo] = List(
    Demo("Basic", "Minimal editor, with read-only and theme toggles.", MaterialIcon.Article, BasicDemo(_)),
    Demo("Languages", "Syntax highlighting switched between Dart, JS, TS, Python, JSON, HTML, Markdown, SQL, YAML, CSS.",
      MaterialIcon.Translate, LanguagesDemo(_)),
    Demo("Options", "Live toggles for font size, word wrap, minimap, line numbers, cursor style.", MaterialIcon.Tune, OptionsDemo(_)),
    Demo("Diagnostics", "Error, warning and info markers, and inline and gutter decorations.", MaterialIcon.ErrorOutline,
      DiagnosticsDemo(_)),
    Demo("Actions & Commands", "Actions with keybindings and context-menu entries, and bare commands.", MaterialIcon.Keyboard,
      ActionsDemo(_)),
    Demo("IntelliSense", "Completion and hover worked out by the app, answered asynchronously.", MaterialIcon.AutoAwesome,
      IntelliSenseDemo(_)),
    Demo("Diff Editor", "The original against the modified, side by side or inline.", MaterialIcon.CompareArrows, DiffDemo(_)),
    Demo("Custom Themes", "Themes defined at runtime, one of them the app's own palette, switched globally.",
      MaterialIcon.Palette, ThemesDemo(_)),
    Demo("Custom Background", "The editor over the app's own ground, with a clear theme.", MaterialIcon.Wallpaper,
      BackgroundDemo(_)),
    Demo("Partial Commit", "Checkboxes on each change and line of a diff, and a change rolled back.", MaterialIcon.Checklist,
      PartialCommitDemo(_)),
    Demo("Files & Navigation", "A model per open file, dirty state and Save, definitions, references, symbols, blame.",
      MaterialIcon.FolderOpen, NavigationDemo(_)))
}

package everyui.perf

/** How much the app draws and moves (`Quality.level`): everything at Full; at Reduced, shorter animations and glides,
  * no shadows or blur, smaller pictures and less built ahead; at Minimal, glides that jump, animations shorter again, no
  * motion of the platform's own and the least built ahead. Each is one step down from the last. */
enum QualityLevel {
  case Full, Reduced, Minimal
}

package everyui.sbt

import java.io.File

/** An Android archive's resources as the APK links them (`AndroidApk.build`'s `libraries`): its name, the package its
  * R class is in (its manifest's `package`), and its `res` directory, opened out of it (Aars.resources). */
case class AarResources(name: String, packageName: String, res: File)

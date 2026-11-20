package everyui.render

/** What an LG TV says it shows and plays beyond what its engine is asked (`Platform.decodes`): HDR10, HLG, Dolby Vision,
  * Dolby Atmos and a UHD panel, read as plain Scala (so the desktop's specs read it as the TV does) from what
  * `WebOSTv` asks:
  *
  *  - webOSTV.js's `webOS.deviceInfo`, where the app bundles it: `hdr10`, `dolbyVision`, `uhd`, `uhd8K` (and
  *    `dolbyAtmos` where its version has it), booleans;
  *  - else the configuration service itself, as webOSTV.js 1.2 asks it (`luna://com.webos.service.config`,
  *    `getConfigs`): `tv.model.supportHDR` (HDR10), `tv.config.supportDolbyHDRContents` (Dolby Vision),
  *    `tv.hw.panelResolution` ("UD" or "8K" is UHD), `tv.config.supportDolbyTVATMOS` or a `tv.model.soundModeType` of
  *    "Dolby Atmos" (Atmos);
  *  - and the runtime's own `deviceInfo` string, where it carries any of the same names.
  *
  * LG names no HLG flag: an LG set that takes HDR10 has taken HLG too since 2017's models, so HLG is read as HDR10;
  * and LG has never made a set that shows HDR10+, so wherever the TV says anything of HDR, HDR10+ is a no. A
  * feature not said is not known (absent), never a no. */
object WebOSDisplay {
  val configService: String = "luna://com.webos.service.config"

  /** The `getConfigs` parameters. */
  val configNames: String = "{\"configNames\":[\"tv.model.supportHDR\",\"tv.config.supportDolbyHDRContents\"," +
    "\"tv.hw.panelResolution\",\"tv.config.supportDolbyTVATMOS\",\"tv.model.soundModeType\"]}"

  /** The features webOS.deviceInfo's object (or the runtime's `deviceInfo` string) says, by EveryUI's names: "hdr10",
    * "hlg", "dolbyvision", "atmos", "uhd". */
  def fromDeviceInfo(json: String): Map[String, Boolean] = {
    val info = LaunchParams.parse(json)
    def flag(name: String): Option[Boolean] = info.get(name).flatMap(v =>
      if (v == "true") Some(true) else if (v == "false") Some(false) else None)
    val hdr = flag("hdr10")
    List("hdr10" -> hdr, "hlg" -> hdr, "hdr10plus" -> hdr.map(_ => false), "dolbyvision" -> flag("dolbyVision"),
      "atmos" -> flag("dolbyAtmos"),
      "uhd" -> flag("uhd").orElse(flag("UHD")).orElse(flag("uhd8K").filter(identity)))
      .collect { case (name, Some(on)) => name -> on }.toMap
  }

  /** The features a `getConfigs` reply says; nothing where it failed or holds no `configs`. */
  def fromConfigs(reply: String): Map[String, Boolean] = {
    val answer = LaunchParams.parse(reply)
    if (answer.get("returnValue").contains("false")) Map.empty
    else answer.get("configs").map(LaunchParams.parse).fold(Map.empty[String, Boolean]) { c =>
      def bool(name: String): Option[Boolean] = c.get(name).map(_ == "true")
      val hdr = bool("tv.model.supportHDR")
      val atmos = bool("tv.config.supportDolbyTVATMOS").map(_ || c.get("tv.model.soundModeType").contains("Dolby Atmos"))
        .orElse(c.get("tv.model.soundModeType").map(_ == "Dolby Atmos"))
      List("hdr10" -> hdr, "hlg" -> hdr, "hdr10plus" -> hdr.map(_ => false),
        "dolbyvision" -> bool("tv.config.supportDolbyHDRContents"),
        "atmos" -> atmos, "uhd" -> c.get("tv.hw.panelResolution").map(r => r == "UD" || r == "8K"))
        .collect { case (name, Some(on)) => name -> on }.toMap
    }
  }
}

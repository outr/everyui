package everyui.app

import everyui.Color
import everyui.component.{Button, ButtonStyle, Component}
import everyui.font.MaterialIcon
import everyui.menu.{Menu, MenuItem, MenuPicture}
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui
import reactify.*

/** A menu whose entries lead with different things, for checking their words line up: letter tiles, a large and a
  * small picture (each drawn at the picture's size whatever its own), a picture that will not load and stands in by
  * its letter, icons, a tick and an entry with nothing before it, most with a line of detail under them. The menu is
  * dropped from its button as the screen opens and again at each click on it;  for the light palette,
  * `rtl=1` for right to left. Logs `menus ok` once built and each entry chosen. */
object MenusDemo {
  // a 96 x 96 logo and a 16 x 16 one, as PNGs: a picture far larger and one smaller than the slot it is drawn in
  private val large: String = "data:image/png;base64," +
    "iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAMAAADVRocKAAAAKlBMVEUAvNQGttYKs9cgoN8hn98uk+T///+bt/VDgutrnO/N2/pLee5m" +
    "e/M9oeW+35I1AAADj0lEQVRo3s2a2W7tIAxFm4k4of3/373K7BnDyZGupbZve2XbmBKTn5//MbqviLJ4Xbzv+37/dcZrkON5eyVeMWJo" +
    "v+WjJP8hIiL/ASOqfjGq9aXIsMf5Z/iMwLMzWEHy1JaeoRANJujjK5qjDoiaMB5/ZIERQ40JpO/IUwTyUSQ8+k9CbslpmibLRZBw69/L" +
    "8ZLe1XFQSrDUjz7JzaRGC0EAHP0HwtLUlfVZdtyQJkwLvL66emonUIDx9HOa9VqUCWz96/qQUgI7TV4ZWIKM7M8pSQsnomCBFtgo7pK2" +
    "WGyAQ+jc5wfYfmDdASuAhqCF9jpAkYcMAHAYSGmBPbgHl9CxBUQBOWeAnNMVOR9EsAmdasAoQD5iuQFLztIEJbAquBWGE5ASsrCZyNyD" +
    "acEzsBVgjxkB5pOZIWYBA4j+nodTKuEAjyAAuAd4fi4hYsCwMJF2QwQbAPeTLonGcgEgAiA9oOU/55UB1qwQaD93RQNgG3gs5LKFAyD2" +
    "CED6Ocl4LDwm9DLrgG17MCrM64xMqDlCixRn6JHPv0mL3yyzRFtBOKAZyq4BZCHnSbXQaTVWtgjDwGOBLiRRZSNDKEWrAVhlis4yawDe" +
    "Bd4SFd2mL1ThwCjBagJWmSMJsNosYEDrtpHkyKsxFCrMuk0F9HuKeg0AN2B2ATNP0iir3LkA38C5VCEAuP6b8QzNBcAsWnnke4ULWFIp" +
    "lhqAXEVrEbBqgD4KKBs4LBDAyB0YW4Xxb0Bdql6jeYCIgeMYFgD0ymYX0t/OMI2A0hK9lypUAPB2GtTHLz1Kn+FWpufqqAH80qMeK/DJ" +
    "FwFiFT7r7GSIA25CuceeWL0Mse1ubDBwWyAvg+LoSM8VVfopSf1ePZveFuoMnBaMDPE3wBYDuwXj7Ku+gMSX6BUzW6MmYCf8Veun9Dfa" +
    "APESWLNEr1i9cQKz0GJgs+BME6iFFgMprdE3/aF2iV6xuMMKRGjUT8mcJMh5oD6OkjMQusc5BhghMA1k+rTH/JlaZJ4p9IkBAyCSFESg" +
    "IbOn30oYw/pNBG267I1+e0KITZZrhtfi8gAP9l/QryOQO4SgPrneeq4f8KXEhC8phmp9MWFWEMoVS4U+mTFbLqwbnPA1lHXNRVRv8ao7" +
    "KFHqb1zUcRMB7Yr8aCYwpte06+Vrr3sbL5QrLqzr5SsYn97qf/WrgYthf/bwzscVDNG99OiKkW99enIxviH6cfwDGjtzU2YDUE8AAAAA"
  private val small: String = "data:image/png;base64," +
    "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAMAAAAoLQ9TAAAAFVBMVEX/cEP/dEj/por/5Nv/////gVn/5NygdgKVAAAAQUlEQVQY052O" +
    "SQ4AIAgDWQr/f7IxCJoQPTi3DgRK9AmLQoUrGwLLOZK1IyUkhJbQENiE8Mp+udG+tB6zqcOPpk8GxxgBlTBeTYQAAAAASUVORK5CYII="

  def items(chose: String => Unit): List[MenuItem] = List(
    MenuItem("Build finished · storefront", () => chose("storefront"), detail = "Deployed 2.0.0 and every test passed",
      picture = Some(MenuPicture.letter("S", Color.fromHex("5c6bc0")))),
    MenuItem("Importer", () => chose("importer"), detail = "Working: one job in the background",
      picture = Some(MenuPicture.letter("D", Color.fromHex("ab47bc")))),
    MenuItem.separator,
    MenuItem("Release finished · Library", () => chose("library"), detail = "The release is staged and signed",
      picture = Some(MenuPicture.image(large, Some(MenuPicture.letter("L", Color.fromHex("26a69a")))))),
    MenuItem("Waiting for you · Garden", () => chose("garden"), detail = "Shall I write a short reply?",
      picture = Some(MenuPicture.image(small))),
    MenuItem("Notes", () => chose("notes"), detail = "A picture that will not load, its letter standing in",
      picture = Some(MenuPicture.image("data:image/png;base64,AAAA", Some(MenuPicture.letter("M",
        Color.fromHex("4db6ac")))))),
    MenuItem("Agent could not finish", () => chose("failed"), MaterialIcon.ErrorOutline,
      detail = "The batch was refused by the database"),
    MenuItem("Assistant", () => chose("assistant"), MaterialIcon.Chat, detail = "A reply is waiting"),
    MenuItem("Plain entry", () => chose("plain")),
    MenuItem.separator,
    MenuItem("Mark all read", () => chose("read"), MaterialIcon.DoneAll),
    MenuItem("Show the read ones", () => chose("shown"), checked = Some(true)),
    MenuItem("Settings…", () => chose("settings"), MaterialIcon.Settings))

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    if (Platform.launchArg("rtl") == "1") everyui.i18n.Direction.set(true)
    ui.backgroundColor := Theme.background()
    val anchor: Var[Option[Component]] = Var(None)
    def open(): Unit = anchor().foreach(a => Menu.below(a, items(what => Platform.log("menus chose " + what))))
    val button = new Button("Notifications", () => open(), ButtonStyle.Outlined)
    anchor @= Some(button)
    button.position.left @= 24.0
    button.position.top @= 24.0
    ui.children += button
    open()
    Platform.log("menus ok")
  }
}

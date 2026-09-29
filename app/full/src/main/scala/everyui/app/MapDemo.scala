package everyui.app

import everyui.app.screen.ScreenManager
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui

/** A map (MapView) over a patch of central Oklahoma: 300 sections of a land survey as polygons in eight colours, each
  * with its number once the map is close enough, a county line dashed round them, a road as a line, five pins that
  * never cluster and a thousand dots that do. Hover a section or a marker for its name; a click on one says what it
  * is, and a click on a cluster glides in to it. The bar fits it all in again, turns the clustering off and on, and
  * the scale bar's units between metres and feet; the legend in the corner says what the colours are.
  *
  * The tiles are drawn here by default (`MapTiles.drawn`, pictures made in the page, so the demo asks nothing of any
  * tile server); `tiles=<template>` takes them from a server (a local one: `tiles=http://localhost:8123/{z}/{x}/{y}.png`),
  * and `tiles=osm` from OpenStreetMap's own (for a look by hand only: its tile usage policy forbids more).
  * `theme=light` shows the light palette, `markers=<n>` and `sections=<n>` change how many there are, and `cluster=0`
  * starts with the markers drawn one by one. Logs
  * `map ok <shapes> shapes, <markers> markers, zoom <z>` once built, `map clicked <what> <key>` for each click, and with
  * `trace=1` `map view zoom <z> at <lat>, <lng>` as the view moves (`MapScreen`). */
object MapDemo extends ScreenManager {
  lazy val screen: MapScreen = new MapScreen

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    run(screen)
  }
}

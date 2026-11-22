package com.cityfix.safenight.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * CartoDB "dark_matter" raster basemap.
 *
 * Why not the default OSM Mapnik source: OSM's raster is a bright daylight map,
 * so it needed a violent colour matrix to look like night — and when its tile
 * host is slow or blocked the map just stays black. Carto's dark basemap is
 * already night-coloured and is served over HTTPS from four CDN subdomains, with
 * no API key. If the CDN is unreachable the app falls back to OSM Mapnik and
 * re-applies the night matrix (see NightMapView).
 */
val CartoDark: OnlineTileSourceBase = object : OnlineTileSourceBase(
    "CartoDark",
    0,
    20,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        getBaseUrl() +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + ".png"
}

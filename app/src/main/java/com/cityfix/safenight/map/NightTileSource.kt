package com.cityfix.safenight.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * Esri "World Dark Gray" canvas — a genuinely dark, label-free basemap.
 *
 * Only used when the OSM tile host cannot be reached. The default OSM raster is
 * a bright daylight map that needs the night [android.graphics.ColorMatrix],
 * while this source is already dark, so it can replace OSM at runtime without
 * the filter. Note the ArcGIS path order is zoom / y / x.
 */
val EsriDarkGray: OnlineTileSourceBase = object : OnlineTileSourceBase(
    "EsriDarkGray",
    0,
    19,
    256,
    ".img",
    arrayOf(
        "https://services.arcgisonline.com/ArcGIS/rest/services/" +
            "Canvas/World_Dark_Gray_Base/MapServer/tile/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        getBaseUrl() +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex)
}

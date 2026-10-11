package com.amerthamer.tramagent.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector

/** Az app funkciói (DK1–DK5 + GTFS) a főképernyő csempéihez és a navigációhoz. */
enum class Feature(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    VehicleLookup("dk1", "Pályaszám kereső", "Forgalmi szám, rendszám", Icons.Rounded.Tag),
    Alerts("dk2", "Aktív zavarok", "GTFS-RT riasztások", Icons.Rounded.WarningAmber),
    Nearby("dk3", "Közeli indulások", "300 m-en belül", Icons.Rounded.NearMe),
    MapSearch("dk4", "Térképes kereső", "Jármű a térképen", Icons.Rounded.Map),
    Tracking("dk5", "Nyomkövetés", "Menetrend és késés megállónként", Icons.Rounded.Timeline),
    Gtfs("gtfs", "GTFS frissítés", "Menetrendi adatok letöltése", Icons.Rounded.CloudDownload),
}

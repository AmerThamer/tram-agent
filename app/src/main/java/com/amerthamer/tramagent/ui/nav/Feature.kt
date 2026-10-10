package com.amerthamer.tramagent.ui.nav

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.amerthamer.tramagent.ui.Dk1Activity
import com.amerthamer.tramagent.ui.Dk2Activity
import com.amerthamer.tramagent.ui.Dk3aActivity
import com.amerthamer.tramagent.ui.Dk4Activity
import com.amerthamer.tramagent.ui.Dk5Activity
import com.amerthamer.tramagent.ui.GtfsActivity

/**
 * Az app funkciói (DK1–DK5 + GTFS).
 *
 * [legacyActivity]: amíg a képernyő nincs átírva Compose-ra, a régi Activity nyílik meg.
 * Ha egy funkció átkerül Compose-ra, a [legacyActivity] null lesz, és a [route] útvonalra navigálunk.
 */
enum class Feature(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val legacyActivity: Class<out Activity>?,
) {
    VehicleLookup("dk1", "Pályaszám kereső", "Forgalmi szám, rendszám", Icons.Rounded.Tag, Dk1Activity::class.java),
    Alerts("dk2", "Aktív zavarok", "GTFS-RT riasztások", Icons.Rounded.WarningAmber, Dk2Activity::class.java),
    Nearby("dk3", "Közeli indulások", "300 m-en belül", Icons.Rounded.NearMe, Dk3aActivity::class.java),
    MapSearch("dk4", "Térképes kereső", "Jármű a térképen", Icons.Rounded.Map, Dk4Activity::class.java),
    Tracking("dk5", "Nyomkövetés", "Menetrend és késés megállónként", Icons.Rounded.Timeline, Dk5Activity::class.java),
    Gtfs("gtfs", "GTFS frissítés", "Menetrendi adatok letöltése", Icons.Rounded.CloudDownload, GtfsActivity::class.java),
}

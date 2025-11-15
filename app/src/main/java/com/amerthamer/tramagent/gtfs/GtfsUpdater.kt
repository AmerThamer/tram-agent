package com.amerthamer.tramagent.gtfs

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

/**
 * GtfsUpdater
 *
 * Feladata: a BKK statikus GTFS állományainak letöltése és kicsomagolása
 * az alkalmazás belső tárhelyére.
 *
 * A GTFS (General Transit Feed Specification) tartalmazza többek között:
 * - megállók (stops.txt)
 * - viszonylatok (routes.txt)
 * - trip-ek (trips.txt)
 * - megállóidők (stop_times.txt)
 *
 * A FUTÁR API bizonyos funkcióihoz (például DK4 és DK5) szükség van a
 * statikus GTFS adatállományokra, hogy megkapjuk a megállók nevét és koordinátáit.
 *
 * A frissítés:
 * 1) letölti a teljes budapest_gtfs.zip fájlt a go.bkk.hu szerverről
 * 2) kicsomagolja belső tárhelyre (filesDir/gtfs/)
 * 3) kilistázza a letöltött állományokat
 */
object GtfsUpdater {

    // Egyszerű OkHttp kliens – nincs logolás, nincs timeout módosítás
    private val client = OkHttpClient()

    /**
     * Publikus hívás: GTFS frissítése az alkalmazás belső tárhelyére.
     * (filesDir/gtfs könyvtár)
     */
    suspend fun update(context: Context): String {
        val dstDir = File(context.filesDir, "gtfs")
        return update(dstDir)
    }

    /**
     * A GTFS zip letöltése és kicsomagolása a megadott könyvtárba.
     *
     * @param dstDir a célkönyvtár (például filesDir/gtfs)
     * @return státusz üzenet (UI-n megjeleníthető)
     */
    suspend fun update(dstDir: File): String = withContext(Dispatchers.IO) {

        // BKK statikus GTFS letöltési URL
        val url = "https://go.bkk.hu/api/static/v1/public-gtfs/budapest_gtfs.zip"
        val req = Request.Builder().url(url).get().build()

        // Ha nincs GTFS könyvtár, létrehozzuk
        if (!dstDir.exists()) {
            if (dstDir.mkdirs()) {
                Log.d("GTFS", "Mappa létrehozva: ${dstDir.absolutePath}")
            } else {
                Log.e("GTFS", "Nem sikerült létrehozni a mappát: ${dstDir.absolutePath}")
                return@withContext "❌ Nem sikerült létrehozni a célmappát."
            }
        }

        // ZIP letöltése
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext "❌ Hiba: HTTP ${resp.code}"

            /**
             * A ZipInputStream lehetővé teszi, hogy a ZIP fájlt streamelve dolgozzuk fel,
             * anélkül hogy egyben memóriába kellene tölteni.
             */
            ZipInputStream(resp.body!!.byteStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(dstDir, entry.name)

                    if (entry.isDirectory) {
                        // Mappák létrehozása
                        outFile.mkdirs()
                    } else {
                        // Fájl írása
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                        Log.d(
                            "GTFS",
                            "Mentve: ${outFile.absolutePath} (${outFile.length()} bájt)"
                        )
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        // A könyvtár tartalmának kilistázása (diagnosztikai célra)
        val files = dstDir.walkTopDown().filter { it.isFile }.toList()
        Log.d("GTFS", "Összes fájl (${files.size} db):")
        files.forEach {
            Log.d("GTFS", "${it.name} - ${it.length()} bájt")
        }

        // UI-n megjeleníthető siker üzenet
        "✅ GTFS frissítés kész, ${files.size} fájl létrehozva."
    }
}

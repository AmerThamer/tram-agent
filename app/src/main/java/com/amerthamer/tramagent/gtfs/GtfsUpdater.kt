package com.amerthamer.tramagent.gtfs

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
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

    private const val URL = "https://go.bkk.hu/api/static/v1/public-gtfs/budapest_gtfs.zip"

    // Egyszerű OkHttp kliens – nincs logolás, nincs timeout módosítás
    private val client = OkHttpClient()

    /** A kicsomagolt állományok listája (név, méret bájtban). */
    data class UpdateResult(val files: List<Pair<String, Long>>)

    /** A GTFS könyvtár az app belső tárhelyén (filesDir/gtfs). */
    fun gtfsDir(context: Context) = File(context.filesDir, "gtfs")

    /**
     * Publikus hívás: GTFS frissítése az alkalmazás belső tárhelyére.
     * (filesDir/gtfs könyvtár)
     */
    suspend fun update(context: Context, onProgress: (Float?, Long) -> Unit = { _, _ -> }): UpdateResult =
        update(gtfsDir(context), onProgress)

    /**
     * A GTFS zip letöltése és kicsomagolása a megadott könyvtárba.
     *
     * Előbb egy ideiglenes mappába csomagol ki, és csak siker esetén cseréli le a régit,
     * így egy félbeszakadt letöltés nem hagy hiányos adatot maga után.
     *
     * @param onProgress letöltési arány 0..1 között (null, ha a szerver nem adja meg a méretet),
     *   valamint az eddig letöltött bájtok száma
     * @throws Exception hálózati vagy írási hiba esetén
     */
    suspend fun update(dstDir: File, onProgress: (Float?, Long) -> Unit = { _, _ -> }): UpdateResult = withContext(Dispatchers.IO) {
        val tmpDir = File(dstDir.parentFile, "gtfs_tmp").apply { deleteRecursively(); mkdirs() }
        val req = Request.Builder().url(URL).get().build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Üres válasz")
            val total = body.contentLength().takeIf { it > 0 }
            var read = 0L
            var lastReported = -1

            // Számláló stream: a tömörített bájtok alapján jelzi a haladást
            val counting = object : FilterInputStream(body.byteStream()) {
                private fun count(n: Int) {
                    if (n <= 0) return
                    read += n
                    // Ismert méretnél százalékonként, különben megabájtonként jelzünk
                    val step = if (total != null) (read * 100 / total).toInt() else (read / 1_000_000).toInt()
                    if (step != lastReported) {
                        lastReported = step
                        onProgress(total?.let { read.toFloat() / it }, read)
                    }
                }
                override fun read(): Int = super.read().also { if (it >= 0) count(1) }
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { count(it) }
            }
            onProgress(if (total == null) null else 0f, 0L)

            /**
             * A ZipInputStream lehetővé teszi, hogy a ZIP fájlt streamelve dolgozzuk fel,
             * anélkül hogy egyben memóriába kellene tölteni.
             */
            ZipInputStream(counting).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(tmpDir, entry.name)
                    // Zip slip védelem: csak a célmappán belülre írunk
                    if (!outFile.canonicalPath.startsWith(tmpDir.canonicalPath + File.separator)) {
                        throw IOException("Érvénytelen bejegyzés a ZIP-ben: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                        Log.d("GTFS", "Mentve: ${outFile.name} (${outFile.length()} bájt)")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        // Csere: régi törlése, új átnevezése
        dstDir.deleteRecursively()
        if (!tmpDir.renameTo(dstDir)) throw IOException("Nem sikerült a GTFS mappa cseréje")

        UpdateResult(
            dstDir.walkTopDown().filter { it.isFile }.map { it.name to it.length() }.sortedByDescending { it.second }.toList()
        )
    }
}

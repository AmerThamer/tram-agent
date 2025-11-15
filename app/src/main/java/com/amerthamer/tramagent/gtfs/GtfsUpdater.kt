package com.amerthamer.tramagent.gtfs

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

object GtfsUpdater {

    private val client = OkHttpClient()

    suspend fun update(context: Context): String {
        val dstDir = File(context.filesDir, "gtfs")
        return update(dstDir)
    }

    suspend fun update(dstDir: File): String = withContext(Dispatchers.IO) {
        val url = "https://go.bkk.hu/api/static/v1/public-gtfs/budapest_gtfs.zip"
        val req = Request.Builder().url(url).get().build()

        if (!dstDir.exists()) {
            if (dstDir.mkdirs()) {
                Log.d("GTFS", "Mappa létrehozva: ${dstDir.absolutePath}")
            } else {
                Log.e("GTFS", "Nem sikerült létrehozni a mappát: ${dstDir.absolutePath}")
                return@withContext "❌ Nem sikerült létrehozni a célmappát."
            }
        }

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext "❌ Hiba: HTTP ${resp.code}"

            ZipInputStream(resp.body!!.byteStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(dstDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                        Log.d("GTFS", "Mentve: ${outFile.absolutePath} (${outFile.length()} bájt)")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        val files = dstDir.walkTopDown().filter { it.isFile }.toList()
        Log.d("GTFS", "Összes fájl (${files.size} db):")
        files.forEach {
            Log.d("GTFS", "${it.name} - ${it.length()} bájt")
        }

        "✅ GTFS frissítés kész, ${files.size} fájl létrehozva."
    }
}

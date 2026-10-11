package com.amerthamer.tramagent

import android.app.Application
import com.amerthamer.tramagent.data.ApiKeyStore
import org.osmdroid.config.Configuration
import java.io.File

class TramAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiKeyStore.init(this)

        // osmdroid: az OSM tile-szerverek szabályai szerint az appot egyértelműen azonosító User-Agent kell
        // (a generikus „com.example…” azonosítót letiltják); a csempe-cache az app cache-ében
        Configuration.getInstance().apply {
            userAgentValue = "TramAgent/${BuildConfig.VERSION_NAME} (Android; +https://github.com/AmerThamer/tram-agent)"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }
}

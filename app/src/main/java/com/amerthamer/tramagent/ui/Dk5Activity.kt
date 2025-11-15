package com.amerthamer.tramagent.ui

import android.graphics.Typeface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amerthamer.tramagent.databinding.ActivityDk5Binding
import com.amerthamer.tramagent.domain.Dk5
import kotlinx.coroutines.launch
import java.io.File

/**
 * NYOMKÖVETÉS képernyő
 *
 * A felhasználó egy jármű pályaszámát adja meg, az Activity pedig
 * a Dk5 domain modul segítségével lekérdezi az adott jármű aktuális tripjét,
 * és részletes megállólistát + késés információt jelenít meg.
 * FONTOS: az aktuális pontig, utána a számolt késést / sietést vetíti ki a hátralévő menetre
 */
class Dk5Activity : AppCompatActivity() {

    // ViewBinding az activity_dk5.xml-hez
    private lateinit var binding: ActivityDk5Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDk5Binding.inflate(layoutInflater)
        setContentView(binding.root)

        // A GTFS állományokat a belső tárhely "gtfs" mappájában keressük
        val gtfsDir = File(filesDir, "gtfs")

        // Az eredmény szöveget félkövérrel jelenítjük meg
        binding.result.setTypeface(null, Typeface.BOLD)

        // Lekérdezés indítása a "Keresés" gombra
        binding.queryButton.setOnClickListener {
            val vehicleLabel = binding.queryInput.text.toString().trim()
            if (vehicleLabel.isEmpty()) {
                binding.result.text = "KÉRLEK, ADD MEG A JÁRMŰ PÁLYASZÁMÁT!"
                return@setOnClickListener
            }

            // A Dk5.run suspend függvény, ezért coroutine-ból hívjuk
            lifecycleScope.launch {
                binding.result.text = "LEKÉRDEZÉS FOLYAMATBAN..."
                val output = Dk5.run(vehicleLabel, gtfsDir)
                binding.result.text = output
            }
        }
    }
}

package com.amerthamer.tramagent.ui

import android.graphics.Typeface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amerthamer.tramagent.databinding.ActivityDk5Binding
import com.amerthamer.tramagent.domain.Dk5
import kotlinx.coroutines.launch
import java.io.File

class Dk5Activity : AppCompatActivity() {

    private lateinit var binding: ActivityDk5Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDk5Binding.inflate(layoutInflater)
        setContentView(binding.root)

        val gtfsDir = File(filesDir, "gtfs")

        binding.result.setTypeface(null, Typeface.BOLD)

        binding.queryButton.setOnClickListener {
            val vehicleLabel = binding.queryInput.text.toString().trim()
            if (vehicleLabel.isEmpty()) {
                binding.result.text = "KÉRLEK, ADD MEG A JÁRMŰ PÁLYASZÁMÁT!"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                binding.result.text = "LEKÉRDEZÉS FOLYAMATBAN..."
                val output = Dk5.run(vehicleLabel, gtfsDir)
                binding.result.text = output
            }
        }
    }
}

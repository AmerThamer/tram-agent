package com.example.na3.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.na3.databinding.ActivityDk5Binding
import com.example.na3.domain.Dk5
import kotlinx.coroutines.launch
import java.io.File

class Dk5Activity : AppCompatActivity() {

    private lateinit var binding: ActivityDk5Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDk5Binding.inflate(layoutInflater)
        setContentView(binding.root)

        val gtfsDir = File(filesDir, "gtfs")

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

package com.example.na3.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.na3.databinding.ActivityGtfsBinding
import com.example.na3.gtfs.GtfsUpdater
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class GtfsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGtfsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGtfsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnDownload.setOnClickListener {
            binding.textResult.text = "FRISSÍTÉS FOLYAMATBAN..."

            lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        GtfsUpdater.update(this@GtfsActivity)
                    }

                    binding.textResult.text = result
                    Toast.makeText(this@GtfsActivity, "GTFS frissítés kész", Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    Log.e("GTFS", "Hiba a frissítés közben", e)
                    binding.textResult.text = "❌ Hiba a frissítés közben: ${e.message}"
                    Toast.makeText(this@GtfsActivity, "Hiba történt a frissítés közben", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

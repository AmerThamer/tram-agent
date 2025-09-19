package com.example.na3.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.na3.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        binding.btnDk1.setOnClickListener {
            startActivity(Intent(this, Dk1Activity::class.java))
        }
        binding.btnDk2.setOnClickListener {
            startActivity(Intent(this, Dk2Activity::class.java))
        }
        binding.btnDk3.setOnClickListener {
            startActivity(Intent(this, Dk3aActivity::class.java))
        }
        binding.btnDk4.setOnClickListener {
            startActivity(Intent(this, Dk4Activity::class.java))
        }
        binding.btnDk5.setOnClickListener {
            startActivity(Intent(this, Dk5Activity::class.java))
        }
        binding.btnGtfs.setOnClickListener {
            startActivity(Intent(this, GtfsActivity::class.java))
        }
    }
}
package com.amerthamer.tramagent.ui

import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.amerthamer.tramagent.databinding.ActivityDk2Binding
import com.amerthamer.tramagent.domain.Dk2
import kotlinx.coroutines.launch

class Dk2Activity : AppCompatActivity() {
    private lateinit var binding: ActivityDk2Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDk2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.queryButton.setOnClickListener {
            lifecycleScope.launch {
                binding.result.text = "Lekérdezés folyamatban..."
                val output = Dk2.run()

                binding.result.text =
                    HtmlCompat.fromHtml(output, HtmlCompat.FROM_HTML_MODE_LEGACY)
                binding.result.movementMethod = LinkMovementMethod.getInstance()
                binding.result.linksClickable = true
            }
        }
    }
}

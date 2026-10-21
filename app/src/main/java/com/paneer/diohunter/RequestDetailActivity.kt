package com.paneer.diohunter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.paneer.diohunter.databinding.ActivityDetailBinding

class RequestDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        val r = CaptureStore.all().firstOrNull { it.id == id } ?: return finish()

        binding.toolbar.title = "${r.method} ${r.host}"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.tvUrl.text = r.url
        binding.tvReqHeaders.text = formatMap(r.reqHeaders)
        binding.tvReqBody.text = r.reqBodyPreview ?: "—"
        binding.tvRespHeaders.text = formatMap(r.respHeaders)
        binding.tvRespBody.text = r.respBodyPreview ?: "—"
        binding.tvStatus.text = buildString {
            append("Status: ").append(r.status ?: "—")
            r.durationMs?.let { append("  ·  ").append(it).append(" ms") }
            r.packageHint?.let { append("\nApp: ").append(it) }
        }
    }

    private fun formatMap(m: Map<String, String>): String =
        if (m.isEmpty()) "—" else m.entries.joinToString("\n") { "${it.key}: ${it.value}" }

    companion object {
        const val EXTRA_ID = "id"
    }
}

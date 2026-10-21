package com.paneer.diohunter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.paneer.diohunter.databinding.ItemEndpointBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EndpointAdapter(
    private val onClick: (HttpRecord) -> Unit
) : RecyclerView.Adapter<EndpointAdapter.VH>() {

    private val items = mutableListOf<HttpRecord>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun submit(list: List<HttpRecord>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemEndpointBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemEndpointBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: HttpRecord) {
            b.tvMethod.text = r.method
            b.tvMethod.setTextColor(methodColor(r.method))
            b.tvHost.text = r.host
            b.tvPath.text = buildString {
                append(r.path)
                if (!r.query.isNullOrEmpty()) append("?").append(r.query.take(80))
            }
            b.tvMeta.text = buildString {
                append(timeFmt.format(Date(r.ts)))
                r.status?.let { append("  ·  ").append(it) }
                r.durationMs?.let { append("  ·  ").append(it).append("ms") }
            }
            b.root.setOnClickListener { onClick(r) }
        }

        private fun methodColor(m: String): Int = when (m.uppercase()) {
            "GET" -> Color.parseColor("#4FC3F7")
            "POST" -> Color.parseColor("#81C784")
            "PUT", "PATCH" -> Color.parseColor("#FFB74D")
            "DELETE" -> Color.parseColor("#E57373")
            "TLS" -> Color.parseColor("#CE93D8")
            else -> Color.parseColor("#E8E8F0")
        }
    }
}

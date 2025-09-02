package com.example.aisleassistant

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aisleassistant.models.WifiInfoItem

class WifiAdapter(private var items: List<WifiInfoItem>) :
    RecyclerView.Adapter<WifiAdapter.VH>() {

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context)
        tv.setPadding(24, 24, 24, 24)
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        val sb = StringBuilder()
        sb.appendLine("SSID: ${it.ssid}")
        sb.appendLine("BSSID: ${it.bssid}")
        sb.appendLine("RSSI: ${it.rssi} dBm")
        sb.appendLine("Freq: ${it.frequency} MHz")
        sb.appendLine("Security: ${it.capabilities}")
        sb.appendLine("Distance: ${it.distanceMm ?: "N/A"} mm")
        sb.appendLine("± ${it.distanceStdDevMm ?: "N/A"} mm")
        holder.tv.text = sb.toString()
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<WifiInfoItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}

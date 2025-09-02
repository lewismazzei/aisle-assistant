package com.example.aisleassistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aisleassistant.data.WifiEntryRecord
import com.example.aisleassistant.data.WifiStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ItemDetailActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: EntryAdapter
    private lateinit var store: WifiStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_detail)

        val itemName = intent.getStringExtra("item_name") ?: "Item"
        supportActionBar?.title = itemName

        store = WifiStore(this)
        recycler = findViewById(R.id.detailRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = EntryAdapter(emptyList())
        recycler.adapter = adapter

        val itemId = intent.getLongExtra("item_id", -1)
        if (itemId != -1L) {
            val entries = store.getEntriesForItem(itemId)
            adapter.submit(entries)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_item_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                exportCurrentItem()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportCurrentItem() {
        val itemId = intent.getLongExtra("item_id", -1)
        val itemName = intent.getStringExtra("item_name") ?: "item"
        if (itemId == -1L) return

        val entries = store.getEntriesForItem(itemId)
        val csv = buildCsv(entries)
        val safeName = itemName.replace("[^A-Za-z0-9_-]".toRegex(), "_")
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(cacheDir, "wifi_${safeName}_${ts}.csv")
        file.writeText(csv)

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Wi-Fi export for $itemName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Export CSV"))
    }

    private fun buildCsv(items: List<WifiEntryRecord>): String {
        val sb = StringBuilder()
        sb.appendLine("captured_at,ssid,bssid,rssi,frequency,capabilities,distance_mm,distance_stddev_mm")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        for (it in items) {
            val whenStr = fmt.format(Date(it.createdAtMs))
            fun q(s: String?): String {
                val v = s ?: ""
                val esc = v.replace("\"", "\"\"")
                return "\"$esc\""
            }
            val rssiStr = it.rssi?.toString() ?: ""
            val freqStr = it.frequency?.toString() ?: ""
            val distStr = it.distanceMm?.toString() ?: ""
            val distStdStr = it.distanceStdDevMm?.toString() ?: ""
            sb.append(whenStr).append(',')
                .append(q(it.ssid)).append(',')
                .append(q(it.bssid)).append(',')
                .append(rssiStr).append(',')
                .append(freqStr).append(',')
                .append(q(it.capabilities)).append(',')
                .append(distStr).append(',')
                .append(distStdStr)
                .append('\n')
        }
        return sb.toString()
    }

    class EntryAdapter(private var items: List<WifiEntryRecord>) :
        RecyclerView.Adapter<EntryAdapter.VH>() {
        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context)
            tv.setPadding(24, 24, 24, 24)
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val it = items[position]
            val whenStr = DateFormat.format("yyyy-MM-dd HH:mm:ss", it.createdAtMs)
            val sb = StringBuilder()
            sb.appendLine("Captured: $whenStr")
            sb.appendLine("SSID: ${it.ssid ?: ""}")
            sb.appendLine("BSSID: ${it.bssid ?: ""}")
            sb.appendLine("RSSI: ${it.rssi ?: 0} dBm")
            sb.appendLine("Freq: ${it.frequency ?: 0} MHz")
            sb.appendLine("Security: ${it.capabilities ?: ""}")
            val dist = it.distanceMm?.let { d -> "$d mm" } ?: "N/A"
            val distStd = it.distanceStdDevMm?.let { d -> "± $d mm" } ?: ""
            sb.appendLine("Distance: $dist ${distStd}")
            holder.tv.text = sb.toString()
        }

        override fun getItemCount() = items.size

        fun submit(newItems: List<WifiEntryRecord>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}

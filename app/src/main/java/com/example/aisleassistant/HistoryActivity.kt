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
import com.example.aisleassistant.data.AllEntryRecord
import com.example.aisleassistant.data.ItemWithStats
import com.example.aisleassistant.data.WifiStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ItemsAdapter
    private lateinit var store: WifiStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        supportActionBar?.title = "Items History"

        store = WifiStore(this)
        recycler = findViewById(R.id.historyRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = ItemsAdapter(emptyList()) { item ->
            val i = Intent(this, ItemDetailActivity::class.java)
            i.putExtra("item_id", item.id)
            i.putExtra("item_name", item.name)
            startActivity(i)
        }
        recycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        val items = store.getItemsWithStats()
        adapter.submit(items)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_all -> {
                exportAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportAll() {
        val entries = store.getAllEntries()
        val csv = buildCsv(entries)
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(cacheDir, "wifi_all_${ts}.csv")
        file.writeText(csv)

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Wi-Fi export for all items")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Export All CSV"))
    }

    private fun buildCsv(items: List<AllEntryRecord>): String {
        val sb = StringBuilder()
        sb.appendLine("item,captured_at,ssid,bssid,rssi,frequency,capabilities,distance_mm,distance_stddev_mm")
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
            sb.append(q(it.itemName)).append(',')
                .append(whenStr).append(',')
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

    class ItemsAdapter(
        private var items: List<ItemWithStats>,
        private val onClick: (ItemWithStats) -> Unit
    ) : RecyclerView.Adapter<ItemsAdapter.VH>() {
        class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context)
            tv.setPadding(24, 24, 24, 24)
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val it = items[position]
            val last = it.lastSeenMs?.let { ms ->
                DateFormat.format("yyyy-MM-dd HH:mm", ms).toString()
            } ?: "—"
            holder.tv.text = "${it.name}\nEntries: ${it.entryCount}  Last: ${last}"
            holder.tv.setOnClickListener { _ -> onClick(it) }
        }

        override fun getItemCount() = items.size

        fun submit(newItems: List<ItemWithStats>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}

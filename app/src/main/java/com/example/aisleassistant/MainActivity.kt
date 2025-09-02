package com.example.aisleassistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aisleassistant.models.WifiInfoItem
import android.net.wifi.rtt.WifiRttManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.util.Log


class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: WifiAdapter
    private var rttManager: WifiRttManager? = null
    private lateinit var submitButton: Button
    private lateinit var itemNameInput: EditText
    private lateinit var store: com.example.aisleassistant.data.WifiStore
    private lateinit var historyButton: Button


    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            try {
                val ok = requiredPermissions().all { granted[it] == true }
                if (!ok) {
                    Toast.makeText(this, "Permissions required for Wi-Fi scan", Toast.LENGTH_LONG).show()
                } else {
                    scanAndRender()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in permission callback", e)
                Toast.makeText(this, "Permission error", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            recycler = findViewById(R.id.wifiRecyclerView)
            submitButton = findViewById(R.id.submitButton)
            itemNameInput = findViewById(R.id.itemNameInput)
            historyButton = findViewById(R.id.historyButton)

            recycler.layoutManager = LinearLayoutManager(this)
            adapter = WifiAdapter(listOf())
            recycler.adapter = adapter

            wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            rttManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                applicationContext.getSystemService(WifiRttManager::class.java)
            } else {
                null
            }

            store = com.example.aisleassistant.data.WifiStore(this)

            submitButton.setOnClickListener {
                val name = itemNameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Enter an item name", Toast.LENGTH_SHORT).show()
                } else {
                    ensurePermissionsThen { scanRenderAndPersist(name) }
                }
            }

            historyButton.setOnClickListener {
                startActivity(Intent(this, HistoryActivity::class.java))
            }

            // Initial scan to show current networks (no persistence)
            ensurePermissionsThen { scanAndRender() }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error initializing app", Toast.LENGTH_LONG).show()
        }
    }

    private fun requiredPermissions(): Array<String> {
        val base = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        if (Build.VERSION.SDK_INT < 33) {
            base += Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            base += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return base.toTypedArray()
    }

    private fun ensurePermissionsThen(block: () -> Unit) {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) block() else requestPermissions.launch(missing.toTypedArray())
    }

    private fun scanAndRender() {
        try {
            val results: List<ScanResult> = wifiManager.scanResults ?: emptyList()

            // Create items from scan results
            val items = results.map { r ->
                WifiInfoItem(
                    ssid = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            r.wifiSsid?.toString() ?: "(hidden)"
                        } else {
                            @Suppress("DEPRECATION")
                            r.SSID ?: "(hidden)"
                        }
                    } catch (e: Exception) {
                        "(error)"
                    },
                    bssid = r.BSSID ?: "-",
                    rssi = r.level,
                    frequency = r.frequency,
                    capabilities = r.capabilities ?: ""
                )
            }.toMutableList()

            adapter.submit(items)

            // Try RTT with better error handling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && rttManager?.isAvailable == true && results.isNotEmpty()) {
                try {
                    val request = RangingRequest.Builder()
                        .addAccessPoints(results.take(3)) // reduce to 3 for stability
                        .build()

                    rttManager?.startRanging(request, mainExecutor,
                        object : RangingResultCallback() {
                            override fun onRangingResults(rangingResults: List<RangingResult>) {
                                try {
                                    val withRtt = items.map { item ->
                                        val match = rangingResults.find { it.macAddress.toString() == item.bssid }
                                        if (match != null && match.status == RangingResult.STATUS_SUCCESS) {
                                            item.copy(
                                                distanceMm = match.distanceMm,
                                                distanceStdDevMm = match.distanceStdDevMm
                                            )
                                        } else item
                                    }
                                    adapter.submit(withRtt)
                                } catch (e: Exception) {
                                    Log.e("RTT", "Error processing RTT results", e)
                                }
                            }

                            override fun onRangingFailure(code: Int) {
                                Log.d("RTT", "RTT failed with code: $code")
                            }
                        })
                } catch (e: Exception) {
                    Log.e("RTT", "Error starting RTT", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in scanAndRender", e)
            Toast.makeText(this, "Error scanning WiFi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanRenderAndPersist(itemName: String) {
        try {
            val results: List<ScanResult> = wifiManager.scanResults ?: emptyList()

            val items = results.map { r ->
                WifiInfoItem(
                    ssid = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            r.wifiSsid?.toString() ?: "(hidden)"
                        } else {
                            @Suppress("DEPRECATION")
                            r.SSID ?: "(hidden)"
                        }
                    } catch (e: Exception) {
                        "(error)"
                    },
                    bssid = r.BSSID ?: "-",
                    rssi = r.level,
                    frequency = r.frequency,
                    capabilities = r.capabilities ?: ""
                )
            }

            // If RTT is available, attempt to enrich with distance and persist enriched rows.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && rttManager?.isAvailable == true && results.isNotEmpty()) {
                try {
                    val request = RangingRequest.Builder()
                        .addAccessPoints(results.take(3))
                        .build()

                    rttManager?.startRanging(request, mainExecutor,
                        object : RangingResultCallback() {
                            override fun onRangingResults(rangingResults: List<RangingResult>) {
                                try {
                                    val withRtt = items.map { item ->
                                        val match = rangingResults.find { it.macAddress.toString() == item.bssid }
                                        if (match != null && match.status == RangingResult.STATUS_SUCCESS) {
                                            item.copy(
                                                distanceMm = match.distanceMm,
                                                distanceStdDevMm = match.distanceStdDevMm
                                            )
                                        } else item
                                    }
                                    // Persist enriched results
                                    store.recordScanForItem(itemName, withRtt)
                                    Toast.makeText(this@MainActivity, "Recorded ${withRtt.size} entries for '$itemName'", Toast.LENGTH_SHORT).show()
                                    // Update UI
                                    adapter.submit(withRtt)
                                } catch (e: Exception) {
                                    Log.e("RTT", "Error processing RTT results", e)
                                    // Fallback: persist without RTT
                                    store.recordScanForItem(itemName, items)
                                    Toast.makeText(this@MainActivity, "Recorded ${items.size} entries for '$itemName'", Toast.LENGTH_SHORT).show()
                                    adapter.submit(items)
                                }
                            }

                            override fun onRangingFailure(code: Int) {
                                Log.d("RTT", "RTT failed with code: $code")
                                // Persist without RTT when ranging fails
                                store.recordScanForItem(itemName, items)
                                Toast.makeText(this@MainActivity, "Recorded ${items.size} entries for '$itemName'", Toast.LENGTH_SHORT).show()
                                adapter.submit(items)
                            }
                        })
                } catch (e: Exception) {
                    Log.e("RTT", "Error starting RTT", e)
                    // Persist without RTT on error starting RTT
                    store.recordScanForItem(itemName, items)
                    Toast.makeText(this, "Recorded ${items.size} entries for '$itemName'", Toast.LENGTH_SHORT).show()
                    adapter.submit(items)
                }
            } else {
                // Persist to local store (one-to-many) without RTT
                store.recordScanForItem(itemName, items)
                Toast.makeText(this, "Recorded ${items.size} entries for '$itemName'", Toast.LENGTH_SHORT).show()
                // Update UI with current scan
                adapter.submit(items)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in scanRenderAndPersist", e)
            Toast.makeText(this, "Error recording WiFi", Toast.LENGTH_SHORT).show()
        }
    }
}

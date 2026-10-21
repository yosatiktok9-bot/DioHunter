package com.paneer.diohunter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.paneer.diohunter.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: EndpointAdapter
    private var filter = ""
    private val appLabels = mutableListOf<String>()
    private val appPackages = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CaptureStore.loadPersisted()
        ensureNotifPermission()

        adapter = EndpointAdapter { record ->
            startActivity(
                Intent(this, RequestDetailActivity::class.java)
                    .putExtra(RequestDetailActivity.EXTRA_ID, record.id)
            )
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        loadApps()
        binding.spinnerApps.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            appLabels
        )

        binding.btnHunt.setOnClickListener {
            if (CaptureStore.capturing) {
                stopHunt()
            } else {
                val idx = binding.spinnerApps.selectedItemPosition
                if (idx in appPackages.indices) {
                    CaptureStore.targetPackage = appPackages[idx]
                }
                startHunt()
            }
        }
        binding.btnClear.setOnClickListener {
            CaptureStore.clear()
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
        }
        binding.btnExport.setOnClickListener {
            try {
                val f = CaptureStore.exportFile()
                Toast.makeText(this, "Saved: ${f.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        binding.etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filter = s?.toString().orEmpty()
                adapter.submit(CaptureStore.filtered(filter))
            }
        })

        lifecycleScope.launch {
            CaptureStore.flow.collectLatest {
                adapter.submit(CaptureStore.filtered(filter))
                updateHuntButton()
            }
        }
        updateHuntButton()
    }

    private fun loadApps() {
        appLabels.clear()
        appPackages.clear()
        appLabels.add("All traffic (no filter)")
        appPackages.add("")
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        for (ai in apps) {
            appLabels.add(pm.getApplicationLabel(ai).toString() + "  ·  " + ai.packageName)
            appPackages.add(ai.packageName)
        }
    }

    private fun startHunt() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            startActivityForResult(prepare, REQ_VPN)
        } else {
            launchVpn()
        }
    }

    private fun stopHunt() {
        startService(Intent(this, CaptureVpnService::class.java).setAction(CaptureVpnService.ACTION_STOP))
        CaptureStore.capturing = false
        updateHuntButton()
    }

    private fun launchVpn() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, CaptureVpnService::class.java)
        )
        CaptureStore.capturing = true
        updateHuntButton()
        Toast.makeText(
            this,
            "Hunting: ${CaptureStore.targetPackage ?: "all"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == Activity.RESULT_OK) {
            launchVpn()
        } else if (requestCode == REQ_VPN) {
            Toast.makeText(this, R.string.vpn_permission, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateHuntButton() {
        binding.btnHunt.text = if (CaptureStore.capturing)
            getString(R.string.stop_capture) else getString(R.string.start_capture)
    }

    private fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    99
                )
            }
        }
    }

    companion object {
        private const val REQ_VPN = 1001
    }
}

package com.paneer.diohunter

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle

/** Thin helper if you want to trigger VPN prepare from outside MainActivity. */
class VpnPrepareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = VpnService.prepare(this)
        if (i != null) startActivityForResult(i, 1) else {
            startService(Intent(this, CaptureVpnService::class.java))
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            startService(Intent(this, CaptureVpnService::class.java))
        }
        finish()
    }
}

package com.paneer.diohunter

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Local VPN that routes selected app traffic through a userspace tunnel.
 * Parses cleartext HTTP fully; for HTTPS logs host via SNI (ClientHello).
 */
class CaptureVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()

    data class TcpSession(
        val key: String,
        var partial: HttpParser.Partial? = null,
        var fromClient: Boolean = true,
        val buffer: ArrayList<Byte> = ArrayList()
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startCapture()
        }
        return START_STICKY
    }

    private fun startCapture() {
        if (running.getAndSet(true)) return
        val pkg = CaptureStore.targetPackage
        startForeground(NOTIF_ID, buildNotif())

        val builder = Builder()
            .setSession("DioHunter")
            .setMtu(1500)
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")

        if (!pkg.isNullOrBlank()) {
            try {
                builder.addAllowedApplication(pkg)
            } catch (e: Exception) {
                Log.w(TAG, "addAllowedApplication failed: $pkg", e)
            }
        }
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {
        }

        tun = builder.establish()
        if (tun == null) {
            Log.e(TAG, "VPN establish failed")
            running.set(false)
            stopSelf()
            return
        }
        CaptureStore.capturing = true
        thread(name = "diohunter-tun", isDaemon = true) { pumpTun() }
    }

    private fun stopCapture() {
        running.set(false)
        CaptureStore.capturing = false
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
        tcpSessions.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    private fun pumpTun() {
        val fd = tun ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val packet = ByteArray(32767)
        try {
            while (running.get()) {
                val len = input.read(packet)
                if (len <= 0) {
                    Thread.sleep(5)
                    continue
                }
                handleIp(packet.copyOf(len))
            }
        } catch (e: Exception) {
            if (running.get()) Log.w(TAG, "tun read end", e)
        }
    }

    private fun handleIp(pkt: ByteArray) {
        if (pkt.size < 20) return
        val vihl = pkt[0].toInt() and 0xff
        val version = vihl shr 4
        if (version != 4) return
        val ihl = (vihl and 0x0f) * 4
        if (pkt.size < ihl + 1) return
        val protocol = pkt[9].toInt() and 0xff
        if (protocol != 6) return

        val srcPort = ((pkt[ihl].toInt() and 0xff) shl 8) or (pkt[ihl + 1].toInt() and 0xff)
        val dstPort = ((pkt[ihl + 2].toInt() and 0xff) shl 8) or (pkt[ihl + 3].toInt() and 0xff)
        val dataOff = ((pkt[ihl + 12].toInt() and 0xff) shr 4) * 4
        val payloadOff = ihl + dataOff
        if (payloadOff >= pkt.size) return
        val payload = pkt.copyOfRange(payloadOff, pkt.size)
        if (payload.isEmpty()) return

        val srcIp = pkt.sliceArray(12..15).joinToString(".") { (it.toInt() and 0xff).toString() }
        val dstIp = pkt.sliceArray(16..19).joinToString(".") { (it.toInt() and 0xff).toString() }
        val key = "$srcIp:$srcPort-$dstIp:$dstPort"

        if (dstPort == 443 || srcPort == 443) {
            val sni = HttpParser.extractSni(payload)
            if (sni != null) {
                CaptureStore.add(
                    HttpRecord(
                        method = "TLS",
                        scheme = "https",
                        host = sni,
                        path = "/",
                        query = null,
                        status = null,
                        reqHeaders = mapOf("SNI" to sni),
                        respHeaders = emptyMap(),
                        reqBodyPreview = null,
                        respBodyPreview = "TLS handshake (install user CA + proxy mode for full HTTPS bodies)",
                        durationMs = null,
                        packageHint = CaptureStore.targetPackage
                    )
                )
            }
            return
        }

        if (dstPort == 80 || srcPort == 80 || dstPort == 8080 || dstPort == 8888) {
            val session = tcpSessions.getOrPut(key) { TcpSession(key) }
            val partial = HttpParser.tryParseRequest(payload)
            if (partial != null && partial.method.isNotBlank()) {
                session.partial = partial
                if (partial.host.isBlank()) partial.host = dstIp
                CaptureStore.add(HttpParser.toRecord(partial, CaptureStore.targetPackage))
            } else {
                session.partial?.let { p ->
                    if (HttpParser.tryParseResponse(payload, p)) {
                        CaptureStore.add(HttpParser.toRecord(p, CaptureStore.targetPackage))
                        tcpSessions.remove(key)
                    }
                }
            }
        }
    }

    private fun buildNotif(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, CaptureVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG = "DioHunter"
        const val CHANNEL_ID = "diohunter_capture"
        const val NOTIF_ID = 42
        const val ACTION_STOP = "com.paneer.diohunter.STOP"
    }
}

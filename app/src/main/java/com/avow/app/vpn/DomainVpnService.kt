package com.avow.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.avow.app.MainActivity
import com.avow.app.R
import com.avow.app.data.VowDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Local, no-server DNS content filter. It routes only the app-facing DNS server address through a
 * VpnService tunnel; for each DNS query it either sinkholes blocked domains (0.0.0.0 / NODATA) or
 * forwards the query to a real upstream resolver over a protected socket and relays the answer back.
 * No user traffic leaves the device via us, and it blocks domains in every browser/app — not just
 * the ones the accessibility service can read.
 *
 * The bug-prone packet/checksum logic lives in [DnsPacket] (unit-tested); this class is the thin
 * Android plumbing around it.
 */
class DomainVpnService : VpnService() {

    companion object {
        private const val TAG = "DomainVpnService"
        const val ACTION_STOP = "com.avow.app.vpn.STOP"
        private const val VIRTUAL_ADDR = "10.111.222.1"
        private const val VIRTUAL_DNS = "10.111.222.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val CHANNEL_ID = "vpn_domain_filter"
        private const val NOTIF_ID = 3101

        fun start(context: Context) {
            context.startService(Intent(context, DomainVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DomainVpnService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var bannedDomains: Set<String> = emptySet()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            teardown()
            stopSelf()
            return START_NOT_STICKY
        }
        // Keep the banned-domain set current from the DataStore (the global BAN DOMAIN SET).
        scope.launch {
            VowDataStore(applicationContext).preferencesFlow.collect { prefs ->
                bannedDomains = (prefs[VowDataStore.BAN_DOMAIN_SET] ?: emptySet())
                    .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            }
        }
        if (!running) startTunnel()
        return START_STICKY
    }

    private fun startTunnel() {
        try {
            val builder = Builder()
                .setSession("aVow domain filter")
                .addAddress(VIRTUAL_ADDR, 32)
                .addDnsServer(VIRTUAL_DNS)
                .addRoute(VIRTUAL_DNS, 32) // ONLY our virtual DNS traverses the tunnel; all else is direct
            builder.setBlocking(true)
            val fd = builder.establish() ?: run {
                Log.e(TAG, "establish() returned null (no VPN permission?)")
                stopSelf()
                return
            }
            tunnel = fd
            running = true
            startForeground(NOTIF_ID, buildNotification())
            worker = Thread({ runLoop(fd) }, "aVow-dns-filter").also { it.start() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN tunnel", e)
            teardown()
            stopSelf()
        }
    }

    private fun runLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val upstream = DatagramSocket().also { protect(it) } // protect => bypasses our own tunnel
        upstream.soTimeout = 4000
        val upstreamAddr = InetSocketAddress(InetAddress.getByName(UPSTREAM_DNS), DnsPacket.DNS_PORT)
        val buffer = ByteArray(32767)
        try {
            while (running) {
                val len = try { input.read(buffer) } catch (e: Exception) { break }
                if (len <= 0) continue
                val packet = buffer.copyOf(len)
                if (DnsPacket.udpDestPort(packet) != DnsPacket.DNS_PORT) continue

                val dns = DnsPacket.dnsPayload(packet)
                val domain = DnsPacket.parseQuestionDomain(dns)
                try {
                    if (domain != null && isBlocked(domain)) {
                        val resp = DnsPacket.buildSinkholeResponse(dns)
                        output.write(DnsPacket.buildUdpResponse(packet, resp))
                    } else {
                        val answer = forward(dns, upstream, upstreamAddr) ?: continue
                        output.write(DnsPacket.buildUdpResponse(packet, answer))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "DNS handling error", e)
                }
            }
        } finally {
            try { upstream.close() } catch (_: Exception) {}
        }
    }

    /** Forwards a raw DNS query to the real upstream resolver and returns its response payload. */
    private fun forward(dns: ByteArray, socket: DatagramSocket, addr: InetSocketAddress): ByteArray? {
        return try {
            socket.send(DatagramPacket(dns, dns.size, addr))
            val resp = ByteArray(1500)
            val dp = DatagramPacket(resp, resp.size)
            socket.receive(dp)
            resp.copyOf(dp.length)
        } catch (e: Exception) {
            null // timeout / network error: drop, the client will retry
        }
    }

    private fun isBlocked(domain: String): Boolean =
        bannedDomains.any { domain == it || domain.endsWith(".$it") }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Domain filter", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Blocks the websites you chose, in every browser." }
            )
        }
        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("aVow — domain filter active")
            .setContentText("Blocking your restricted sites in every browser.")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun teardown() {
        running = false
        try { worker?.interrupt() } catch (_: Exception) {}
        worker = null
        try { tunnel?.close() } catch (_: Exception) {}
        tunnel = null
    }

    override fun onDestroy() {
        teardown()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }
}

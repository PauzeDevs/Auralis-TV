package dev.pauze.auralis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * DNS-only filtering VPN. It does not decrypt HTTPS or inspect app content.
 *
 * Note: DNS filtering can block requests to known ad/tracker hostnames, but cannot
 * reliably remove ads served from the same hostname as requested content.
 */
class AdBlockVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFiltering()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, createNotification())
        if (running.compareAndSet(false, true)) {
            thread(name = "Auralis-DNS-Filter", isDaemon = true) { establishTunnel() }
        }
        return START_STICKY
    }

    private fun establishTunnel() {
        try {
            val descriptor = Builder()
                .setSession("Auralis DNS Protection")
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(VPN_DNS)
                .addRoute(VPN_DNS, 32)
                .setMtu(1500)
                .establish() ?: run {
                    running.set(false)
                    stopSelf()
                    return
                }
            tunnel = descriptor
            val input = FileInputStream(descriptor.fileDescriptor)
            val output = FileOutputStream(descriptor.fileDescriptor)
            val buffer = ByteArray(32767)

            while (running.get()) {
                val length = input.read(buffer)
                if (length <= 0) continue
                val packet = buffer.copyOf(length)
                val parsed = parseDnsUdp(packet) ?: continue
                val dnsRequest = parsed.dnsPayload
                val blocked = isBlocked(parseQueryName(dnsRequest))

                val response = if (blocked) {
                    makeBlockedDnsResponse(dnsRequest)
                } else {
                    queryUpstream(dnsRequest)
                } ?: continue

                val responsePacket = buildIpv4UdpPacket(
                    sourceIp = parsed.destinationIp,
                    destinationIp = parsed.sourceIp,
                    sourcePort = parsed.destinationPort,
                    destinationPort = parsed.sourcePort,
                    payload = response
                )
                output.write(responsePacket)
            }
        } catch (_: Exception) {
            // VPN may be revoked or the device may shut down. Stop cleanly.
        } finally {
            tunnel?.close()
            tunnel = null
            running.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun stopFiltering() {
        running.set(false)
        tunnel?.close()
        tunnel = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun isBlocked(host: String): Boolean {
        if (host.isBlank()) return false
        val normalized = host.trimEnd('.').lowercase()
        return BLOCKED_DOMAINS.any { normalized == it || normalized.endsWith(".$it") }
    }

    private fun queryUpstream(query: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                protect(socket)
                socket.soTimeout = 3500
                val address = InetAddress.getByName(UPSTREAM_DNS)
                socket.send(DatagramPacket(query, query.size, address, DNS_PORT))
                val data = ByteArray(4096)
                val response = DatagramPacket(data, data.size)
                socket.receive(response)
                response.data.copyOf(response.length)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDnsUdp(packet: ByteArray): ParsedDnsUdp? {
        if (packet.size < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != 4) return null
        val ipHeaderLength = (packet[0].toInt() and 0xF) * 4
        if (packet.size < ipHeaderLength + 8) return null
        if ((packet[9].toInt() and 0xFF) != 17) return null // UDP only
        val destinationPort = u16(packet, ipHeaderLength + 2)
        if (destinationPort != DNS_PORT) return null
        val udpLength = u16(packet, ipHeaderLength + 4)
        if (udpLength < 8 || packet.size < ipHeaderLength + udpLength) return null
        return ParsedDnsUdp(
            sourceIp = packet.copyOfRange(12, 16),
            destinationIp = packet.copyOfRange(16, 20),
            sourcePort = u16(packet, ipHeaderLength),
            destinationPort = destinationPort,
            dnsPayload = packet.copyOfRange(ipHeaderLength + 8, ipHeaderLength + udpLength)
        )
    }

    private fun parseQueryName(message: ByteArray): String {
        if (message.size < 17) return ""
        var cursor = 12
        val labels = mutableListOf<String>()
        while (cursor < message.size) {
            val length = message[cursor++].toInt() and 0xFF
            if (length == 0) break
            if ((length and 0xC0) != 0 || cursor + length > message.size) return ""
            labels += String(message, cursor, length, Charsets.US_ASCII)
            cursor += length
        }
        return labels.joinToString(".")
    }

    private fun makeBlockedDnsResponse(query: ByteArray): ByteArray? {
        if (query.size < 12) return null
        val response = query.copyOf()
        response[2] = ((query[2].toInt() and 0xFF) or 0x80).toByte() // QR
        response[3] = ((query[3].toInt() and 0xF0) or 0x03).toByte() // NXDOMAIN
        response[6] = 0
        response[7] = 0
        response[8] = 0
        response[9] = 0
        response[10] = 0
        response[11] = 0
        return response
    }

    private fun buildIpv4UdpPacket(
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = 8 + payload.size
        val packet = ByteArray(20 + udpLength)
        packet[0] = 0x45
        put16(packet, 2, packet.size)
        packet[8] = 64
        packet[9] = 17
        System.arraycopy(sourceIp, 0, packet, 12, 4)
        System.arraycopy(destinationIp, 0, packet, 16, 4)
        put16(packet, 10, checksum(packet, 0, 20))
        put16(packet, 20, sourcePort)
        put16(packet, 22, destinationPort)
        put16(packet, 24, udpLength)
        System.arraycopy(payload, 0, packet, 28, payload.size)
        // UDP checksum zero is permitted for IPv4.
        return packet
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < offset + length) sum += (data[i].toInt() and 0xFF) shl 8
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun put16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    private fun createNotification(): Notification {
        val channelId = "auralis_adblock"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Auralis Ad Blocking",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Auralis DNS protection")
            .setContentText("Filtering known ad and tracker domains")
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    override fun onDestroy() {
        stopFiltering()
        super.onDestroy()
    }

    private data class ParsedDnsUdp(
        val sourceIp: ByteArray,
        val destinationIp: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val dnsPayload: ByteArray
    )

    companion object {
        const val ACTION_STOP = "dev.pauze.auralis.action.STOP_ADBLOCK"
        private const val NOTIFICATION_ID = 4201
        private const val DNS_PORT = 53
        private const val VPN_ADDRESS = "10.111.0.1"
        private const val VPN_DNS = "10.111.0.2"
        private const val UPSTREAM_DNS = "1.1.1.1"

        // A compact starter set. A production build should refresh maintained lists
        // with explicit user-visible source and update controls.
        private val BLOCKED_DOMAINS = setOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "adnxs.com",
            "adsrvr.org",
            "adform.net",
            "taboola.com",
            "outbrain.com",
            "scorecardresearch.com",
            "app-measurement.com",
            "tracking.example"
        )
    }
}

package com.voidprivacy.app.tools.tor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voidprivacy.app.MainActivity
import com.voidprivacy.app.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Tor VPN Service with bidirectional TCP forwarding.
 *
 * Implements a minimal userspace TCP stack:
 * - Parses IP/TCP packets from the TUN interface
 * - Handles TCP handshake (SYN → SYN-ACK → ACK)
 * - Forwards data through Tor SOCKS proxy (127.0.0.1:9050)
 * - Reconstructs response IP/TCP packets and writes them back to TUN
 * - Handles TCP teardown (FIN → FIN-ACK)
 * - Kill switch: drops all traffic if Tor stops
 *
 * Limitations:
 * - No TCP retransmission (relies on SOCKS socket reliability)
 * - No window scaling (fixed 65535 window)
 * - No TCP options (MSS, SACK, timestamps)
 * - UDP dropped (DNS handled by Tor SOCKS for TCP connections)
 */
class TorVpnService : VpnService() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.voidprivacy.app.AppSettings.wrapLocale(newBase))
    }


    companion object {
        private const val TAG = "TorVpn"
        private const val CHANNEL_ID = "tor_vpn"
        private const val NOTIFICATION_ID = 42
        private const val TOR_SOCKS_PORT = 9050
        private const val MTU = 1500
        private const val VPN_ADDR = "10.215.173.1"
        private const val DNS_ADDR = "10.215.173.2"
        private const val WINDOW_SIZE = 65535

        /** True while the VPN TUN is up — other screens use this for metadata warnings. */
        @Volatile
        @JvmStatic
        var isVpnActive = false
            private set

        // TCP flags
        private const val FIN = 0x01
        private const val SYN = 0x02
        private const val RST = 0x04
        private const val PSH = 0x08
        private const val ACK = 0x10
    }

    /** Per-connection TCP state */
    private class TcpConnection(
        val srcIp: String,
        val dstIp: String,
        val srcPort: Int,
        val dstPort: Int,
    ) {
        var socket: Socket? = null
        var mySeq: Long = (Math.random() * 100_000).toLong() // our ISN
        var theirSeq: Long = 0   // app's sequence number
        var established = false
        var closing = false
        val key = "$srcIp:$srcPort->$dstIp:$dstPort"
    }

    private var tunFd: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private val connections = ConcurrentHashMap<String, TcpConnection>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        isVpnActive = true
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return
        if (!TorManager.isRunning) {
            Log.w(TAG, "Tor not running — VPN not started (kill switch)")
            stopSelf()
            return
        }

        try {
            val builder = Builder()
                .setSession(getString(R.string.torvpn_notification_title))
                .setMtu(MTU)
                .addAddress(VPN_ADDR, 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(DNS_ADDR)
                .setBlocking(false)
            builder.addDisallowedApplication(packageName)

            tunFd = builder.establish() ?: run {
                Log.e(TAG, "Failed to establish TUN")
                stopSelf()
                return
            }

            running = true
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
            Thread({ packetLoop() }, "TorVpn-Loop").start()
            Log.i(TAG, "Tor VPN started (bidirectional)")
        } catch (e: Exception) {
            Log.e(TAG, "VPN start failed: ${e.message}")
            stopVpn()
        }
    }

    // ─── Packet Loop ───────────────────────────────────────────────

    private fun packetLoop() {
        val fd = tunFd ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(MTU + 64)

        while (running) {
            try {
                val len = input.read(buf)
                if (len <= 0) continue
                if (!TorManager.isRunning) { Thread.sleep(1000); continue }

                val version = (buf[0].toInt() shr 4) and 0xF
                if (version != 4) continue

                val ihl = (buf[0].toInt() and 0xF) * 4
                val protocol = buf[9].toInt() and 0xFF
                val srcIp = ipStr(buf, 12)
                val dstIp = ipStr(buf, 16)

                when (protocol) {
                    6 -> handleTcp(buf, len, ihl, srcIp, dstIp, output)   // TCP
                    17 -> handleUdp(buf, len, ihl, srcIp, dstIp, output)  // UDP (DNS)
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "Packet error: ${e.message}")
            }
        }
        cleanup()
    }

    private fun handleTcp(
        buf: ByteArray, len: Int, ihl: Int,
        srcIp: String, dstIp: String,
        output: FileOutputStream,
    ) {
        if (len < ihl + 20) return
        val t = ihl // TCP header offset

        val srcPort = u16(buf, t)
        val dstPort = u16(buf, t + 2)
        val seq = u32(buf, t + 4)
        val ackNum = u32(buf, t + 8)
        val tcpHl = ((buf[t + 12].toInt() shr 4) and 0xF) * 4
        val flags = buf[t + 13].toInt() and 0xFF
        val payloadOff = ihl + tcpHl
        val payloadLen = len - payloadOff

        val connKey = "$srcIp:$srcPort->$dstIp:$dstPort"

        when {
            // ── SYN: new connection ──
            (flags and SYN != 0) && (flags and ACK == 0) -> {
                val conn = TcpConnection(srcIp, dstIp, srcPort, dstPort)
                conn.theirSeq = seq + 1 // expect next seq = seq+1
                connections[connKey] = conn

                // Send SYN-ACK
                conn.mySeq++ // SYN consumes 1 seq
                val synAck = buildPacket(dstIp, srcIp, dstPort, srcPort, conn.mySeq, conn.theirSeq, SYN or ACK, ByteArray(0))
                synchronized(output) { output.write(synAck); output.flush() }

                // Connect through Tor SOCKS in background
                Thread({
                    try {
                        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", TOR_SOCKS_PORT))
                        val sock = Socket(proxy)
                        sock.connect(InetSocketAddress(dstIp, dstPort), 15_000)
                        conn.socket = sock
                        conn.established = true
                        // Start response reader
                        Thread({ responseReader(conn, output) }, "TorVpn-Resp-${conn.key}").start()
                    } catch (e: Exception) {
                        Log.w(TAG, "Tor connect failed: ${e.message}")
                        // Send RST
                        val rst = buildPacket(dstIp, srcIp, dstPort, srcPort, conn.mySeq, conn.theirSeq, RST or ACK, ByteArray(0))
                        try { synchronized(output) { output.write(rst); output.flush() } } catch (_: Exception) {}
                        connections.remove(connKey)
                    }
                }, "TorVpn-Connect-$connKey").start()
            }

            // ── ACK (handshake completion or data ack) ──
            (flags and ACK != 0) && payloadLen == 0 -> {
                val conn = connections[connKey] ?: return
                conn.theirSeq = seq // update expected seq
                // Pure ACK — no action needed
            }

            // ── DATA (PSH+ACK) ──
            (flags and ACK != 0) && payloadLen > 0 -> {
                val conn = connections[connKey] ?: return
                conn.theirSeq = seq + payloadLen

                // ACK the data
                val ackPkt = buildPacket(dstIp, srcIp, dstPort, srcPort, conn.mySeq, conn.theirSeq, ACK, ByteArray(0))
                synchronized(output) { output.write(ackPkt); output.flush() }

                // Forward payload through SOCKS
                val sock = conn.socket
                if (sock != null && conn.established) {
                    try {
                        sock.getOutputStream().write(buf, payloadOff, payloadLen)
                        sock.getOutputStream().flush()
                    } catch (_: Exception) {
                        closeConnection(conn, connKey, dstIp, srcIp, dstPort, srcPort, output)
                    }
                }
            }

            // ── FIN: close connection ──
            (flags and FIN != 0) -> {
                val conn = connections[connKey]
                if (conn != null) {
                    conn.theirSeq = seq + 1
                    // Send FIN-ACK
                    val finAck = buildPacket(dstIp, srcIp, dstPort, srcPort, conn.mySeq, conn.theirSeq, FIN or ACK, ByteArray(0))
                    conn.mySeq++
                    synchronized(output) { output.write(finAck); output.flush() }
                    closeConnection(conn, connKey, dstIp, srcIp, dstPort, srcPort, output)
                }
            }
        }
    }

    // ─── UDP / DNS Handling ─────────────────────────────────────────

    private fun handleUdp(
        buf: ByteArray, len: Int, ihl: Int,
        srcIp: String, dstIp: String,
        output: FileOutputStream,
    ) {
        if (len < ihl + 8) return
        val u = ihl // UDP header offset
        val srcPort = u16(buf, u)
        val dstPort = u16(buf, u + 2)
        val udpLen = u16(buf, u + 4)
        val payloadOff = ihl + 8
        val payloadLen = udpLen - 8
        if (payloadLen <= 0 || payloadOff + payloadLen > len) return

        // Only handle DNS (port 53) — drop other UDP
        if (dstPort != 53) return

        val dnsQuery = buf.copyOfRange(payloadOff, payloadOff + payloadLen)

        // Resolve DNS-over-TCP through Tor SOCKS in a separate thread
        Thread({
            try {
                val dnsResponse = resolveDnsOverTcp(dnsQuery)
                if (dnsResponse != null) {
                    val pkt = buildUdpPacket(dstIp, srcIp, 53, srcPort, dnsResponse)
                    synchronized(output) { output.write(pkt); output.flush() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "DNS resolution failed: ${e.message}")
            }
        }, "TorVpn-DNS").start()
    }

    /**
     * Resolve a DNS query via DNS-over-TCP through Tor SOCKS.
     * Connects to Cloudflare DNS (1.1.1.1:53) through Tor.
     * DNS-over-TCP format: [2-byte length][DNS wire format] (RFC 1035 §4.2.2)
     */
    private fun resolveDnsOverTcp(dnsQuery: ByteArray): ByteArray? {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", TOR_SOCKS_PORT))
        val sock = Socket(proxy)
        try {
            sock.connect(InetSocketAddress("1.1.1.1", 53), 10_000)
            sock.soTimeout = 10_000
            val out = sock.getOutputStream()
            val inp = sock.getInputStream()

            // Send: 2-byte length prefix + DNS query
            val lenBytes = byteArrayOf((dnsQuery.size shr 8).toByte(), dnsQuery.size.toByte())
            out.write(lenBytes)
            out.write(dnsQuery)
            out.flush()

            // Read: 2-byte length prefix + DNS response
            val respLenBuf = ByteArray(2)
            var read = 0
            while (read < 2) {
                val n = inp.read(respLenBuf, read, 2 - read)
                if (n <= 0) return null
                read += n
            }
            val respLen = ((respLenBuf[0].toInt() and 0xFF) shl 8) or (respLenBuf[1].toInt() and 0xFF)
            if (respLen <= 0 || respLen > 4096) return null

            val dnsResponse = ByteArray(respLen)
            read = 0
            while (read < respLen) {
                val n = inp.read(dnsResponse, read, respLen - read)
                if (n <= 0) return null
                read += n
            }
            return dnsResponse
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
    }

    /**
     * Build a complete IP packet with UDP header and payload.
     */
    private fun buildUdpPacket(
        srcIp: String, dstIp: String,
        srcPort: Int, dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLen = 8 + payload.size
        val ipLen = 20 + udpLen
        val pkt = ByteArray(ipLen)

        // IP Header
        pkt[0] = 0x45
        pkt[1] = 0x00
        putU16(pkt, 2, ipLen)
        putU16(pkt, 4, 0)
        putU16(pkt, 6, 0x4000)
        pkt[8] = 64 // TTL
        pkt[9] = 17 // protocol: UDP
        putU16(pkt, 10, 0) // IP checksum (fill later)
        putIp(pkt, 12, srcIp)
        putIp(pkt, 16, dstIp)
        val ipCksum = checksum(pkt, 0, 20)
        putU16(pkt, 10, ipCksum)

        // UDP Header
        val u = 20
        putU16(pkt, u, srcPort)
        putU16(pkt, u + 2, dstPort)
        putU16(pkt, u + 4, udpLen)
        putU16(pkt, u + 6, 0) // UDP checksum (fill later)

        // Payload
        System.arraycopy(payload, 0, pkt, u + 8, payload.size)

        // UDP checksum (pseudo-header + UDP header + payload)
        val udpCksum = udpChecksum(pkt, srcIp, dstIp, u, udpLen)
        putU16(pkt, u + 6, udpCksum)

        return pkt
    }

    /** UDP checksum with pseudo-header (same structure as TCP) */
    private fun udpChecksum(pkt: ByteArray, srcIp: String, dstIp: String, udpOff: Int, udpLen: Int): Int {
        val pseudo = ByteArray(12 + udpLen)
        putIp(pseudo, 0, srcIp)
        putIp(pseudo, 4, dstIp)
        pseudo[8] = 0
        pseudo[9] = 17 // UDP
        putU16(pseudo, 10, udpLen)
        System.arraycopy(pkt, udpOff, pseudo, 12, udpLen)

        var sum = 0L
        var i = 0
        while (i < pseudo.size - 1) {
            sum += ((pseudo[i].toInt() and 0xFF) shl 8) or (pseudo[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (pseudo.size % 2 != 0) {
            sum += (pseudo[pseudo.size - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = (sum.inv() and 0xFFFF).toInt()
        return if (result == 0) 0xFFFF else result // UDP: 0 means no checksum, use 0xFFFF instead
    }

    // ─── Response Reader (SOCKS → TUN) ────────────────────────────

    private fun responseReader(conn: TcpConnection, output: FileOutputStream) {
        try {
            val sock = conn.socket ?: return
            val buf = ByteArray(4096)
            val input = sock.getInputStream()

            while (running && !sock.isClosed && !conn.closing) {
                val n = input.read(buf)
                if (n <= 0) break

                // Build response packet: src=original dst, dst=original src
                val payload = buf.copyOfRange(0, n)
                val pkt = buildPacket(
                    conn.dstIp, conn.srcIp,
                    conn.dstPort, conn.srcPort,
                    conn.mySeq, conn.theirSeq,
                    PSH or ACK, payload
                )
                conn.mySeq += n

                synchronized(output) {
                    output.write(pkt)
                    output.flush()
                }
            }
        } catch (_: Exception) {
        } finally {
            if (!conn.closing) {
                // Send FIN to app
                try {
                    val fin = buildPacket(conn.dstIp, conn.srcIp, conn.dstPort, conn.srcPort, conn.mySeq, conn.theirSeq, FIN or ACK, ByteArray(0))
                    conn.mySeq++
                    synchronized(output) { output.write(fin); output.flush() }
                } catch (_: Exception) {}
            }
            connections.remove(conn.key)
            try { conn.socket?.close() } catch (_: Exception) {}
        }
    }

    // ─── Packet Building ───────────────────────────────────────────

    /**
     * Build a complete IP packet with TCP header and payload.
     */
    private fun buildPacket(
        srcIp: String, dstIp: String,
        srcPort: Int, dstPort: Int,
        seq: Long, ack: Long,
        flags: Int, payload: ByteArray,
    ): ByteArray {
        val tcpLen = 20 + payload.size
        val ipLen = 20 + tcpLen
        val pkt = ByteArray(ipLen)

        // ── IP Header (20 bytes) ──
        pkt[0] = 0x45 // version=4, IHL=5
        pkt[1] = 0x00 // DSCP/ECN
        putU16(pkt, 2, ipLen) // total length
        putU16(pkt, 4, 0) // identification
        putU16(pkt, 6, 0x4000) // flags: DF
        pkt[8] = 64 // TTL
        pkt[9] = 6 // protocol: TCP
        putU16(pkt, 10, 0) // checksum (fill later)
        putIp(pkt, 12, srcIp)
        putIp(pkt, 16, dstIp)

        // IP checksum
        val ipCksum = checksum(pkt, 0, 20)
        putU16(pkt, 10, ipCksum)

        // ── TCP Header (20 bytes) ──
        val t = 20
        putU16(pkt, t, srcPort)
        putU16(pkt, t + 2, dstPort)
        putU32(pkt, t + 4, seq)
        putU32(pkt, t + 8, ack)
        pkt[t + 12] = 0x50 // data offset: 5 words (20 bytes)
        pkt[t + 13] = flags.toByte()
        putU16(pkt, t + 14, WINDOW_SIZE) // window
        putU16(pkt, t + 16, 0) // checksum (fill later)
        putU16(pkt, t + 18, 0) // urgent pointer

        // Payload
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, pkt, t + 20, payload.size)
        }

        // TCP checksum (pseudo-header + TCP header + payload)
        val tcpCksum = tcpChecksum(pkt, srcIp, dstIp, t, tcpLen)
        putU16(pkt, t + 16, tcpCksum)

        return pkt
    }

    /** IP header checksum: ones-complement sum of 16-bit words */
    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    /** TCP checksum with pseudo-header */
    private fun tcpChecksum(pkt: ByteArray, srcIp: String, dstIp: String, tcpOff: Int, tcpLen: Int): Int {
        // Pseudo-header: src IP (4) + dst IP (4) + zero (1) + protocol (1) + TCP length (2) = 12 bytes
        val pseudo = ByteArray(12 + tcpLen)
        putIp(pseudo, 0, srcIp)
        putIp(pseudo, 4, dstIp)
        pseudo[8] = 0
        pseudo[9] = 6 // TCP
        putU16(pseudo, 10, tcpLen)
        System.arraycopy(pkt, tcpOff, pseudo, 12, tcpLen)

        var sum = 0L
        var i = 0
        while (i < pseudo.size - 1) {
            sum += ((pseudo[i].toInt() and 0xFF) shl 8) or (pseudo[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (pseudo.size % 2 != 0) {
            sum += (pseudo[pseudo.size - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private fun closeConnection(conn: TcpConnection, key: String, srcIp: String, dstIp: String, srcPort: Int, dstPort: Int, output: FileOutputStream) {
        conn.closing = true
        connections.remove(key)
        try { conn.socket?.close() } catch (_: Exception) {}
    }

    private fun ipStr(buf: ByteArray, off: Int): String =
        "${buf[off].toInt() and 0xFF}.${buf[off + 1].toInt() and 0xFF}.${buf[off + 2].toInt() and 0xFF}.${buf[off + 3].toInt() and 0xFF}"

    private fun putIp(buf: ByteArray, off: Int, ip: String) {
        val parts = ip.split(".")
        for (i in 0..3) buf[off + i] = parts[i].toInt().toByte()
    }

    private fun u16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    private fun u32(buf: ByteArray, off: Int): Long =
        ((buf[off].toLong() and 0xFF) shl 24) or ((buf[off + 1].toLong() and 0xFF) shl 16) or
        ((buf[off + 2].toLong() and 0xFF) shl 8) or (buf[off + 3].toLong() and 0xFF)

    private fun putU16(buf: ByteArray, off: Int, v: Int) {
        buf[off] = (v shr 8).toByte()
        buf[off + 1] = v.toByte()
    }

    private fun putU32(buf: ByteArray, off: Int, v: Long) {
        buf[off] = (v shr 24).toByte()
        buf[off + 1] = (v shr 16).toByte()
        buf[off + 2] = (v shr 8).toByte()
        buf[off + 3] = v.toByte()
    }

    // ─── Lifecycle ─────────────────────────────────────────────────

    private fun cleanup() {
        connections.values.forEach { try { it.socket?.close() } catch (_: Exception) {} }
        connections.clear()
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null
    }

    fun stopVpn() {
        running = false
        isVpnActive = false
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Tor VPN stopped")
    }

    override fun onRevoke() { stopVpn() }

    override fun onDestroy() {
        running = false
        isVpnActive = false
        cleanup()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.torvpn_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.torvpn_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TorVpnService::class.java).apply { action = "STOP" }
        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.torvpn_notification_title))
            .setContentText(getString(R.string.torvpn_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPending)
            .addAction(0, getString(R.string.torvpn_stop), stopPending)
            .setOngoing(true)
            .build()
    }
}

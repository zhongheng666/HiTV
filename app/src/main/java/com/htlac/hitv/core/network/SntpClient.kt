package com.htlac.hitv.core.network

import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * 这是一个轻量级的 SNTP 客户端
 * 负责底层 UDP 协议的封包、发包和解包，获取精准的网络时间
 */
object SntpClient {
    private const val ORIGINATE_TIME_OFFSET = 24
    private const val RECEIVE_TIME_OFFSET = 32
    private const val TRANSMIT_TIME_OFFSET = 40
    private const val NTP_PACKET_SIZE = 48
    private const val NTP_PORT = 123
    private const val NTP_MODE_CLIENT = 3
    private const val NTP_VERSION = 3
    private const val OFFSET_1900_TO_1970 = 2208988800L

    var ntpTime: Long = 0
        private set

    /**
     * 向指定的 NTP 服务器请求时间
     */
    fun requestTime(host: String, timeout: Int): Boolean {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = timeout
            val address = InetAddress.getByName(host)
            val buffer = ByteArray(NTP_PACKET_SIZE)
            val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)

            // 设置版本号和模式 (Version 3, Mode 3 Client)
            buffer[0] = (NTP_MODE_CLIENT or (NTP_VERSION shl 3)).toByte()

            val requestTime = System.currentTimeMillis()
            val requestTicks = SystemClock.elapsedRealtime()
            writeTimeStamp(buffer, TRANSMIT_TIME_OFFSET, requestTime)

            socket.send(request)

            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)

            val responseTicks = SystemClock.elapsedRealtime()
            val responseTime = requestTime + (responseTicks - requestTicks)

            val originateTime = readTimeStamp(buffer, ORIGINATE_TIME_OFFSET)
            val receiveTime = readTimeStamp(buffer, RECEIVE_TIME_OFFSET)
            val transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET)

            val clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2

            ntpTime = responseTime + clockOffset
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            socket?.close()
        }
    }

    private fun read32(buffer: ByteArray, offset: Int): Long {
        val b0 = buffer[offset].toLong() and 0xFF
        val b1 = buffer[offset + 1].toLong() and 0xFF
        val b2 = buffer[offset + 2].toLong() and 0xFF
        val b3 = buffer[offset + 3].toLong() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readTimeStamp(buffer: ByteArray, offset: Int): Long {
        val seconds = read32(buffer, offset)
        val fraction = read32(buffer, offset + 4)
        if (seconds == 0L && fraction == 0L) return 0L
        return ((seconds - OFFSET_1900_TO_1970) * 1000) + ((fraction * 1000L) / 0x100000000L)
    }

    private fun writeTimeStamp(buffer: ByteArray, offset: Int, time: Long) {
        var offsetTemp = offset
        var seconds = time / 1000L
        val milliseconds = time - seconds * 1000L
        seconds += OFFSET_1900_TO_1970

        buffer[offsetTemp++] = (seconds shr 24).toByte()
        buffer[offsetTemp++] = (seconds shr 16).toByte()
        buffer[offsetTemp++] = (seconds shr 8).toByte()
        buffer[offsetTemp++] = (seconds shr 0).toByte()

        val fraction = milliseconds * 0x100000000L / 1000L
        buffer[offsetTemp++] = (fraction shr 24).toByte()
        buffer[offsetTemp++] = (fraction shr 16).toByte()
        buffer[offsetTemp++] = (fraction shr 8).toByte()
        buffer[offsetTemp] = (Math.random() * 255.0).toInt().toByte()
    }
}
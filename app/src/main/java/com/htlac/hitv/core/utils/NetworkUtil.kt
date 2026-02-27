package com.htlac.hitv.core.utils

import java.net.NetworkInterface

object NetworkUtil {
    // 获取电视盒子的局域网 IPv4 地址 (例如 192.168.1.100)
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    // 排除回环地址(127.0.0.1) 和 IPv6 地址
                    if (!addr.isLoopbackAddress && addr.hostAddress?.indexOf(':') == -1) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}
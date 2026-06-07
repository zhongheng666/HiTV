package com.htlac.hitv.core.utils

import java.security.MessageDigest

object HashUtil {
    // 将传入的字符串生成 32 位 MD5 唯一标识
    fun md5(string: String): String {
        return try {
            val bytes = MessageDigest.getInstance("MD5").digest(string.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            string.hashCode().toString() // 极端异常兜底方案
        }
    }
}
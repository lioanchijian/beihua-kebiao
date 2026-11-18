package com.buct.course.login

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 北化统一身份认证 (sidportal.buct.edu.cn, 金智 linkid 平台) 的密码加密。
 *
 * 前端(loginNew.js / cas-login main bundle)实现:
 *   CryptoJS.AES.encrypt(明文, key=Base64.parse(croypto), {mode: ECB, padding: Pkcs7}).toString()
 * 即: AES-ECB, PKCS7 填充, key 为 login-croypto 的 Base64 解码字节, 输出 Base64。
 * Java 侧用 AES/ECB/PKCS5Padding（16 字节块下 PKCS5 与 PKCS7 等价）。
 */
internal object BuctAesCrypto {
    class CryptoException(message: String) : Exception(message)

    fun encrypt(b64Key: String, plain: String): String {
        val keyBytes = try {
            Base64.decode(b64Key, Base64.NO_WRAP)
        } catch (_: Exception) {
            throw CryptoException("统一认证密钥格式无效")
        }
        if (keyBytes.isEmpty()) throw CryptoException("统一认证密钥为空")
        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = try {
            Cipher.getInstance("AES/ECB/PKCS5Padding")
        } catch (e: Exception) {
            throw CryptoException("AES 初始化失败")
        }
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ct = try {
            cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            throw CryptoException("密码加密失败")
        }
        return Base64.encodeToString(ct, Base64.NO_WRAP)
    }
}

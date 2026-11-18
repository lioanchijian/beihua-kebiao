package com.buct.course.login

import android.util.Base64
import org.bouncycastle.asn1.gm.GMNamedCurves
import org.bouncycastle.crypto.digests.SM3Digest
import java.math.BigInteger
import java.security.SecureRandom

/**
 * 北化统一身份认证 (portal.buct.edu.cn) 的 SM2 国密加密。
 *
 * 输出必须与前端 js/sm2.min.js 的 window.sm2.encrypt 完全一致：
 *   - 公钥: Base64 (04||X||Y, 65 字节)
 *   - 密文: Base64( hex(C1 无04前缀 || C3 || C2) ), C1C3C2 模式
 *   - 密码明文: UTF-8
 */
internal object BuctSm2Crypto {
    class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private const val CURVE_NAME = "sm2p256v1"

    private val curveParams by lazy { GMNamedCurves.getByName(CURVE_NAME) }
    private val random by lazy { SecureRandom() }

    fun encrypt(plaintext: String, publicKeyBase64: String): String {
        val publicKeyBytes = runCatching { Base64.decode(publicKeyBase64, Base64.NO_WRAP) }
            .getOrElse { throw CryptoException("SM2 公钥不是有效的 Base64") }
        val curve = curveParams.curve
        val publicPoint = runCatching { curve.decodePoint(publicKeyBytes) }
            .getOrElse { throw CryptoException("SM2 公钥点无效") }
        val plainBytes = plaintext.toByteArray(Charsets.UTF_8)
        val n = curveParams.n

        // 随机数 k ∈ [1, n-1]
        var k: BigInteger
        do {
            k = BigInteger(n.bitLength(), random)
        } while (k.signum() <= 0 || k >= n)

        // C1 = k * G  (无 04 前缀的 x1||y1, 各32字节)
        val c1Point = curveParams.g.multiply(k).normalize()
        val x1Hex = leftPadHex(c1Point.affineXCoord.toBigInteger())
        val y1Hex = leftPadHex(c1Point.affineYCoord.toBigInteger())

        // (x2, y2) = k * 公钥点
        val p2 = publicPoint.multiply(k).normalize()
        val x2Hex = leftPadHex(p2.affineXCoord.toBigInteger())
        val y2Hex = leftPadHex(p2.affineYCoord.toBigInteger())

        // 对称密钥流 t = KDF(x2||y2, len)
        val z = x2Hex.hexToBytes() + y2Hex.hexToBytes()
        val t = kdf(z, plainBytes.size)

        // C2 = msg XOR t
        val c2 = ByteArray(plainBytes.size)
        for (i in plainBytes.indices) {
            c2[i] = (plainBytes[i].toInt() xor t[i].toInt()).toByte()
        }

        // C3 = SM3(x2 || M || y2)
        val c3Hex = sm3Hex(x2Hex + plainBytes.toHexString() + y2Hex)

        // C1(无04) || C3 || C2 -> hex 解码为原始字节 -> Base64
        val hex = x1Hex + y1Hex + c3Hex + c2.toHexString()
        return Base64.encodeToString(hex.hexToBytes(), Base64.NO_WRAP)
    }

    /** SM2 标准 KDF: 逐轮 SM3(Z || 计数器BE32)，拼接至 len 字节。 */
    private fun kdf(z: ByteArray, len: Int): ByteArray {
        val digest = SM3Digest()
        val out = ByteArray(len)
        var offset = 0
        var counter = 1
        while (offset < len) {
            digest.reset()
            digest.update(z, 0, z.size)
            digest.update(
                byteArrayOf(
                    (counter ushr 24).toByte(),
                    (counter ushr 16).toByte(),
                    (counter ushr 8).toByte(),
                    counter.toByte()
                ), 0, 4
            )
            val hash = ByteArray(digest.digestSize)
            digest.doFinal(hash, 0)
            val take = minOf(hash.size, len - offset)
            System.arraycopy(hash, 0, out, offset, take)
            offset += take
            counter++
        }
        return out
    }

    /** SM3(hex 字符串转字节后) -> 64 位 hex 字符串 */
    private fun sm3Hex(hex: String): String {
        val digest = SM3Digest()
        val input = hex.hexToBytes()
        digest.update(input, 0, input.size)
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out.toHexString()
    }

    /** BigInteger -> 32字节大端 hex (64字符) */
    private fun leftPadHex(value: BigInteger): String {
        val raw = value.toByteArray()
        val padded = ByteArray(32)
        val copyLen = minOf(raw.size, 32)
        System.arraycopy(raw, raw.size - copyLen, padded, 32 - copyLen, copyLen)
        return padded.toHexString()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val raw = toByteArray(Charsets.US_ASCII)
        val out = ByteArray(raw.size / 2)
        for (i in out.indices) {
            out[i] = Integer.parseInt(substring(i * 2, i * 2 + 2), 16).toByte()
        }
        return out
    }
}

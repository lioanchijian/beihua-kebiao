package com.buct.course.login

import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 北化统一认证/教务 (*.buct.edu.cn) 专用 TLS 信任管理器（双链支持）。
 *
 * 学校有两个认证入口，证书签发者不同：
 *  - 校园网入口 portal.buct.edu.cn：GlobalSign RSA OV SSL CA 2018 ← GlobalSign Root CA - R3
 *    （部分 Android 系统信任库缺 R3 根，需内置）
 *  - 公网入口 experimental-auth-endpoint.buct.edu.cn：ZeroSSL ECC DV SSL CA 2 ← Sectigo E46
 *    （部分 Android 版本缺 E46/USERTrust，需内置）
 *
 * 校验顺序：先走系统默认信任库；失败时用手工签名链（内置 GlobalSign + ZeroSSL 链）兜底。
 */
class BuctTlsTrustManager : X509TrustManager {

    private val systemTrustManager: X509TrustManager

    // GlobalSign 链
    private val gsIntermediate: X509Certificate  // GlobalSign RSA OV SSL CA 2018
    private val gsTrustPoint: X509Certificate   // GlobalSign Root CA - R3（信任点）
    // ZeroSSL 链
    private val zeroSslIntermediate: X509Certificate  // ZeroSSL ECC DV SSL CA 2
    private val sectigoTrustPoint: X509Certificate    // Sectigo Public Server Authentication Root E46（信任点）

    init {
        try {
            val factory = CertificateFactory.getInstance("X.509")
            gsIntermediate = loadCert(BuctCertificates.INTERMEDIATE_B64, factory)
            gsTrustPoint = loadCert(BuctCertificates.ROOT_B64, factory)
            zeroSslIntermediate = loadCert(BuctCertificates.ZEROSSL_INTERMEDIATE_B64, factory)
            sectigoTrustPoint = loadCert(BuctCertificates.SECTIGO_E46_B64, factory)
            systemTrustManager = defaultSystemTrustManager()
        } catch (e: Exception) {
            throw IllegalStateException("无法加载北化证书链", e)
        }
    }

    private fun loadCert(base64: String, factory: CertificateFactory): X509Certificate {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        return factory.generateCertificate(bytes.inputStream()) as X509Certificate
    }

    private fun defaultSystemTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        for (tm in factory.trustManagers) {
            if (tm is X509TrustManager) return tm
        }
        throw IllegalStateException("系统无默认 TrustManager")
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        // 客户端证书校验：本场景不需要
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        // 1) 系统默认信任库优先
        try {
            systemTrustManager.checkServerTrusted(chain, authType)
            return
        } catch (_: CertificateException) {
            // 继续内置链兜底
        }
        // 2) 手工签名链：GlobalSign 链 / ZeroSSL 链
        val leaf = chain.firstOrNull()
            ?: throw CertificateException("无服务器证书")
        if (verifyChain(leaf, gsIntermediate, gsTrustPoint)) return
        if (verifyChain(leaf, zeroSslIntermediate, sectigoTrustPoint)) return
        throw CertificateException("北化证书校验失败：未知证书链")
    }

    /**
     * 手工验证：leaf 由 intermediate 签发，intermediate 由 trustPoint 签发（trustPoint 为信任点）。
     */
    private fun verifyChain(leaf: X509Certificate, intermediate: X509Certificate, trustPoint: X509Certificate): Boolean {
        try {
            leaf.verify(intermediate.publicKey)
            intermediate.verify(trustPoint.publicKey)
            leaf.checkValidity()
            intermediate.checkValidity()
            return true
        } catch (_: GeneralSecurityException) {
            return false
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        systemTrustManager.acceptedIssuers + arrayOf(gsTrustPoint, gsIntermediate, sectigoTrustPoint, zeroSslIntermediate)

    /** 生成用于 OkHttp 的 SSLContext。 */
    fun sslSocketFactory(): javax.net.ssl.SSLSocketFactory {
        val context = javax.net.ssl.SSLContext.getInstance("TLS")
        context.init(null, arrayOf<javax.net.ssl.TrustManager>(this), null)
        return context.socketFactory
    }
}

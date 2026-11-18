package com.buct.course.academic

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 南京南软研究生管理信息系统（GMIS 5.x）的公共协议细节。
 *
 * 已在北京化工大学 gmis.buct.edu.cn 实测：
 * - 登录公钥是页面隐藏域 #pubkey，密码用 RSA/ECB/PKCS1Padding 加密后 Base64；
 * - 登录接口 home/stulogin_do，表单字段 json；验证码字段 VeriCode；
 * - 课表、成绩等接口返回明文 JSON，但部分老版本会把响应做 AES/ECB/PKCS7 + Base64。
 */
internal object SouthsoftProtocol {
    private const val AES_KEY = "southsoft12345!#"
    private const val LOGIN_PAGE = "home/stulogin"

    fun appPath(path: String): String = path

    fun loginPagePath(): String = LOGIN_PAGE

    fun loginSubmitPath(): String = "home/stulogin_do"

    fun captchaPath(): String = "home/verificationcode?codetype=stucode&t=%d"

    fun decodePayload(text: String): Any? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()?.let { return it }
        val bytes = runCatching { Base64.decode(trimmed, Base64.DEFAULT) }.getOrNull() ?: return null
        val plain = runCatching {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(AES_KEY.toByteArray(StandardCharsets.UTF_8), "AES"))
            cipher.doFinal(bytes).toString(StandardCharsets.UTF_8)
        }.getOrNull() ?: return null
        return runCatching { JSONTokener(plain).nextValue() }.getOrNull()
    }

    fun jsonObject(text: String): JSONObject? = when (val value = decodePayload(text)) {
        is JSONObject -> value
        is JSONArray -> null
        else -> null
    }

    fun jsonArray(text: String): JSONArray? = when (val value = decodePayload(text)) {
        is JSONArray -> value
        is JSONObject -> value.optJSONArray("data") ?: value.optJSONArray("rows") ?: value.optJSONArray("list")
        else -> null
    }

    fun rsaEncrypt(publicKeyPem: String, value: String): String {
        val body = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s"), "")
        val keyBytes = Base64.decode(body, Base64.DEFAULT)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    fun message(json: JSONObject): String = listOf("msg", "message", "error", "errmsg")
        .firstNotNullOfOrNull { key -> json.optString(key).takeIf(String::isNotBlank) }.orEmpty()

    fun isSuccess(json: JSONObject): Boolean {
        val flag = json.optString("jg", json.optString("success", json.optString("code")))
        return flag == "1" || flag.equals("true", true) || flag == "200" || flag == "0"
    }

    fun indicatesCaptcha(message: String): Boolean =
        Regex("验证码|校验码|附加码|captcha|verification", RegexOption.IGNORE_CASE).containsMatchIn(message)

    fun indicatesCredentials(message: String): Boolean =
        Regex("密码错误|密码不正确|用户名或密码|账号或密码|学号或密码|用户不存在|账号不存在|invalid.*(?:password|credentials)",
            RegexOption.IGNORE_CASE).containsMatchIn(message)
}

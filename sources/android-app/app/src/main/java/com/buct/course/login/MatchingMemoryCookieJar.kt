package com.buct.course.login

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

internal class MatchingMemoryCookieJar(
    private val clockMillis: () -> Long = System::currentTimeMillis
) : CookieJar {
    private val cookies = mutableListOf<StoredCookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = clockMillis()
        for (cookie in cookies) {
            this.cookies.removeAll { stored -> stored.cookie.hasSameIdentityAs(cookie) }
            if (cookie.expiresAt > now) {
                this.cookies += StoredCookie(cookie, url.host, url.port)
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = clockMillis()
        cookies.removeAll { it.cookie.expiresAt <= now }
        return cookies.map(StoredCookie::cookie).filter { it.matches(url) }
    }

    @Synchronized
    fun cookieHeaderFor(url: HttpUrl, setByHost: String? = null): String {
        val now = clockMillis()
        cookies.removeAll { it.cookie.expiresAt <= now }
        return cookies.asSequence()
            .filter { stored -> setByHost == null || stored.setByHost == setByHost }
            .map(StoredCookie::cookie)
            .filter { cookie -> cookie.matches(url) }
            .joinToString("; ") { cookie -> "${cookie.name}=${cookie.value}" }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
    }

    /** 把既有会话 Cookie 头（如 sidPortalSucceed 产出的 sidCookie）按主机种入 jar，供独立 client 复用。 */
    @Synchronized
    fun seed(cookieHeader: String, setByHost: String, port: Int = 443) {
        for (part in cookieHeader.split(";")) {
            val i = part.indexOf('=')
            if (i <= 0) continue
            val name = part.substring(0, i).trim()
            val value = part.substring(i + 1).trim()
            if (value.isEmpty()) continue
            val cookie = Cookie.Builder()
                .name(name)
                .value(value)
                .domain(domainFor(name, setByHost))
                .path("/")
                .expiresAt(Long.MAX_VALUE)
                .build()
            cookies.removeAll { it.cookie.name == name && it.cookie.domain == domainFor(name, setByHost) }
            cookies += StoredCookie(cookie, setByHost, port)
        }
    }

    /** 域归属：_zte_cid_ 为全局（.buct.edu.cn）；其余 Cookie 按响应来源 host 归属。
     *  2026-10-20 浏览器 Cookie 实证：SOURCEID_TGC/rg_objectid 由 sidportal 登录响应种在
     *  sidportal.buct.edu.cn，访问 /webauthn/register 时会携带；此前误按 sidself 域种入，
     *  导致 register 缺少登录上下文并返回 400 operation not allowed。 */
    private fun domainFor(name: String, setByHost: String): String = when (name) {
        "_zte_cid_" -> "buct.edu.cn"
        else -> setByHost
    }

    /** 返回由指定来源（host:port）的响应种下的全部 Cookie（不受 path 匹配限制），供会话迁移使用。 */
    @Synchronized
    fun cookiesSetBy(host: String, port: Int): List<Cookie> {
        val now = clockMillis()
        cookies.removeAll { it.cookie.expiresAt <= now }
        return cookies.filter { it.setByHost == host && it.setByPort == port }.map(StoredCookie::cookie)
    }

    private fun Cookie.hasSameIdentityAs(other: Cookie): Boolean =
        name == other.name && domain == other.domain && path == other.path

    private data class StoredCookie(val cookie: Cookie, val setByHost: String, val setByPort: Int)
}

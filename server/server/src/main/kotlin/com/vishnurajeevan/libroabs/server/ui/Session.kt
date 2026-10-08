package com.vishnurajeevan.libroabs.server.ui

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Stateless, signed login cookies. A token is `<expiry-epoch-seconds>.<hmac>`, signed with a key derived from the
 * configured password, so sessions survive restarts and are invalidated by changing the password.
 */
class SessionSigner(password: String, private val ttlSeconds: Long = DEFAULT_TTL_SECONDS) {
  private val key = MessageDigest.getInstance("SHA-256").digest("libro-webui-session:$password".toByteArray())
  private val passwordDigest = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())

  fun issue(nowSeconds: Long = System.currentTimeMillis() / 1000): String {
    val expiry = nowSeconds + ttlSeconds
    return "$expiry.${sign(expiry.toString())}"
  }

  fun verify(token: String?, nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
    if (token.isNullOrEmpty()) return false
    val dot = token.indexOf('.')
    if (dot <= 0) return false
    val expiryText = token.substring(0, dot)
    val expiry = expiryText.toLongOrNull() ?: return false
    if (expiry < nowSeconds) return false
    return constantTimeEquals(token.substring(dot + 1), sign(expiryText))
  }

  fun passwordMatches(candidate: String?): Boolean {
    if (candidate == null) return false
    return MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(candidate.toByteArray()), passwordDigest)
  }

  val ttl: Long get() = ttlSeconds

  private fun sign(data: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.toByteArray()))
  }

  private fun constantTimeEquals(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

  companion object {
    const val DEFAULT_TTL_SECONDS = 30L * 24 * 60 * 60
    const val COOKIE_NAME = "libro_session"
  }
}

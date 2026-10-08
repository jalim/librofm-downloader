package com.vishnurajeevan.libroabs.server.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionSignerTest {
  private val signer = SessionSigner("correct horse", ttlSeconds = 100)

  @Test
  fun validTokenVerifies() {
    assertTrue(signer.verify(signer.issue(nowSeconds = 1000), nowSeconds = 1050))
  }

  @Test
  fun expiredTokenIsRejected() {
    assertFalse(signer.verify(signer.issue(nowSeconds = 1000), nowSeconds = 1101))
  }

  @Test
  fun tamperedTokenIsRejected() {
    val token = signer.issue(nowSeconds = 1000)
    val (expiry, sig) = token.split('.')
    assertFalse(signer.verify("${expiry.toLong() + 1000}.$sig", nowSeconds = 1050))
    assertFalse(signer.verify("$expiry.${sig.reversed()}", nowSeconds = 1050))
    assertFalse(signer.verify("garbage", nowSeconds = 1050))
    assertFalse(signer.verify("", nowSeconds = 1050))
    assertFalse(signer.verify(null, nowSeconds = 1050))
    assertFalse(signer.verify("abc.def", nowSeconds = 1050))
  }

  @Test
  fun tokensFromAnotherPasswordAreRejected() {
    val other = SessionSigner("battery staple", ttlSeconds = 100)
    assertFalse(signer.verify(other.issue(nowSeconds = 1000), nowSeconds = 1050))
  }

  @Test
  fun passwordComparison() {
    assertTrue(signer.passwordMatches("correct horse"))
    assertFalse(signer.passwordMatches("correct horse "))
    assertFalse(signer.passwordMatches(""))
    assertFalse(signer.passwordMatches(null))
  }
}

package com.hack.kidhack.data.crypto

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 거래정보 전자서명(무결성) 생성 — 서버 [TxSignatureVerifier] 와 1:1 로 맞춘 규약.
 *
 * payload = `amount:refId:nonce` 를 공유 비밀키로 HMAC-SHA256 서명하고 hex 로 인코딩한다.
 * 송금은 refId=childId, QR 결제는 refId=0 을 쓴다(서버와 동일).
 *
 * ⚠️ 공유 비밀키를 앱에 두는 것 자체가 약하지만(리버싱으로 추출 가능 — VULN-MOBILE-RE-1 과 겹침),
 * 더 큰 결함은 서버가 서명 필드를 빼면 검증을 skip 한다는 점([VULN-EFIN-2])이라 앱은 정직하게 서명을 붙인다.
 */
object TxSigner {

    // 서버 기본값(kidhack.efin.tx-signing-secret)과 동일. 실서비스라면 안전 배포가 필요하다.
    private const val SECRET = "kidhack-efin-tx-secret"
    private val random = SecureRandom()

    /** 일회성 nonce(16바이트 hex). */
    fun newNonce(): String {
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** amount:refId:nonce 를 HMAC-SHA256 서명한 hex 문자열. */
    fun sign(amount: Long, refId: Long, nonce: String): String {
        val payload = "$amount:$refId:$nonce"
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
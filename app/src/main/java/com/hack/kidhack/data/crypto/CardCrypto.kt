package com.hack.kidhack.data.crypto

import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * 카드 등록용 하이브리드 E2E 암호화(클라이언트 측). 서버 [HybridDecryptor] 와 1:1 로 맞춘 규약.
 *
 * 절차: 1회용 AES-256 키로 payload(JSON)를 AES-GCM 암호화 → 그 AES 키를 서버 공개키로
 * RSA-OAEP(SHA-256, MGF1=SHA-256) 암호화. 결과 3필드(encryptedKey·iv·ciphertext)는 모두 표준 Base64.
 *
 * - AES: `AES/GCM/NoPadding`, IV 12바이트 랜덤, 인증태그 128비트(태그는 ciphertext 뒤에 붙는 JCA 기본형).
 * - RSA: `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` + 명시적 [OAEPParameterSpec]. MGF1 도 SHA-256 로
 *   못박는다(JCA 기본 MGF1 이 SHA-1 이라 명시하지 않으면 서버 복호가 깨진다).
 * - Base64 는 서버 `Base64.getDecoder()` 와 같은 표준(비-URL) 인코딩, 줄바꿈 없음.
 *
 * [SAFE-MOBILE-CARD] 의도적 대조군 — 안전 구현. 카드 등록은 E2E 하이브리드 암호화(AES-256-GCM + RSA-OAEP) +
 * nonce/timestamp 재전송 방지를 갖춰, 취약점을 심지 않았다. 백엔드 `/api/wallet/cards`(취약점 없는 대조군)와 1:1 대응.
 * 다른 모바일 취약점(VULN-MOBILE-*)과 비교 학습용이며, 이 파일에는 취약점을 넣지 않는다. 명세: mobile/docs/vuln-spec-mobile.md
 */
object CardCrypto {

    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val AES_KEY_BITS = 256
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    private val random = SecureRandom()

    data class EncryptedCard(
        val encryptedKey: String,
        val iv: String,
        val ciphertext: String,
    )

    /**
     * @param publicKeyDerBase64 `GET /wallet/cards/public-key` 의 `publicKey`(X.509 DER, Base64)
     * @param plaintextJson 카드 평문 payload JSON(UTF-8) — 서버 `CardPlain` 스키마와 필드명 일치
     */
    fun encrypt(publicKeyDerBase64: String, plaintextJson: String): EncryptedCard {
        val aesKey = ByteArray(AES_KEY_BITS / 8).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }

        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val ciphertext = aesCipher.doFinal(plaintextJson.toByteArray(Charsets.UTF_8))

        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(decode(publicKeyDerBase64)))
        val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION).apply {
            init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT),
            )
        }
        val encryptedKey = rsaCipher.doFinal(aesKey)

        return EncryptedCard(
            encryptedKey = encode(encryptedKey),
            iv = encode(iv),
            ciphertext = encode(ciphertext),
        )
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text.trim())
}

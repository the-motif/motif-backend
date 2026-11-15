package com.motif.settlement.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// AES-256-GCM / 저장 형식: Base64(IV 12바이트 + 암호문·태그)
// 키는 motif.crypto.secret-key (Base64 32바이트), 운영은 환경 변수로 주입
@Component
class FieldEncryptor(
    @Value("\${motif.crypto.secret-key}") secretKey: String,
) {

    private val key = SecretKeySpec(Base64.getDecoder().decode(secretKey).also {
        require(it.size == 32) { "motif.crypto.secret-key는 Base64 32바이트" }
    }, "AES")
    private val random = SecureRandom()

    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray()))
    }

    fun decrypt(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        // 응답용 / 뒤 4자리만
        fun mask(value: String): String = "*".repeat(maxOf(value.length - 4, 0)) + value.takeLast(4)
    }
}

// 엔티티 필드 자동 암호화 / Hibernate가 Spring 빈으로 생성
// 암호화 빈은 쓸 때 조회 → FieldEncryptor 없는 슬라이스 테스트(@DataJpaTest)에서도 생성 가능
@Component
@Converter
class EncryptedStringConverter(
    private val encryptor: ObjectProvider<FieldEncryptor>,
) : AttributeConverter<String?, String?> {

    override fun convertToDatabaseColumn(attribute: String?): String? = attribute?.let { encryptor.getObject().encrypt(it) }

    override fun convertToEntityAttribute(dbData: String?): String? = dbData?.let { encryptor.getObject().decrypt(it) }
}

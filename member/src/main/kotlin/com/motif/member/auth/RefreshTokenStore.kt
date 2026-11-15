package com.motif.member.auth

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

// refresh:{토큰 SHA-256} → 회원 ID / refresh-member:{회원 ID} → 그 회원의 토큰 해시 Set (전체 폐기용)
@Component
class RefreshTokenStore(
    private val redisTemplate: StringRedisTemplate,
    private val jwtProperties: JwtProperties,
) {

    private val secureRandom = SecureRandom()

    fun issue(memberId: Long): String {
        val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val hash = hash(token)
        val ttl = jwtProperties.refreshTokenTtl

        redisTemplate.opsForValue().set(TOKEN_PREFIX + hash, memberId.toString(), ttl)
        redisTemplate.opsForSet().add(memberKey(memberId), hash)
        redisTemplate.expire(memberKey(memberId), ttl)
        return token
    }

    // 꺼내면서 삭제 / 같은 토큰 두 번 사용 불가
    fun consume(token: String): Long? {
        val hash = hash(token)
        val memberId = redisTemplate.opsForValue().getAndDelete(TOKEN_PREFIX + hash)?.toLongOrNull()
            ?: return null
        redisTemplate.opsForSet().remove(memberKey(memberId), hash)
        return memberId
    }

    fun revoke(token: String) {
        consume(token)
    }

    // 탈퇴·정지 시 모든 기기 로그아웃
    fun revokeAll(memberId: Long) {
        val hashes = redisTemplate.opsForSet().members(memberKey(memberId)).orEmpty()
        if (hashes.isNotEmpty()) {
            redisTemplate.delete(hashes.map { TOKEN_PREFIX + it })
        }
        redisTemplate.delete(memberKey(memberId))
    }

    private fun memberKey(memberId: Long) = MEMBER_PREFIX + memberId

    private fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TOKEN_PREFIX = "refresh:"
        private const val MEMBER_PREFIX = "refresh-member:"
    }
}

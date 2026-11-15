package com.motif.member.auth

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

// 정지·탈퇴 회원의 남은 액세스 토큰 차단 / TTL = 액세스 토큰 수명
@Component
class BlockedMemberStore(
    private val redisTemplate: StringRedisTemplate,
    private val jwtProperties: JwtProperties,
) {

    fun block(memberId: Long) {
        redisTemplate.opsForValue().set(key(memberId), "1", jwtProperties.accessTokenTtl)
    }

    fun unblock(memberId: Long) {
        redisTemplate.delete(key(memberId))
    }

    fun isBlocked(memberId: Long): Boolean = redisTemplate.hasKey(key(memberId))

    private fun key(memberId: Long) = "blocked-member:$memberId"
}

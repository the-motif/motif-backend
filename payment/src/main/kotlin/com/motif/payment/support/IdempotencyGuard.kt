package com.motif.payment.support

import com.motif.common.exception.BusinessException
import com.motif.payment.exception.PaymentErrorCode
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

// 멱등 키 1차 방어 (Redis) / 2차는 DB 유니크 제약
// 흐름: 이미 끝난 요청이면 DB 결과 반환 → 처리 중이면 409 → 처음이면 선점 후 실행
@Component
class IdempotencyGuard(
    private val redisTemplate: StringRedisTemplate,
) {

    fun <T> execute(scope: String, key: String, findCompleted: () -> T?, action: () -> T): T {
        findCompleted()?.let { return it }

        val redisKey = "idem:$scope:$key"
        val acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, IN_PROGRESS_TTL) == true
        if (!acquired) {
            findCompleted()?.let { return it }
            throw BusinessException(PaymentErrorCode.REQUEST_IN_PROGRESS)
        }

        try {
            val result = action()
            redisTemplate.opsForValue().set(redisKey, DONE, DONE_TTL)
            return result
        } catch (e: Exception) {
            // 실패한 요청은 같은 키로 재시도 가능
            redisTemplate.delete(redisKey)
            throw e
        }
    }

    companion object {
        private const val IN_PROGRESS = "IN_PROGRESS"
        private const val DONE = "DONE"
        private val IN_PROGRESS_TTL: Duration = Duration.ofSeconds(30)
        private val DONE_TTL: Duration = Duration.ofHours(24)
    }
}

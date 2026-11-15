package com.motif.payment.support

import com.motif.common.exception.BusinessException
import com.motif.common.lock.RedisDistributedLock
import com.motif.payment.exception.PaymentErrorCode
import org.springframework.stereotype.Component
import java.time.Duration

// common 분산 락 + 실패 시 409
@Component
class RedisLockManager(
    private val distributedLock: RedisDistributedLock,
) {

    // 못 잡으면 기다리지 않고 409 / lease는 작업 최대 시간보다 길게
    fun <T> withLock(key: String, lease: Duration = Duration.ofSeconds(10), action: () -> T): T {
        val token = distributedLock.tryLock(key, lease)
            ?: throw BusinessException(PaymentErrorCode.REQUEST_IN_PROGRESS)
        try {
            return action()
        } finally {
            distributedLock.unlock(key, token)
        }
    }
}

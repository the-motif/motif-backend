package com.motif.settlement.service

import com.motif.common.exception.BusinessException
import com.motif.member.api.MemberQueryApi
import com.motif.settlement.domain.FeePolicy
import com.motif.settlement.domain.RevenueType
import com.motif.settlement.exception.SettlementErrorCode
import com.motif.settlement.repository.FeePolicyRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime

// 수수료 정책 관리 / 소급 금지: 적용·종료 시각은 현재 이후만
@Service
@Transactional
class FeePolicyService(
    private val feePolicyRepository: FeePolicyRepository,
    private val memberQueryApi: MemberQueryApi,
    private val clock: Clock,
) {

    // creatorId null = 기본 요율 변경 / effectiveFrom null = 지금부터
    fun create(
        adminId: Long,
        creatorId: Long?,
        revenueType: RevenueType,
        feeRate: BigDecimal,
        effectiveFrom: LocalDateTime?,
        reason: String,
    ): FeePolicy {
        validateRate(feeRate)
        val now = LocalDateTime.now(clock)
        val from = effectiveFrom ?: now
        if (from.isBefore(now)) {
            throw BusinessException(SettlementErrorCode.RETROACTIVE_POLICY)
        }
        if (creatorId != null && !memberQueryApi.isActiveCreator(creatorId)) {
            throw BusinessException(SettlementErrorCode.NOT_A_CREATOR)
        }
        return feePolicyRepository.save(FeePolicy(creatorId, revenueType, feeRate, from, reason, adminId))
    }

    // 계약 종료 → 종료 시각부터 기본 요율 / endAt null = 지금
    fun end(adminId: Long, policyId: Long, endAt: LocalDateTime?, reason: String): FeePolicy {
        val policy = feePolicyRepository.findByIdForUpdate(policyId)
            ?: throw BusinessException(SettlementErrorCode.FEE_POLICY_NOT_FOUND)
        if (!policy.isContract || policy.effectiveTo != null) {
            throw BusinessException(SettlementErrorCode.FEE_POLICY_NOT_ENDABLE)
        }
        val now = LocalDateTime.now(clock)
        val at = endAt ?: now
        if (at.isBefore(now)) {
            throw BusinessException(SettlementErrorCode.RETROACTIVE_POLICY)
        }
        policy.end(at, adminId, reason)
        return policy
    }

    @Transactional(readOnly = true)
    fun history(creatorId: Long?): List<FeePolicy> =
        if (creatorId == null) {
            feePolicyRepository.findAllByCreatorIdIsNullOrderByIdDesc()
        } else {
            feePolicyRepository.findAllByCreatorIdOrderByIdDesc(creatorId)
        }

    // 판매 시각 기준 적용 정책 / 정산·조회 공통
    @Transactional(readOnly = true)
    fun applicable(creatorId: Long, type: RevenueType, at: LocalDateTime): FeePolicy =
        feePolicyRepository.findApplicable(creatorId, type, at, PageRequest.of(0, 1)).firstOrNull()
            ?: error("수수료 정책 없음 type=$type")

    @Transactional(readOnly = true)
    fun currentPolicies(creatorId: Long): Map<RevenueType, FeePolicy> {
        val now = LocalDateTime.now(clock)
        return RevenueType.entries.associateWith { applicable(creatorId, it, now) }
    }

    private fun validateRate(rate: BigDecimal) {
        if (rate < BigDecimal.ZERO || rate > MAX_FEE_RATE || rate.stripTrailingZeros().scale() > 4) {
            throw BusinessException(SettlementErrorCode.INVALID_FEE_RATE)
        }
    }

    companion object {
        val MAX_FEE_RATE: BigDecimal = BigDecimal("0.5000")
    }
}

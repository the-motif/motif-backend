package com.motif.settlement.service

import com.motif.common.exception.BusinessException
import com.motif.settlement.domain.CreatorType
import com.motif.settlement.domain.SettlementAccount
import com.motif.settlement.domain.TaxProfile
import com.motif.settlement.exception.SettlementErrorCode
import com.motif.settlement.repository.SettlementAccountRepository
import com.motif.settlement.repository.TaxProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 정산 계좌·세금 정보 / 등록·변경하면 PENDING → 관리자 확인 후 VERIFIED
// 실제 계좌 실명 확인(1원 인증)은 외부 연동 때 교체
@Service
@Transactional
class SettlementInfoService(
    private val accountRepository: SettlementAccountRepository,
    private val taxProfileRepository: TaxProfileRepository,
    private val clock: Clock,
) {

    fun saveAccount(creatorId: Long, bankCode: String, accountNumber: String, holderName: String): SettlementAccount {
        val account = accountRepository.findByCreatorId(creatorId)
            ?: return accountRepository.save(SettlementAccount(creatorId, bankCode, accountNumber, holderName))
        account.change(bankCode, accountNumber, holderName)
        return account
    }

    fun saveTaxProfile(
        creatorId: Long,
        creatorType: CreatorType,
        legalName: String,
        residentNo: String?,
        businessNo: String?,
    ): TaxProfile {
        // 유형에 맞는 번호만 저장
        val (resident, business) = when (creatorType) {
            CreatorType.INDIVIDUAL -> (residentNo ?: invalid()) to null
            CreatorType.BUSINESS -> null to (businessNo ?: invalid())
        }
        val profile = taxProfileRepository.findByCreatorId(creatorId)
            ?: return taxProfileRepository.save(TaxProfile(creatorId, creatorType, legalName, resident, business))
        profile.change(creatorType, legalName, resident, business)
        return profile
    }

    @Transactional(readOnly = true)
    fun getAccount(creatorId: Long): SettlementAccount? = accountRepository.findByCreatorId(creatorId)

    @Transactional(readOnly = true)
    fun getTaxProfile(creatorId: Long): TaxProfile? = taxProfileRepository.findByCreatorId(creatorId)

    fun reviewAccount(creatorId: Long, approved: Boolean): SettlementAccount =
        (accountRepository.findByCreatorId(creatorId) ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_INFO_NOT_FOUND))
            .also { it.review(approved, LocalDateTime.now(clock)) }

    fun reviewTaxProfile(creatorId: Long, approved: Boolean): TaxProfile =
        (taxProfileRepository.findByCreatorId(creatorId) ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_INFO_NOT_FOUND))
            .also { it.review(approved, LocalDateTime.now(clock)) }

    private fun invalid(): Nothing = throw BusinessException(SettlementErrorCode.INVALID_TAX_PROFILE)
}

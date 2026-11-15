package com.motif.settlement.bank

import org.springframework.stereotype.Component

// 펌뱅킹 송금 / transferNo가 같으면 은행이 중복 송금하지 않는 것을 전제
interface BankTransferGateway {

    // 성공·최종 실패는 결과로, 일시 오류(타임아웃 등)는 BankTemporaryException
    fun transfer(transferNo: String, bankCode: String, accountNumber: String, amount: Long): TransferResult
}

sealed interface TransferResult {
    data object Success : TransferResult
    data class Failure(val reason: String) : TransferResult
}

// 결과를 모름 → 같은 transferNo로 재시도
class BankTemporaryException(message: String) : RuntimeException(message)

// 모의 은행 / 계좌번호 999로 시작하면 최종 실패, 998로 시작하면 일시 오류
@Component
class FakeBankTransferGateway : BankTransferGateway {

    private val completed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun transfer(transferNo: String, bankCode: String, accountNumber: String, amount: Long): TransferResult {
        if (transferNo in completed) return TransferResult.Success
        return when {
            accountNumber.startsWith("999") -> TransferResult.Failure("모의 은행: 계좌 없음")
            accountNumber.startsWith("998") -> throw BankTemporaryException("모의 은행: 응답 시간 초과")
            else -> TransferResult.Success.also { completed += transferNo }
        }
    }
}

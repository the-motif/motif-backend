package com.motif.settlement.service

import com.motif.common.exception.BusinessException
import com.motif.common.lock.RedisDistributedLock
import com.motif.payment.api.PaymentReconciliationApi
import com.motif.payment.api.PaymentTransactionView
import com.motif.settlement.domain.MismatchItem
import com.motif.settlement.domain.ReconKey
import com.motif.settlement.domain.ReconTx
import com.motif.settlement.domain.ReconciliationComparator
import com.motif.settlement.domain.ReconciliationMismatch
import com.motif.settlement.domain.ReconciliationRun
import com.motif.settlement.domain.ReconciliationSummary
import com.motif.settlement.domain.SalesRecord
import com.motif.settlement.domain.SalesType
import com.motif.settlement.exception.SettlementErrorCode
import com.motif.settlement.repository.ReconciliationMismatchRepository
import com.motif.settlement.repository.ReconciliationRunRepository
import com.motif.settlement.repository.SalesRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

// 정산 대사 / 대상일 D에 일어난 결제 거래 vs 판매 원장
// 일일 정산(00:10) 전에 실행, 미해결 불일치가 있는 창작자는 정산 보류
@Service
class ReconciliationJob(
    private val distributedLock: RedisDistributedLock,
    private val paymentApi: PaymentReconciliationApi,
    private val salesRecordRepository: SalesRecordRepository,
    private val writer: ReconciliationWriter,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${motif.reconciliation.cron:0 5 0 * * *}", zone = "Asia/Seoul")
    fun scheduledRun() {
        try {
            run(LocalDate.now(clock).minusDays(1))
        } catch (e: BusinessException) {
            log.info("대사 건너뜀: {}", e.message)
        }
    }

    fun run(targetDate: LocalDate): ReconciliationRun {
        if (!targetDate.isBefore(LocalDate.now(clock))) {
            throw BusinessException(SettlementErrorCode.INVALID_RECONCILIATION_DATE)
        }
        val lockKey = "reconciliation:$targetDate"
        val token = distributedLock.tryLock(lockKey, LOCK_LEASE)
            ?: throw BusinessException(SettlementErrorCode.RECONCILIATION_RUNNING)
        try {
            val runId = writer.start(targetDate)
            try {
                val (summary, items) = compare(targetDate)
                return writer.finish(runId, summary, items)
            } catch (e: Exception) {
                log.error("대사 실패 date={} runId={}", targetDate, runId, e)
                writer.fail(runId)
                throw e
            }
        } finally {
            distributedLock.unlock(lockKey, token)
        }
    }

    private fun compare(targetDate: LocalDate): Pair<ReconciliationSummary, List<MismatchItem>> {
        val from = targetDate.atStartOfDay()
        val to = from.plusDays(1)
        val payment = paymentApi.transactionsBetween(from, to).map(::fromPayment).associateByTo(mutableMapOf()) { it.key }
        val settlement = salesRecordRepository.findAllOccurredBetween(from, to).map(::fromSales).associateByTo(mutableMapOf()) { it.key }
        val summary = ReconciliationSummary(
            payment.size, payment.values.sumOf { it.amount },
            settlement.size, settlement.values.sumOf { it.amount },
        )

        // 날짜 경계 보정 / 한쪽에만 있는 키는 반대쪽 전체 기간에서 다시 찾음 (자정 직전 거래 시각이 몇 ms 다를 수 있음)
        val onlyPayment = payment.keys - settlement.keys
        if (onlyPayment.isNotEmpty()) {
            salesRecordRepository.findAllByPurchaseIdIn(onlyPayment.purchaseIds()).map(::fromSales)
                .filter { it.key in onlyPayment }
                .forEach { settlement[it.key] = it }
        }
        val onlySettlement = settlement.keys - payment.keys
        if (onlySettlement.isNotEmpty()) {
            paymentApi.transactionsOf(onlySettlement.purchaseIds()).map(::fromPayment)
                .filter { it.key in onlySettlement }
                .forEach { payment[it.key] = it }
        }
        return summary to ReconciliationComparator.compare(payment.values, settlement.values)
    }

    private fun Set<ReconKey>.purchaseIds() = mapTo(mutableSetOf()) { it.purchaseId }

    private fun fromPayment(v: PaymentTransactionView) =
        ReconTx(v.purchaseId, SalesType.valueOf(v.kind.name), v.creatorId, v.amount)

    private fun fromSales(r: SalesRecord) = ReconTx(r.purchaseId, r.type, r.creatorId, r.listPrice)

    companion object {
        private val LOCK_LEASE: Duration = Duration.ofMinutes(10)
    }
}

@Service
@Transactional
class ReconciliationWriter(
    private val runRepository: ReconciliationRunRepository,
    private val mismatchRepository: ReconciliationMismatchRepository,
    private val clock: Clock,
) {

    fun start(targetDate: LocalDate): Long =
        runRepository.save(ReconciliationRun(targetDate, runRepository.findMaxAttempt(targetDate) + 1)).id!!

    // 이전 실행의 미해결 건은 닫고 최신 실행 결과만 열어 둠
    fun finish(runId: Long, summary: ReconciliationSummary, items: List<MismatchItem>): ReconciliationRun {
        val now = LocalDateTime.now(clock)
        val run = runRepository.findById(runId).get()
        val stillOpen = items.mapTo(mutableSetOf()) { it.key to it.type }
        mismatchRepository.findUnresolvedByTargetDate(run.targetDate).forEach { old ->
            val note = if ((ReconKey(old.purchaseId, old.salesType) to old.mismatchType) in stillOpen) {
                "재대사에서도 불일치 / run=$runId 로 이어짐"
            } else {
                "재대사 일치 / run=$runId"
            }
            old.resolve(ReconciliationMismatch.SYSTEM_RESOLVER, note, now)
        }
        mismatchRepository.saveAll(
            items.map {
                ReconciliationMismatch(runId, it.key.purchaseId, it.key.type, it.creatorId, it.type, it.paymentAmount, it.settlementAmount)
            },
        )
        run.finish(summary, items.size, now)
        mismatchRepository.flush()
        return run
    }

    fun fail(runId: Long) {
        runRepository.findById(runId).ifPresent { it.fail(LocalDateTime.now(clock)) }
    }

    fun resolve(adminId: Long, mismatchId: Long, note: String): ReconciliationMismatch {
        val mismatch = mismatchRepository.findByIdForUpdate(mismatchId)
            ?: throw BusinessException(SettlementErrorCode.MISMATCH_NOT_FOUND)
        if (mismatch.resolved) throw BusinessException(SettlementErrorCode.MISMATCH_ALREADY_RESOLVED)
        mismatch.resolve(adminId, note, LocalDateTime.now(clock))
        // 정산 보류 확인은 JDBC → 바로 flush
        return mismatchRepository.saveAndFlush(mismatch)
    }
}

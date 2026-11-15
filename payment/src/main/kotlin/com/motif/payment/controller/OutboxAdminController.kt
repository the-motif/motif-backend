package com.motif.payment.controller

import com.motif.common.response.SliceResponse
import com.motif.payment.domain.OutboxEvent
import com.motif.payment.domain.OutboxStatus
import com.motif.payment.outbox.OutboxAdminService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

data class OutboxEventResponse(
    val id: Long,
    val eventId: String,
    val aggregateType: String,
    val aggregateId: Long,
    val eventType: String,
    val status: OutboxStatus,
    val retryCount: Int,
    val traceId: String?,
    val createdAt: LocalDateTime,
    val publishedAt: LocalDateTime?,
) {
    companion object {
        fun from(e: OutboxEvent) = OutboxEventResponse(
            e.id!!, e.eventId, e.aggregateType, e.aggregateId, e.eventType, e.status, e.retryCount, e.traceId, e.createdAt, e.publishedAt,
        )
    }
}

@Tag(name = "이벤트 (관리자)")
@RestController
class OutboxAdminController(
    private val outboxAdminService: OutboxAdminService,
) {

    @Operation(summary = "이벤트 목록", description = "기본 FAILED (재시도 5번 실패)")
    @GetMapping("/api/admin/outbox-events")
    fun list(
        @RequestParam(defaultValue = "FAILED") status: OutboxStatus,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) size: Int,
    ): SliceResponse<OutboxEventResponse> =
        SliceResponse.of(outboxAdminService.findByStatus(status, PageRequest.of(page, size)), OutboxEventResponse::from)

    @Operation(summary = "이벤트 재발행", description = "PENDING으로 되돌림 → 1초 안에 발행 / 받는 쪽이 중복을 거르므로 안전")
    @PostMapping("/api/admin/outbox-events/{id}/requeue")
    fun requeue(@PathVariable id: Long): OutboxEventResponse = OutboxEventResponse.from(outboxAdminService.requeue(id))
}

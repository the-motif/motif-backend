-- 창작자 개별 수수료 계약 종료 / 종료 후엔 기본 요율
ALTER TABLE fee_policy
    ADD COLUMN effective_to DATETIME(6)  NULL COMMENT 'NULL이면 종료 없음 / 이 시각부터 적용 안 함' AFTER effective_from,
    ADD COLUMN ended_by     BIGINT       NULL COMMENT '종료한 관리자 member.id',
    ADD COLUMN end_reason   VARCHAR(255) NULL;

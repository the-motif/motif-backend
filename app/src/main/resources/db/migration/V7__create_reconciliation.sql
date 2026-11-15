-- 정산 대사 / 결제 거래 vs 판매 원장

CREATE TABLE reconciliation_run (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    target_date       DATE        NOT NULL COMMENT '대사 대상 일자',
    attempt           INT         NOT NULL DEFAULT 1 COMMENT '같은 날짜 재실행 차수',
    status            VARCHAR(20) NOT NULL COMMENT 'RUNNING / MATCHED / MISMATCHED / FAILED',
    payment_count     INT         NULL,
    payment_amount    BIGINT      NULL,
    settlement_count  INT         NULL,
    settlement_amount BIGINT      NULL,
    mismatch_count    INT         NOT NULL DEFAULT 0,
    finished_at       DATETIME(6) NULL,
    created_at        DATETIME(6) NOT NULL COMMENT '시작 시각',
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reconciliation_run_date_attempt (target_date, attempt)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE reconciliation_mismatch (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    run_id            BIGINT       NOT NULL,
    purchase_id       BIGINT       NOT NULL,
    sales_type        VARCHAR(20)  NOT NULL COMMENT 'SALE / REFUND',
    creator_id        BIGINT       NOT NULL COMMENT '해결 전 정산 보류 대상',
    mismatch_type     VARCHAR(30)  NOT NULL COMMENT 'MISSING_IN_SETTLEMENT / MISSING_IN_PAYMENT / AMOUNT_DIFF / STATUS_DIFF',
    payment_amount    BIGINT       NULL,
    settlement_amount BIGINT       NULL,
    resolved_at       DATETIME(6)  NULL COMMENT 'NULL이면 미해결',
    resolved_by       BIGINT       NULL COMMENT '0이면 재대사 자동 해결',
    resolution_note   VARCHAR(255) NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reconciliation_mismatch (run_id, purchase_id, sales_type, mismatch_type),
    KEY idx_reconciliation_mismatch_creator (creator_id, resolved_at),
    CONSTRAINT fk_reconciliation_mismatch_run FOREIGN KEY (run_id) REFERENCES reconciliation_run (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

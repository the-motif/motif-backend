-- 원천세 월 집계 / 지급 완료일 기준, 신고용

CREATE TABLE withholding_tax_monthly (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    tax_month    CHAR(6)     NOT NULL COMMENT 'YYYYMM / 지급 완료일 기준',
    creator_id   BIGINT      NOT NULL,
    creator_type VARCHAR(20) NOT NULL COMMENT '지급 시점 스냅샷 / 월 중 변경 시 행 2개',
    payout_count INT         NOT NULL,
    gross_amount BIGINT      NOT NULL,
    income_tax   BIGINT      NOT NULL,
    local_tax    BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_withholding_tax_monthly (tax_month, creator_id, creator_type),
    KEY idx_withholding_tax_monthly_creator (creator_id, tax_month)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- settlement 모듈 정산 테이블 / 기본 수수료·보류 정책 포함

CREATE TABLE fee_policy (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    creator_id     BIGINT        NULL COMMENT 'NULL이면 기본 수수료율',
    revenue_type   VARCHAR(20)   NOT NULL COMMENT 'CONTENT / MEMBERSHIP / DONATION',
    fee_rate       DECIMAL(5, 4) NOT NULL COMMENT '0.1500 = 15%',
    effective_from DATETIME(6)   NOT NULL COMMENT '이 시각 이후 판매분부터',
    reason         VARCHAR(255)  NULL,
    created_by     BIGINT        NOT NULL COMMENT '변경한 관리자 member.id / 0 = 시스템',
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_fee_policy_lookup (creator_id, revenue_type, effective_from),
    CONSTRAINT ck_fee_policy_rate CHECK (fee_rate >= 0 AND fee_rate < 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE hold_policy (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    hold_rate      DECIMAL(5, 4) NOT NULL COMMENT '보류 비율',
    hold_days      INT           NOT NULL COMMENT '보류 기간 / 환불 가능 기간 이상',
    effective_from DATETIME(6)   NOT NULL,
    created_by     BIGINT        NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_hold_policy_effective (effective_from),
    CONSTRAINT ck_hold_policy CHECK (hold_rate >= 0 AND hold_rate <= 1 AND hold_days >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sales_record (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    event_id        VARCHAR(64)   NOT NULL COMMENT '처리한 이벤트 ID',
    purchase_id     BIGINT        NOT NULL,
    creator_id      BIGINT        NOT NULL,
    type            VARCHAR(20)   NOT NULL COMMENT 'SALE / REFUND',
    revenue_type    VARCHAR(20)   NOT NULL,
    list_price      BIGINT        NOT NULL COMMENT '환불이면 음수',
    fee_rate        DECIMAL(5, 4) NOT NULL COMMENT '판매 시점 수수료율',
    fee_amount      BIGINT        NOT NULL COMMENT '원 미만 내림 / 환불이면 음수',
    occurred_at     DATETIME(6)   NOT NULL COMMENT '구매·환불 시각',
    settlement_date DATE          NULL COMMENT 'NULL이면 미정산',
    created_at      DATETIME(6)   NOT NULL COMMENT '이벤트 수신 시각',
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_record_event (event_id),
    UNIQUE KEY uk_sales_record_purchase_type (purchase_id, type),
    KEY idx_sales_record_settlement (settlement_date, creator_id),
    KEY idx_sales_record_creator_occurred (creator_id, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE daily_settlement (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    settlement_date     DATE        NOT NULL,
    creator_id          BIGINT      NOT NULL,
    gross_sales         BIGINT      NOT NULL COMMENT 'SALE 정가 합',
    refund_amount       BIGINT      NOT NULL COMMENT 'REFUND 정가 합 (양수)',
    fee_amount          BIGINT      NOT NULL COMMENT '순수수료',
    net_amount          BIGINT      NOT NULL COMMENT 'gross - refund - fee',
    hold_amount         BIGINT      NOT NULL COMMENT '오늘 묶은 보류금',
    hold_release_date   DATE        NOT NULL,
    hold_released_at    DATETIME(6) NULL COMMENT 'NULL이면 보류 중',
    hold_release_amount BIGINT      NOT NULL COMMENT '오늘 풀린 보류금',
    carry_over_amount   BIGINT      NOT NULL COMMENT '전날 마이너스 이월분 (0 이하)',
    payable_amount      BIGINT      NOT NULL COMMENT '음수면 다음 날 이월',
    status              VARCHAR(20) NOT NULL COMMENT 'CALCULATED / CONFIRMED',
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_settlement_date_creator (settlement_date, creator_id),
    KEY idx_daily_settlement_release (hold_release_date, hold_released_at),
    KEY idx_daily_settlement_creator_date (creator_id, settlement_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE creator_balance (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    creator_id       BIGINT      NOT NULL,
    available_amount BIGINT      NOT NULL DEFAULT 0 COMMENT '출금 가능',
    held_amount      BIGINT      NOT NULL DEFAULT 0 COMMENT '보류 중',
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_creator_balance_creator (creator_id),
    CONSTRAINT ck_creator_balance CHECK (available_amount >= 0 AND held_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 행 추가만
CREATE TABLE balance_ledger (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    creator_id    BIGINT       NOT NULL,
    bucket        VARCHAR(20)  NOT NULL COMMENT 'AVAILABLE / HELD',
    type          VARCHAR(30)  NOT NULL,
    amount        BIGINT       NOT NULL COMMENT '+ 증가 / - 감소',
    balance_after BIGINT       NOT NULL COMMENT '이 bucket 잔액',
    ref_id        BIGINT       NOT NULL,
    memo          VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_balance_ledger_ref (type, bucket, ref_id),
    KEY idx_balance_ledger_creator (creator_id, bucket, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 기본 정책
INSERT INTO fee_policy (creator_id, revenue_type, fee_rate, effective_from, reason, created_by, created_at)
VALUES (NULL, 'CONTENT', 0.1500, '2026-01-01 00:00:00', '기본값', 0, NOW(6)),
       (NULL, 'MEMBERSHIP', 0.1500, '2026-01-01 00:00:00', '기본값', 0, NOW(6)),
       (NULL, 'DONATION', 0.0500, '2026-01-01 00:00:00', '기본값', 0, NOW(6));

INSERT INTO hold_policy (hold_rate, hold_days, effective_from, created_by, created_at)
VALUES (0.1000, 7, '2026-01-01 00:00:00', 0, NOW(6));

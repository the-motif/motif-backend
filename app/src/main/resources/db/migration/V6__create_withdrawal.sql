-- 정산 계좌·세금 정보·출금·지급

CREATE TABLE settlement_account (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    creator_id     BIGINT       NOT NULL,
    bank_code      VARCHAR(10)  NOT NULL,
    account_number VARCHAR(512) NOT NULL COMMENT '암호화 저장',
    holder_name    VARCHAR(50)  NOT NULL,
    verify_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / VERIFIED / REJECTED',
    verified_at    DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_account_creator (creator_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tax_profile (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    creator_id      BIGINT       NOT NULL,
    creator_type    VARCHAR(20)  NOT NULL COMMENT 'INDIVIDUAL / BUSINESS',
    legal_name      VARCHAR(50)  NOT NULL COMMENT '실명 또는 상호',
    resident_no_enc VARCHAR(512) NULL COMMENT '개인 / 주민등록번호 암호화',
    business_no     VARCHAR(20)  NULL COMMENT '사업자등록번호',
    verify_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    verified_at     DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tax_profile_creator (creator_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 행 추가만
CREATE TABLE tax_policy (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    creator_type    VARCHAR(20)   NOT NULL,
    income_tax_rate DECIMAL(5, 4) NOT NULL COMMENT '지급액 대비 / 개인 0.0300',
    local_tax_rate  DECIMAL(5, 4) NOT NULL COMMENT '소득세 대비 / 0.1000',
    effective_from  DATETIME(6)   NOT NULL,
    created_by      BIGINT        NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tax_policy_type_effective (creator_type, effective_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE withdrawal_request (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    creator_id      BIGINT       NOT NULL,
    amount          BIGINT       NOT NULL,
    idempotency_key VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED / APPROVED / REJECTED / PAID / FAILED',
    reject_reason   VARCHAR(255) NULL,
    reviewed_by     BIGINT       NULL,
    reviewed_at     DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_withdrawal_request_idempotency (idempotency_key),
    KEY idx_withdrawal_request_creator (creator_id, created_at),
    KEY idx_withdrawal_request_status (status),
    CONSTRAINT ck_withdrawal_request_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE payout (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    payout_no             VARCHAR(64)  NOT NULL COMMENT '재시도해도 같은 값',
    withdrawal_request_id BIGINT       NOT NULL,
    creator_id            BIGINT       NOT NULL,
    creator_type          VARCHAR(20)  NOT NULL COMMENT '지급 시점 스냅샷',
    gross_amount          BIGINT       NOT NULL COMMENT '세전',
    income_tax            BIGINT       NOT NULL DEFAULT 0,
    local_tax             BIGINT       NOT NULL DEFAULT 0,
    net_amount            BIGINT       NOT NULL COMMENT '실제 송금액',
    bank_code             VARCHAR(10)  NOT NULL COMMENT '지급 시점 스냅샷',
    account_number        VARCHAR(512) NOT NULL COMMENT '암호화',
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / COMPLETED / FAILED',
    fail_reason           VARCHAR(255) NULL,
    paid_at               DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payout_payout_no (payout_no),
    UNIQUE KEY uk_payout_withdrawal (withdrawal_request_id),
    KEY idx_payout_creator_paid (creator_id, paid_at),
    CONSTRAINT fk_payout_withdrawal FOREIGN KEY (withdrawal_request_id) REFERENCES withdrawal_request (id),
    CONSTRAINT ck_payout_amount CHECK (net_amount = gross_amount - income_tax - local_tax AND net_amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 개인 3.3% (소득세 3% + 지방소득세 = 소득세의 10%) / 사업자 원천징수 없음
INSERT INTO tax_policy (creator_type, income_tax_rate, local_tax_rate, effective_from, created_by, created_at)
VALUES ('INDIVIDUAL', 0.0300, 0.1000, '2026-01-01 00:00:00', 0, NOW(6)),
       ('BUSINESS', 0.0000, 0.0000, '2026-01-01 00:00:00', 0, NOW(6));

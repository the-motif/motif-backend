-- payment 모듈 테이블

CREATE TABLE point_wallet (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    member_id    BIGINT      NOT NULL COMMENT 'member.id',
    paid_balance BIGINT      NOT NULL DEFAULT 0 COMMENT '유상 잔액',
    free_balance BIGINT      NOT NULL DEFAULT 0 COMMENT '무상 잔액',
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_wallet_member (member_id),
    CONSTRAINT ck_point_wallet_balance CHECK (paid_balance >= 0 AND free_balance >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE point_lot (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    wallet_id        BIGINT      NOT NULL,
    kind             VARCHAR(20) NOT NULL COMMENT 'PAID / FREE',
    source_type      VARCHAR(20) NOT NULL COMMENT 'CHARGE / GRANT',
    source_id        BIGINT      NOT NULL,
    original_amount  BIGINT      NOT NULL,
    remaining_amount BIGINT      NOT NULL,
    expires_at       DATETIME(6) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_lot_source (source_type, source_id),
    KEY idx_point_lot_wallet_kind_expires (wallet_id, kind, expires_at),
    CONSTRAINT fk_point_lot_wallet FOREIGN KEY (wallet_id) REFERENCES point_wallet (id),
    CONSTRAINT ck_point_lot_remaining CHECK (remaining_amount >= 0 AND remaining_amount <= original_amount)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE charge_order (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_no        VARCHAR(64)  NOT NULL COMMENT 'PG 주문 번호',
    member_id       BIGINT       NOT NULL COMMENT 'member.id',
    amount          BIGINT       NOT NULL,
    canceled_amount BIGINT       NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(64)  NOT NULL,
    pg_provider     VARCHAR(30)  NULL,
    pay_method      VARCHAR(20)  NULL,
    pg_payment_key  VARCHAR(200) NULL COMMENT 'PG 승인 키',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PAID / FAILED / PARTIAL_CANCELED / CANCELED',
    paid_at         DATETIME(6)  NULL,
    canceled_at     DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_charge_order_order_no (order_no),
    UNIQUE KEY uk_charge_order_idempotency (idempotency_key),
    UNIQUE KEY uk_charge_order_pg_payment_key (pg_payment_key),
    KEY idx_charge_order_member (member_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE charge_cancel (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    charge_order_id BIGINT       NOT NULL,
    amount          BIGINT       NOT NULL,
    reason          VARCHAR(255) NULL,
    idempotency_key VARCHAR(64)  NOT NULL,
    pg_cancel_key   VARCHAR(200) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED / COMPLETED / FAILED',
    completed_at    DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_charge_cancel_idempotency (idempotency_key),
    UNIQUE KEY uk_charge_cancel_pg_cancel_key (pg_cancel_key),
    CONSTRAINT fk_charge_cancel_charge_order FOREIGN KEY (charge_order_id) REFERENCES charge_order (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE pg_webhook_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    pg_provider  VARCHAR(30)  NOT NULL,
    pg_event_id  VARCHAR(100) NOT NULL,
    event_type   VARCHAR(50)  NOT NULL,
    order_no     VARCHAR(64)  NULL,
    payload      JSON         NOT NULL,
    processed_at DATETIME(6)  NULL COMMENT 'NULL이면 미처리',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pg_webhook_log_event (pg_provider, pg_event_id),
    KEY idx_pg_webhook_log_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE purchase (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    buyer_id          BIGINT       NOT NULL COMMENT 'member.id',
    recipient_id      BIGINT       NOT NULL COMMENT '선물 아니면 buyer_id',
    seller_id         BIGINT       NOT NULL COMMENT '창작자 member.id',
    target_type       VARCHAR(20)  NOT NULL COMMENT 'CONTENT / MEMBERSHIP / DONATION',
    target_id         BIGINT       NOT NULL,
    list_price        BIGINT       NOT NULL COMMENT '정가 스냅샷 / 정산 기준',
    coupon_discount   BIGINT       NOT NULL DEFAULT 0,
    member_coupon_id  BIGINT       NULL COMMENT 'member_coupon.id',
    paid_point_amount BIGINT       NOT NULL,
    free_point_amount BIGINT       NOT NULL DEFAULT 0,
    message           VARCHAR(200) NULL,
    idempotency_key   VARCHAR(64)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED' COMMENT 'COMPLETED / REFUNDED',
    refunded_at       DATETIME(6)  NULL,
    refund_reason     VARCHAR(255) NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_idempotency (idempotency_key),
    KEY idx_purchase_buyer_created (buyer_id, created_at),
    KEY idx_purchase_seller_created (seller_id, created_at),
    KEY idx_purchase_target (target_type, target_id),
    CONSTRAINT ck_purchase_amount CHECK (list_price = coupon_discount + paid_point_amount + free_point_amount)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE point_lot_usage (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    lot_id      BIGINT      NOT NULL,
    purchase_id BIGINT      NOT NULL,
    amount      BIGINT      NOT NULL,
    restored_at DATETIME(6) NULL COMMENT '환불로 복구한 시각',
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_lot_usage_purchase_lot (purchase_id, lot_id),
    KEY idx_point_lot_usage_lot (lot_id),
    CONSTRAINT fk_point_lot_usage_lot FOREIGN KEY (lot_id) REFERENCES point_lot (id),
    CONSTRAINT fk_point_lot_usage_purchase FOREIGN KEY (purchase_id) REFERENCES purchase (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 행 추가만
CREATE TABLE point_transaction (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    wallet_id     BIGINT      NOT NULL,
    type          VARCHAR(20) NOT NULL COMMENT 'CHARGE / GRANT / USE / REFUND / CHARGE_CANCEL / EXPIRE',
    amount        BIGINT      NOT NULL COMMENT '증가 + / 감소 -',
    paid_amount   BIGINT      NOT NULL,
    free_amount   BIGINT      NOT NULL,
    balance_after BIGINT      NOT NULL COMMENT '총 잔액',
    ref_type      VARCHAR(20) NOT NULL COMMENT 'CHARGE_ORDER / CHARGE_CANCEL / PURCHASE / POINT_GRANT / POINT_LOT',
    ref_id        BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_transaction_ref (ref_type, ref_id, type),
    KEY idx_point_transaction_wallet_created (wallet_id, created_at),
    CONSTRAINT fk_point_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES point_wallet (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE outbox_event (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    event_id       VARCHAR(64) NOT NULL COMMENT 'UUID',
    aggregate_type VARCHAR(30) NOT NULL,
    aggregate_id   BIGINT      NOT NULL,
    event_type     VARCHAR(50) NOT NULL,
    payload        JSON        NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PUBLISHED / FAILED',
    retry_count    INT         NOT NULL DEFAULT 0,
    published_at   DATETIME(6) NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_event_id (event_id),
    KEY idx_outbox_event_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

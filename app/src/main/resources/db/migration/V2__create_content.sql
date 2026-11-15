-- content 모듈 테이블

CREATE TABLE category (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(30) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE membership_plan (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    creator_id    BIGINT       NOT NULL COMMENT 'member.id',
    name          VARCHAR(50)  NOT NULL,
    description   VARCHAR(500) NULL,
    monthly_price BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / CLOSED',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_membership_plan_creator (creator_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE content (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    creator_id         BIGINT       NOT NULL COMMENT 'member.id',
    category_id        BIGINT       NOT NULL,
    title              VARCHAR(100) NOT NULL,
    preview            TEXT         NULL COMMENT '구매 전 공개',
    thumbnail_url      VARCHAR(500) NULL,
    access_type        VARCHAR(20)  NOT NULL COMMENT 'FREE / PAID / MEMBERSHIP',
    price              BIGINT       NOT NULL DEFAULT 0 COMMENT 'FREE면 0',
    membership_plan_id BIGINT       NULL COMMENT 'MEMBERSHIP일 때만',
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / STOPPED',
    view_count         BIGINT       NOT NULL DEFAULT 0,
    like_count         INT          NOT NULL DEFAULT 0,
    published_at       DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_content_status_published (status, published_at),
    KEY idx_content_creator_status (creator_id, status),
    KEY idx_content_category_status (category_id, status),
    CONSTRAINT fk_content_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_content_membership_plan FOREIGN KEY (membership_plan_id) REFERENCES membership_plan (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE content_body (
    content_id BIGINT      NOT NULL,
    body       MEDIUMTEXT  NOT NULL COMMENT '구매자·구독자만 열람',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (content_id),
    CONSTRAINT fk_content_body_content FOREIGN KEY (content_id) REFERENCES content (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE content_access (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    member_id       BIGINT      NOT NULL COMMENT 'member.id',
    content_id      BIGINT      NOT NULL,
    source          VARCHAR(20) NOT NULL COMMENT 'PURCHASE / GIFT',
    purchase_id     BIGINT      NOT NULL COMMENT '최근 purchase.id',
    first_viewed_at DATETIME(6) NULL COMMENT 'NULL이면 열람 전 / 환불 가능',
    revoked_at      DATETIME(6) NULL COMMENT '환불 시 회수',
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_content_access_member_content (member_id, content_id),
    CONSTRAINT fk_content_access_content FOREIGN KEY (content_id) REFERENCES content (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE membership_subscription (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    plan_id     BIGINT      NOT NULL,
    member_id   BIGINT      NOT NULL COMMENT 'member.id',
    purchase_id BIGINT      NOT NULL COMMENT 'purchase.id',
    status      VARCHAR(20) NOT NULL COMMENT 'ACTIVE / EXPIRED / CANCELED',
    started_at  DATETIME(6) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_membership_subscription_member_plan_status (member_id, plan_id, status),
    KEY idx_membership_subscription_expires (expires_at),
    CONSTRAINT fk_membership_subscription_plan FOREIGN KEY (plan_id) REFERENCES membership_plan (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

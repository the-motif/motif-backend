-- member 모듈

CREATE TABLE member (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    email            VARCHAR(255) NOT NULL,
    password         VARCHAR(255) NOT NULL COMMENT 'BCrypt 해시',
    nickname         VARCHAR(30)  NOT NULL,
    role             VARCHAR(20)  NOT NULL DEFAULT 'READER' COMMENT 'READER / CREATOR / ADMIN',
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / SUSPENDED / WITHDRAWN',
    suspended_reason VARCHAR(255) NULL,
    withdrawn_at     DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_email (email),
    UNIQUE KEY uk_member_nickname (nickname)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE creator_profile (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    member_id         BIGINT       NOT NULL,
    bio               VARCHAR(500) NULL,
    profile_image_url VARCHAR(500) NULL,
    follower_count    INT          NOT NULL DEFAULT 0 COMMENT 'follow 기준 비정규화',
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_creator_profile_member (member_id),
    CONSTRAINT fk_creator_profile_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE creator_application (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    member_id         BIGINT       NOT NULL,
    intro             VARCHAR(500) NOT NULL,
    portfolio_url     VARCHAR(500) NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED / APPROVED / REJECTED',
    -- 심사 중 신청 1개만 / 엔티티에서 insertable·updatable false
    pending_member_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'REQUESTED' THEN member_id END) STORED,
    reject_reason     VARCHAR(255) NULL,
    reviewed_by       BIGINT       NULL COMMENT '관리자 member.id',
    reviewed_at       DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_creator_application_pending (pending_member_id),
    KEY idx_creator_application_member_status (member_id, status),
    KEY idx_creator_application_status (status),
    CONSTRAINT fk_creator_application_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

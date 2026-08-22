-- Dear Jolly 초기 스키마.
-- docs/ERD.md §7 의 DDL 을 그대로 옮긴 것이다. 문서와 이 파일이 어긋나면 문서를 기준으로 고친다.

CREATE TABLE users (
    user_id        BIGINT       NOT NULL AUTO_INCREMENT,
    oauth_provider VARCHAR(10)  NOT NULL,
    oauth_id       VARCHAR(255) NOT NULL,
    email          VARCHAR(255) NULL,
    nickname       VARCHAR(20)  NULL,
    role           VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    refresh_token  VARCHAR(500) NULL,
    oauth_refresh_token VARCHAR(500) NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    deleted_at     DATETIME(6)  NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_oauth (oauth_provider, oauth_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE terms_agreements (
    terms_agreement_id BIGINT      NOT NULL AUTO_INCREMENT,
    user_id            BIGINT      NOT NULL,
    type               VARCHAR(20) NOT NULL,
    agreed             BOOLEAN     NOT NULL,
    terms_version      VARCHAR(20) NOT NULL,
    agreed_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (terms_agreement_id),
    CONSTRAINT fk_terms_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    KEY idx_terms_latest (user_id, type, agreed_at DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE stamps (
    stamp_id    BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(30)  NOT NULL,
    description VARCHAR(100) NOT NULL,
    image_key   VARCHAR(255) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (stamp_id),
    UNIQUE KEY uk_stamps_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE letters (
    letter_id   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    stamp_id    BIGINT       NULL,
    content     VARCHAR(500) NOT NULL,
    letter_date DATE         NOT NULL,
    status      VARCHAR(30)  NOT NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    retry_count INT          NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (letter_id),
    CONSTRAINT fk_letters_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_letters_stamp FOREIGN KEY (stamp_id) REFERENCES stamps (stamp_id),
    KEY idx_letters_list (user_id, letter_date DESC, letter_id DESC),
    KEY idx_letters_pending (status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE feedbacks (
    feedback_id       BIGINT        NOT NULL AUTO_INCREMENT,
    letter_id         BIGINT        NOT NULL,
    corrected_content VARCHAR(1000) NOT NULL,
    model             VARCHAR(50)   NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (feedback_id),
    UNIQUE KEY uk_feedbacks_letter (letter_id),
    CONSTRAINT fk_feedbacks_letter FOREIGN KEY (letter_id) REFERENCES letters (letter_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE correction_segments (
    correction_segment_id BIGINT       NOT NULL AUTO_INCREMENT,
    feedback_id           BIGINT       NOT NULL,
    sequence              INT          NOT NULL,
    original_text         VARCHAR(500) NOT NULL,
    corrected_text        VARCHAR(500) NOT NULL,
    correction_type       VARCHAR(20)  NOT NULL,
    PRIMARY KEY (correction_segment_id),
    UNIQUE KEY uk_segments_order (feedback_id, sequence),
    CONSTRAINT fk_segments_feedback FOREIGN KEY (feedback_id) REFERENCES feedbacks (feedback_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE feedback_tips (
    feedback_tip_id BIGINT       NOT NULL AUTO_INCREMENT,
    feedback_id     BIGINT       NOT NULL,
    content         VARCHAR(500) NOT NULL,
    sort_order      INT          NOT NULL,
    PRIMARY KEY (feedback_tip_id),
    UNIQUE KEY uk_tips_order (feedback_id, sort_order),
    CONSTRAINT fk_tips_feedback FOREIGN KEY (feedback_id) REFERENCES feedbacks (feedback_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 초기 우표 마스터 데이터
INSERT INTO stamps (name, description, image_key, is_active) VALUES
    ('장미',      '마음을 담아 건네는 붉은 장미',        'stamps/flower_stamp.png',  TRUE),
    ('호박',      '포근하고 든든한 가을 호박',           'stamps/pumpkin_stamp.png', TRUE),
    ('네잎클로버', '오늘 하루에 깃든 작은 행운',          'stamps/clover_stamp.png',  TRUE),
    ('초승달',    '조용히 하루를 마무리하는 밤의 달',     'stamps/moon_stamp.png',    TRUE);

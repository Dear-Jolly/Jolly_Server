CREATE TABLE app_versions (
    platform              VARCHAR(10) NOT NULL,
    min_supported_version VARCHAR(20) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (platform)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

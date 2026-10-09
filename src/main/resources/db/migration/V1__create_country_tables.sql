CREATE TABLE country_info (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    iso_code          VARCHAR(2)   NOT NULL,
    name              VARCHAR(150) NOT NULL,
    capital_city      VARCHAR(150) NULL,
    phone_code        VARCHAR(20)  NULL,
    continent_code    VARCHAR(5)   NULL,
    currency_iso_code VARCHAR(5)   NULL,
    flag_url          VARCHAR(500) NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_country_info_iso_code UNIQUE (iso_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE languages (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    iso_code   VARCHAR(10)  NULL,
    name       VARCHAR(100) NOT NULL,
    country_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_languages_country
        FOREIGN KEY (country_id)
        REFERENCES country_info (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_languages_country_id ON languages (country_id);

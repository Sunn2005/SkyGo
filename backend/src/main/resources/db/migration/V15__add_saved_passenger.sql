-- Reusable passenger profiles belong to an account, not to a booking snapshot.
-- Passenger type and infant companion are trip-specific and are intentionally omitted.
SET NAMES utf8mb4;

CREATE TABLE saved_passenger (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) COLLATE utf8mb4_nopad_bin NOT NULL UNIQUE,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(8) COLLATE utf8mb4_nopad_bin,
    given_names VARCHAR(200) NOT NULL,
    surname VARCHAR(200) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL
        CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    document_type VARCHAR(16) COLLATE utf8mb4_nopad_bin NOT NULL
        CHECK (document_type IN ('PERSONAL_ID', 'PASSPORT')),
    document_number_ciphertext VARBINARY(1024) NOT NULL,
    document_number_last4 VARCHAR(4) NOT NULL,
    issuing_country_code CHAR(2) COLLATE utf8mb4_bin NOT NULL,
    nationality_country_code CHAR(2) COLLATE utf8mb4_bin,
    document_expires_on DATE,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    KEY ix_saved_passenger_user (user_id, created_at_utc, id),
    CONSTRAINT fk_saved_passenger_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CHECK (title IS NULL OR title IN ('MR', 'MRS', 'MS')),
    CHECK (
        (document_type = 'PASSPORT' AND nationality_country_code IS NOT NULL AND document_expires_on IS NOT NULL)
        OR (document_type = 'PERSONAL_ID' AND nationality_country_code IS NULL AND document_expires_on IS NULL)
    )
) ENGINE=InnoDB;

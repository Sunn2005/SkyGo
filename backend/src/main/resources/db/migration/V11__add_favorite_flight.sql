CREATE TABLE favorite_flight (
    user_id BIGINT UNSIGNED NOT NULL,
    flight_id BIGINT UNSIGNED NOT NULL,
    created_at_utc DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (user_id, flight_id),
    KEY ix_favorite_history (user_id, created_at_utc, flight_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_favorite_flight FOREIGN KEY (flight_id) REFERENCES flight(id)
) ENGINE=InnoDB;

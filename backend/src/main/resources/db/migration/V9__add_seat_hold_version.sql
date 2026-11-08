-- Selection revision, independent of the booking review revision and snapshot schema.
-- Preserve existing holds, reservations and their original expiry times.
ALTER TABLE seat_hold
    ADD COLUMN hold_version BIGINT NOT NULL DEFAULT 1 AFTER status,
    ADD CONSTRAINT ck_seat_hold_version CHECK (hold_version >= 1);

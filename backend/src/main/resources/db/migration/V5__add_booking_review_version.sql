-- Content revision for optimistic updates; independent of snapshot JSON format version.
-- Existing reviews retain their IDs, encrypted payloads, lifecycle and expiry.
ALTER TABLE booking_review
    ADD COLUMN review_version INT UNSIGNED NOT NULL DEFAULT 1 AFTER snapshot_schema_version,
    ADD CONSTRAINT ck_booking_review_version CHECK (review_version >= 1);

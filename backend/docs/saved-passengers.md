# Saved passengers

All endpoints require `Authorization: Bearer <token>`. The account is taken from
the authenticated session; clients do not send `userId`. IDs are public UUIDs.
Responses use `Cache-Control: no-store`. JSON errors use the existing `error`
envelope. There is a maximum of 50 profiles per account.

| Method | Path | Success |
| --- | --- | --- |
| GET | `/api/v1/saved-passengers` | 200, `{ "data": [summary, ...] }` |
| GET | `/api/v1/saved-passengers/{id}` | 200, `{ "data": profile }` |
| POST | `/api/v1/saved-passengers` | 201 with `Location`, `{ "data": profile }` |
| PUT | `/api/v1/saved-passengers/{id}` | 200, `{ "data": profile }` |
| DELETE | `/api/v1/saved-passengers/{id}` | 204, empty body |

POST and PUT accept the same complete body:

```json
{
  "title": "MR",
  "givenNames": "Van An",
  "surname": "Nguyen",
  "dateOfBirth": "1990-01-02",
  "gender": "MALE",
  "document": {
    "documentType": "PASSPORT",
    "number": "A1234567",
    "issuingCountryCode": "VN",
    "nationalityCountryCode": "VN",
    "expiresOn": "2035-01-01"
  }
}
```

`title` is optional (`MR`, `MRS`, `MS`); gender is `MALE`, `FEMALE`, or `OTHER`.
Dates use ISO `YYYY-MM-DD`. `PERSONAL_ID` requires 12 digits and null/omitted
nationality and expiry. `PASSPORT` accepts 6–12 letters/digits and requires a
valid ISO country for nationality plus an expiry date. Names are limited to
200 characters; birth date must be between 1900-01-01 and today (UTC).

A profile is the request body plus `savedPassengerId`. It contains the full
document number for explicit prefill/edit operations. The list endpoint only
returns `savedPassengerId`, `givenNames`, `surname`, `dateOfBirth`,
`documentType`, and `documentNumberLast4`; it never decrypts document numbers.

Missing profiles and profiles owned by a different account both return 404
`SAVED_PASSENGER_NOT_FOUND`. Invalid input returns 422 `VALIDATION_ERROR`;
exceeding 50 profiles returns 409 `SAVED_PASSENGER_LIMIT`.

Numbers are encrypted using the existing `SKYGO_BOOKING_DOCUMENT_ENCRYPTION_KEY`
(base64-encoded 32-byte AES key) and `SKYGO_BOOKING_DOCUMENT_KEY_ID` settings.
AES-GCM associated data uses a separate saved-passenger namespace bound to the
owner and profile ID. The database stores ciphertext and the last four characters.

Saving a profile is explicit and independent of confirming a booking. Booking
validation still checks age, trip requirements and document expiry. Profile
updates/deletes never alter booking snapshots. Applying a saved profile preserves
the selected passenger slot, infant companion and baggage for the current trip.

Frontend entry points:

- Account → Hành khách đã lưu: add, edit, delete with confirmation.
- Each passenger card during booking: choose a saved profile, save a new one,
  or explicitly save edits for future bookings. Already assigned profiles and
  profiles with an incompatible age on departure are unavailable in the picker.

The MariaDB API integration test uses an isolated database specified by
`SKYGO_TEST_DB_URL`, `SKYGO_TEST_DB_USERNAME`, and `SKYGO_TEST_DB_PASSWORD`.
Run `SavedPassengerIntegrationTest` and `BookingDocumentEncryptionServiceTest`.

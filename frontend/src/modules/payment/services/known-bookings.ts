// The backend currently has no booking-list endpoint. Retain IDs only and always
// load status, price, passengers and tickets from the authenticated detail API.
type BookingStorage = Pick<Storage, "getItem" | "setItem">;
const key = (owner: string) => `skygo:api-booking-ids:v1:${owner}`;
export function knownBookingIds(
  owner: string,
  storage: BookingStorage,
): string[] {
  if (owner === "guest") return [];
  try {
    const value: unknown = JSON.parse(storage.getItem(key(owner)) ?? "[]");
    return Array.isArray(value)
      ? [
          ...new Set(
            value.filter(
              (id): id is string =>
                typeof id === "string" && id.length > 0 && id.length <= 128,
            ),
          ),
        ].slice(0, 30)
      : [];
  } catch {
    return [];
  }
}
export function rememberBookingId(
  owner: string,
  id: string,
  storage: BookingStorage,
) {
  if (owner === "guest") return;
  try {
    storage.setItem(
      key(owner),
      JSON.stringify(
        [
          id,
          ...knownBookingIds(owner, storage).filter((old) => old !== id),
        ].slice(0, 30),
      ),
    );
  } catch {
    /* A local-storage failure must not turn a committed booking into a failure. */
  }
}

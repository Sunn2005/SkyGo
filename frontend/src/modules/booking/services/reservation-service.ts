import type { Flight, Search } from "../../flights/types";
export type Reservation = {
  id: string;
  owner: string;
  expiresAt: number;
  keys: string[];
  count: number;
  serverNow: number;
};
// In-memory demonstration only. BE must enforce inventory transactions across clients.
export function createReservationService(now = Date.now, capacity = 12) {
  const holds = new Map<string, Reservation>();
  const sold = new Map<string, number>();
  let serial = 0;
  const clean = () => {
    for (const [id, h] of holds) if (h.expiresAt <= now()) holds.delete(id);
  };
  return {
    reserve(
      owner: string,
      search: Search,
      selected: Flight[],
      previous?: Reservation | null,
    ): Reservation {
      clean();
      if (owner === "guest") throw new Error("Bạn cần đăng nhập để giữ chỗ.");
      if (previous && (!holds.has(previous.id) || previous.expiresAt <= now()))
        throw new Error("Thời gian giữ chỗ đã hết. Vui lòng tìm lại chuyến.");
      const keys = selected.map((f, i) =>
        [
          i ? search.to : search.from,
          i ? search.from : search.to,
          i ? search.secondDate : search.date,
          f.id,
          f.cabin ?? search.cabin,
        ].join("|"),
      );
      if (
        keys.some(
          (key) =>
            (sold.get(key) ?? 0) +
              [...holds.values()]
                .filter((h) => h.id !== previous?.id && h.keys.includes(key))
                .reduce((n, h) => n + h.count, 0) +
              (search.count + (search.children ?? 0)) >
            capacity,
        )
      )
        throw new Error(
          "Không đủ chỗ cho số hành khách đã chọn. Vui lòng chọn chuyến khác.",
        );
      const hold = {
        id: previous?.id ?? `hold-${now()}-${++serial}`,
        owner,
        expiresAt: previous?.expiresAt ?? now() + 15 * 60000,
        keys,
        count: search.count + (search.children ?? 0),
        serverNow: now(),
      };
      holds.set(hold.id, hold);
      return hold;
    },
    release(id: string) {
      holds.delete(id);
    },
    confirm(id: string) {
      clean();
      const hold = holds.get(id);
      if (!hold) throw new Error("Giữ chỗ đã hết hạn.");
      for (const key of hold.keys)
        sold.set(key, (sold.get(key) ?? 0) + hold.count);
      holds.delete(id);
    },
  };
}
export const reservationService = createReservationService();

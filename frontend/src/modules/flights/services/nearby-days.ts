import type { NearbyDay } from "./flight-service";
import type { Search } from "../types";

export function shiftDate(date: string, amount: number) {
  const value = new Date(`${date}T12:00:00Z`);
  value.setUTCDate(value.getUTCDate() + amount);
  return value.toISOString().slice(0, 10);
}

export function nearbyWindowSearch(
  search: Search,
  direction: "OUTBOUND" | "RETURN",
  edge: string,
  side: "previous" | "next",
): Search {
  // A previous window overlaps four days: using an already-selectable day as
  // its center avoids submitting a past center date near the airport's today.
  let center = side === "next" ? shiftDate(edge, 4) : edge;
  if (search.type === "round-trip") {
    if (direction === "OUTBOUND" && center >= search.secondDate)
      center = shiftDate(search.secondDate, -1);
    if (direction === "RETURN" && center <= search.date)
      center = shiftDate(search.date, 1);
  }
  return {
    ...search,
    ...(direction === "OUTBOUND" ? { date: center } : { secondDate: center }),
  };
}

export function mergeNearbyDays(current: NearbyDay[], incoming: NearbyDay[]) {
  // Preserve pages already fetched when adjacent windows overlap.
  return [
    ...new Map(
      [...incoming, ...current].map((day) => [day.date, day]),
    ).values(),
  ].sort((a, b) => a.date.localeCompare(b.date));
}

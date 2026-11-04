import type { TripType, Search, Leg } from "../types";

export const tripLabel = (type: TripType) =>
  ({
    "one-way": "Một chiều",
    "round-trip": "Khứ hồi",
  })[type];
export function getLegs(s: Search): Leg[] {
  const legs: Leg[] = [{ from: s.from, to: s.to, date: s.date }];
  if (s.type === "round-trip")
    legs.push({
      from: s.to,
      to: s.from,
      date: s.secondDate,
    });
  return legs;
}

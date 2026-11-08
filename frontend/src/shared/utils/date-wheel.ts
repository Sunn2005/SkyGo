export type DatePart = "year" | "month" | "day";
export const dateParts = (iso: string) => ({
  year: Number(iso.slice(0, 4)),
  month: Number(iso.slice(5, 7)),
  day: Number(iso.slice(8, 10)),
});
const monthDays = (year: number, month: number) =>
  new Date(Date.UTC(year, month, 0)).getUTCDate();
const range = (min: number, max: number) =>
  Array.from({ length: max - min + 1 }, (_, i) => min + i);

export function dateWheelRanges(value: string, min: string, max: string) {
  const selected = dateParts(value),
    first = dateParts(min),
    last = dateParts(max);
  return {
    year: range(first.year, last.year).reverse(),
    month: range(
      selected.year === first.year ? first.month : 1,
      selected.year === last.year ? last.month : 12,
    ),
    day: range(
      value.slice(0, 7) === min.slice(0, 7) ? first.day : 1,
      value.slice(0, 7) === max.slice(0, 7)
        ? last.day
        : monthDays(selected.year, selected.month),
    ),
  };
}

export function changeDatePart(
  value: string,
  part: DatePart,
  next: number,
  min: string,
  max: string,
) {
  const parts = { ...dateParts(value), [part]: next };
  parts.month = Math.max(1, Math.min(12, parts.month));
  parts.day = Math.max(
    1,
    Math.min(parts.day, monthDays(parts.year, parts.month)),
  );
  const iso = `${parts.year}-${String(parts.month).padStart(2, "0")}-${String(parts.day).padStart(2, "0")}`;
  return iso < min ? min : iso > max ? max : iso;
}

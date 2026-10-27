export const airports = [
  {
    code: "LHR",
    city: "London",
    name: "Sân bay Heathrow",
    terminal: "Nhà ga 2",
  },
  {
    code: "JFK",
    city: "New York",
    name: "Sân bay John F. Kennedy",
    terminal: "Nhà ga 4",
  },
  {
    code: "SGN",
    city: "TP. Hồ Chí Minh",
    name: "Sân bay Tân Sơn Nhất",
    terminal: "Nhà ga quốc tế",
  },
  {
    code: "HAN",
    city: "Hà Nội",
    name: "Sân bay Nội Bài",
    terminal: "Nhà ga T2",
  },
  {
    code: "DAD",
    city: "Đà Nẵng",
    name: "Sân bay Đà Nẵng",
    terminal: "Nhà ga T1",
  },
  {
    code: "SIN",
    city: "Singapore",
    name: "Sân bay Changi",
    terminal: "Nhà ga 3",
  },
] as const;
export type AirportCode = (typeof airports)[number]["code"];
export type TripType = "one-way" | "round-trip" | "multi-city";
export type Cabin = "Phổ thông" | "Thương gia";
export type Flight = {
  id: string;
  airline: string;
  number: string;
  departure: string;
  arrival: string;
  price: number;
  duration: string;
  stops: number;
};
export const flights: Flight[] = [
  {
    id: "sh201",
    airline: "SkyHaven",
    number: "SH 201",
    departure: "10:30",
    arrival: "13:30",
    price: 486,
    duration: "8 giờ 00 phút",
    stops: 0,
  },
  {
    id: "ew107",
    airline: "EcoWings",
    number: "EW 107",
    departure: "13:15",
    arrival: "16:15",
    price: 529,
    duration: "8 giờ 00 phút",
    stops: 0,
  },
  {
    id: "sh205",
    airline: "SkyHaven",
    number: "SH 205",
    departure: "17:00",
    arrival: "20:00",
    price: 562,
    duration: "8 giờ 00 phút",
    stops: 0,
  },
  {
    id: "ew109",
    airline: "EcoWings",
    number: "EW 109",
    departure: "08:00",
    arrival: "14:30",
    price: 452,
    duration: "11 giờ 30 phút",
    stops: 1,
  },
];
export type Traveller = {
  firstName: string;
  lastName: string;
  birthDate: string;
  gender: string;
  bags: number;
};
export type Search = {
  type: TripType;
  from: AirportCode;
  to: AirportCode;
  onward: AirportCode;
  date: string;
  secondDate: string;
  count: number;
  cabin: Cabin;
};
export type Leg = { from: AirportCode; to: AirportCode; date: string };
export const initialSearch: Search = {
  type: "one-way",
  from: "LHR",
  to: "JFK",
  onward: "SGN",
  date: "2026-11-16",
  secondDate: "2026-11-23",
  count: 1,
  cabin: "Phổ thông",
};
export const newTraveller = (i: number): Traveller => ({
  firstName: i === 0 ? "Minh" : "",
  lastName: i === 0 ? "Nguyễn" : "",
  birthDate: "12/03/1990",
  gender: "Nam",
  bags: 1,
});
export const airport = (code: AirportCode) =>
  airports.find((a) => a.code === code)!;
export const money = (amount: number) => "$" + amount.toLocaleString("vi-VN");
export const fullName = (p: Traveller) => `${p.lastName} ${p.firstName}`.trim();
export const tripLabel = (type: TripType) =>
  ({
    "one-way": "Một chiều",
    "round-trip": "Khứ hồi",
    "multi-city": "Nhiều chặng",
  })[type];
export function dateLabel(value: string) {
  return new Date(value + "T12:00:00").toLocaleDateString("vi-VN", {
    weekday: "short",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}
export function validDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const d = new Date(value + "T12:00:00");
  return (
    Number.isFinite(d.getTime()) &&
    d.getFullYear() === Number(value.slice(0, 4)) &&
    d.getMonth() + 1 === Number(value.slice(5, 7)) &&
    d.getDate() === Number(value.slice(8, 10))
  );
}
export function validBirthDate(value: string, departure: string) {
  if (!/^\d{2}\/\d{2}\/\d{4}$/.test(value)) return false;
  const [day, month, year] = value.split("/");
  const iso = `${year}-${month}-${day}`;
  const latest = new Date(departure + "T12:00:00");
  latest.setFullYear(latest.getFullYear() - 12);
  return (
    validDate(iso) &&
    Number(year) >= 1900 &&
    new Date(iso + "T12:00:00") <= latest
  );
}
export function getLegs(s: Search): Leg[] {
  const legs: Leg[] = [{ from: s.from, to: s.to, date: s.date }];
  if (s.type !== "one-way")
    legs.push({
      from: s.to,
      to: s.type === "round-trip" ? s.from : s.onward,
      date: s.secondDate,
    });
  return legs;
}
export const occupiedSeats = new Set([
  "10B",
  "10E",
  "11A",
  "11B",
  "11E",
  "12B",
  "12D",
  "13C",
  "13E",
  "14A",
  "14B",
  "14E",
  "14F",
  "15B",
  "15D",
  "16C",
  "16E",
]);
export const seatKind = (seat: string) =>
  ["A", "F"].includes(seat.slice(-1))
    ? "Cạnh cửa sổ"
    : ["C", "D"].includes(seat.slice(-1))
      ? "Cạnh lối đi"
      : "Ghế giữa";

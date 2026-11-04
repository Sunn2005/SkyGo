import type { AirportCode } from "../types";

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
export const airport = (code: AirportCode) =>
  airports.find((a) => a.code === code)!;

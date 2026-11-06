import type { AirportCode } from "../types";

export const continents = [
  "Châu Á",
  "Châu Âu",
  "Châu Mỹ",
  "Châu Phi",
  "Châu Đại Dương",
] as const;

// Airport catalogue follows Flight_SkyGo.pdf; retain PQC/SIN used by existing routes.
// Unspecified terminal information must come from the selected flight, not be guessed.
export const airports = [
  {
    code: "SGN",
    city: "TP. Hồ Chí Minh",
    name: "Tân Sơn Nhất",
    continent: "Châu Á",
    country: "Việt Nam",
    terminal: "Nhà ga quốc tế",
  },
  {
    code: "HAN",
    city: "Hà Nội",
    name: "Nội Bài",
    continent: "Châu Á",
    country: "Việt Nam",
    terminal: "Nhà ga T2",
  },
  {
    code: "DAD",
    city: "Đà Nẵng",
    name: "Đà Nẵng",
    continent: "Châu Á",
    country: "Việt Nam",
    terminal: "Nhà ga T1",
  },
  {
    code: "CXR",
    city: "Nha Trang",
    name: "Cam Ranh",
    continent: "Châu Á",
    country: "Việt Nam",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  },
  {
    code: "PQC",
    city: "Phú Quốc",
    name: "Phú Quốc",
    continent: "Châu Á",
    country: "Việt Nam",
    terminal: "Nhà ga nội địa",
  },
  {
    code: "SIN",
    city: "Singapore",
    name: "Changi",
    continent: "Châu Á",
    country: "Singapore",
    terminal: "Nhà ga 3",
  },
  {
    code: "LHR",
    city: "London",
    name: "Heathrow",
    continent: "Châu Âu",
    country: "Anh",
    terminal: "Nhà ga 2",
  },
  {
    code: "CDG",
    city: "Paris",
    name: "Charles de Gaulle",
    continent: "Châu Âu",
    country: "Pháp",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  },
  {
    code: "FRA",
    city: "Frankfurt",
    name: "Frankfurt",
    continent: "Châu Âu",
    country: "Đức",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  },
  {
    code: "JFK",
    city: "New York",
    name: "John F. Kennedy",
    continent: "Châu Mỹ",
    country: "Mỹ",
    terminal: "Nhà ga 4",
  },
  {
    code: "LAX",
    city: "Los Angeles",
    name: "Los Angeles",
    continent: "Châu Mỹ",
    country: "Mỹ",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  },
  {
    code: "GRU",
    city: "São Paulo",
    name: "Guarulhos",
    continent: "Châu Mỹ",
    country: "Brazil",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  },
  {
    code: "JNB",
    city: "Johannesburg",
    name: "O.R. Tambo",
    continent: "Châu Phi",
    country: "Nam Phi",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  },
  {
    code: "CAI",
    city: "Cairo",
    name: "Cairo",
    continent: "Châu Phi",
    country: "Ai Cập",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  },
  {
    code: "ADD",
    city: "Addis Ababa",
    name: "Addis Ababa",
    continent: "Châu Phi",
    country: "Ethiopia",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  },
  {
    code: "SYD",
    city: "Sydney",
    name: "Sydney",
    continent: "Châu Đại Dương",
    country: "Úc",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  },
  {
    code: "AKL",
    city: "Auckland",
    name: "Auckland",
    continent: "Châu Đại Dương",
    country: "New Zealand",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  },
  {
    code: "MEL",
    city: "Melbourne",
    name: "Melbourne",
    continent: "Châu Đại Dương",
    country: "Úc",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  },
] as const;

type AirportInfo = {
  code: string;
  name: string;
  city: string;
  country: string;
  terminal: string;
};
const apiAirports = new Map<string, AirportInfo>();
export function rememberAirports(
  items: { code: string; name: string; city: string; countryName: string }[],
) {
  for (const item of items)
    apiAirports.set(item.code, {
      ...item,
      country: item.countryName,
      terminal: "Thông tin nhà ga sẽ cập nhật",
    });
}
export const airport = (code: AirportCode): AirportInfo =>
  apiAirports.get(code) ??
  airports.find((a) => a.code === code) ?? {
    code,
    name: code,
    city: code,
    country: "",
    terminal: "Thông tin nhà ga sẽ cập nhật",
  };

export const normalizeAirportQuery = (value: string) =>
  value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[đĐ]/g, "d")
    .toLowerCase()
    .trim();

export function searchAirports(query: string) {
  const needle = normalizeAirportQuery(query);
  return airports.filter((a) =>
    normalizeAirportQuery(
      `${a.name} ${a.city} ${a.code} ${a.country} ${a.continent}`,
    ).includes(needle),
  );
}

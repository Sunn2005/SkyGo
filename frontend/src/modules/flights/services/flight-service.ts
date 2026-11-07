import { apiRequest } from "@/shared/api/client";
import { rememberAirports } from "../data/airports";
import type { Flight, Search, Cabin } from "../types";
export type Page<T> = {
  data: T[];
  pagination: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
};
export type Region = {
  regionCode: string;
  regionName: string;
  sortOrder: number;
};
export type ApiAirport = {
  code: string;
  name: string;
  city: string;
  countryName: string;
  regionCode: string;
};
export type ApiFlight = {
  flightId: string;
  flightNumber: string;
  airline: { code: string; name: string };
  origin: { code: string; city: string };
  destination: { code: string; city: string };
  departureAt: string;
  arrivalAt: string;
  durationMinutes: number;
  fares: {
    cabinClass: "ECONOMY" | "BUSINESS";
    price: { amount: number; currency: string; includesVat: boolean };
  }[];
};
export const loadRegions = (signal?: AbortSignal) =>
  apiRequest<Region[]>("/api/v1/regions", { signal });
export async function loadAirports(
  options: { keyword?: string; origin?: string; page?: number },
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({
    page: String(options.page ?? 0),
    size: "100",
  });
  if (options.keyword?.trim()) query.set("keyword", options.keyword.trim());
  if (options.origin) query.set("origin", options.origin);
  const result = await apiRequest<Page<ApiAirport>>(
    `/api/v1/airports?${query}`,
    { signal, unwrap: false },
  );
  rememberAirports(result.data);
  return result;
}
// Both pickers use the full catalog; route availability belongs to flight search.
export async function loadPickerAirports(
  keyword: string,
  excludedCode?: string,
  signal?: AbortSignal,
) {
  const all: ApiAirport[] = [];
  let page = 0;
  while (true) {
    const result = await loadAirports({ keyword, page }, signal);
    all.push(...result.data.filter((item) => item.code !== excludedCode));
    if (++page >= result.pagination.totalPages) return all;
  }
}
export function mapFlight(item: ApiFlight): Flight {
  const economy = item.fares.find((fare) => fare.cabinClass === "ECONOMY");
  const business = item.fares.find((fare) => fare.cabinClass === "BUSINESS");
  return {
    source: "api",
    id: item.flightId,
    number: item.flightNumber,
    airline: item.airline.name,
    departure: item.departureAt.slice(11, 16),
    arrival: item.arrivalAt.slice(11, 16),
    departureAt: item.departureAt,
    arrivalAt: item.arrivalAt,
    duration: `${Math.floor(item.durationMinutes / 60)}h ${item.durationMinutes % 60}m`,
    stops: 0,
    price: economy?.price.amount ?? business?.price.amount ?? 0,
    businessPrice: business?.price.amount,
    availableCabins: item.fares.map((fare): Cabin =>
      fare.cabinClass === "ECONOMY" ? "Phổ thông" : "Thương gia",
    ),
  };
}
export async function searchFlights(
  search: Search,
  direction: "OUTBOUND" | "RETURN",
  page = 0,
  signal?: AbortSignal,
) {
  const result = await apiRequest<Page<ApiFlight>>("/api/v1/flights/search", {
    method: "POST",
    unwrap: false,
    signal,
    body: {
      tripType: search.type === "one-way" ? "ONE_WAY" : "ROUND_TRIP",
      origin: search.from,
      destination: search.to,
      departureDate: search.date,
      ...(search.type === "round-trip"
        ? { returnDate: search.secondDate }
        : {}),
      passengerCounts: {
        adults: search.count,
        children: search.children ?? 0,
        infants: search.infants ?? 0,
      },
      direction,
      page,
      size: 20,
    },
  });
  return { ...result, data: result.data.map(mapFlight) };
}

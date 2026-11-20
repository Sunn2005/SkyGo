import { apiRequest } from "@/shared/api/client";
import { cacheRead, withOfflineCache } from "@/shared/api/offline-cache";

type Airport = { code: string; city: string };
export type HomeRoute = {
  routeId: number;
  origin: Airport;
  destination: Airport;
  priceFrom: { amount: number; currency: string; includesVat: boolean };
  imageUrl: string | null;
};
export type HomeData = {
  routeSource: "POPULAR" | "SUGGESTED" | "EMPTY";
  routeSectionTitle: string;
  secondarySectionTitle: string;
  featuredRoute: HomeRoute | null;
  otherRoutes: HomeRoute[];
  upcomingFlight: {
    bookingId: string;
    bookingCode: string;
    flightNumber: string;
    origin: Airport;
    destination: Airport;
    departureAt: string;
    arrivalAt: string;
    status: string;
  } | null;
};
/** Last home payload saved on device, shown instantly while loadHome refreshes. */
export const cachedHome = (token?: string) =>
  cacheRead<HomeData>(token ? "user" : "public", "home") ?? null;
export const loadHome =(token?: string, signal?: AbortSignal) =>
  withOfflineCache(token ? "user" : "public", "home", () =>
    apiRequest<HomeData>("/api/v1/home", { token, signal }),
  );

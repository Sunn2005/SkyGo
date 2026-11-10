import { apiRequest } from "@/shared/api/client";
import type { ApiFlight, Page } from "./flight-service";

export type SavedFlight = {
  savedAt: string;
  availability: "AVAILABLE" | "SOLD_OUT" | "CLOSED" | "DEPARTED";
  flight: ApiFlight;
};
export type FavoritePage = Page<SavedFlight> & { favoriteFlightIds: string[] };
const path = (id: string) =>
  `/api/v1/favorite-flights/${encodeURIComponent(id)}`;
export const favoriteApi = {
  list: (token: string, page = 0, signal?: AbortSignal) =>
    apiRequest<FavoritePage>(`/api/v1/favorite-flights?page=${page}&size=20`, {
      token,
      signal,
      unwrap: false,
    }),
  save: (token: string, id: string) =>
    apiRequest<void>(path(id), { token, method: "PUT" }),
  remove: (token: string, id: string) =>
    apiRequest<void>(path(id), { token, method: "DELETE" }),
};

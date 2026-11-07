import { apiRequest } from "@/shared/api/client";
import type { Search, Flight } from "../../flights/types";
import type { Traveller } from "../types";

export type Direction = "OUTBOUND" | "RETURN";
export type Hold = {
  seatHoldId: string;
  status: string;
  tripType: string;
  expiresAt: string;
  bookingId: string | null;
  passengerCounts: { adults: number; children: number; infants: number };
  legs: { direction: Direction; flightId: string; cabinClass: string }[];
};
export type BaggageLeg = {
  direction: Direction;
  checkedBaggagePolicy: {
    pieceMaxKg: number;
    maxLinearDimensionsCm: number;
    maxExtraPiecesPerPassenger: number;
    extraPiecePriceExclVat: number;
  };
  passengerBaggage: {
    clientPassengerId: string;
    canPurchaseExtra: boolean;
    selectedExtraPieces: number;
    extraAmountExclVat: number;
    included: {
      carryOnMaxKg: number;
      checkedPieces: number;
      strollerPieces: number;
      strollerMaxKg: number;
    };
  }[];
};
export type Review = {
  reviewId: string;
  version: number;
  expiresAt: string;
  currency: string;
  totalAmount: number;
  legs: BaggageLeg[];
  price: {
    fareSubtotal: number;
    extraBaggageAmount: number;
    vatRate: number;
    vatAmount: number;
    totalAmount: number;
  };
};
export type BaggageOptions = { version: number; legs: BaggageLeg[] };
export type CreatedBooking = {
  bookingId: string;
  bookingCode: string;
  bookingStatus: string;
  holdExpiresAt: string;
  totalAmount: number;
  currency: string;
};
export const passengerCounts = (s: Search) => ({
  adults: s.count,
  children: s.children ?? 0,
  infants: s.infants ?? 0,
});
export const tripType = (s: Search) =>
  s.type === "one-way" ? "ONE_WAY" : "ROUND_TRIP";
export const selection = (s: Search, f: Flight, index: number) => ({
  direction: (index ? "RETURN" : "OUTBOUND") as Direction,
  flightId: f.id,
  cabinClass: (f.cabin ?? s.cabin) === "Thương gia" ? "BUSINESS" : "ECONOMY",
});
export const quoteRequest = (id: string, s: Search, selected: Flight[]) => ({
  seatHoldId: id,
  tripType: tripType(s),
  passengerCounts: passengerCounts(s),
  selections: selected.map((f, i) => selection(s, f, i)),
});
const isoDate = (value: string) =>
  value.replace(/^(\d{2})\/(\d{2})\/(\d{4})$/, "$3-$2-$1");
export function passengerRequest(p: Traveller, i: number) {
  const passport = p.documentType === "passport";
  return {
    clientPassengerId: `p${i + 1}`,
    passengerType: (p.kind ?? "adult").toUpperCase(),
    title:
      (p.kind ?? "adult") === "adult"
        ? { Ông: "MR", Bà: "MRS", "Cô/Chị": "MS" }[p.title ?? "Ông"]
        : null,
    givenNames: p.firstName.trim(),
    surname: p.lastName.trim(),
    dateOfBirth: isoDate(p.birthDate),
    gender: { Nam: "MALE", Nữ: "FEMALE", Khác: "OTHER" }[p.gender],
    document: {
      documentType: passport ? "PASSPORT" : "PERSONAL_ID",
      number: p.documentNumber.trim(),
      issuingCountryCode: passport
        ? p.issuingCountry.trim().toUpperCase()
        : "VN",
      nationalityCountryCode: passport
        ? p.nationality.trim().toUpperCase()
        : null,
      expiresOn: passport ? isoDate(p.documentExpiry) : null,
    },
  };
}
export const bookingApi = {
  current: (token: string) =>
    apiRequest<Hold | null>("/api/v1/seat-holds/current", { token }),
  hold: (token: string, s: Search, f: Flight) =>
    apiRequest<Hold>("/api/v1/seat-holds", {
      token,
      method: "POST",
      body: {
        tripType: tripType(s),
        passengerCounts: passengerCounts(s),
        selection: selection(s, f, 0),
      },
    }),
  leg: (token: string, id: string, s: Search, f: Flight, index: number) =>
    apiRequest<Hold>(
      `/api/v1/seat-holds/${encodeURIComponent(id)}/legs/${index ? "RETURN" : "OUTBOUND"}`,
      {
        token,
        method: "PUT",
        body: { flightId: f.id, cabinClass: selection(s, f, index).cabinClass },
      },
    ),
  counts: (token: string, id: string, s: Search) =>
    apiRequest<Hold>(
      `/api/v1/seat-holds/${encodeURIComponent(id)}/passenger-counts`,
      { token, method: "PUT", body: passengerCounts(s) },
    ),
  quote: (token: string, body: ReturnType<typeof quoteRequest>) =>
    apiRequest<{ totalAmount: number; expiresAt: string }>(
      "/api/v1/booking-quotes",
      { token, method: "POST", body },
    ),
  review: (
    token: string,
    body: ReturnType<typeof quoteRequest> & {
      passengers: ReturnType<typeof passengerRequest>[];
      contact: { email: string; phoneNumber: string };
    },
  ) =>
    apiRequest<Review>("/api/v1/booking-reviews", {
      token,
      method: "POST",
      body,
    }),
  getReview: (token: string, id: string) =>
    apiRequest<Review>(`/api/v1/booking-reviews/${encodeURIComponent(id)}`, {
      token,
    }),
  baggageOptions: (token: string, id: string) =>
    apiRequest<BaggageOptions>(
      `/api/v1/booking-reviews/${encodeURIComponent(id)}/baggage-options`,
      { token },
    ),
  baggage: (
    token: string,
    id: string,
    expectedVersion: number,
    selections: {
      clientPassengerId: string;
      direction: Direction;
      extraPieces: number;
    }[],
  ) =>
    apiRequest<Review>(
      `/api/v1/booking-reviews/${encodeURIComponent(id)}/baggage`,
      { token, method: "PUT", body: { expectedVersion, selections } },
    ),
  create: (token: string, review: Review, key: string) =>
    apiRequest<CreatedBooking>("/api/v1/bookings", {
      token,
      method: "POST",
      headers: { "Idempotency-Key": key },
      body: { reviewId: review.reviewId, expectedVersion: review.version },
    }),
};

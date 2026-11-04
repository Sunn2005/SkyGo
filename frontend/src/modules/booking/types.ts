import type { Flight, Search } from "../flights/types";
import type { PaymentAttempt } from "../payment/types";

export type Traveller = {
  firstName: string;
  lastName: string;
  birthDate: string;
  gender: string;
  extraBags: Record<number, number>;
  documentType: "cccd" | "passport";
  documentNumber: string;
  nationality: string;
  issuingCountry: string;
  documentExpiry: string;
};

export type BookingInput = {
  search: Search;
  selected: Flight[];
  travellers: Traveller[];
  email: string;
  phone: string;
};

// Fare already includes taxes/fees; no separate tax or optional service charges.
export type BookingPrice = { fare: number; baggage: number; total: number };
export type BookingSnapshot = BookingInput &
  BookingPrice & { reference: string };

export type Booking = {
  id: string;
  owner: string;
  idempotencyKey: string;
  fingerprint: string;
  status: "pending_payment" | "confirmed";
  createdAt: string;
  receipt: BookingSnapshot;
  payments: PaymentAttempt[];
};
export interface BookingService {
  create(
    owner: string,
    input: BookingInput,
    idempotencyKey: string,
  ): Promise<Booking>;
  createPayment(owner: string, bookingId: string): Promise<Booking>;
  get(owner: string, bookingId: string): Promise<Booking>;
  list(owner: string): Promise<Booking[]>;
}

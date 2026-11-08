import { apiRequest } from "@/shared/api/client";

export type BookingPrice = {
  fareSubtotal: number;
  extraBaggageAmount: number;
  vatRate: number;
  vatAmount: number;
  totalAmount: number;
};
export type BookingLeg = {
  direction: "OUTBOUND" | "RETURN";
  flightId: string;
  flightNumber: string;
  airline: { code: string; name: string };
  cabinClass: string;
  origin: { code: string; name: string; city: string; countryCode: string };
  destination: {
    code: string;
    name: string;
    city: string;
    countryCode: string;
  };
  departureAt: string;
  arrivalAt: string;
  durationMinutes: number;
};
export type BookingDetail = {
  bookingId: string;
  bookingCode: string;
  bookingStatus: string;
  requiresRehold: boolean;
  canResumePayment: boolean;
  bookingClosesAt: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  createdAt: string;
  serverTime: string;
  tripType: string;
  passengerCounts: { adults: number; children: number; infants: number };
  contact: { email: string; phoneNumber: string };
  seatHold: {
    seatHoldId: string;
    status: string;
    expiresAt: string;
    expired: boolean;
  } | null;
  price: BookingPrice;
  currency: string;
  payment: {
    paymentId: string;
    method: string;
    status: string;
    amount: number;
    currency: string;
    transactionCode: string | null;
    paidAt: string | null;
  } | null;
  legs: BookingLeg[];
  passengers: {
    passengerId: string;
    clientPassengerId: string;
    passengerType: string;
    givenNames: string;
    surname: string;
    dateOfBirth: string;
    gender: string;
    document: { type: string; maskedNumber: string };
    benefits: {
      direction: string;
      carryOnMaxKg: number;
      checkedPieces: number;
      extraCheckedPieces: number;
      includedMealsPerLeg: number;
      seatType: string;
      strollerPieces: number;
    }[];
    ticketNumber: string | null;
  }[];
  invoice: { invoiceId: string; issuedAt: string } | null;
};
export type PaymentLink = {
  bookingId: string;
  bookingCode: string;
  bookingStatus: string;
  seatHoldId: string;
  expiresAt: string;
  tripType: string;
  passengerCounts: BookingDetail["passengerCounts"];
  legs: BookingLeg[];
  price: BookingPrice;
  currency: string;
  paymentLink: {
    paymentId: string;
    paymentLinkId: string;
    orderCode: number;
    status: string;
    amount: number;
    currency: string;
    description: string;
    qrCode: string;
    checkoutUrl: string;
  };
};
const bookingPath = (id: string) =>
  `/api/v1/bookings/${encodeURIComponent(id)}`;
export const paymentApi = {
  resume: (
    token: string,
    id: string,
    confirmation?: { expectedSeatHoldId: string; expectedVersion: number },
    signal?: AbortSignal,
  ) =>
    apiRequest<unknown>(`${bookingPath(id)}/resume-payment`, {
      token,
      signal,
      method: "POST",
      body: confirmation,
    }),
  detail: (token: string, id: string, signal?: AbortSignal) =>
    apiRequest<BookingDetail>(bookingPath(id), { token, signal }),
  link: (token: string, id: string, signal?: AbortSignal) =>
    apiRequest<PaymentLink>(`${bookingPath(id)}/payment-link`, {
      token,
      signal,
      method: "POST",
    }),
  open: (token: string, id: string, signal?: AbortSignal) =>
    apiRequest<unknown>(`${bookingPath(id)}/open-for-payment`, {
      token,
      signal,
      method: "POST",
    }),
};

export type ChatRole = "USER" | "ASSISTANT";
export type ChatTurn = { role: ChatRole; content: string };
export type AiAirport = {
  code: string;
  name: string;
  city: string;
  country: string;
  timeZone: string;
};
export type AiSource = {
  type: string;
  id: string;
  title: string;
  asOf: string | null;
};
export type AiFlightOption = {
  flightId: string;
  flightNumber: string;
  cabinClass: string;
  departureAt: string;
  arrivalAt: string;
  durationMinutes: number;
  availableSeats: number;
  adultPrice: number;
  partyTotal: number;
  currency: string;
};
export type AiFlightResult = {
  origin: AiAirport;
  destination: AiAirport;
  departureDate: string;
  passengerCounts: { adults: number; children: number; infants: number };
  sort: string;
  totalFareOptions: number;
  hasMore: boolean;
  includesVat: boolean;
  includesExtraBaggage: boolean;
  priceScope: string;
  asOf: string;
  options: AiFlightOption[];
};
export type AiBooking = {
  bookingId: string;
  bookingCode: string;
  bookingStatus: string;
  paymentStatus: string | null;
  createdAt: string;
  tripType: string;
  totalAmount: number;
  currency: string;
  passengerCounts: { adults: number; children: number; infants: number };
  legs: {
    direction: string;
    origin: { code: string; city: string };
    destination: { code: string; city: string };
    departureAt: string;
    cabinClass: string;
  }[];
};
export type AiBookingResult = {
  days: number;
  limit: number;
  status: string;
  asOf: string;
  bookings: AiBooking[];
};
export type AiClarification = {
  field: string;
  message: string;
  airportOptions: AiAirport[];
};
export type AiChatResponse = {
  status: "ANSWERED" | "NEEDS_CLARIFICATION" | "LOGIN_REQUIRED" | "NO_DATA";
  answer: string;
  sources: AiSource[];
  flightSearches: AiFlightResult[];
  bookingSearches: AiBookingResult[];
  clarifications: AiClarification[];
};
export type ChatRequest = { message: string; history: ChatTurn[] };
export type ChatMessage = {
  id: string;
  role: ChatRole;
  content: string;
  response?: AiChatResponse;
  question?: string;
  failure?: { request: ChatRequest; retryable: boolean };
};

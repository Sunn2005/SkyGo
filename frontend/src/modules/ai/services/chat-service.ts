import { apiRequest } from "@/shared/api/client";
import type {
  AiChatResponse,
  ChatMessage,
  ChatRequest,
  ChatTurn,
} from "../types";

export const CHAT_TIMEOUT_MS = 150000;

export function historyForRequest(messages: ChatMessage[]): ChatTurn[] {
  return messages
    .filter((message, index) => {
      const next = messages[index + 1];
      return (
        !message.failure &&
        message.response?.status !== "LOGIN_REQUIRED" &&
        !(
          message.role === "USER" &&
          (next?.failure || next?.response?.status === "LOGIN_REQUIRED")
        )
      );
    })
    .slice(-6)
    .map(({ role, content }) => ({ role, content: content.slice(0, 2000) }));
}

export async function sendChat(
  request: ChatRequest,
  token: string | undefined,
  signal: AbortSignal,
) {
  const response = await apiRequest<unknown>("/api/v1/ai/chat", {
    method: "POST",
    body: request,
    token,
    signal,
    timeoutMs: CHAT_TIMEOUT_MS,
  });
  if (!isChatResponse(response))
    throw new Error("Trợ lý chưa trả về kết quả hợp lệ. Vui lòng thử lại sau.");
  return response;
}

type Obj = Record<string, unknown>;
const object = (value: unknown): value is Obj =>
  !!value && typeof value === "object" && !Array.isArray(value);
const string = (value: unknown): value is string => typeof value === "string";
const number = (value: unknown): value is number =>
  typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
const list = (
  value: unknown,
  guard: (entry: unknown) => boolean,
  max: number,
) => Array.isArray(value) && value.length <= max && value.every(guard);
const airport = (v: unknown) =>
  object(v) && [v.code, v.name, v.city, v.country, v.timeZone].every(string);
const counts = (v: unknown) =>
  object(v) && [v.adults, v.children, v.infants].every(number);
const simpleAirport = (v: unknown) =>
  object(v) && string(v.code) && string(v.city);

export function isChatResponse(v: unknown): v is AiChatResponse {
  if (
    !object(v) ||
    !["ANSWERED", "NEEDS_CLARIFICATION", "LOGIN_REQUIRED", "NO_DATA"].includes(
      String(v.status),
    ) ||
    !string(v.answer) ||
    v.answer.length > 30000
  )
    return false;
  return (
    list(
      v.sources,
      (s) =>
        object(s) &&
        [s.type, s.id, s.title].every(string) &&
        (s.asOf === null || string(s.asOf)),
      100,
    ) &&
    list(
      v.clarifications,
      (c) =>
        object(c) &&
        string(c.field) &&
        string(c.message) &&
        list(c.airportOptions, airport, 10),
      20,
    ) &&
    list(
      v.flightSearches,
      (f) =>
        object(f) &&
        airport(f.origin) &&
        airport(f.destination) &&
        counts(f.passengerCounts) &&
        [f.departureDate, f.sort, f.asOf, f.priceScope].every(string) &&
        number(f.totalFareOptions) &&
        typeof f.hasMore === "boolean" &&
        typeof f.includesVat === "boolean" &&
        typeof f.includesExtraBaggage === "boolean" &&
        list(
          f.options,
          (o) =>
            object(o) &&
            [
              o.flightId,
              o.flightNumber,
              o.cabinClass,
              o.departureAt,
              o.arrivalAt,
              o.currency,
            ].every(string) &&
            [
              o.durationMinutes,
              o.availableSeats,
              o.adultPrice,
              o.partyTotal,
            ].every(number),
          10,
        ),
      4,
    ) &&
    list(
      v.bookingSearches,
      (b) =>
        object(b) &&
        number(b.days) &&
        number(b.limit) &&
        string(b.status) &&
        string(b.asOf) &&
        list(
          b.bookings,
          (o) =>
            object(o) &&
            [
              o.bookingId,
              o.bookingCode,
              o.bookingStatus,
              o.createdAt,
              o.tripType,
              o.currency,
            ].every(string) &&
            (o.paymentStatus === null || string(o.paymentStatus)) &&
            number(o.totalAmount) &&
            counts(o.passengerCounts) &&
            list(
              o.legs,
              (leg) =>
                object(leg) &&
                simpleAirport(leg.origin) &&
                simpleAirport(leg.destination) &&
                [leg.direction, leg.departureAt, leg.cabinClass].every(string),
              2,
            ),
          10,
        ),
      4,
    )
  );
}

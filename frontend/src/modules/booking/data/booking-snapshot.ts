import type { BookingInput, BookingSnapshot } from "../types";

// Whitelist the current model when creating requests or reading older local data.
export function bookingInput(input: BookingInput): BookingInput {
  const s = input.search;
  return {
    ...(input.reservationId ? { reservationId: input.reservationId } : {}),
    ...(input.expiresAt ? { expiresAt: input.expiresAt } : {}),
    search: {
      type: s.type,
      from: s.from,
      to: s.to,
      date: s.date,
      secondDate: s.secondDate,
      count: s.count,
      ...(s.children ? { children: s.children } : {}),
      ...(s.infants ? { infants: s.infants } : {}),
      cabin: s.cabin,
    },
    selected: input.selected,
    travellers: input.travellers.map((p) => ({
      ...(p.kind ? { kind: p.kind } : {}),
      ...(p.title ? { title: p.title } : {}),
      ...(p.companionIndex !== undefined
        ? { companionIndex: p.companionIndex }
        : {}),
      firstName: p.firstName,
      lastName: p.lastName,
      birthDate: p.birthDate,
      gender: p.gender,
      extraBags: p.extraBags,
      documentType: p.documentType,
      documentNumber: p.documentNumber,
      nationality: p.nationality,
      issuingCountry: p.issuingCountry,
      documentExpiry: p.documentExpiry,
    })),
    email: input.email,
    phone: input.phone,
  };
}
export function bookingSnapshot(snapshot: BookingSnapshot): BookingSnapshot {
  return {
    ...bookingInput(snapshot),
    reference: snapshot.reference,
    fare: snapshot.fare,
    baggage: snapshot.baggage,
    total: snapshot.total,
  };
}

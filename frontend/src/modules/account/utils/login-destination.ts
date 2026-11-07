const destinations = {
  detail: "/results",
  "flight-selection": "/results",
  results: "/results",
  traveller: "/traveller",
  baggage: "/baggage",
  payment: "/payment",
  payos: "/payos",
  booking: "/booking",
  success: "/success",
  bookings: "/bookings",
} as const;

export function loginDestination(returnTo?: string) {
  const key = returnTo?.replace(/^\//, "");
  return {
    href:
      key && Object.hasOwn(destinations, key)
        ? destinations[key as keyof typeof destinations]
        : ("/" as const),
    resumeFlight: key === "flight-selection" || key === "detail",
  };
}

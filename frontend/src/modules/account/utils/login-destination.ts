const destinations = {
  "ai-chat": "/ai-chat",
  detail: "/results",
  "flight-selection": "/results",
  results: "/results",
  saved: "/saved",
  traveller: "/traveller",
  baggage: "/baggage",
  payment: "/payment",
  payos: "/payos",
  booking: "/booking",
  success: "/success",
  bookings: "/bookings",
  "payment/success": "/payment/success",
  "payment/cancel": "/payment/cancel",
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

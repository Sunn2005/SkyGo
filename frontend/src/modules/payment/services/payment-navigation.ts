export type PaymentReturnTo = "results" | "booking";

export function paymentReturnTo(
  value?: string,
  fallback?: PaymentReturnTo,
): PaymentReturnTo {
  return value === "results" || value === "booking"
    ? value
    : (fallback ?? "booking");
}

export function paymentReturnPath(destination: PaymentReturnTo) {
  return destination === "results"
    ? ("/results" as const)
    : ("/booking" as const);
}

// Back gestures and hardware/browser Back must follow the same confirmed exit as the header.
export function isPaymentBackAction(type: string) {
  return ["GO_BACK", "POP", "POP_TO", "POP_TO_TOP"].includes(type);
}

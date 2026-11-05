export type PaymentOutcome = "paid" | "failed" | "cancelled" | "expired";
export type PaymentStatus = "pending" | PaymentOutcome;
export type PaymentAttempt = {
  id: string;
  status: PaymentStatus;
  createdAt: string;
  expiresAt: string;
  completedAt?: string;
};

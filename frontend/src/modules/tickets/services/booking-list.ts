import { apiRequest } from "@/shared/api/client";
import { withOfflineCache } from "@/shared/api/offline-cache";

export type BookingSummary = {
  bookingId: string;
  bookingCode: string;
  bookingStatus: string;
  paymentStatus: string | null;
  createdAt: string;
  tripType: "ONE_WAY" | "ROUND_TRIP";
  passengerCounts: { adults: number; children: number; infants: number };
  totalAmount: number;
  currency: string;
  legs: {
    direction: "OUTBOUND" | "RETURN";
    origin: { code: string; city: string };
    destination: { code: string; city: string };
    departureAt: string;
    cabinClass: "ECONOMY" | "BUSINESS";
  }[];
};

export type BookingPage = {
  data: BookingSummary[];
  pagination: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
};

export type BookingFilter = "all" | "paid" | "pending";

export const listBookings = (
  token: string,
  page = 0,
  filter: BookingFilter = "all",
  signal?: AbortSignal,
) =>
  withOfflineCache("user", `bookings:${filter}:${page}`, () =>
    apiRequest<BookingPage>(
      `/api/v1/bookings?page=${page}&size=20&status=${filter}`,
      {
        token,
        signal,
        unwrap: false,
      },
    ),
  );

export const isSummaryPaid = (item: BookingSummary) =>
  item.bookingStatus === "PAID" && item.paymentStatus === "SUCCEEDED";

export const summaryStatus = (item: BookingSummary) => {
  if (isSummaryPaid(item)) return { label: "Đã xác nhận", tone: "success" };
  if (
    item.bookingStatus === "RECONCILIATION_REQUIRED" ||
    item.paymentStatus === "RECONCILIATION_REQUIRED"
  )
    return { label: "Cần đối soát", tone: "warning" };
  if (item.bookingStatus === "CANCELLED")
    return { label: "Đã hủy", tone: "neutral" };
  if (item.bookingStatus === "EXPIRED")
    return { label: "Quá hạn đặt vé", tone: "neutral" };
  if (item.bookingStatus === "AWAITING_REHOLD")
    return { label: "Cần giữ chỗ lại", tone: "warning" };
  return { label: "Chờ thanh toán", tone: "warning" };
};

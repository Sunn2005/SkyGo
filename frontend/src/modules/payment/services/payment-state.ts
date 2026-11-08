import type { BookingDetail, PaymentLink } from "./payment-service";
export const isBookingPaid = (booking: BookingDetail) =>
  booking.bookingStatus === "PAID" && booking.payment?.status === "SUCCEEDED";
export const needsReconciliation = (booking: BookingDetail) =>
  booking.bookingStatus === "RECONCILIATION_REQUIRED" ||
  booking.payment?.status === "RECONCILIATION_REQUIRED";
export function serverNow(
  booking: BookingDetail,
  receivedAt: number,
  now = Date.now(),
) {
  return Date.parse(booking.serverTime) + Math.max(0, now - receivedAt);
}
export function holdExpired(
  booking: BookingDetail,
  receivedAt: number,
  now = Date.now(),
) {
  return (
    booking.bookingStatus === "EXPIRED" ||
    booking.payment?.status === "EXPIRED" ||
    !booking.seatHold ||
    booking.seatHold.expired ||
    Date.parse(booking.seatHold.expiresAt) <=
      serverNow(booking, receivedAt, now)
  );
}
export function canPay(
  booking: BookingDetail,
  receivedAt: number,
  now = Date.now(),
) {
  return (
    booking.bookingStatus === "PENDING_PAYMENT" &&
    booking.payment?.status === "PENDING" &&
    booking.seatHold?.status === "ACTIVE" &&
    !needsReconciliation(booking) &&
    !holdExpired(booking, receivedAt, now)
  );
}
export function linkMatchesBooking(link: PaymentLink, booking: BookingDetail) {
  return (
    link.bookingId === booking.bookingId &&
    link.seatHoldId === booking.seatHold?.seatHoldId &&
    link.paymentLink.paymentId === booking.payment?.paymentId &&
    link.paymentLink.amount === booking.price.totalAmount &&
    link.paymentLink.currency === booking.currency &&
    ["PENDING", "ACTIVE"].includes(link.paymentLink.status)
  );
}
export function paymentMessage(
  code?: string,
  fallback = "Chưa thể xử lý thanh toán. Vui lòng thử lại.",
) {
  const messages: Record<string, string> = {
    PAYOS_NOT_CONFIGURED:
      "Máy chủ chưa cấu hình PayOS. Đơn đã được lưu; vui lòng thử thanh toán lại sau.",
    PAYMENT_LINK_UNVERIFIED:
      "Chưa xác minh được link thanh toán. Vui lòng kiểm tra trạng thái hoặc liên hệ hỗ trợ; chưa tạo link khác.",
    PAYMENT_STATUS_UNVERIFIED:
      "Giao dịch cũ cần được đối soát trước khi thanh toán tiếp. Vui lòng liên hệ hỗ trợ.",
    PAYMENT_RECONCILIATION_REQUIRED:
      "Giao dịch cần đối soát. Vui lòng liên hệ hỗ trợ trước khi thanh toán tiếp.",
    SEAT_HOLD_EXPIRED:
      "Thời gian giữ chỗ đã hết. Vui lòng mở lại đơn để kiểm tra khả năng giữ chỗ.",
    INSUFFICIENT_CAPACITY:
      "Không còn đủ chỗ cho hành trình này. Vui lòng tìm chuyến khác.",
    UNAUTHENTICATED: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  };
  return code ? (messages[code] ?? fallback) : fallback;
}

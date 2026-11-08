import type { BookingDetail } from "@/modules/payment/services/payment-service";
import {
  holdExpired,
  isBookingPaid,
  needsReconciliation,
  serverNow,
} from "@/modules/payment/services/payment-state";

export function bookingPresentation(
  booking: BookingDetail,
  receivedAt: number,
  now = Date.now(),
) {
  if (isBookingPaid(booking))
    return {
      key: "paid",
      title: "Đã xác nhận",
      description: "Thanh toán thành công. Vé của bạn đã được xác nhận.",
      tone: "success",
    } as const;
  if (needsReconciliation(booking))
    return {
      key: "reconciliation",
      title: "Cần đối soát",
      description:
        "Giao dịch đang được kiểm tra. Vui lòng liên hệ hỗ trợ trước khi thanh toán thêm.",
      tone: "warning",
    } as const;
  if (booking.bookingStatus === "CANCELLED")
    return {
      key: "cancelled",
      title: "Đã hủy",
      description: "Đơn đã quá thời hạn đặt vé. Bạn có thể tìm hành trình mới.",
      tone: "neutral",
    } as const;
  if (
    booking.bookingStatus === "PAID" ||
    booking.payment?.status === "SUCCEEDED"
  )
    return {
      key: "confirming",
      title: "Đang xác nhận",
      description:
        "Hệ thống đang cập nhật thông tin vé. Vui lòng kiểm tra lại sau.",
      tone: "warning",
    } as const;
  if (
    booking.bookingClosesAt &&
    serverNow(booking, receivedAt, now) >= Date.parse(booking.bookingClosesAt)
  )
    return {
      key: "closed",
      title: "Quá hạn đặt vé",
      description:
        "Hành trình này đã đóng đặt vé và không thể tiếp tục thanh toán.",
      tone: "neutral",
    } as const;
  if (holdExpired(booking, receivedAt, now))
    return {
      key: "rehold",
      title: "Cần giữ chỗ lại",
      description:
        "Đơn và giá đã được lưu. Tiếp tục thanh toán để kiểm tra chỗ còn trống.",
      tone: "warning",
    } as const;
  return {
    key: "pending",
    title: "Chờ thanh toán",
    description: "Hoàn tất thanh toán trong thời gian giữ chỗ để xác nhận vé.",
    tone: "warning",
  } as const;
}

// Keep the calendar date and time at the airport, not the phone's timezone.
export const flightDate = (iso: string) =>
  `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`;
export const passengerCountLabel = (counts: BookingDetail["passengerCounts"]) =>
  [
    counts.adults && `${counts.adults} người lớn`,
    counts.children && `${counts.children} trẻ em`,
    counts.infants && `${counts.infants} em bé`,
  ]
    .filter(Boolean)
    .join(" · ");

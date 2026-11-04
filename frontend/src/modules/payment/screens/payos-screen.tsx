import { useEffect, useRef, useState } from "react";
import { Redirect, router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import type { PaymentOutcome } from "../types";
import { money } from "@/shared/utils/format";
import {
  Button,
  Card,
  Label,
  Notice,
  SummaryRow,
} from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
const labels = {
  pending: "Đang chờ thanh toán",
  paid: "Đã thanh toán",
  failed: "Thanh toán thất bại",
  cancelled: "Đã hủy thanh toán",
  expired: "Yêu cầu thanh toán hết hạn",
};
export default function PayosScreen() {
  const b = useBooking();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const locked = useRef(false);
  const booking = b.currentBooking;
  const payment = booking?.payments.at(-1);
  const refresh = b.refreshBooking;
  useEffect(() => {
    if (!booking || payment?.status !== "pending") return;
    const timer = setInterval(() => {
      void refresh().catch(() =>
        setError("Chưa lấy được trạng thái. Vui lòng kiểm tra lại."),
      );
    }, 3000);
    return () => clearInterval(timer);
  }, [booking, payment?.status, refresh]);
  const perform = async (action: "refresh" | "retry" | PaymentOutcome) => {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    setError("");
    try {
      const result =
        action === "refresh"
          ? await b.refreshBooking()
          : action === "retry"
            ? await b.retryPayment()
            : await b.simulatePayment(action);
      if (result.status === "confirmed") router.replace("/success");
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Chưa thể xử lý. Vui lòng thử lại.",
      );
    } finally {
      locked.current = false;
      setBusy(false);
    }
  };
  if (!booking) return <Redirect href="/bookings" />;
  return (
    <Screen title="Thanh toán PayOS" onBack={() => router.replace("/bookings")}>
      <Label size={26} weight="bold">
        {booking.status === "confirmed"
          ? "Đã thanh toán"
          : payment
            ? labels[payment.status]
            : "Chưa tạo yêu cầu thanh toán"}
      </Label>
      <Card>
        <SummaryRow label="Mã đặt chỗ" value={booking.id} />
        <SummaryRow
          label="Tổng · VND"
          value={money(booking.receipt.total)}
          bold
        />
        {payment && (
          <>
            <Label selectable size={12}>
              Giao dịch: {payment.id}
            </Label>
            <Label size={12}>
              Hết hạn: {new Date(payment.expiresAt).toLocaleString("vi-VN")}
            </Label>
          </>
        )}
      </Card>
      {booking.status === "confirmed" ? (
        <Button
          title="Xem đặt chỗ đã thanh toán"
          onPress={() => router.replace("/success")}
        />
      ) : payment?.status === "pending" ? (
        <>
          <Notice>
            PayOS mô phỏng: chọn một kết quả để thử luồng. Kết quả được cập nhật
            trong dịch vụ mẫu trước khi ứng dụng lấy lại trạng thái.
          </Notice>
          {(
            [
              ["paid", "Mô phỏng thanh toán thành công"],
              ["failed", "Mô phỏng thanh toán thất bại"],
              ["cancelled", "Mô phỏng hủy thanh toán"],
              ["expired", "Mô phỏng hết hạn"],
            ] as const
          ).map(([outcome, label]) => (
            <Button
              key={outcome}
              title={label}
              secondary={outcome !== "paid"}
              disabled={busy}
              onPress={() => void perform(outcome)}
            />
          ))}
        </>
      ) : (
        <>
          <Notice>
            Đặt chỗ vẫn được giữ. Bạn có thể tạo yêu cầu thanh toán mới cho cùng
            mã đặt chỗ.
          </Notice>
          <Button
            title="Thử thanh toán lại"
            loading={busy}
            onPress={() => void perform("retry")}
          />
        </>
      )}
      <Button
        title="Kiểm tra trạng thái"
        secondary
        disabled={busy}
        onPress={() => void perform("refresh")}
      />
      <Button
        title="Về danh sách đặt chỗ"
        secondary
        disabled={busy}
        onPress={() => router.replace("/bookings")}
      />
      {!!error && <Notice error>{error}</Notice>}
    </Screen>
  );
}

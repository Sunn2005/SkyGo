import { router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { Screen } from "@/shared/components/screen";
import { Button, Notice } from "@/shared/components/ui";
import LivePayosScreen from "./live-payos-screen";

export default function PaymentReturnScreen({
  outcome,
}: {
  outcome: "success" | "cancel";
}) {
  const { livePayment } = useBooking();
  // Callback query parameters are untrusted. Only authenticated BE state can
  // confirm payment. A cold start has no in-memory selection: let the user pick
  // their saved order rather than guessing which order an orderCode refers to.
  if (livePayment)
    return (
      <LivePayosScreen
        key={livePayment.id}
        bookingId={livePayment.id}
        returnOutcome={outcome}
        returnTo={livePayment.returnTo}
      />
    );
  return (
    <Screen title="Kiểm tra thanh toán" onBack={() => router.replace("/")}>
      <Notice>
        {outcome === "cancel"
          ? "Bạn đã rời trang thanh toán. Mở đơn trong lịch sử để kiểm tra trạng thái hoặc tiếp tục thanh toán."
          : "Bạn đã quay lại từ PayOS. Mở đơn trong lịch sử để kiểm tra kết quả thanh toán và vé."}
      </Notice>
      <Button
        title="Mở lịch sử đặt chỗ"
        onPress={() => router.replace("/bookings")}
      />
    </Screen>
  );
}

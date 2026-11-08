import { useCallback, useState } from "react";
import { router, useFocusEffect } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { useAccount } from "@/modules/account/context/account-context";
import {
  paymentApi,
  type BookingDetail,
} from "@/modules/payment/services/payment-service";
import { knownBookingIds } from "@/modules/payment/services/known-bookings";
import {
  holdExpired,
  isBookingPaid,
  needsReconciliation,
} from "@/modules/payment/services/payment-state";
import { money } from "@/shared/utils/format";
import { Button, Card, Label, Notice, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
export default function BookingsScreen() {
  const b = useBooking();
  const { session } = useAccount();
  const token = session?.accessToken;
  const owner = b.accountId;
  const [items, setItems] = useState<
    { data: BookingDetail; receivedAt: number }[]
  >([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useFocusEffect(
    useCallback(() => {
      void retry;
      if (!token) return;
      const controller = new AbortController();
      const ids = knownBookingIds(owner, localStorage);
      setLoading(true);
      setError("");
      setItems([]);
      void Promise.allSettled(
        ids.map(async (id) => ({
          data: await paymentApi.detail(token, id, controller.signal),
          receivedAt: Date.now(),
        })),
      )
        .then((results) => {
          if (controller.signal.aborted) return;
          setItems(
            results
              .flatMap((result) =>
                result.status === "fulfilled" ? [result.value] : [],
              )
              .sort(
                (a, b) =>
                  Date.parse(b.data.createdAt) - Date.parse(a.data.createdAt),
              ),
          );
          if (results.some((result) => result.status === "rejected"))
            setError("Một số đơn chưa tải được. Vui lòng thử lại.");
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
      return () => controller.abort();
    }, [token, owner, retry]),
  );
  return (
    <Screen title="Lịch sử đặt chỗ">
      <Label size={26} weight="bold">
        Hành trình của bạn
      </Label>
      <Label size={12} muted>
        Các đơn đã tạo hoặc mở trên thiết bị này. Trạng thái và vé được cập nhật
        từ hệ thống.
      </Label>
      {loading && <Notice>Đang tải đặt chỗ…</Notice>}
      {!!error && (
        <>
          <Notice error>{error}</Notice>
          <Button title="Tải lại" onPress={() => setRetry((n) => n + 1)} />
        </>
      )}
      {items.map(({ data, receivedAt }) => (
        <Tap
          key={data.bookingId}
          onPress={() => {
            b.selectPaymentBooking(data.bookingId);
            router.push("/booking");
          }}
        >
          <Card>
            <Label weight="bold">{data.bookingCode}</Label>
            {data.legs.map((leg) => (
              <Label key={leg.direction}>
                {leg.origin.code} → {leg.destination.code} ·{" "}
                {leg.departureAt.slice(0, 10)}
              </Label>
            ))}
            <Label>
              {isBookingPaid(data)
                ? "Đã thanh toán · Xem vé"
                : needsReconciliation(data)
                  ? "Cần đối soát"
                  : holdExpired(data, receivedAt)
                    ? "Hết hạn giữ chỗ"
                    : "Chờ thanh toán · Tiếp tục"}
            </Label>
            <Label weight="bold">{money(data.price.totalAmount)}</Label>
          </Card>
        </Tap>
      ))}
      {!loading && !items.length && !error && (
        <Notice>
          Chưa có đơn đặt vé được lưu trên thiết bị này cho tài khoản của bạn.
        </Notice>
      )}
      <Button title="Tìm chuyến bay" onPress={() => router.push("/search")} />
    </Screen>
  );
}

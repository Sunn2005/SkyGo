import { useRef, useState } from "react";
import { router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { money } from "@/shared/utils/format";
import { Button, Card, Label, Notice, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
export default function BookingsScreen() {
  const b = useBooking();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const open = async (id: string) => {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      await b.openBooking(id);
      router.push("/booking");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Chưa thể mở đặt chỗ.");
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  return (
    <Screen title="Lịch sử đặt chỗ">
      <Label size={26} weight="bold">
        Hành trình của bạn
      </Label>
      {b.historyLoading && <Label>Đang tải đặt chỗ…</Label>}
      {!!b.historyError && (
        <>
          <Notice error>{b.historyError}</Notice>
          <Button
            title="Tải lại đặt chỗ"
            secondary
            disabled={b.historyLoading}
            onPress={() => void b.reloadHistory()}
          />
        </>
      )}
      {!!error && <Notice error>{error}</Notice>}
      {b.history.map((item) => (
        <Tap
          key={item.receipt.reference}
          disabled={busy}
          onPress={() => void open(item.id)}
        >
          <Card>
            <Label selectable weight="semibold">
              {item.receipt.reference}
            </Label>
            <Label>
              {item.receipt.search.from} → {item.receipt.search.to} ·{" "}
              {item.receipt.search.type === "round-trip"
                ? "Khứ hồi"
                : "Một chiều"}
            </Label>
            <Label size={13}>
              {item.receipt.search.date} · {item.receipt.travellers.length}{" "}
              người
            </Label>
            <Label>
              {item.status === "confirmed"
                ? "Đã thanh toán · Xem vé"
                : item.payments.at(-1)?.status === "expired"
                  ? "Hết hạn · Tìm lại chuyến bay"
                  : "Chờ thanh toán · Tiếp tục"}
            </Label>
            <Label weight="bold">{money(item.receipt.total)}</Label>
          </Card>
        </Tap>
      ))}
      {!b.history.length && !b.historyLoading && (
        <Label muted>Bạn chưa có đặt chỗ nào trên thiết bị này.</Label>
      )}
      <Button
        title="Tìm chuyến bay"
        onPress={() => router.replace("/search")}
      />
      <Notice>
        Đặt chỗ mẫu được lưu trên thiết bị theo tài khoản. Dùng thông tin giả
        khi thử nghiệm.
      </Notice>
    </Screen>
  );
}

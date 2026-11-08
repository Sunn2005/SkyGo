import { useCallback, useLayoutEffect, useRef, useState } from "react";
import { View } from "react-native";
import { router, useFocusEffect } from "expo-router";
import { useAccount } from "@/modules/account/context/account-context";
import { useBooking } from "@/modules/booking/context/booking-context";
import { paymentApi } from "@/modules/payment/services/payment-service";
import {
  holdExpired,
  isBookingPaid,
  needsReconciliation,
} from "@/modules/payment/services/payment-state";
import { Screen } from "@/shared/components/screen";
import { Button, Label, Notice, Row } from "@/shared/components/ui";
import { Panel, InfoRow } from "@/modules/booking/components/design";
import { money } from "@/shared/utils/format";

export default function LiveBookingScreen({
  success = false,
}: {
  success?: boolean;
}) {
  const b = useBooking();
  const { session } = useAccount();
  const token = session?.accessToken;
  const current = b.livePayment;
  const id = current?.id;
  const data = current?.detail;
  const acceptRef = useRef(b.acceptPaymentDetail);
  useLayoutEffect(() => {
    acceptRef.current = b.acceptPaymentDetail;
  }, [b.acceptPaymentDetail]);
  const [loading, setLoading] = useState(!data);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useFocusEffect(
    useCallback(() => {
      void retry;
      if (!id || !token) return;
      const controller = new AbortController();
      setLoading(true);
      setError("");
      paymentApi
        .detail(token, id, controller.signal)
        .then((result) => {
          if (!controller.signal.aborted) acceptRef.current(result);
        })
        .catch((cause) => {
          if (!controller.signal.aborted)
            setError(
              cause instanceof Error ? cause.message : "Không tải được đơn.",
            );
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
      return () => controller.abort();
    }, [id, token, retry]),
  );
  const paid = !!data && isBookingPaid(data);
  const reconciling = !!data && needsReconciliation(data);
  const expired = !!data && !paid && holdExpired(data, current!.receivedAt);
  return (
    <Screen
      design
      title={success && paid ? "Đặt vé thành công" : "Chi tiết đặt chỗ"}
      onBack={() => router.replace("/")}
    >
      {loading && <Notice>Đang tải thông tin đặt chỗ…</Notice>}
      {!!error && (
        <>
          <Notice error>{error}</Notice>
          <Button title="Tải lại" onPress={() => setRetry((n) => n + 1)} />
        </>
      )}
      {data && (
        <>
          {success && paid && (
            <Panel tinted>
              <Label size={26} weight="bold">
                ✓ Đặt vé thành công
              </Label>
              <Label>Thanh toán đã được hệ thống xác nhận.</Label>
            </Panel>
          )}
          <Row style={{ justifyContent: "space-between" }}>
            <Label size={23} weight="bold" selectable>
              {data.bookingCode}
            </Label>
            <Label>
              {paid
                ? "Đã thanh toán"
                : reconciling
                  ? "Cần đối soát"
                  : expired
                    ? "Hết hạn"
                    : "Chờ thanh toán"}
            </Label>
          </Row>
          <Label size={12}>
            Tạo lúc {new Date(data.createdAt).toLocaleString("vi-VN")}
          </Label>
          {data.legs.map((leg) => (
            <Panel key={leg.direction}>
              <Label size={18} weight="bold">
                {leg.origin.city} → {leg.destination.city}
              </Label>
              <Label>
                {leg.airline.name} · {leg.flightNumber} ·{" "}
                {leg.cabinClass === "BUSINESS" ? "Thương gia" : "Phổ thông"}
              </Label>
              <InfoRow
                label="Khởi hành"
                value={`${leg.departureAt.slice(0, 10)} · ${leg.departureAt.slice(11, 16)} (${leg.origin.code})`}
              />
              <InfoRow
                label="Đến"
                value={`${leg.arrivalAt.slice(0, 10)} · ${leg.arrivalAt.slice(11, 16)} (${leg.destination.code})`}
              />
              <Label size={11}>Giờ địa phương tại từng sân bay</Label>
            </Panel>
          ))}
          <Panel>
            <Label size={17} weight="bold">
              Hành khách
            </Label>
            {data.passengers.map((p) => (
              <View key={p.passengerId} style={{ gap: 8 }}>
                <Label weight="semibold">
                  {p.surname} {p.givenNames}
                </Label>
                <InfoRow
                  label={
                    p.document.type === "PASSPORT" ? "Hộ chiếu" : "Giấy tờ"
                  }
                  value={p.document.maskedNumber}
                />
                {paid && (
                  <InfoRow
                    label="Số vé"
                    value={p.ticketNumber ?? "Đang cập nhật"}
                    bold
                  />
                )}
                {p.benefits.map((benefit) => (
                  <View key={benefit.direction} style={{ gap: 4 }}>
                    <Label size={12}>
                      {benefit.direction === "RETURN" ? "Chặng về" : "Chặng đi"}{" "}
                      · Xách tay {benefit.carryOnMaxKg} kg
                    </Label>
                    <Label size={12}>
                      Ký gửi miễn cước {benefit.checkedPieces} kiện · Mua thêm{" "}
                      {benefit.extraCheckedPieces} kiện
                    </Label>
                    {benefit.strollerPieces > 0 && (
                      <Label size={12}>
                        Xe đẩy: {benefit.strollerPieces} kiện
                      </Label>
                    )}
                  </View>
                ))}
              </View>
            ))}
          </Panel>
          <Panel>
            <Label weight="bold">Thông tin liên hệ</Label>
            <InfoRow label="Email" value={data.contact.email} />
            <InfoRow label="Điện thoại" value={data.contact.phoneNumber} />
          </Panel>
          <Panel>
            <Label weight="bold">Chi tiết giá</Label>
            <InfoRow
              label="Vé chưa VAT"
              value={money(data.price.fareSubtotal)}
            />
            <InfoRow
              label="Hành lý chưa VAT"
              value={money(data.price.extraBaggageAmount)}
            />
            <InfoRow label="VAT" value={money(data.price.vatAmount)} />
            <InfoRow
              label="Tổng cộng"
              value={money(data.price.totalAmount)}
              bold
            />
          </Panel>
          {data.payment && (
            <Panel>
              <Label weight="bold">Thanh toán</Label>
              <InfoRow
                label="Trạng thái"
                value={paid ? "Đã thanh toán" : data.payment.status}
              />
              <InfoRow
                label="Phương thức"
                value={
                  data.payment.method === "PAYOS"
                    ? "PayOS"
                    : data.payment.method
                }
              />
              {data.payment.transactionCode && (
                <InfoRow
                  label="Mã giao dịch"
                  value={data.payment.transactionCode}
                />
              )}
              {paid && data.payment.paidAt && (
                <InfoRow
                  label="Thời điểm thanh toán"
                  value={new Date(data.payment.paidAt).toLocaleString("vi-VN")}
                />
              )}
              {paid && data.invoice && (
                <InfoRow label="Mã hóa đơn" value={data.invoice.invoiceId} />
              )}
            </Panel>
          )}
          {reconciling && (
            <Notice error>
              Giao dịch cần đối soát. Vui lòng liên hệ hỗ trợ trước khi thanh
              toán tiếp.
            </Notice>
          )}
          {!paid && !reconciling && (
            <Button
              title={expired ? "Mở lại thanh toán" : "Tiếp tục thanh toán"}
              onPress={() => router.push("/payos")}
            />
          )}
        </>
      )}
      <Button
        secondary
        title="Về trang chủ"
        onPress={() => router.replace("/")}
      />
    </Screen>
  );
}

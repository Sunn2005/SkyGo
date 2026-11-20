import { useCallback, useLayoutEffect, useRef, useState } from "react";
import { View } from "react-native";
import { router, useFocusEffect } from "expo-router";
import { useAccount } from "@/modules/account/context/account-context";
import { useBooking } from "@/modules/booking/context/booking-context";
import { paymentApi } from "@/modules/payment/services/payment-service";
import {
  canResume,
  canPay,
  isBookingPaid,
} from "@/modules/payment/services/payment-state";
import { Screen } from "@/shared/components/screen";
import {
  Button,
  Card,
  Icon,
  Label,
  Notice,
  Row,
  Tap,
} from "@/shared/components/ui";
import { InfoRow } from "@/modules/booking/components/design";
import { colors as c } from "@/shared/theme";
import { money } from "@/shared/utils/format";
import {
  BookingDisclosure,
  BookingJourney,
  BookingSectionTitle,
  BookingStatus,
} from "../components/booking-ui";
import {
  bookingPresentation,
  passengerCountLabel,
} from "../services/booking-presentation";

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
  const showSuccess = success && paid;
  const status = data ? bookingPresentation(data, current!.receivedAt) : null;
  const payable =
    !!data &&
    (canPay(data, current!.receivedAt) || canResume(data, current!.receivedAt));
  const destination = data?.legs.at(-1)?.destination.city;
  return (
    <Screen
      design
      title={showSuccess ? "Đặt vé thành công" : "Chi tiết đặt chỗ"}
      onBack={() =>
        showSuccess
          ? router.replace("/")
          : router.canGoBack()
            ? router.back()
            : router.replace("/bookings")
      }
      footer={
        data ? (
          <Button
            title={
              showSuccess
                ? "Xem chi tiết vé"
                : payable
                  ? status?.key === "rehold"
                    ? "Giữ chỗ lại để thanh toán"
                    : "Tiếp tục thanh toán"
                  : status?.key === "reconciliation"
                    ? "Liên hệ hỗ trợ"
                    : "Về trang chủ"
            }
            onPress={() => {
              if (!showSuccess && payable && data) {
                b.selectPaymentBooking(data.bookingId);
                router.push({
                  pathname: "/payos",
                  params: { returnTo: "booking" },
                });
                return;
              }
              return showSuccess
                ? router.replace("/booking")
                : status?.key === "reconciliation"
                  ? router.push("/help")
                  : router.replace("/");
            }}
          />
        ) : undefined
      }
    >
      {loading && (
        <Label size={12} muted>
          Đang cập nhật thông tin đặt chỗ…
        </Label>
      )}
      {!!error && (
        <>
          <Notice error>{error}</Notice>
          <Button
            secondary
            title="Tải lại thông tin"
            onPress={() => setRetry((n) => n + 1)}
          />
        </>
      )}
      {data && (
        <>
          {showSuccess && (
            <View
              style={{ alignItems: "center", gap: 12, paddingVertical: 12 }}
            >
              <View
                style={{
                  width: 72,
                  height: 72,
                  borderRadius: 36,
                  backgroundColor: "#E1F4E9",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <Icon name="6-7220-imgCheck" size={34} />
              </View>
              <Label size={25} weight="bold" style={{ textAlign: "center" }}>
                Vé của bạn đã được xác nhận!
              </Label>
              <Label size={13} muted style={{ textAlign: "center" }}>
                {destination ? `${destination} đang chờ bạn. ` : ""}Chúc bạn một
                hành trình thật trọn vẹn.
              </Label>
            </View>
          )}
          <Card style={{ padding: 18, gap: 12 }}>
            <Row
              style={{
                justifyContent: "space-between",
                flexWrap: "wrap",
                gap: 8,
              }}
            >
              <Label size={11} muted>
                MÃ ĐẶT CHỖ
              </Label>
              <BookingStatus booking={data} receivedAt={current!.receivedAt} />
            </Row>
            <Label
              size={21}
              weight="bold"
              selectable
              numberOfLines={1}
              adjustsFontSizeToFit
              minimumFontScale={0.7}
            >
              {data.bookingCode}
            </Label>
            <Label size={12} muted>
              {passengerCountLabel(data.passengerCounts)} ·{" "}
              {data.tripType === "ROUND_TRIP" ? "Khứ hồi" : "Một chiều"}
            </Label>
            {!showSuccess && (
              <View
                style={{
                  borderTopWidth: 1,
                  borderTopColor: c.line,
                  paddingTop: 10,
                }}
              >
                <Label size={12} color={paid ? "#237047" : c.muted}>
                  {status?.description}
                </Label>
              </View>
            )}
          </Card>
          {!showSuccess && (
            <BookingSectionTitle>Hành trình của bạn</BookingSectionTitle>
          )}
          {data.legs.map((leg) => (
            <Card key={leg.direction} style={{ padding: 18 }}>
              <BookingJourney leg={leg} />
            </Card>
          ))}
          {showSuccess ? (
            <>
              <Card style={{ gap: 10 }}>
                <Label size={14} weight="bold">
                  Hành khách & vé điện tử
                </Label>
                {data.passengers.map((p) => (
                  <View key={p.passengerId} style={{ gap: 4 }}>
                    <Label size={13} weight="semibold">
                      {p.surname} {p.givenNames}
                    </Label>
                    <Label size={12} color={c.primary} selectable>
                      Số vé: {p.ticketNumber ?? "Đang cập nhật"}
                    </Label>
                  </View>
                ))}
              </Card>
              <Row style={{ justifyContent: "space-between", padding: 6 }}>
                <Label size={13} muted>
                  Đã thanh toán
                </Label>
                <Label size={20} weight="bold" color={c.primary}>
                  {money(data.price.totalAmount)}
                </Label>
              </Row>
              <Notice>
                Giữ mã đặt chỗ và kiểm tra giấy tờ, hành lý trước ngày bay.
                Thông tin vé nằm trong chi tiết đặt chỗ và lịch sử của bạn.
              </Notice>
              <Tap
                label="Về trang chủ"
                onPress={() => router.replace("/")}
                style={{ alignItems: "center" }}
              >
                <Label size={13} weight="semibold" color={c.primary}>
                  Về trang chủ
                </Label>
              </Tap>
            </>
          ) : (
            <>
              <BookingSectionTitle>Hành khách & vé điện tử</BookingSectionTitle>
              {data.passengers.map((p, index) => (
                <Card key={p.passengerId} style={{ gap: 12 }}>
                  <Row style={{ gap: 10 }}>
                    <View
                      style={{
                        width: 34,
                        height: 34,
                        borderRadius: 17,
                        backgroundColor: c.tint,
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      <Label size={14} weight="bold" color={c.primary}>
                        {index + 1}
                      </Label>
                    </View>
                    <View style={{ flex: 1, gap: 3 }}>
                      <Label size={14} weight="bold">
                        {p.surname} {p.givenNames}
                      </Label>
                      <Label size={11} muted>
                        {p.passengerType === "INFANT"
                          ? "Em bé"
                          : p.passengerType === "CHILD"
                            ? "Trẻ em"
                            : "Người lớn"}
                      </Label>
                    </View>
                  </Row>
                  {paid && (
                    <View
                      style={{
                        backgroundColor: c.tint,
                        borderRadius: 10,
                        padding: 12,
                        gap: 4,
                      }}
                    >
                      <Label size={10} muted>
                        SỐ VÉ ĐIỆN TỬ
                      </Label>
                      <Label
                        size={14}
                        weight="semibold"
                        color={c.primary}
                        selectable
                      >
                        {p.ticketNumber ?? "Đang cập nhật"}
                      </Label>
                    </View>
                  )}
                  <InfoRow
                    label={
                      p.document.type === "PASSPORT" ? "Hộ chiếu" : "Giấy tờ"
                    }
                    value={p.document.maskedNumber}
                  />
                  {p.benefits.map((benefit) => (
                    <View
                      key={benefit.direction}
                      style={{
                        gap: 4,
                        borderTopWidth: 1,
                        borderTopColor: c.line,
                        paddingTop: 10,
                      }}
                    >
                      <Label size={11} weight="semibold">
                        {benefit.direction === "RETURN"
                          ? "Hành lý chuyến về"
                          : "Hành lý chuyến đi"}
                      </Label>
                      <Label size={12} muted>
                        Xách tay {benefit.carryOnMaxKg} kg · Ký gửi{" "}
                        {benefit.checkedPieces} kiện miễn cước
                        {benefit.extraCheckedPieces
                          ? ` + ${benefit.extraCheckedPieces} kiện mua thêm`
                          : ""}
                      </Label>
                      {benefit.strollerPieces > 0 && (
                        <Label size={12} muted>
                          Xe đẩy: {benefit.strollerPieces} kiện
                        </Label>
                      )}
                    </View>
                  ))}
                </Card>
              ))}
              <BookingSectionTitle>Thanh toán</BookingSectionTitle>
              <Card style={{ gap: 6 }}>
                <Label size={12} muted>
                  {paid ? "Tổng đã thanh toán" : "Tổng thanh toán"}
                </Label>
                <Label size={28} weight="bold" color={c.primary}>
                  {money(data.price.totalAmount)}
                </Label>
                <Label size={11} muted>
                  Đã bao gồm thuế VAT và hành lý đã chọn
                </Label>
              </Card>
              <BookingDisclosure
                title="Chi tiết thanh toán"
                subtitle={
                  paid ? "Giá vé, giao dịch và hóa đơn" : "Giá vé và thuế phí"
                }
              >
                <InfoRow
                  label="Vé chưa VAT"
                  value={money(data.price.fareSubtotal)}
                />
                <InfoRow
                  label="Hành lý mua thêm"
                  value={money(data.price.extraBaggageAmount)}
                />
                <InfoRow label="VAT" value={money(data.price.vatAmount)} />
                <InfoRow
                  label="Tổng cộng"
                  value={money(data.price.totalAmount)}
                  bold
                />
                {data.payment && (
                  <InfoRow
                    label="Phương thức"
                    value={
                      data.payment.method === "PAYOS"
                        ? "PayOS"
                        : data.payment.method
                    }
                  />
                )}
                {data.payment?.transactionCode && (
                  <InfoRow
                    label="Mã giao dịch"
                    value={data.payment.transactionCode}
                  />
                )}
                {paid && data.payment?.paidAt && (
                  <InfoRow
                    label="Thanh toán lúc"
                    value={new Date(data.payment.paidAt).toLocaleString(
                      "vi-VN",
                    )}
                  />
                )}
                {paid && data.invoice && (
                  <InfoRow label="Mã hóa đơn" value={data.invoice.invoiceId} />
                )}
              </BookingDisclosure>
              <BookingDisclosure
                title="Thông tin liên hệ"
                subtitle="Nơi nhận thông tin hành trình"
              >
                <InfoRow label="Email" value={data.contact.email} />
                <InfoRow label="Điện thoại" value={data.contact.phoneNumber} />
              </BookingDisclosure>
              {data.cancelledAt && (
                <Notice>
                  Đơn đã hủy lúc{" "}
                  {new Date(data.cancelledAt).toLocaleString("vi-VN")} do quá
                  thời hạn đặt vé.
                </Notice>
              )}
              <Label size={10} muted style={{ textAlign: "center" }}>
                Đặt lúc {new Date(data.createdAt).toLocaleString("vi-VN")}
              </Label>
              <Tap
                onPress={() => router.push("/help")}
                style={{ alignItems: "center" }}
              >
                <Label size={13} weight="semibold" color={c.primary}>
                  Cần trợ giúp về đặt chỗ?
                </Label>
              </Tap>
            </>
          )}
        </>
      )}
    </Screen>
  );
}

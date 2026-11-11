import { useMemo, useState } from "react";
import { StyleSheet, View } from "react-native";
import { Label, Row, Tap } from "@/shared/components/ui";
import { HomeIcon } from "@/modules/flights/components/home-icon";
import { colors as c } from "@/shared/theme";
import type {
  AiBooking,
  AiBookingResult,
  AiFlightOption,
  AiFlightResult,
  ChatMessage,
} from "../types";

export const displayDate = (iso: string) =>
  /^\d{4}-\d{2}-\d{2}/.test(iso)
    ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`
    : "Chưa xác định";
const time = (iso: string) => iso.slice(11, 16);
const money = (amount: number, currency: string) =>
  `${amount.toLocaleString("vi-VN")} ${currency === "VND" ? "₫" : currency}`;
const cabin = (value: string) =>
  value === "BUSINESS" ? "Thương gia" : "Phổ thông";
export const asOf = (value: string | null) => {
  if (!value || !Number.isFinite(Date.parse(value))) return "";
  return new Date(value).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
};

type Result =
  | { key: string; kind: "flight"; group: AiFlightResult; item: AiFlightOption }
  | { key: string; kind: "booking"; group: AiBookingResult; item: AiBooking };

export function ChatMessageView({
  message,
  disabled,
  onCompose,
  onDate,
  onLogin,
  onRetry,
}: {
  message: ChatMessage;
  disabled: boolean;
  onCompose: (text: string) => void;
  onDate: () => void;
  onLogin: () => void;
  onRetry: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const [allResults, setAllResults] = useState(false);
  const [showSources, setShowSources] = useState(false);
  const response = message.response;
  const results = useMemo(
    (): Result[] => [
      ...(response?.flightSearches.flatMap((group, g) =>
        group.options.map((item, i): Result => ({
          key: `f-${g}-${i}`,
          kind: "flight",
          group,
          item,
        })),
      ) ?? []),
      ...(response?.bookingSearches.flatMap((group, g) =>
        group.bookings.map((item, i): Result => ({
          key: `b-${g}-${i}`,
          kind: "booking",
          group,
          item,
        })),
      ) ?? []),
    ],
    [response],
  );
  if (message.role === "USER")
    return (
      <View style={s.userBubble}>
        <Label selectable color="white">
          {message.content}
        </Label>
      </View>
    );
  const content =
    expanded || message.content.length <= 650
      ? message.content
      : `${message.content.slice(0, 650)}…`;
  return (
    <View style={s.assistant}>
      <Row style={{ gap: 7 }}>
        <HomeIcon name="chat" color={c.primary} size={19} />
        <Label size={12} weight="semibold" color={c.primary}>
          Trợ lý SkyGo
        </Label>
      </Row>
      <View style={[s.answer, message.failure && s.failure]}>
        <Label
          selectable
          accessibilityRole={message.failure ? "alert" : undefined}
        >
          {content.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
            part.startsWith("**") && part.endsWith("**") ? (
              <Label key={i} weight="semibold">
                {part.slice(2, -2)}
              </Label>
            ) : (
              part
            ),
          )}
        </Label>
        {message.content.length > 650 && (
          <Tap
            label={expanded ? "Thu gọn câu trả lời" : "Đọc tiếp câu trả lời"}
            onPress={() => setExpanded(!expanded)}
          >
            <Label size={13} weight="semibold" color={c.primary}>
              {expanded ? "Thu gọn" : "Đọc tiếp"}
            </Label>
          </Tap>
        )}
        {message.failure?.retryable && (
          <Tap label="Gửi lại câu hỏi" disabled={disabled} onPress={onRetry}>
            <Label color={c.primary} weight="semibold">
              Thử lại
            </Label>
          </Tap>
        )}
        {response?.status === "LOGIN_REQUIRED" && (
          <Tap
            label="Đăng nhập để xem lịch sử"
            onPress={onLogin}
            style={s.action}
          >
            <Label color="white" weight="semibold">
              Đăng nhập
            </Label>
          </Tap>
        )}
        {response?.clarifications.map((detail, index) => (
          <View key={`${detail.field}-${index}`} style={{ gap: 6 }}>
            {!!detail.airportOptions.length && (
              <>
                <Label size={12} muted>
                  {detail.field === "origin"
                    ? "Chọn điểm khởi hành"
                    : "Chọn điểm đến"}
                </Label>
                {detail.airportOptions.map((airport) => (
                  <Tap
                    key={airport.code}
                    label={`Chọn ${airport.city}, ${airport.code}`}
                    disabled={disabled}
                    onPress={() =>
                      onCompose(
                        `${detail.field === "origin" ? "Điểm khởi hành" : "Điểm đến"} là ${airport.code} (${airport.city}).`,
                      )
                    }
                    style={s.choice}
                  >
                    <Label weight="semibold">
                      {airport.city} ({airport.code})
                    </Label>
                    <Label size={12} muted>
                      {airport.name}
                    </Label>
                  </Tap>
                ))}
              </>
            )}
            {detail.field === "departureDate" && (
              <Tap
                label="Chọn ngày bay"
                disabled={disabled}
                onPress={onDate}
                style={s.choice}
              >
                <Row style={{ gap: 8 }}>
                  <HomeIcon name="calendar" color={c.primary} size={19} />
                  <Label weight="semibold" color={c.primary}>
                    Chọn ngày bay
                  </Label>
                </Row>
              </Tap>
            )}
          </View>
        ))}
      </View>
      {results
        .slice(0, allResults ? results.length : 3)
        .map((result) =>
          result.kind === "flight" ? (
            <FlightCard
              key={result.key}
              group={result.group}
              option={result.item}
            />
          ) : (
            <BookingCard
              key={result.key}
              booking={result.item}
              asOf={result.group.asOf}
            />
          ),
        )}
      {results.length > 3 && (
        <Tap
          onPress={() => setAllResults(!allResults)}
          label={allResults ? "Thu gọn kết quả" : "Xem thêm kết quả"}
        >
          <Label size={13} color={c.primary} weight="semibold">
            {allResults
              ? "Thu gọn kết quả"
              : `Xem thêm ${results.length - 3} kết quả`}
          </Label>
        </Tap>
      )}
      {response?.flightSearches.some((group) => group.hasMore) && (
        <Label size={11} muted>
          Đây là một số lựa chọn phù hợp. Bạn có thể hỏi theo hạng vé hoặc giờ
          bay để thu hẹp kết quả.
        </Label>
      )}
      {!!response?.sources.length && (
        <View style={s.sources}>
          <Tap
            onPress={() => setShowSources(!showSources)}
            label={showSources ? "Ẩn nguồn thông tin" : "Xem nguồn thông tin"}
          >
            <Row style={{ gap: 6 }}>
              <HomeIcon name="down" color={c.muted} size={16} />
              <Label size={12} muted>
                Nguồn thông tin ({response.sources.length})
              </Label>
            </Row>
          </Tap>
          {showSources &&
            response.sources.map((source, i) => (
              <View
                key={`${source.type}-${source.id}-${i}`}
                style={{ gap: 2, paddingBottom: 10 }}
              >
                <Label size={12} weight="medium">
                  {source.title}
                </Label>
                <Label size={11} muted>
                  {source.type === "MARIADB_VECTOR" || source.type === "QDRANT"
                    ? "Tài liệu đã lưu"
                    : "Dữ liệu SkyGo"}
                  {source.asOf ? ` · cập nhật ${asOf(source.asOf)}` : ""}
                </Label>
              </View>
            ))}
        </View>
      )}
    </View>
  );
}

function FlightCard({
  group,
  option,
}: {
  group: AiFlightResult;
  option: AiFlightOption;
}) {
  const [details, setDetails] = useState(false);
  const p = group.passengerCounts;
  const passengers = `${p.adults} người lớn${p.children ? `, ${p.children} trẻ em` : ""}${p.infants ? `, ${p.infants} em bé` : ""}`;
  return (
    <View style={s.card}>
      <Row
        style={{ justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}
      >
        <Label size={13} weight="semibold">
          {group.origin.code} → {group.destination.code}
        </Label>
        <View style={s.badge}>
          <Label size={11} color={c.primary} weight="semibold">
            {cabin(option.cabinClass)}
          </Label>
        </View>
      </Row>
      <Label size={12} muted>
        {displayDate(group.departureDate)} · {option.flightNumber}
      </Label>
      <Row style={{ justifyContent: "space-between", gap: 6 }}>
        <View style={{ flex: 1 }}>
          <Label size={24} weight="bold">
            {time(option.departureAt)}
          </Label>
          <Label size={12} muted>
            {group.origin.city}
          </Label>
        </View>
        <View style={{ alignItems: "center", gap: 3 }}>
          <HomeIcon name="plane" size={19} color={c.primary} />
          <Label size={11} muted>
            {Math.floor(option.durationMinutes / 60)}h{" "}
            {option.durationMinutes % 60}p
          </Label>
        </View>
        <View style={{ flex: 1, alignItems: "flex-end" }}>
          <Label size={24} weight="bold">
            {time(option.arrivalAt)}
          </Label>
          <Label size={12} muted style={{ textAlign: "right" }}>
            {group.destination.city}
          </Label>
        </View>
      </Row>
      <View style={s.price}>
        <Label size={21} weight="bold" color={c.primary}>
          {money(option.partyTotal, option.currency)}
        </Label>
        <Label size={11} muted>
          Tổng {passengers} · một chiều
        </Label>
        <Label size={11} muted>
          {group.includesVat ? "Đã gồm VAT" : "Chưa gồm VAT"}
          {group.includesExtraBaggage ? "" : " · chưa gồm hành lý mua thêm"}
        </Label>
      </View>
      <Tap
        label={
          details ? "Thu gọn chi tiết chuyến bay" : "Xem chi tiết chuyến bay"
        }
        onPress={() => setDetails(!details)}
      >
        <Label size={12} weight="semibold" color={c.primary}>
          {details ? "Thu gọn" : "Xem chi tiết"}
        </Label>
      </Tap>
      {details && (
        <View style={{ gap: 5 }}>
          <Label size={12}>
            Giá 1 người lớn: {money(option.adultPrice, option.currency)}
          </Label>
          <Label size={12}>
            {group.origin.name} → {group.destination.name}
          </Label>
          <Label size={12}>
            Đến ngày {displayDate(option.arrivalAt)} · giờ địa phương tại mỗi
            sân bay
          </Label>
          <Label size={12}>
            Còn {option.availableSeats} ghế tại thời điểm tra cứu
          </Label>
          <Label size={11} muted>
            Cập nhật {asOf(group.asOf)}. Giá và chỗ có thể thay đổi khi đặt vé.
          </Label>
        </View>
      )}
    </View>
  );
}

function BookingCard({
  booking,
  asOf: updatedAt,
}: {
  booking: AiBooking;
  asOf: string;
}) {
  const [details, setDetails] = useState(false);
  const statuses: Record<string, string> = {
    PAID: "Đã thanh toán",
    PENDING_PAYMENT: "Chờ thanh toán",
    AWAITING_REHOLD: "Cần giữ lại chỗ",
    EXPIRED: "Đã hết hạn",
    CANCELLED: "Đã hủy",
  };
  const payments: Record<string, string> = {
    SUCCEEDED: "Thành công",
    PENDING: "Đang chờ",
    EXPIRED: "Hết hạn",
    RECONCILIATION_REQUIRED: "Đang kiểm tra thanh toán",
  };
  return (
    <View style={s.card}>
      <Label size={13} weight="semibold">
        Đơn {booking.bookingCode}
      </Label>
      <Label
        size={12}
        color={booking.bookingStatus === "PAID" ? c.primary : c.muted}
      >
        {statuses[booking.bookingStatus] ?? "Đang cập nhật trạng thái"}
      </Label>
      {booking.legs.map((leg, i) => (
        <View key={i} style={{ gap: 2 }}>
          <Label size={16} weight="semibold">
            {leg.origin.code} → {leg.destination.code}
          </Label>
          <Label size={12} muted>
            {displayDate(leg.departureAt)} · {time(leg.departureAt)} ·{" "}
            {cabin(leg.cabinClass)}
          </Label>
        </View>
      ))}
      <Label size={18} color={c.primary} weight="bold">
        {money(booking.totalAmount, booking.currency)}
      </Label>
      <Label size={11} muted>
        Tổng toàn bộ đơn · đặt ngày{" "}
        {Number.isFinite(Date.parse(booking.createdAt))
          ? new Date(booking.createdAt).toLocaleDateString("vi-VN")
          : "Chưa xác định"}
      </Label>
      <Tap
        label={details ? "Thu gọn đơn đặt vé" : "Xem chi tiết đơn đặt vé"}
        onPress={() => setDetails(!details)}
      >
        <Label size={12} weight="semibold" color={c.primary}>
          {details ? "Thu gọn" : "Xem chi tiết"}
        </Label>
      </Tap>
      {details && (
        <View style={{ gap: 5 }}>
          <Label size={12}>
            {booking.passengerCounts.adults} người lớn,{" "}
            {booking.passengerCounts.children} trẻ em,{" "}
            {booking.passengerCounts.infants} em bé
          </Label>
          <Label size={12}>
            Thanh toán:{" "}
            {booking.paymentStatus
              ? (payments[booking.paymentStatus] ?? "Đang cập nhật")
              : "Chưa ghi nhận"}
          </Label>
          <Label size={11} muted>
            Cập nhật {asOf(updatedAt)}
          </Label>
        </View>
      )}
    </View>
  );
}

const s = StyleSheet.create({
  userBubble: {
    alignSelf: "flex-end",
    maxWidth: "88%",
    backgroundColor: c.primary,
    padding: 14,
    borderRadius: 18,
    borderBottomRightRadius: 5,
  },
  assistant: { gap: 9, alignSelf: "stretch" },
  answer: {
    borderRadius: 16,
    borderTopLeftRadius: 5,
    backgroundColor: "white",
    padding: 14,
    gap: 7,
    borderWidth: 1,
    borderColor: c.line,
  },
  failure: { backgroundColor: c.dangerTint, borderColor: "#F5D4CC" },
  card: {
    backgroundColor: "white",
    padding: 14,
    gap: 8,
    borderWidth: 1,
    borderColor: c.line,
    borderRadius: 16,
  },
  badge: {
    backgroundColor: c.tint,
    paddingHorizontal: 9,
    paddingVertical: 4,
    borderRadius: 8,
  },
  price: { gap: 3, paddingTop: 10, borderTopWidth: 1, borderColor: c.line },
  sources: { paddingHorizontal: 4, gap: 2 },
  choice: {
    borderWidth: 1,
    borderColor: c.line,
    borderRadius: 10,
    padding: 10,
    backgroundColor: c.tint,
    gap: 3,
  },
  action: {
    borderRadius: 12,
    paddingHorizontal: 16,
    alignItems: "center",
    backgroundColor: c.primary,
    marginTop: 5,
  },
});

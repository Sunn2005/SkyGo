import { useResponsiveLayout } from "@/shared/hooks/use-responsive-layout";
import { useState, useRef } from "react";
import { View } from "react-native";
import { Host, Picker } from "@expo/ui";
import { Redirect, router, useLocalSearchParams } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { bookingErrors, isDomestic } from "../services/booking-rules";
import { ActionBar } from "@/modules/booking/components/action-bar";
import { DateField } from "@/shared/components/date-field";
import { ChoiceField } from "@/shared/components/choice-field";
import { Field } from "@/shared/components/fields";
import { Icon, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { TripSummary } from "@/modules/booking/components/trip-summary";
import { colors as c, space } from "@/shared/theme";

export default function TravellerScreen() {
  const b = useBooking();
  const { stackFields, stackDates } = useResponsiveLayout();
  const { returnTo } = useLocalSearchParams<{ returnTo?: string }>();
  const locked = useRef(false);
  const [pending, setPending] = useState(false);
  const [apiError, setApiError] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  if (b.selected.length !== b.legs.length) return <Redirect href="/" />;
  const validate = async () => {
    if (locked.current) return;
    const next = bookingErrors(
      b.search,
      b.selected,
      b.travellers,
      b.email,
      b.phone,
    );
    setErrors(next);
    if (Object.keys(next).length || !confirmed) return;
    locked.current = true;
    setPending(true);
    setApiError("");
    try {
      await b.confirmPassengers();
      if (returnTo === "payment") router.back();
      else router.push("/payment");
    } catch (cause) {
      setApiError(
        cause instanceof Error
          ? cause.message
          : "Không thể xác nhận hành khách.",
      );
    } finally {
      locked.current = false;
      setPending(false);
    }
  };
  return (
    <Screen
      design
      title="Thông tin hành khách"
      step={0}
      footer={
        <ActionBar
          title={returnTo ? "Lưu thông tin" : "Xác nhận"}
          price={returnTo ? b.total : b.fare}
          disabled={!confirmed || pending}
          loading={pending}
          onPress={() => void validate()}
        />
      }
    >
      <TripSummary />
      {!!apiError && <Notice error>{apiError}</Notice>}
      <Label size={17} weight="bold">
        Thông tin hành khách
      </Label>
      {b.travellers.map((p, i) => (
        <View
          key={i}
          style={{
            gap: 14,
            backgroundColor: "white",
            padding: 14,
            borderRadius: 10,
          }}
        >
          <View style={{ gap: space.xs }}>
            <Row style={{ gap: space.sm }}>
              <Icon name="6-6816-imgUserRound" size={18} />
              <Label size={18} weight="semibold">
                Hành khách {i + 1} ·{" "}
                {p.kind === "infant"
                  ? "Em bé · Không có ghế riêng"
                  : p.kind === "child"
                    ? "Trẻ em"
                    : "Người lớn"}
              </Label>
            </Row>
            <Label size={12} muted>
              Nhập họ tên chính xác như trên CCCD hoặc hộ chiếu.
            </Label>
          </View>
          {(p.kind ?? "adult") === "adult" && (
            <Row>
              {(["Ông", "Bà", "Cô/Chị"] as const).map((title) => (
                <Tap
                  key={title}
                  selected={p.title === title}
                  onPress={() => b.updateTraveller(i, { title })}
                  style={{
                    flex: 1,
                    borderRadius: 8,
                    padding: 8,
                    backgroundColor: p.title === title ? c.tint : c.surface,
                    alignItems: "center",
                  }}
                >
                  <Label color={p.title === title ? c.primary : c.ink}>
                    {title}
                  </Label>
                </Tap>
              ))}
            </Row>
          )}
          {p.kind === "infant" && (
            <View style={{ gap: 6 }}>
              <Label size={12}>Người lớn đi kèm · Không có ghế riêng</Label>
              <Host matchContents colorScheme="light" seedColor={c.primary}>
                <Picker
                  selectedValue={p.companionIndex ?? 0}
                  onValueChange={(value) =>
                    b.updateTraveller(i, { companionIndex: value })
                  }
                >
                  {b.travellers.map((adult, index) =>
                    (adult.kind ?? "adult") === "adult" ? (
                      <Picker.Item
                        key={index}
                        value={index}
                        label={
                          `${adult.lastName} ${adult.firstName}`.trim() ||
                          `Người lớn ${index + 1}`
                        }
                      />
                    ) : null,
                  )}
                </Picker>
              </Host>
              {!!errors[`${i}-companionIndex`] && (
                <Notice error>{errors[`${i}-companionIndex`]}</Notice>
              )}
            </View>
          )}
          <Row
            style={{
              alignItems: "stretch",
              flexDirection: stackFields ? "column" : "row",
            }}
          >
            <Field
              half={!stackFields}
              label="Tên đệm và tên"
              value={p.firstName}
              onChangeText={(value) =>
                b.updateTraveller(i, { firstName: value })
              }
              error={errors[`${i}-firstName`]}
              autoComplete="given-name"
            />
            <Field
              half={!stackFields}
              label="Họ"
              value={p.lastName}
              onChangeText={(value) =>
                b.updateTraveller(i, { lastName: value })
              }
              error={errors[`${i}-lastName`]}
              autoComplete="family-name"
            />
          </Row>
          <Row
            style={{
              alignItems: "stretch",
              flexDirection: stackDates ? "column" : "row",
            }}
          >
            <DateField
              half={!stackDates}
              label="Ngày sinh"
              value={p.birthDate}
              onChangeText={(value) =>
                b.updateTraveller(i, { birthDate: value })
              }
              error={errors[`${i}-birthDate`]}
              pastOnly
            />
            <ChoiceField
              half={!stackDates}
              label="Giới tính"
              value={p.gender}
              options={["Nam", "Nữ", "Khác"] as const}
              onChange={(gender) => b.updateTraveller(i, { gender })}
            />
          </Row>
          <Label size={12} muted>
            Loại giấy tờ
          </Label>
          <Host matchContents colorScheme="light" seedColor={c.primary}>
            <Picker
              selectedValue={p.documentType}
              onValueChange={(value) =>
                b.updateTraveller(i, {
                  documentType: value as "cccd" | "passport",
                  documentNumber: "",
                })
              }
            >
              {isDomestic(b.search) && (
                <Picker.Item label="CCCD" value="cccd" />
              )}
              <Picker.Item label="Hộ chiếu" value="passport" />
            </Picker>
          </Host>
          {!!errors[`${i}-documentType`] && (
            <Notice error>{errors[`${i}-documentType`]}</Notice>
          )}
          {p.documentType === "passport" ? (
            <>
              <Row
                style={{
                  alignItems: "stretch",
                  flexDirection: stackFields ? "column" : "row",
                }}
              >
                <Field
                  half={!stackFields}
                  label="Quốc tịch (mã ISO, ví dụ VN)"
                  value={p.nationality}
                  onChangeText={(value) =>
                    b.updateTraveller(i, { nationality: value })
                  }
                  error={errors[`${i}-nationality`]}
                />
                <Field
                  half={!stackFields}
                  label="Số hộ chiếu"
                  value={p.documentNumber}
                  autoCapitalize="characters"
                  autoCorrect={false}
                  maxLength={12}
                  onChangeText={(value) =>
                    b.updateTraveller(i, {
                      documentNumber: value.toUpperCase(),
                    })
                  }
                  error={errors[`${i}-documentNumber`]}
                />
              </Row>
              <Row
                style={{
                  alignItems: "stretch",
                  flexDirection: stackFields ? "column" : "row",
                }}
              >
                <DateField
                  half={!stackFields}
                  label="Ngày hết hạn (NN/TT/NNNN)"
                  value={p.documentExpiry}
                  onChangeText={(value) =>
                    b.updateTraveller(i, { documentExpiry: value })
                  }
                  error={errors[`${i}-documentExpiry`]}
                />
                <Field
                  half={!stackFields}
                  label="Quốc gia cấp (mã ISO, ví dụ VN)"
                  value={p.issuingCountry}
                  onChangeText={(value) =>
                    b.updateTraveller(i, { issuingCountry: value })
                  }
                  error={errors[`${i}-issuingCountry`]}
                />
              </Row>
            </>
          ) : (
            <Field
              label="Số CCCD"
              value={p.documentNumber}
              keyboardType="number-pad"
              maxLength={12}
              onChangeText={(value) =>
                b.updateTraveller(i, { documentNumber: value })
              }
              error={errors[`${i}-documentNumber`]}
            />
          )}
        </View>
      ))}
      <View style={{ gap: space.xs }}>
        <Label size={18} weight="semibold">
          Thông tin liên hệ
        </Label>
        <Label size={12} muted>
          Email và số điện thoại được điền từ tài khoản đăng nhập. Bạn có thể
          chỉnh sửa để nhận thông tin chuyến bay.
        </Label>
      </View>
      <Field
        label="Địa chỉ email"
        value={b.email}
        onChangeText={b.setEmail}
        keyboardType="email-address"
        autoCapitalize="none"
        autoCorrect={false}
        autoComplete="email"
        error={errors.email}
      />
      <Field
        label="Số điện thoại"
        value={b.phone}
        onChangeText={b.setPhone}
        keyboardType="phone-pad"
        autoComplete="tel"
        error={errors.phone}
      />
      {Object.keys(errors).length > 0 && (
        <Notice error>
          Kiểm tra các mục được đánh dấu phía trên rồi thử lại.
        </Notice>
      )}
      <Tap
        label="Xác nhận thông tin hành khách chính xác"
        selected={confirmed}
        onPress={() => setConfirmed((value) => !value)}
      >
        <Row
          style={{
            alignItems: "stretch",
            flexDirection: stackFields ? "column" : "row",
          }}
        >
          <Label size={24} color={c.primary}>
            {confirmed ? "☑" : "☐"}
          </Label>
          <View style={{ flex: 1, gap: 6 }}>
            <Label size={13}>
              Tôi xác nhận thông tin đã nhập là hoàn toàn chính xác và khớp với
              giấy tờ tùy thân / hộ chiếu. *
            </Label>
            <Label size={12} muted>
              Họ tên khớp với CCCD / hộ chiếu, nếu sai tôi xin chịu hoàn toàn
              trách nhiệm.
            </Label>
          </View>
        </Row>
      </Tap>
    </Screen>
  );
}

import { useState } from "react";
import { View } from "react-native";
import { Host, Picker } from "@expo/ui";
import { Redirect, router, useLocalSearchParams } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { bookingErrors } from "../services/booking-rules";
import { ActionBar } from "@/modules/booking/components/action-bar";
import { Field } from "@/shared/components/fields";
import { Icon, Label, Notice, Row } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { TripSummary } from "@/modules/booking/components/trip-summary";
import { colors as c, space } from "@/shared/theme";

export default function TravellerScreen() {
  const b = useBooking();
  const { returnTo } = useLocalSearchParams<{ returnTo?: string }>();
  const [errors, setErrors] = useState<Record<string, string>>({});
  if (b.selected.length !== b.legs.length) return <Redirect href="/" />;
  const validate = () => {
    const next = bookingErrors(
      b.search,
      b.selected,
      b.travellers,
      b.email,
      b.phone,
    );
    setErrors(next);
    if (Object.keys(next).length) return;
    b.setEmail(b.email.trim());
    b.setPhone(b.phone.trim());
    if (returnTo === "payment") router.back();
    else router.push("/baggage");
  };
  return (
    <Screen
      title="Thông tin hành khách"
      step={0}
      footer={
        <ActionBar
          title={returnTo ? "Lưu thông tin" : "Tiếp tục đến hành lý"}
          price={returnTo ? b.total : b.fare}
          onPress={validate}
        />
      }
    >
      <TripSummary />
      {b.travellers.map((p, i) => (
        <View key={i} style={{ gap: 14 }}>
          <View style={{ gap: space.xs }}>
            <Row style={{ gap: space.sm }}>
              <Icon name="6-6816-imgUserRound" size={18} />
              <Label size={18} weight="semibold">
                Hành khách {i + 1} · Người lớn
              </Label>
            </Row>
            <Label size={12} muted>
              Nhập họ tên chính xác như trên CCCD hoặc hộ chiếu.
            </Label>
          </View>
          <Row style={{ alignItems: "flex-start" }}>
            <Field
              half
              label="Họ"
              value={p.lastName}
              onChangeText={(value) =>
                b.updateTraveller(i, { lastName: value })
              }
              error={errors[`${i}-lastName`]}
              autoComplete="family-name"
            />
            <Field
              half
              label="Tên đệm và tên"
              value={p.firstName}
              onChangeText={(value) =>
                b.updateTraveller(i, { firstName: value })
              }
              error={errors[`${i}-firstName`]}
              autoComplete="given-name"
            />
          </Row>
          <Row style={{ alignItems: "flex-start" }}>
            <Field
              half
              label="Ngày sinh (NN/TT/NNNN)"
              value={p.birthDate}
              onChangeText={(value) =>
                b.updateTraveller(i, { birthDate: value })
              }
              error={errors[`${i}-birthDate`]}
              keyboardType="numbers-and-punctuation"
              maxLength={10}
            />
            <View style={{ flex: 1, gap: 6 }}>
              <Label size={12} muted>
                Giới tính
              </Label>
              <View
                style={{
                  minHeight: 46,
                  borderWidth: 1,
                  borderColor: c.line,
                  borderRadius: 8,
                  justifyContent: "center",
                }}
              >
                <Host matchContents colorScheme="light" seedColor={c.primary}>
                  <Picker
                    selectedValue={p.gender}
                    onValueChange={(value) =>
                      b.updateTraveller(i, { gender: value })
                    }
                  >
                    <Picker.Item label="Nam" value="Nam" />
                    <Picker.Item label="Nữ" value="Nữ" />
                    <Picker.Item label="Khác" value="Khác" />
                  </Picker>
                </Host>
              </View>
            </View>
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
              <Picker.Item label="CCCD" value="cccd" />
              <Picker.Item label="Hộ chiếu" value="passport" />
            </Picker>
          </Host>
          <Field
            label={p.documentType === "cccd" ? "Số CCCD" : "Số hộ chiếu"}
            value={p.documentNumber}
            keyboardType={p.documentType === "cccd" ? "number-pad" : "default"}
            autoCapitalize="characters"
            autoCorrect={false}
            maxLength={12}
            onChangeText={(value) =>
              b.updateTraveller(i, { documentNumber: value.toUpperCase() })
            }
            error={errors[`${i}-documentNumber`]}
          />
          {p.documentType === "passport" && (
            <>
              <Field
                label="Quốc tịch"
                value={p.nationality}
                onChangeText={(value) =>
                  b.updateTraveller(i, { nationality: value })
                }
                error={errors[`${i}-nationality`]}
              />
              <Field
                label="Quốc gia cấp"
                value={p.issuingCountry}
                onChangeText={(value) =>
                  b.updateTraveller(i, { issuingCountry: value })
                }
                error={errors[`${i}-issuingCountry`]}
              />
              <Field
                label="Ngày hết hạn (NN/TT/NNNN)"
                value={p.documentExpiry}
                keyboardType="numbers-and-punctuation"
                maxLength={10}
                onChangeText={(value) =>
                  b.updateTraveller(i, { documentExpiry: value })
                }
                error={errors[`${i}-documentExpiry`]}
              />
            </>
          )}
        </View>
      ))}
      <View style={{ gap: space.xs }}>
        <Label size={18} weight="semibold">
          Thông tin liên hệ
        </Label>
        <Label size={12} muted>
          Email và số điện thoại nhận thông tin chuyến bay.
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
      <Notice icon="6-6816-imgShieldCheck">
        Đây là bản mẫu. Hãy dùng thông tin giả để trải nghiệm; booking được lưu
        trên thiết bị theo tài khoản.
      </Notice>
    </Screen>
  );
}

import { useState } from "react";
import { View } from "react-native";
import { Host, Picker } from "@expo/ui";
import { Redirect, router, useLocalSearchParams } from "expo-router";
import { useBooking } from "@/context/booking-context";
import { validBirthDate } from "@/data/booking";
import {
  ActionBar,
  Field,
  Icon,
  Label,
  Notice,
  Row,
  Screen,
  TripSummary,
} from "@/components/ui";
import { colors as c, space } from "@/theme";

export default function TravellerScreen() {
  const b = useBooking();
  const { returnTo } = useLocalSearchParams<{ returnTo?: string }>();
  const [errors, setErrors] = useState<Record<string, string>>({});
  if (b.selected.length !== b.legs.length) return <Redirect href="/" />;
  const validate = () => {
    const next: Record<string, string> = {};
    b.travellers.forEach((p, i) => {
      if (!p.firstName.trim()) next[`${i}-firstName`] = "Vui lòng nhập tên.";
      if (!p.lastName.trim()) next[`${i}-lastName`] = "Vui lòng nhập họ.";
      if (!validBirthDate(p.birthDate, b.search.date))
        next[`${i}-birthDate`] = "Ngày hợp lệ, từ 12 tuổi (NN/TT/NNNN).";
    });
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(b.email.trim()))
      next.email = "Vui lòng nhập email hợp lệ.";
    if (
      !/^\+?[\d\s()-]{8,20}$/.test(b.phone.trim()) ||
      b.phone.replace(/\D/g, "").length < 8
    )
      next.phone = "Vui lòng nhập số điện thoại hợp lệ.";
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
          price={returnTo ? b.total : b.base}
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
              Nhập họ tên chính xác như trên hộ chiếu.
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
        Đây là bản mẫu. Hãy dùng thông tin giả để trải nghiệm; dữ liệu chỉ lưu
        trong phiên sử dụng.
      </Notice>
    </Screen>
  );
}

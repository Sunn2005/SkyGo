import { useState } from "react";
import { router } from "expo-router";
import { useBooking } from "@/context/booking-context";
import {
  ActionBar,
  Card,
  Icon,
  Label,
  Notice,
  Row,
  Screen,
  Tap,
} from "@/components/ui";
import { colors as c } from "@/theme";

export default function CardsScreen() {
  const b = useBooking();
  const [card, setCard] = useState(b.card);
  return (
    <Screen
      title="Phương thức thanh toán"
      footer={
        <ActionBar
          title="Dùng thẻ mẫu này"
          onPress={() => {
            b.setCard(card);
            router.back();
          }}
        />
      }
    >
      <Label size={20} weight="semibold">
        Chọn thẻ để trải nghiệm
      </Label>
      <Notice>
        Chỉ có thẻ mẫu. Không nhập thông tin thẻ thật; ứng dụng không thực hiện
        giao dịch.
      </Notice>
      {[
        "Mastercard ···· 9876",
        "Visa ···· 4242",
        "Thẻ mẫu bị từ chối ···· 0002",
      ].map((value) => (
        <Tap
          key={value}
          onPress={() => setCard(value)}
          selected={card === value}
        >
          <Card
            selected={card === value}
            style={{ backgroundColor: card === value ? c.tint : c.white }}
          >
            <Row>
              <Icon name="6-7139-imgCreditCard" size={24} />
              <Label weight="semibold" style={{ flex: 1 }}>
                {value}
              </Label>
              {card === value && <Label color={c.primary}>Đã chọn</Label>}
            </Row>
          </Card>
        </Tap>
      ))}
      <Label size={12} muted>
        Thẻ kết thúc 0002 mô phỏng giao dịch thất bại để bạn thử đổi phương thức
        và thanh toán lại.
      </Label>
    </Screen>
  );
}

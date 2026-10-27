import { useState } from "react";
import { Card, Label, Screen, Tap } from "@/components/ui";
import { colors as c } from "@/theme";

const faqs = [
  [
    "Đây có phải đặt vé thật không?",
    "SkyGo hiện là bản trải nghiệm với dữ liệu mẫu. Thanh toán không trừ tiền, không tạo vé của hãng và không gửi email.",
  ],
  [
    "Có thể thử toàn bộ hành trình không?",
    "Có. Chọn điểm đi, điểm đến và ngày bay, chọn chuyến, điền thông tin giả, thêm hành lý, chọn chỗ rồi thanh toán bằng thẻ mẫu.",
  ],
  [
    "Giá vé và tiền tệ được tính thế nào?",
    "Giá hiển thị bằng USD và đã gồm thuế phí. Ký gửi $45/kiện/người/chặng; chỗ ngồi $24/ghế/chặng. Bảo vệ chuyến đi tùy chọn $29/người cho cả hành trình.",
  ],
  [
    "Vé khứ hồi và nhiều chặng hoạt động ra sao?",
    "Bạn chọn chuyến cho từng chặng trước khi điền thông tin hành khách. Hành lý áp dụng cho tất cả chặng; chỗ ngồi chọn riêng theo chặng và hành khách.",
  ],
  [
    "Làm sao thử thanh toán thất bại?",
    "Ở bước thanh toán, chọn thẻ mẫu kết thúc 0002. Sau thông báo bị từ chối, đổi sang Mastercard 9876 hoặc Visa 4242 để thử lại.",
  ],
  [
    "Dữ liệu của tôi được lưu ở đâu?",
    "Thông tin chỉ nằm trong bộ nhớ của phiên ứng dụng. Khởi động lại ứng dụng sẽ xóa vé và các lựa chọn. Hãy dùng thông tin giả khi trải nghiệm.",
  ],
];
export default function HelpScreen() {
  const [open, setOpen] = useState<number | null>(0);
  return (
    <Screen title="Trợ giúp">
      <Label size={24} weight="bold">
        Đồng hành cùng bạn
      </Label>
      <Label muted>Câu hỏi thường gặp về bản trải nghiệm SkyGo.</Label>
      {faqs.map(([question, answer], i) => (
        <Card key={question}>
          <Tap label={question} onPress={() => setOpen(open === i ? null : i)}>
            <Label weight="semibold" color={open === i ? c.primary : c.ink}>
              {question}
            </Label>
          </Tap>
          {open === i && (
            <Label size={14} muted>
              {answer}
            </Label>
          )}
        </Card>
      ))}
    </Screen>
  );
}

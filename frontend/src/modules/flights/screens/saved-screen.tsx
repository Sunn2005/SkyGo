import { router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { flights } from "@/modules/flights/data/flights";
import { money } from "@/shared/utils/format";
import { Card, Label, Row, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { colors as c } from "@/shared/theme";

export default function SavedScreen() {
  const b = useBooking();
  return (
    <Screen title="Chuyến bay đã lưu">
      {flights
        .filter((f) => b.favorites.includes(f.id))
        .map((f) => (
          <Card key={f.id}>
            <Label weight="semibold">
              {f.airline} · {f.number}
            </Label>
            <Row>
              <Label style={{ flex: 1 }}>
                {f.departure} → {f.arrival}
              </Label>
              <Label weight="bold">{money(b.priceFor(f))}</Label>
            </Row>
            <Row>
              <Tap
                onPress={() => {
                  b.startSearch();
                  b.setCandidate(f);
                  router.push("/detail");
                }}
                style={{ flex: 1 }}
              >
                <Label color={c.primary} weight="semibold">
                  Xem & đặt chuyến bay
                </Label>
              </Tap>
              <Tap
                onPress={() =>
                  b.setFavorites((ids) => ids.filter((id) => id !== f.id))
                }
              >
                <Label muted>Bỏ lưu</Label>
              </Tap>
            </Row>
          </Card>
        ))}
      {!b.favorites.length && (
        <>
          <Label size={22} weight="semibold">
            Lưu một hành trình yêu thích
          </Label>
          <Label muted>
            Chạm biểu tượng trái tim trong kết quả tìm kiếm để lưu chuyến bay
            tại đây.
          </Label>
          <Tap onPress={() => router.replace("/")}>
            <Label color={c.primary} weight="semibold">
              Tìm chuyến bay
            </Label>
          </Tap>
        </>
      )}
      <Label size={12} muted>
        Giá mẫu cho hành trình bạn đang tìm, mỗi người, mỗi chặng. Chuyến bay đã
        lưu chỉ giữ trong phiên hiện tại.
      </Label>
    </Screen>
  );
}

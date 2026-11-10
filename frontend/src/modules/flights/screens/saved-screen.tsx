import { router } from "expo-router";
import { useBooking } from "@/modules/booking/context/booking-context";
import { money } from "@/shared/utils/format";
import { Button, Card, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Screen } from "@/shared/components/screen";
import { colors as c } from "@/shared/theme";
import { rememberAirports } from "../data/airports";
import { useFavoriteFlights } from "../services/use-favorite-flights";
import { flightDate } from "@/modules/tickets/services/booking-presentation";

export default function SavedScreen() {
  const b = useBooking();
  const saved = useFavoriteFlights();
  const labels = {
    AVAILABLE: "Còn mở bán",
    SOLD_OUT: "Hết chỗ",
    CLOSED: "Ngừng bán",
    DEPARTED: "Đã khởi hành",
  };
  return (
    <Screen title="Chuyến bay đã lưu">
      {!saved.signedIn && (
        <>
          <Label>Đăng nhập để xem các chuyến bay bạn đã lưu.</Label>
          <Button
            title="Đăng nhập"
            onPress={() =>
              router.push({ pathname: "/login", params: { returnTo: "saved" } })
            }
          />
        </>
      )}
      {saved.loading && <Notice>Đang tải chuyến bay đã lưu…</Notice>}
      {!!saved.error && (
        <>
          <Notice error>{saved.error}</Notice>
          <Button secondary title="Tải lại" onPress={saved.refresh} />
        </>
      )}
      {saved.page?.data.map(({ flight: f, availability }) => (
        <Card key={f.flightId}>
          <Label weight="semibold">
            {f.airline.name} · {f.flightNumber}
          </Label>
          <Label size={16} weight="bold">
            {f.origin.city} ({f.origin.code}) → {f.destination.city} (
            {f.destination.code})
          </Label>
          <Label>
            {flightDate(f.departureAt)} · {f.departureAt.slice(11, 16)} –{" "}
            {f.arrivalAt.slice(11, 16)}
          </Label>
          <Label color={availability === "AVAILABLE" ? c.primary : c.muted}>
            {labels[availability]}
          </Label>
          {f.fares.map((fare) => (
            <Row key={fare.cabinClass}>
              <Label style={{ flex: 1 }}>
                {fare.cabinClass === "BUSINESS" ? "Thương gia" : "Phổ thông"}
              </Label>
              <Label weight="semibold">{money(fare.price.amount)}</Label>
            </Row>
          ))}
          <Row>
            {availability === "AVAILABLE" && (
              <Tap
                style={{ flex: 1 }}
                onPress={() => {
                  rememberAirports(
                    [f.origin, f.destination].map((a) => ({
                      ...a,
                      name: a.city,
                      countryName: "",
                    })),
                  );
                  b.startSearch();
                  b.setSearch((old) => ({
                    ...old,
                    type: "one-way",
                    from: f.origin.code,
                    to: f.destination.code,
                    date: f.departureAt.slice(0, 10),
                    cabin: f.fares.some((fare) => fare.cabinClass === "ECONOMY")
                      ? "Phổ thông"
                      : "Thương gia",
                  }));
                  router.push({
                    pathname: "/results",
                    params: {
                      savedFlightId: f.flightId,
                      savedFlightDate: f.departureAt.slice(0, 10),
                    },
                  });
                }}
              >
                <Label color={c.primary} weight="semibold">
                  Xem & đặt chuyến bay
                </Label>
              </Tap>
            )}
            <Tap
              disabled={saved.loadingMore || saved.busy.length > 0}
              onPress={() => {
                void saved.toggle(f.flightId, true).then((changed) => {
                  if (changed) saved.refresh();
                });
              }}
            >
              <Label muted>
                {saved.busy.includes(f.flightId) ? "Đang bỏ lưu…" : "Bỏ lưu"}
              </Label>
            </Tap>
          </Row>
        </Card>
      ))}
      {saved.signedIn &&
        !saved.loading &&
        !saved.error &&
        saved.page?.pagination.totalElements === 0 && (
          <>
            <Label size={22} weight="semibold">
              Lưu một hành trình yêu thích
            </Label>
            <Label muted>
              Chạm biểu tượng trái tim trong kết quả tìm kiếm để lưu chuyến bay
              tại đây.
            </Label>
            <Button
              title="Tìm chuyến bay"
              onPress={() => router.push("/search")}
            />
          </>
        )}
      {saved.page &&
        saved.page.pagination.page + 1 < saved.page.pagination.totalPages && (
          <Button
            secondary
            title="Xem thêm"
            loading={saved.loadingMore}
            onPress={() => void saved.loadMore()}
          />
        )}
      {saved.signedIn && (
        <Label size={12} muted>
          Giá từ cho 1 người lớn, đã gồm VAT. Giá và chỗ trống được kiểm tra lại
          khi đặt vé.
        </Label>
      )}
    </Screen>
  );
}

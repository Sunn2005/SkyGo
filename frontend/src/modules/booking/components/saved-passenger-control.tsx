import { useEffect, useRef, useState } from "react";
import { Modal, ScrollView, View } from "react-native";
import { useAccount } from "@/modules/account/context/account-context";
import { Button, Card, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { colors as c } from "@/shared/theme";
import { dateLabel } from "@/shared/utils/date";
import { ApiError } from "@/shared/api/client";
import type { Traveller } from "../types";
import {
  savedPassengerApi,
  profileInput,
  profileTraveller,
  profileErrors,
  passengerKindOnDate,
  applySavedPassenger,
  type SavedPassengerSummary,
} from "../services/saved-passenger-service";

type Props = {
  traveller: Traveller;
  travellers: Traveller[];
  index: number;
  departure: string;
  domestic: boolean;
  onChange: (patch: Partial<Traveller>) => void;
};

export function SavedPassengerControl(props: Props) {
  const { session } = useAccount();
  const token = session?.accessToken;
  return token ? (
    <PassengerControl key={token} {...props} token={token} />
  ) : null;
}

function PassengerControl({
  traveller,
  travellers,
  index,
  departure,
  domestic,
  onChange,
  token,
}: Props & { token: string }) {
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<SavedPassengerSummary[]>([]);
  const [busy, setBusy] = useState(false);
  const locked = useRef(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [baseline, setBaseline] = useState("");
  const fingerprint = JSON.stringify(profileInput(traveller));
  const isCurrent = () => mounted.current;
  const perform = async (action: () => Promise<void>) => {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    setError("");
    try {
      await action();
    } catch (cause) {
      if (isCurrent())
        setError(
          cause instanceof Error
            ? cause.message
            : "Chưa thể tải hành khách đã lưu.",
        );
    } finally {
      locked.current = false;
      if (isCurrent()) setBusy(false);
    }
  };
  const load = () =>
    perform(async () => {
      setOpen(true);
      setItems([]);
      const data = await savedPassengerApi.list(token!);
      if (isCurrent()) setItems(data);
    });
  const choose = (id: string) =>
    perform(async () => {
      const profile = await savedPassengerApi.get(token!, id);
      if (!isCurrent()) return;
      const next = profileTraveller(profile);
      const needsPassport = !domestic && next.documentType !== "passport";
      onChange(applySavedPassenger(profile, traveller, domestic));
      setBaseline(JSON.stringify(profileInput(next)));
      setMessage(
        needsPassport
          ? "Đã điền thông tin cá nhân. Hành trình quốc tế cần bổ sung hộ chiếu."
          : "Đã điền từ hồ sơ. Vui lòng kiểm tra thông tin và hạn giấy tờ cho chuyến này.",
      );
      setOpen(false);
    });
  const save = () => {
    const errors = profileErrors(traveller);
    if (Object.keys(errors).length) {
      setError(Object.values(errors).join("\n"));
      return;
    }
    return perform(async () => {
      const body = profileInput(traveller);
      try {
        const result = traveller.savedPassengerId
          ? await savedPassengerApi.update(
              token!,
              traveller.savedPassengerId,
              body,
            )
          : await savedPassengerApi.create(token!, body);
        if (!isCurrent()) return;
        onChange({ savedPassengerId: result.savedPassengerId });
        setBaseline(JSON.stringify(body));
        setMessage("Đã lưu thông tin hành khách cho lần sau.");
      } catch (cause) {
        if (
          isCurrent() &&
          cause instanceof ApiError &&
          cause.code === "SAVED_PASSENGER_NOT_FOUND"
        ) {
          onChange({ savedPassengerId: undefined });
          setBaseline("");
          setMessage("");
        }
        throw cause;
      }
    });
  };
  return (
    <>
      <View style={{ gap: 8 }}>
        <Row
          style={{
            gap: 16,
            alignItems: "stretch",
            justifyContent: "space-between",
          }}
        >
          <Tap
            label="Chọn hành khách đã lưu"
            disabled={busy}
            onPress={() => void load()}
            style={{
              width: "44%",
              minWidth: 0,
              minHeight: 44,
              paddingHorizontal: 6,
              paddingVertical: 6,
              borderRadius: 8,
              borderWidth: 1,
              borderColor: c.primary,
              backgroundColor: c.tint,
              alignItems: "center",
            }}
          >
            <Label
              size={12}
              color={c.primary}
              weight="semibold"
              style={{ textAlign: "center" }}
            >
              Chọn hành khách đã lưu
            </Label>
          </Tap>
          <Tap
            disabled={busy || (Boolean(baseline) && baseline === fingerprint)}
            onPress={() => void save()}
            style={{
              width: "44%",
              minWidth: 0,
              minHeight: 44,
              paddingHorizontal: 6,
              paddingVertical: 6,
              borderRadius: 8,
              borderWidth: 1,
              borderColor: c.primary,
              backgroundColor: c.tint,
              alignItems: "center",
            }}
          >
            <Label
              size={12}
              color={c.primary}
              weight="semibold"
              style={{ textAlign: "center" }}
            >
              {busy
                ? "Đang xử lý…"
                : traveller.savedPassengerId
                  ? "Lưu thay đổi cho lần sau"
                  : "Lưu hành khách này"}
            </Label>
          </Tap>
        </Row>
        {!!message && (
          <Label size={12} color={c.primary}>
            {message}
          </Label>
        )}
        {!!error && !open && <Notice error>{error}</Notice>}
      </View>
      <Modal
        visible={open}
        transparent
        animationType="slide"
        onRequestClose={() => setOpen(false)}
      >
        <View
          style={{
            flex: 1,
            justifyContent: "center",
            padding: 20,
            backgroundColor: "#102A4380",
          }}
        >
          <View
            accessibilityViewIsModal
            style={{
              backgroundColor: c.white,
              padding: 20,
              borderRadius: 16,
              gap: 12,
              maxHeight: "85%",
              width: "100%",
              maxWidth: 480,
              alignSelf: "center",
            }}
          >
            <Label size={19} weight="bold">
              Chọn cho hành khách {index + 1}
            </Label>
            <Label size={12} muted>
              Hồ sơ phù hợp độ tuổi tại ngày khởi hành. Mỗi người chỉ chọn một
              lần trong đơn.
            </Label>
            <ScrollView contentContainerStyle={{ gap: 10 }}>
              {busy && <Label>Đang tải…</Label>}
              {!!error && (
                <>
                  <Notice error>{error}</Notice>
                  <Button
                    title="Tải lại danh sách"
                    onPress={() => void load()}
                    disabled={busy}
                  />
                </>
              )}
              {!busy && !error && !items.length && (
                <Label muted>
                  Bạn chưa lưu hành khách nào. Đóng danh sách và nhập thông tin
                  để lưu.
                </Label>
              )}
              {items.map((p) => {
                const used = travellers.some(
                  (t, i) =>
                    i !== index && t.savedPassengerId === p.savedPassengerId,
                );
                const wrongAge =
                  passengerKindOnDate(p.dateOfBirth, departure) !==
                  (traveller.kind ?? "adult");
                return (
                  <Tap
                    key={p.savedPassengerId}
                    disabled={busy || used || wrongAge}
                    onPress={() => void choose(p.savedPassengerId)}
                  >
                    <Card
                      selected={
                        traveller.savedPassengerId === p.savedPassengerId
                      }
                    >
                      <Label weight="semibold">
                        {p.surname} {p.givenNames}
                      </Label>
                      <Label size={12}>
                        {dateLabel(p.dateOfBirth)} ·{" "}
                        {p.documentType === "PASSPORT" ? "Hộ chiếu" : "CCCD"} ·
                        •••• {p.documentNumberLast4}
                      </Label>
                      {(used || wrongAge) && (
                        <Label size={12}>
                          {used
                            ? "Đã chọn cho hành khách khác"
                            : "Không thuộc nhóm tuổi của vị trí này"}
                        </Label>
                      )}
                    </Card>
                  </Tap>
                );
              })}
            </ScrollView>
            <Button secondary title="Đóng" onPress={() => setOpen(false)} />
          </View>
        </View>
      </Modal>
    </>
  );
}

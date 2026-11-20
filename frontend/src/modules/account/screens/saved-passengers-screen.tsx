import { useCallback, useEffect, useRef, useState } from "react";
import { Modal, View } from "react-native";
import { Redirect, useFocusEffect } from "expo-router";
import { useAccount } from "../context/account-context";
import { Screen } from "@/shared/components/screen";
import { Button, Card, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { ChoiceField } from "@/shared/components/choice-field";
import { colors as c } from "@/shared/theme";
import { dateLabel } from "@/shared/utils/date";
import { ApiError } from "@/shared/api/client";
import { newTraveller } from "@/modules/booking/data/defaults";
import { PassengerIdentityFields } from "@/modules/booking/components/passenger-identity-fields";
import type { Traveller } from "@/modules/booking/types";
import {
  savedPassengerApi,
  profileErrors,
  profileInput,
  profileTraveller,
  type SavedPassengerSummary,
} from "@/modules/booking/services/saved-passenger-service";

export default function SavedPassengersScreen() {
  const { session } = useAccount();
  if (!session) return <Redirect href="/profile" />;
  return (
    <PassengerManager key={session.accessToken} token={session.accessToken} />
  );
}

function PassengerManager({ token }: { token: string }) {
  const [items, setItems] = useState<SavedPassengerSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const locked = useRef(false);
  const mounted = useRef(true);
  const [editing, setEditing] = useState<Traveller | null>(null);
  const [deleting, setDeleting] = useState<SavedPassengerSummary | null>(null);
  const sequence = useRef(0);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const load = useCallback(async () => {
    const request = ++sequence.current;
    setLoading(true);
    setError("");
    try {
      const data = await savedPassengerApi.list(token);
      if (mounted.current && request === sequence.current) setItems(data);
    } catch (cause) {
      if (mounted.current && request === sequence.current)
        setError(
          cause instanceof Error ? cause.message : "Chưa tải được danh sách.",
        );
    } finally {
      if (mounted.current && request === sequence.current) setLoading(false);
    }
  }, [token]);
  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );
  const perform = async (action: () => Promise<void>) => {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    setError("");
    try {
      await action();
    } catch (cause) {
      if (mounted.current)
        setError(
          cause instanceof Error ? cause.message : "Chưa lưu được thay đổi.",
        );
    } finally {
      locked.current = false;
      if (mounted.current) setBusy(false);
    }
  };
  const edit = (id: string) =>
    perform(async () => {
      const result = await savedPassengerApi.get(token, id);
      if (mounted.current) setEditing(profileTraveller(result));
    });
  if (editing)
    return (
      <PassengerEditor
        key={editing.savedPassengerId ?? "new"}
        initial={editing}
        token={token}
        onCancel={() => setEditing(null)}
        onSaved={() => {
          setEditing(null);
          void load();
        }}
      />
    );
  return (
    <Screen title="Hành khách đã lưu">
      <Label size={22} weight="bold">
        Hành khách đã lưu
      </Label>
      <Label muted>
        Chọn nhanh khi đặt vé cho bản thân và người thân. Bạn có thể lưu tối đa
        50 hồ sơ.
      </Label>
      <Button
        title="Thêm hành khách"
        disabled={busy}
        onPress={() => setEditing(newTraveller(0))}
      />
      {!!error && <Notice error>{error}</Notice>}
      {loading ? (
        <Label>Đang tải danh sách…</Label>
      ) : (
        !error &&
        !items.length && (
          <Card>
            <Label>Bạn chưa lưu hành khách nào.</Label>
          </Card>
        )
      )}
      <Tap disabled={busy || loading} onPress={() => void load()}>
        <Label color={c.primary}>Tải lại danh sách</Label>
      </Tap>
      {items.map((p) => (
        <Card key={p.savedPassengerId}>
          <Label size={17} weight="semibold">
            {p.surname} {p.givenNames}
          </Label>
          <Label size={13}>Ngày sinh: {dateLabel(p.dateOfBirth)}</Label>
          <Label size={13}>
            {p.documentType === "PASSPORT" ? "Hộ chiếu" : "CCCD"}: ••••{" "}
            {p.documentNumberLast4}
          </Label>
          <Row>
            <Tap
              disabled={busy}
              onPress={() => void edit(p.savedPassengerId)}
              style={{ flex: 1 }}
            >
              <Label color={c.primary} weight="semibold">
                Chỉnh sửa
              </Label>
            </Tap>
            <Tap
              disabled={busy}
              label={`Xóa hồ sơ ${p.surname} ${p.givenNames}`}
              onPress={() => setDeleting(p)}
            >
              <Label color={c.danger}>Xóa</Label>
            </Tap>
          </Row>
        </Card>
      ))}
      <Modal
        visible={Boolean(deleting)}
        transparent
        animationType="fade"
        onRequestClose={() => {
          if (!busy) setDeleting(null);
        }}
      >
        <View
          style={{
            flex: 1,
            justifyContent: "center",
            padding: 24,
            backgroundColor: "#102A4380",
          }}
        >
          <View
            accessibilityViewIsModal
            style={{
              padding: 20,
              borderRadius: 16,
              gap: 14,
              backgroundColor: c.white,
              maxWidth: 440,
              width: "100%",
              alignSelf: "center",
            }}
          >
            <Label size={19} weight="bold">
              Xóa hành khách đã lưu?
            </Label>
            <Label>
              Bạn muốn xóa hồ sơ của {deleting?.surname} {deleting?.givenNames}?
            </Label>
            <Label size={12} muted>
              Các đơn vé đã đặt vẫn giữ nguyên thông tin.
            </Label>
            {!!error && <Notice error>{error}</Notice>}
            <Button
              title="Xóa hồ sơ"
              loading={busy}
              onPress={() =>
                void perform(async () => {
                  if (!deleting) return;
                  await savedPassengerApi.remove(
                    token,
                    deleting.savedPassengerId,
                  );
                  if (!mounted.current) return;
                  setItems((old) =>
                    old.filter(
                      (p) => p.savedPassengerId !== deleting.savedPassengerId,
                    ),
                  );
                  setDeleting(null);
                })
              }
            />
            <Button
              secondary
              title="Giữ lại hồ sơ"
              disabled={busy}
              onPress={() => {
                setDeleting(null);
                setError("");
              }}
            />
          </View>
        </View>
      </Modal>
    </Screen>
  );
}

function PassengerEditor({
  initial,
  token,
  onCancel,
  onSaved,
}: {
  initial: Traveller;
  token: string;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [draft, setDraft] = useState(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const locked = useRef(false);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const save = async () => {
    if (locked.current) return;
    const next = profileErrors(draft);
    setErrors(next);
    if (Object.keys(next).length) return;
    locked.current = true;
    setBusy(true);
    setError("");
    try {
      const body = profileInput(draft);
      if (draft.savedPassengerId)
        await savedPassengerApi.update(token, draft.savedPassengerId, body);
      else await savedPassengerApi.create(token, body);
      if (mounted.current) onSaved();
    } catch (cause) {
      if (mounted.current) {
        setError(
          cause instanceof Error ? cause.message : "Chưa lưu được thông tin.",
        );
        if (cause instanceof ApiError) {
          const fields: Record<string, string> = {
            givenNames: "firstName",
            surname: "lastName",
            dateOfBirth: "birthDate",
            "document.number": "documentNumber",
            "document.issuingCountryCode": "issuingCountry",
            "document.nationalityCountryCode": "nationality",
            "document.expiresOn": "documentExpiry",
          };
          setErrors(
            Object.fromEntries(
              cause.details.map((d) => [fields[d.field] ?? d.field, d.message]),
            ),
          );
        }
      }
    } finally {
      locked.current = false;
      if (mounted.current) setBusy(false);
    }
  };
  return (
    <Screen
      title={
        draft.savedPassengerId ? "Chỉnh sửa hành khách" : "Thêm hành khách"
      }
      onBack={() => {
        if (!busy) onCancel();
      }}
    >
      <Tap disabled={busy} onPress={onCancel}>
        <Label color={c.primary}>‹ Về danh sách hành khách</Label>
      </Tap>
      <Label size={21} weight="bold">
        {draft.savedPassengerId ? "Chỉnh sửa hành khách" : "Thêm hành khách"}
      </Label>
      <Label size={12} muted>
        Thông tin dùng để điền nhanh cho lần đặt vé sau. Tuổi và hạn giấy tờ sẽ
        được kiểm tra lại theo từng chuyến bay.
      </Label>
      {!!error && <Notice error>{error}</Notice>}
      <Card>
        <ChoiceField
          label="Danh xưng (nếu có)"
          value={draft.title ?? "Không chọn"}
          options={["Không chọn", "Ông", "Bà", "Cô/Chị"] as const}
          onChange={(value) =>
            setDraft((p) => ({
              ...p,
              title:
                value === "Không chọn"
                  ? undefined
                  : (value as Traveller["title"]),
            }))
          }
        />
        <PassengerIdentityFields
          p={draft}
          errors={errors}
          onChange={(patch) => setDraft((p) => ({ ...p, ...patch }))}
        />
      </Card>
      <Button
        title="Lưu thông tin"
        loading={busy}
        onPress={() => void save()}
      />
    </Screen>
  );
}

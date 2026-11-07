import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { View } from "react-native";
import { router } from "expo-router";
import { useBooking } from "../context/booking-context";
import type { BaggageOptions } from "../data/api-booking-service";
import { Screen } from "@/shared/components/screen";
import { Button, Label, Notice, Row, Tap } from "@/shared/components/ui";
import { Panel } from "../components/design";
import { TripSummary } from "../components/trip-summary";
import { fullName } from "../utils/traveller";
import { money } from "@/shared/utils/format";

export default function LiveBaggageScreen() {
  const b = useBooking();
  const loadRef = useRef(b.loadBaggageOptions);
  useLayoutEffect(() => {
    loadRef.current = b.loadBaggageOptions;
  }, [b.loadBaggageOptions]);
  const [options, setOptions] = useState<BaggageOptions | null>(null);
  const [draft, setDraft] = useState<Record<string, number>>({});
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [retry, setRetry] = useState(0);
  const locked = useRef(false);
  const reviewId = b.review?.reviewId;
  useEffect(() => {
    let active = true;
    loadRef
      .current()
      .then((data) => {
        if (!active) return;
        setOptions(data);
        setDraft(
          Object.fromEntries(
            data.legs.flatMap((leg) =>
              leg.passengerBaggage.map((p) => [
                `${leg.direction}:${p.clientPassengerId}`,
                p.selectedExtraPieces,
              ]),
            ),
          ),
        );
      })
      .catch((cause) => {
        if (active)
          setError(
            cause instanceof Error ? cause.message : "Không tải được hành lý.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [reviewId, retry]);
  const selections =
    options?.legs.flatMap((leg) =>
      leg.passengerBaggage
        .filter((p) => p.canPurchaseExtra)
        .map((p) => ({
          clientPassengerId: p.clientPassengerId,
          direction: leg.direction,
          extraPieces: draft[`${leg.direction}:${p.clientPassengerId}`] ?? 0,
        })),
    ) ?? [];
  const total =
    options?.legs.reduce(
      (sum, leg) =>
        sum +
        leg.passengerBaggage.reduce(
          (n, p) =>
            n +
            (draft[`${leg.direction}:${p.clientPassengerId}`] ?? 0) *
              leg.checkedBaggagePolicy.extraPiecePriceExclVat,
          0,
        ),
      0,
    ) ?? 0;
  const save = async () => {
    if (locked.current || !options) return;
    locked.current = true;
    setPending(true);
    setError("");
    try {
      await b.saveBaggage(options.version, selections);
      router.back();
    } catch (cause) {
      setOptions(null);
      setError(
        cause instanceof Error ? cause.message : "Không lưu được hành lý.",
      );
    } finally {
      locked.current = false;
      setPending(false);
    }
  };
  return (
    <Screen
      design
      title="Hành lý"
      step={1}
      footer={
        <Row>
          <View style={{ flex: 1 }}>
            <Label size={20} weight="bold">
              {money(total)}
            </Label>
            <Label size={11}>Chưa gồm VAT</Label>
          </View>
          <Button
            title="Lưu hành lý"
            disabled={loading || pending || !options}
            onPress={() => void save()}
          />
        </Row>
      }
    >
      <TripSummary />
      {loading && <Notice>Đang tải hành lý…</Notice>}
      {!!error && (
        <>
          <Notice error>{error}</Notice>
          <Button
            title="Tải lại lựa chọn đã lưu"
            disabled={pending}
            onPress={() => {
              setLoading(true);
              setError("");
              setOptions(null);
              setRetry((n) => n + 1);
            }}
          />
        </>
      )}
      {!loading &&
        options?.legs.map((leg, l) => (
          <View key={leg.direction} style={{ gap: 12 }}>
            <Label weight="bold">{l ? "Chặng về" : "Chặng đi"}</Label>
            {leg.passengerBaggage.map((p) => {
              const policy = leg.checkedBaggagePolicy;
              const key = `${leg.direction}:${p.clientPassengerId}`;
              const n = draft[key] ?? 0;
              const traveller =
                b.travellers[Number(p.clientPassengerId.slice(1)) - 1];
              return (
                <Panel key={p.clientPassengerId}>
                  <Label weight="bold">
                    {traveller ? fullName(traveller) : p.clientPassengerId}
                  </Label>
                  <Label>Xách tay: {p.included.carryOnMaxKg} kg</Label>
                  <Label>
                    Ký gửi miễn cước: {p.included.checkedPieces} kiện ×{" "}
                    {policy.pieceMaxKg} kg
                  </Label>
                  {p.included.strollerPieces > 0 && (
                    <Label>
                      Xe đẩy: {p.included.strollerPieces} kiện, tối đa{" "}
                      {p.included.strollerMaxKg} kg
                    </Label>
                  )}
                  {p.canPurchaseExtra ? (
                    <>
                      <Label size={12}>
                        Mỗi kiện tối đa {policy.pieceMaxKg} kg, tổng ba chiều{" "}
                        {policy.maxLinearDimensionsCm} cm. Mua thêm tối đa{" "}
                        {policy.maxExtraPiecesPerPassenger} kiện/người/chiều.
                      </Label>
                      <Row>
                        <Tap
                          label={`Bớt hành lý ${p.clientPassengerId} ${leg.direction}`}
                          disabled={pending || n === 0}
                          onPress={() =>
                            setDraft((d) => ({ ...d, [key]: n - 1 }))
                          }
                        >
                          <Label size={24}>−</Label>
                        </Tap>
                        <Label>{n}</Label>
                        <Tap
                          label={`Thêm hành lý ${p.clientPassengerId} ${leg.direction}`}
                          disabled={
                            pending || n >= policy.maxExtraPiecesPerPassenger
                          }
                          onPress={() =>
                            setDraft((d) => ({ ...d, [key]: n + 1 }))
                          }
                        >
                          <Label size={24}>+</Label>
                        </Tap>
                        <Label size={12}>
                          {money(policy.extraPiecePriceExclVat)}/kiện
                        </Label>
                      </Row>
                      <Label>
                        {money(n * policy.extraPiecePriceExclVat)} · Chưa gồm
                        VAT
                      </Label>
                    </>
                  ) : (
                    <Label size={12}>Em bé không mua hành lý riêng.</Label>
                  )}
                </Panel>
              );
            })}
          </View>
        ))}
    </Screen>
  );
}

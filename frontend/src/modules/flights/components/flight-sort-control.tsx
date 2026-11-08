import { Label, Row, Tap } from "@/shared/components/ui";
import { colors as c } from "@/shared/theme";

export function FlightSortControl({
  value,
  onChange,
  disabled = false,
}: {
  value: "best" | "cheapest";
  onChange: (value: "best" | "cheapest") => void;
  disabled?: boolean;
}) {
  return (
    <Row style={{ gap: 10, paddingTop: 6 }}>
      {(
        [
          { value: "best", label: "Khởi hành sớm nhất" },
          { value: "cheapest", label: "Giá thấp nhất" },
        ] as const
      ).map((option) => {
        const selected = value === option.value;
        return (
          <Tap
            key={option.value}
            label={option.label}
            selected={selected}
            disabled={disabled}
            onPress={() => onChange(option.value)}
            style={{
              flex: 1,
              minHeight: 48,
              paddingHorizontal: 8,
              paddingVertical: 10,
              alignItems: "center",
              borderRadius: 12,
              borderWidth: 1.5,
              borderColor: selected ? c.primary : c.line,
              backgroundColor: selected ? c.tint : c.surface,
            }}
          >
            <Label
              size={13}
              weight="semibold"
              color={selected ? c.primary : c.ink}
              style={{ textAlign: "center" }}
            >
              {option.label}
            </Label>
          </Tap>
        );
      })}
    </Row>
  );
}

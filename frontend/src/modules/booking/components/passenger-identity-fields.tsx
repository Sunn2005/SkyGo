import { useResponsiveLayout } from "@/shared/hooks/use-responsive-layout";
import { DateField } from "@/shared/components/date-field";
import { ChoiceField } from "@/shared/components/choice-field";
import { Field } from "@/shared/components/fields";
import { Notice, Row } from "@/shared/components/ui";
import type { Traveller } from "../types";

export function PassengerIdentityFields({
  p,
  onChange,
  errors,
  domestic = true,
}: {
  p: Traveller;
  onChange: (patch: Partial<Traveller>) => void;
  errors: Record<string, string>;
  domestic?: boolean;
}) {
  const { stackFields, stackDates } = useResponsiveLayout();
  return (
    <>
      <Row
        style={{
          alignItems: "stretch",
          flexDirection: stackFields ? "column" : "row",
        }}
      >
        <Field
          half={!stackFields}
          label="Tên đệm và tên"
          value={p.firstName}
          onChangeText={(value) => onChange({ firstName: value })}
          error={errors.firstName}
          autoComplete="given-name"
        />
        <Field
          half={!stackFields}
          label="Họ"
          value={p.lastName}
          onChangeText={(value) => onChange({ lastName: value })}
          error={errors.lastName}
          autoComplete="family-name"
        />
      </Row>
      <Row
        style={{
          alignItems: "stretch",
          flexDirection: stackDates ? "column" : "row",
        }}
      >
        <DateField
          half={!stackDates}
          label="Ngày sinh"
          value={p.birthDate}
          onChangeText={(value) => onChange({ birthDate: value })}
          error={errors.birthDate}
          pastOnly
        />
        <ChoiceField
          half={!stackDates}
          label="Giới tính"
          value={p.gender}
          options={["Nam", "Nữ", "Khác"] as const}
          onChange={(gender) => onChange({ gender })}
        />
      </Row>
      <ChoiceField<Traveller["documentType"]>
        label="Loại giấy tờ"
        value={p.documentType}
        options={domestic ? ["cccd", "passport"] : ["passport"]}
        getOptionLabel={(value) => (value === "cccd" ? "CCCD" : "Hộ chiếu")}
        onChange={(documentType) => {
          if (documentType !== p.documentType)
            onChange({ documentType, documentNumber: "" });
        }}
      />
      {!!errors.documentType && <Notice error>{errors.documentType}</Notice>}
      {p.documentType === "passport" ? (
        <>
          <Row
            style={{
              alignItems: "stretch",
              flexDirection: stackFields ? "column" : "row",
            }}
          >
            <Field
              half={!stackFields}
              label="Quốc tịch (mã ISO, ví dụ VN)"
              value={p.nationality}
              onChangeText={(value) => onChange({ nationality: value })}
              error={errors.nationality}
            />
            <Field
              half={!stackFields}
              label="Số hộ chiếu"
              value={p.documentNumber}
              autoCapitalize="characters"
              autoCorrect={false}
              maxLength={12}
              onChangeText={(value) =>
                onChange({
                  documentNumber: value.toUpperCase(),
                })
              }
              error={errors.documentNumber}
            />
          </Row>
          <Row
            style={{
              alignItems: "stretch",
              flexDirection: stackFields ? "column" : "row",
            }}
          >
            <DateField
              half={!stackFields}
              label="Ngày hết hạn (NN/TT/NNNN)"
              value={p.documentExpiry}
              onChangeText={(value) => onChange({ documentExpiry: value })}
              error={errors.documentExpiry}
            />
            <Field
              half={!stackFields}
              label="Quốc gia cấp (mã ISO, ví dụ VN)"
              value={p.issuingCountry}
              onChangeText={(value) => onChange({ issuingCountry: value })}
              error={errors.issuingCountry}
            />
          </Row>
        </>
      ) : (
        <Field
          label="Số CCCD"
          value={p.documentNumber}
          keyboardType="number-pad"
          maxLength={12}
          onChangeText={(value) => onChange({ documentNumber: value })}
          error={errors.documentNumber}
        />
      )}
    </>
  );
}

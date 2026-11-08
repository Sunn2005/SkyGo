import { useRef, useState } from "react";
import { View } from "react-native";
import { Button, Notice } from "@/shared/components/ui";
import { saveQrImage } from "../services/save-qr-image";

export function SaveQrButton({
  uri,
  name,
  disabled = false,
}: {
  uri: string;
  name: string;
  disabled?: boolean;
}) {
  const locked = useRef(false);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<{
    uri: string;
    message: string;
    error: boolean;
  } | null>(null);
  const save = async () => {
    if (locked.current || disabled) return;
    locked.current = true;
    setSaving(true);
    setResult(null);
    try {
      const message = await saveQrImage(uri, name);
      setResult({ uri, message, error: false });
    } catch {
      setResult({
        uri,
        message:
          "Chưa lưu được mã QR. Hãy kiểm tra quyền lưu ảnh của SkyGo trong Cài đặt rồi thử lại.",
        error: true,
      });
    } finally {
      locked.current = false;
      setSaving(false);
    }
  };
  return (
    <View style={{ gap: 8 }}>
      <Button
        title="Lưu mã QR"
        loading={saving}
        disabled={disabled}
        onPress={() => void save()}
      />
      {result?.uri === uri && (
        <Notice error={result.error}>{result.message}</Notice>
      )}
    </View>
  );
}

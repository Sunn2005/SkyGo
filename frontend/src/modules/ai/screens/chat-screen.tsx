import { useEffect, useRef, useState, type ComponentRef } from "react";
import {
  ActivityIndicator,
  FlatList,
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  Platform,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from "react-native";
import { router } from "expo-router";
import { Stack } from "expo-router/stack";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useAccount } from "@/modules/account/context/account-context";
import { AccountError } from "@/modules/account/types";
import { BookingHistoryLoginDialog } from "@/modules/account/components/booking-history-login-dialog";
import { HomeIcon } from "@/modules/flights/components/home-icon";
import {
  FlightCalendar,
  localDateISO,
} from "@/modules/flights/components/flight-calendar";
import { Button, Label, Row, Tap } from "@/shared/components/ui";
import { colors as c, fonts } from "@/shared/theme";
import { useAiChat } from "../context/chat-context";
import { ChatMessageView } from "../components/chat-message";
import type { ChatMessage } from "../types";

const suggestions = [
  {
    title: "Tìm vé rẻ",
    prompt: "Tôi muốn tìm chuyến bay giá rẻ. Bạn cần những thông tin gì?",
    icon: "plane",
  },
  {
    title: "Hỏi hành lý",
    prompt: "Quy định hành lý ký gửi của SkyGo như thế nào?",
    icon: "ticket",
  },
  {
    title: "Đơn gần đây",
    prompt: "Cho tôi xem 3 đơn đặt vé gần nhất trong 15 ngày qua.",
    icon: "clock",
  },
] as const;

export default function ChatScreen() {
  const chat = useAiChat();
  const { session, refreshUser } = useAccount();
  const inset = useSafeAreaInsets();
  const list = useRef<FlatList<ChatMessage>>(null);
  const input = useRef<ComponentRef<typeof TextInput>>(null);
  const nearBottom = useRef(true);
  const [showLatest, setShowLatest] = useState(false);
  const [keyboardVisible, setKeyboardVisible] = useState(false);
  const [inputHeight, setInputHeight] = useState(46);
  const [now, setNow] = useState(Date.now);
  const [loginPrompt, setLoginPrompt] = useState<string | null>(null);
  const [dismissedLogin, setDismissedLogin] = useState<string | null>(null);
  const [newChat, setNewChat] = useState(false);
  const [datePicker, setDatePicker] = useState(false);
  const [date, setDate] = useState(() => localDateISO(new Date()));
  const [loginBusy, setLoginBusy] = useState(false);
  const [loginError, setLoginError] = useState("");
  const last = chat.messages.at(-1);
  const automaticLogin =
    last?.response?.status === "LOGIN_REQUIRED" && last.id !== dismissedLogin
      ? last.id
      : null;
  const loginMessage = chat.messages.find(
    (message) => message.id === (loginPrompt ?? automaticLogin),
  );
  const remaining = Math.max(0, Math.ceil((chat.cooldownUntil - now) / 1000));
  const blocked = chat.busy || remaining > 0;
  const scrollToEnd = () => {
    nearBottom.current = true;
    setShowLatest(false);
    list.current?.scrollToEnd({ animated: true });
  };

  useEffect(() => {
    if (chat.cooldownUntil <= Date.now()) return;
    const timer = setInterval(() => {
      const timestamp = Date.now();
      setNow(timestamp);
      if (timestamp >= chat.cooldownUntil) clearInterval(timer);
    }, 1000);
    return () => clearInterval(timer);
  }, [chat.cooldownUntil]);
  useEffect(() => {
    const shown = Keyboard.addListener(
      Platform.OS === "ios" ? "keyboardWillShow" : "keyboardDidShow",
      () => setKeyboardVisible(true),
    );
    const hidden = Keyboard.addListener(
      Platform.OS === "ios" ? "keyboardWillHide" : "keyboardDidHide",
      () => setKeyboardVisible(false),
    );
    return () => {
      shown.remove();
      hidden.remove();
    };
  }, []);
  useEffect(() => {
    if (last?.response?.status === "LOGIN_REQUIRED") {
      Keyboard.dismiss();
    }
  }, [last?.id, last?.response?.status]);

  const compose = (text: string) => {
    chat.setDraft(`${chat.draft.trim()}${chat.draft.trim() ? " " : ""}${text}`);
    input.current?.focus();
  };
  const send = () => {
    if (!chat.draft.trim() || blocked) return;
    nearBottom.current = true;
    setShowLatest(false);
    setInputHeight(46);
    setLoginError("");
    void chat.send();
  };
  const dismissLogin = () => {
    setDismissedLogin(last?.id ?? null);
    setLoginPrompt(null);
    setLoginError("");
  };
  const confirmLogin = async () => {
    if (!loginMessage?.question || loginBusy) return;
    setLoginBusy(true);
    setLoginError("");
    const question = loginMessage.question;
    try {
      // Check an apparently signed-in account before opening the login form.
      // Only expired credentials may clear the session; network errors stay here.
      if (session) {
        try {
          await refreshUser();
          chat.setDraft(question);
          dismissLogin();
          input.current?.focus();
          return;
        } catch (cause) {
          if (!(
            cause instanceof AccountError && cause.code === "UNAUTHENTICATED"
          )) {
            setLoginError(
              cause instanceof Error
                ? cause.message
                : "Chưa kiểm tra được phiên đăng nhập. Vui lòng thử lại.",
            );
            return;
          }
        }
      }
      chat.prepareLogin(question);
      dismissLogin();
      router.push({ pathname: "/login", params: { returnTo: "ai-chat" } });
    } finally {
      setLoginBusy(false);
    }
  };
  const today = localDateISO(new Date(now));
  const maxDate = localDateISO(new Date(now + 365 * 86400000));

  return (
    <View style={[s.screen, { paddingTop: inset.top }]}>
      <Stack.Screen options={{ headerShown: false }} />
      <View style={s.header}>
        <Tap
          label="Quay lại"
          onPress={() =>
            router.canGoBack() ? router.back() : router.replace("/")
          }
          style={s.iconButton}
        >
          <View style={{ transform: [{ rotate: "180deg" }] }}>
            <HomeIcon name="arrow" size={23} color={c.ink} />
          </View>
        </Tap>
        <View style={{ flex: 1, gap: 2 }}>
          <Label size={18} weight="semibold">
            Trợ lý SkyGo
          </Label>
          <Label size={11} muted>
            Tra cứu chuyến bay và hành trình
          </Label>
        </View>
        <Tap
          label="Tạo cuộc trò chuyện mới"
          onPress={() => {
            Keyboard.dismiss();
            setNewChat(true);
          }}
          style={s.iconButton}
        >
          <HomeIcon name="plus" color={c.primary} size={24} />
        </Tap>
      </View>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        enabled={Platform.OS !== "web"}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
        // The custom header and safe-area padding are already in this view's
        // layout coordinates. Adding them again leaves a gap above the keyboard.
        keyboardVerticalOffset={0}
      >
        <View style={{ flex: 1 }}>
          <FlatList
            ref={list}
            style={{ flex: 1 }}
            data={chat.messages}
            keyExtractor={(item) => item.id}
            contentContainerStyle={[
              s.messages,
              !chat.messages.length && { flexGrow: 1 },
            ]}
            keyboardShouldPersistTaps="handled"
            keyboardDismissMode="on-drag"
            automaticallyAdjustKeyboardInsets={false}
            automaticallyAdjustContentInsets={false}
            contentInsetAdjustmentBehavior="never"
            showsVerticalScrollIndicator={false}
            initialNumToRender={8}
            windowSize={7}
            onScroll={(event) => {
              const { contentOffset, contentSize, layoutMeasurement } =
                event.nativeEvent;
              nearBottom.current =
                contentSize.height -
                  contentOffset.y -
                  layoutMeasurement.height <
                120;
              setShowLatest(!nearBottom.current);
            }}
            scrollEventThrottle={100}
            onContentSizeChange={() => {
              if (nearBottom.current)
                list.current?.scrollToEnd({ animated: true });
            }}
            onLayout={() => {
              if (nearBottom.current)
                list.current?.scrollToEnd({ animated: false });
            }}
            ItemSeparatorComponent={() => <View style={{ height: 20 }} />}
            ListEmptyComponent={
              <View style={[s.empty, keyboardVisible && s.emptyCompact]}>
                <View style={[s.avatar, keyboardVisible && s.avatarCompact]}>
                  <HomeIcon
                    name="chat"
                    color={c.primary}
                    size={keyboardVisible ? 26 : 44}
                  />
                </View>
                <Label
                  size={keyboardVisible ? 22 : 26}
                  weight="bold"
                  style={{ textAlign: "center", letterSpacing: -0.5 }}
                >
                  Bạn muốn đi đâu?
                </Label>
                {!keyboardVisible && (
                  <Label
                    size={14}
                    muted
                    style={{ textAlign: "center", maxWidth: 300 }}
                  >
                    Hỏi về giá vé, hành lý hoặc những chuyến đi đã đặt. SkyGo sẽ
                    giúp bạn tra cứu.
                  </Label>
                )}
                <View style={s.suggestions}>
                  {suggestions.map((suggestion) => (
                    <Tap
                      key={suggestion.title}
                      label={suggestion.title}
                      onPress={() => {
                        chat.setDraft(suggestion.prompt);
                        input.current?.focus();
                      }}
                      style={s.suggestion}
                    >
                      <HomeIcon
                        name={suggestion.icon}
                        color={c.primary}
                        size={22}
                      />
                      <Label
                        size={12}
                        weight="semibold"
                        style={{ textAlign: "center" }}
                      >
                        {suggestion.title}
                      </Label>
                    </Tap>
                  ))}
                </View>
                {!keyboardVisible && (
                  <Label size={11} muted style={{ textAlign: "center" }}>
                    Chọn gợi ý hoặc viết câu hỏi của bạn bên dưới.
                  </Label>
                )}
              </View>
            }
            renderItem={({ item }) => (
              <ChatMessageView
                message={item}
                disabled={blocked}
                onCompose={compose}
                onDate={() => {
                  Keyboard.dismiss();
                  setNow(Date.now());
                  setDate(localDateISO(new Date()));
                  setDatePicker(true);
                }}
                onLogin={() => {
                  setLoginError("");
                  setLoginPrompt(item.id);
                }}
                onRetry={() => {
                  nearBottom.current = true;
                  void chat.retry(item);
                }}
              />
            )}
            ListFooterComponent={
              chat.busy ? (
                <View style={s.loading} accessibilityLiveRegion="polite">
                  <ActivityIndicator color={c.primary} size="small" />
                  <Label size={13} muted>
                    SkyGo đang tra cứu…
                  </Label>
                </View>
              ) : chat.messages.length ? (
                <View style={{ height: 8 }} />
              ) : undefined
            }
          />
          {showLatest && (
            <Tap
              label="Cuộn đến tin nhắn mới nhất"
              onPress={scrollToEnd}
              style={s.latest}
            >
              <Row style={{ gap: 6 }}>
                <HomeIcon name="down" color={c.primary} size={17} />
                <Label size={12} color={c.primary} weight="semibold">
                  Tin mới nhất
                </Label>
              </Row>
            </Tap>
          )}
        </View>
        <View
          style={[
            s.composer,
            {
              paddingBottom: keyboardVisible ? 10 : Math.max(inset.bottom, 12),
            },
          ]}
        >
          {remaining > 0 && (
            <Label size={12} muted accessibilityLiveRegion="polite">
              Bạn có thể gửi tiếp sau khoảng {remaining} giây.
            </Label>
          )}
          <Row style={{ gap: 8, alignItems: "flex-end" }}>
            <TextInput
              ref={input}
              value={chat.draft}
              onChangeText={chat.setDraft}
              placeholder="Hỏi trợ lý SkyGo…"
              placeholderTextColor={c.muted}
              accessibilityLabel="Câu hỏi cho trợ lý SkyGo"
              multiline
              maxLength={2000}
              textAlignVertical="top"
              underlineColorAndroid="transparent"
              autoCapitalize="sentences"
              onContentSizeChange={(event) =>
                setInputHeight(
                  Math.max(
                    46,
                    Math.min(112, event.nativeEvent.contentSize.height),
                  ),
                )
              }
              style={[s.input, { height: inputHeight }]}
            />
            <Tap
              label="Gửi câu hỏi"
              disabled={blocked || !chat.draft.trim()}
              onPress={send}
              style={s.send}
            >
              <HomeIcon name="send" color="white" size={23} />
            </Tap>
          </Row>
          <Row style={{ justifyContent: "space-between", gap: 8 }}>
            <Label size={10} muted style={{ flex: 1 }}>
              Giá và chỗ được kiểm tra lại khi đặt vé.
            </Label>
            {chat.draft.length >= 1800 && (
              <Label size={10} muted>
                {chat.draft.length}/2000
              </Label>
            )}
          </Row>
        </View>
      </KeyboardAvoidingView>
      <BookingHistoryLoginDialog
        visible={!!loginMessage}
        busy={loginBusy}
        error={loginError}
        onCancel={dismissLogin}
        onConfirm={() => void confirmLogin()}
      />
      <Modal
        transparent
        visible={newChat}
        animationType="fade"
        onRequestClose={() => setNewChat(false)}
      >
        <View style={s.overlay}>
          <View style={s.dialog} accessibilityViewIsModal>
            <Label size={20} weight="bold">
              Bắt đầu cuộc trò chuyện mới?
            </Label>
            <Label size={14} muted>
              Nội dung trò chuyện hiện tại sẽ được xóa khỏi phiên này.
            </Label>
            <Button
              title="Bắt đầu mới"
              onPress={() => {
                chat.reset();
                nearBottom.current = true;
                setShowLatest(false);
                setNewChat(false);
                setInputHeight(46);
              }}
            />
            <Tap
              label="Giữ cuộc trò chuyện"
              onPress={() => setNewChat(false)}
              style={{ alignItems: "center" }}
            >
              <Label weight="semibold" muted>
                Giữ cuộc trò chuyện
              </Label>
            </Tap>
          </View>
        </View>
      </Modal>
      <Modal
        transparent
        visible={datePicker}
        animationType="slide"
        onRequestClose={() => setDatePicker(false)}
      >
        <View style={s.sheetOverlay}>
          <View
            style={[s.sheet, { paddingBottom: Math.max(inset.bottom, 16) }]}
            accessibilityViewIsModal
          >
            <Row style={{ justifyContent: "space-between" }}>
              <Label size={20} weight="bold">
                Chọn ngày bay
              </Label>
              <Tap
                label="Đóng lịch"
                onPress={() => setDatePicker(false)}
                style={s.iconButton}
              >
                <HomeIcon name="close" color={c.ink} size={22} />
              </Tap>
            </Row>
            <ScrollView>
              <FlightCalendar
                key={datePicker ? "calendar-open" : "calendar-closed"}
                value={date}
                minDate={today}
                maxDate={maxDate}
                onChange={setDate}
              />
            </ScrollView>
            <Button
              title="Dùng ngày này"
              onPress={() => {
                setDatePicker(false);
                compose(`Ngày bay là ${date}.`);
              }}
            />
          </View>
        </View>
      </Modal>
    </View>
  );
}

const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#F2F7F8" },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 8,
    backgroundColor: "white",
    borderBottomWidth: 1,
    borderBottomColor: c.line,
  },
  iconButton: { width: 44, minHeight: 44, alignItems: "center", flexShrink: 0 },
  messages: { paddingHorizontal: 16, paddingTop: 20, paddingBottom: 20 },
  empty: {
    flexGrow: 1,
    justifyContent: "center",
    alignItems: "center",
    gap: 16,
    paddingVertical: 28,
  },
  emptyCompact: { paddingVertical: 12, gap: 10 },
  avatarCompact: { width: 48, height: 48, borderRadius: 16, marginBottom: 0 },
  avatar: {
    width: 88,
    height: 88,
    borderRadius: 28,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#DEF0EE",
    marginBottom: 4,
  },
  suggestions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    alignSelf: "stretch",
    justifyContent: "center",
    marginTop: 8,
  },
  suggestion: {
    flex: 1,
    minWidth: 86,
    alignItems: "center",
    gap: 8,
    backgroundColor: "white",
    borderWidth: 1,
    borderColor: c.line,
    borderRadius: 14,
    paddingHorizontal: 7,
    paddingVertical: 14,
  },
  composer: {
    backgroundColor: "white",
    borderTopWidth: 1,
    borderColor: c.line,
    paddingHorizontal: 14,
    paddingTop: 12,
    gap: 7,
  },
  input: {
    flex: 1,
    minWidth: 0,
    backgroundColor: "#F3F6F8",
    borderWidth: 1,
    borderColor: c.line,
    borderRadius: 16,
    fontFamily: fonts.regular,
    color: c.ink,
    fontSize: 15,
    lineHeight: 22,
    paddingHorizontal: 13,
    paddingVertical: 11,
    maxHeight: 112,
  },
  send: {
    width: 46,
    minHeight: 46,
    borderRadius: 15,
    backgroundColor: c.primary,
    alignItems: "center",
    marginBottom: 1,
  },
  loading: {
    flexDirection: "row",
    alignItems: "center",
    gap: 9,
    paddingTop: 20,
    paddingBottom: 4,
  },
  latest: {
    position: "absolute",
    bottom: 10,
    alignSelf: "center",
    backgroundColor: "white",
    borderRadius: 22,
    borderWidth: 1,
    borderColor: c.line,
    paddingHorizontal: 15,
    boxShadow: "0 2px 8px rgba(16,42,67,0.08)",
  },
  overlay: {
    flex: 1,
    backgroundColor: "#102A4380",
    padding: 24,
    justifyContent: "center",
    alignItems: "center",
  },
  dialog: {
    width: "100%",
    maxWidth: 360,
    padding: 22,
    borderRadius: 20,
    backgroundColor: "white",
    gap: 16,
  },
  sheetOverlay: {
    flex: 1,
    backgroundColor: "#102A4380",
    justifyContent: "flex-end",
    alignItems: "center",
  },
  sheet: {
    width: "100%",
    maxWidth: Platform.OS === "web" ? 402 : 600,
    maxHeight: "85%",
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 18,
    gap: 12,
    backgroundColor: "white",
  },
});

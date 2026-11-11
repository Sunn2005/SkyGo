import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useAccount } from "@/modules/account/context/account-context";
import { ApiError } from "@/shared/api/client";
import { historyForRequest, sendChat } from "../services/chat-service";
import type { ChatMessage, ChatRequest } from "../types";

type State = {
  owner: string;
  messages: ChatMessage[];
  draft: string;
  busy: boolean;
  cooldownUntil: number;
};
const fresh = (owner: string, draft = ""): State => ({
  owner,
  messages: [],
  draft,
  busy: false,
  cooldownUntil: 0,
});
let serial = 0;
const id = () => `chat-${Date.now()}-${++serial}`;
type ChatContextValue = Omit<State, "owner"> & {
  send: (text?: string) => Promise<void>;
  retry: (message: ChatMessage) => Promise<void>;
  setDraft: (text: string) => void;
  reset: () => void;
  prepareLogin: (question: string) => void;
};
const ChatContext = createContext<ChatContextValue | null>(null);

export function AiChatProvider({ children }: { children: ReactNode }) {
  const { session } = useAccount();
  const owner = session?.accessToken ?? "guest";
  const [state, setState] = useState<State>(() => fresh(owner));
  const requestRef = useRef<{
    controller: AbortController;
    owner: string;
  } | null>(null);
  const pendingLogin = useRef<{ question: string; until: number } | null>(null);
  // Render no previous account's data, even during the render before the reset effect.
  const current = state.owner === owner ? state : fresh(owner);
  useEffect(() => {
    requestRef.current?.controller.abort();
    requestRef.current = null;
    const draft =
      owner !== "guest" &&
      pendingLogin.current &&
      pendingLogin.current.until > Date.now()
        ? pendingLogin.current.question
        : "";
    // Keep the explicitly selected question across an expired-token -> guest
    // transition, until login succeeds. Never carry conversation history across.
    if (owner !== "guest") pendingLogin.current = null;
    setState((previous) =>
      previous.owner === owner ? previous : fresh(owner, draft),
    );
    return () => {
      requestRef.current?.controller.abort();
      requestRef.current = null;
    };
  }, [owner]);

  const setDraft = useCallback(
    (draft: string) => {
      setState((previous) =>
        previous.owner === owner
          ? { ...previous, draft: draft.slice(0, 2000) }
          : previous,
      );
    },
    [owner],
  );
  const reset = useCallback(() => {
    requestRef.current?.controller.abort();
    requestRef.current = null;
    pendingLogin.current = null;
    setState((previous) => ({
      ...fresh(owner),
      cooldownUntil: previous.owner === owner ? previous.cooldownUntil : 0,
    }));
  }, [owner]);

  const execute = async (request: ChatRequest, retryMessage?: ChatMessage) => {
    if (
      requestRef.current ||
      state.owner !== owner ||
      state.cooldownUntil > Date.now() ||
      !request.message.trim()
    )
      return;
    const active = { controller: new AbortController(), owner };
    requestRef.current = active;
    setState((previous) =>
      previous.owner !== owner
        ? previous
        : {
            ...previous,
            busy: true,
            draft: "",
            messages:
              retryMessage && previous.messages.at(-1)?.id === retryMessage.id
                ? previous.messages.filter((m) => m.id !== retryMessage.id)
                : ([
                    ...previous.messages,
                    { id: id(), role: "USER", content: request.message },
                  ].slice(-40) as ChatMessage[]),
          },
    );
    try {
      const response = await sendChat(
        request,
        session?.accessToken,
        active.controller.signal,
      );
      if (active.controller.signal.aborted || requestRef.current !== active)
        return;
      setState((previous) =>
        previous.owner !== owner
          ? previous
          : {
              ...previous,
              messages: [
                ...previous.messages,
                {
                  id: id(),
                  role: "ASSISTANT",
                  content: response.answer,
                  response,
                  question: request.message,
                },
              ].slice(-40) as ChatMessage[],
            },
      );
    } catch (cause) {
      if (active.controller.signal.aborted || requestRef.current !== active)
        return;
      const rateLimited = cause instanceof ApiError && cause.status === 429;
      const retryable =
        !(cause instanceof ApiError) ||
        cause.status === 408 ||
        cause.status === 429 ||
        cause.status >= 500;
      const content =
        cause instanceof ApiError && cause.code === "AI_DISABLED"
          ? "Trợ lý chưa sẵn sàng lúc này. Bạn vui lòng quay lại sau."
          : rateLimited
            ? "Trợ lý đang bận hoặc đã chạm hạn mức. Bạn vui lòng đợi một chút rồi thử lại."
            : cause instanceof ApiError &&
                (cause.status === 408 || cause.status === 504)
              ? "Trợ lý trả lời lâu hơn dự kiến. Bạn có thể thử lại sau."
              : cause instanceof Error
                ? cause.message
                : "Chưa gửi được câu hỏi. Vui lòng thử lại.";
      setState((previous) =>
        previous.owner !== owner
          ? previous
          : {
              ...previous,
              cooldownUntil: rateLimited
                ? Date.now() +
                  ((cause as ApiError).retryAfterSeconds ?? 60) * 1000
                : previous.cooldownUntil,
              messages: [
                ...previous.messages,
                {
                  id: id(),
                  role: "ASSISTANT",
                  content,
                  failure: { request, retryable },
                },
              ].slice(-40) as ChatMessage[],
            },
      );
    } finally {
      if (requestRef.current === active) {
        requestRef.current = null;
        setState((previous) =>
          previous.owner === owner ? { ...previous, busy: false } : previous,
        );
      }
    }
  };

  return (
    <ChatContext.Provider
      value={{
        ...current,
        setDraft,
        reset,
        prepareLogin: (question) => {
          pendingLogin.current = {
            question: question.slice(0, 2000),
            until: Date.now() + 300000,
          };
        },
        send: (text) => {
          const message = (text ?? current.draft).trim().slice(0, 2000);
          return execute({
            message,
            history: historyForRequest(current.messages),
          });
        },
        retry: (message) =>
          message.failure
            ? execute(message.failure.request, message)
            : Promise.resolve(),
      }}
    >
      {children}
    </ChatContext.Provider>
  );
}

export function useAiChat() {
  const value = useContext(ChatContext);
  if (!value) throw new Error("useAiChat must be used inside AiChatProvider");
  return value;
}

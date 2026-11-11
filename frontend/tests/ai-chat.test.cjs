/* global __dirname */
const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const ts = require("typescript");
const dir = fs.mkdtempSync(path.join(os.tmpdir(), "skygo-ai-chat-"));

// Compile only the adapter and pure helpers. Every HTTP response below is mocked.
for (const [source, target] of [
  ["shared/api/client", "client"],
  ["modules/ai/services/chat-service", "chat-service"],
  ["modules/account/utils/login-destination", "login-destination"],
]) {
  const sourceText = fs
    .readFileSync(path.join(__dirname, "../src", source + ".ts"), "utf8")
    .replace(
      'import { Platform } from "react-native";',
      'const Platform = { OS: "web" };',
    )
    .replace('"@/shared/api/client"', '"./client"');
  fs.writeFileSync(
    path.join(dir, target + ".js"),
    ts.transpileModule(sourceText, {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
      },
    }).outputText,
  );
}
const { historyForRequest, isChatResponse, sendChat } = require(
  path.join(dir, "chat-service.js"),
);
const { ApiError, apiRequest } = require(path.join(dir, "client.js"));
const originalFetch = global.fetch;
after(() => {
  global.fetch = originalFetch;
  fs.rmSync(dir, { recursive: true, force: true });
});

const empty = (status = "ANSWERED") => ({
  status,
  answer: "Kết quả tra cứu",
  sources: [],
  flightSearches: [],
  bookingSearches: [],
  clarifications: [],
});
const airport = (code) => ({
  code,
  name: "Airport " + code,
  city: "City " + code,
  country: "VN",
  timeZone: "Asia/Ho_Chi_Minh",
});
const passengerCounts = { adults: 2, children: 1, infants: 0 };
const complete = () => ({
  ...empty(),
  sources: [
    {
      type: "MARIADB",
      id: "flight:1",
      title: "SG101",
      asOf: "2026-11-10T12:00:00Z",
    },
  ],
  flightSearches: [
    {
      origin: airport("SGN"),
      destination: airport("HAN"),
      departureDate: "2026-11-20",
      passengerCounts,
      sort: "CHEAPEST",
      totalFareOptions: 1,
      hasMore: false,
      includesVat: true,
      includesExtraBaggage: false,
      priceScope: "ONE_WAY_PARTY",
      asOf: "2026-11-10T12:00:00Z",
      options: [
        {
          flightId: "f1",
          flightNumber: "SG101",
          cabinClass: "ECONOMY",
          departureAt: "2026-11-20T23:00:00+07:00",
          arrivalAt: "2026-11-21T01:00:00+07:00",
          durationMinutes: 120,
          availableSeats: 5,
          adultPrice: 1000000,
          partyTotal: 2750000,
          currency: "VND",
        },
      ],
    },
  ],
  bookingSearches: [
    {
      days: 15,
      limit: 3,
      status: "ALL",
      asOf: "2026-11-10T12:00:00Z",
      bookings: [
        {
          bookingId: "b1",
          bookingCode: "SKY123",
          bookingStatus: "PAID",
          paymentStatus: "SUCCEEDED",
          createdAt: "2026-11-09T10:00:00Z",
          tripType: "ONE_WAY",
          totalAmount: 2750000,
          currency: "VND",
          passengerCounts,
          legs: [
            {
              direction: "OUTBOUND",
              origin: { code: "SGN", city: "Hồ Chí Minh" },
              destination: { code: "HAN", city: "Hà Nội" },
              departureAt: "2026-11-20T23:00:00+07:00",
              cabinClass: "ECONOMY",
            },
          ],
        },
      ],
    },
  ],
  clarifications: [
    {
      field: "destination",
      message: "Chọn sân bay",
      airportOptions: [airport("HAN")],
    },
  ],
});

test("all business statuses and structured API results are accepted", () => {
  for (const status of [
    "ANSWERED",
    "NEEDS_CLARIFICATION",
    "LOGIN_REQUIRED",
    "NO_DATA",
  ]) {
    assert.equal(isChatResponse(empty(status)), true);
  }
  assert.equal(isChatResponse(complete()), true);
  const withNullTime = empty();
  withNullTime.sources.push({
    type: "MARIADB_VECTOR",
    id: "doc1",
    title: "Hành lý",
    asOf: null,
  });
  assert.equal(isChatResponse(withNullTime), true);
});

test("invalid nested results are rejected before reaching card components", () => {
  for (const corrupt of [
    (r) => {
      r.flightSearches[0].options = null;
    },
    (r) => {
      r.flightSearches[0].options[0].partyTotal = -1;
    },
    (r) => {
      r.bookingSearches[0].bookings[0].legs[0].origin = null;
    },
    (r) => {
      r.clarifications[0].airportOptions = "HAN";
    },
    (r) => {
      r.status = "UNKNOWN";
    },
    (r) => {
      r.answer = "a".repeat(30001);
    },
  ]) {
    const reply = complete();
    corrupt(reply);
    assert.equal(isChatResponse(reply), false);
  }
  assert.equal(isChatResponse(null), false);
});

test("history is bounded and excludes error/login turns and structured personal data", () => {
  const messages = Array.from({ length: 10 }, (_, index) => ({
    id: String(index),
    role: index % 2 ? "ASSISTANT" : "USER",
    content: "x".repeat(2100),
    response: index % 2 ? complete() : undefined,
  }));
  const history = historyForRequest(messages);
  assert.equal(history.length, 6);
  assert.equal(history[0].role, "USER");
  assert.ok(history.every((turn) => turn.content.length === 2000));
  assert.ok(
    history.every((turn) => Object.keys(turn).sort().join() === "content,role"),
  );
  assert.equal(JSON.stringify(history).includes("SKY123"), false);
  const blocked = [
    { id: "1", role: "USER", content: "My bookings" },
    {
      id: "2",
      role: "ASSISTANT",
      content: "Login",
      response: empty("LOGIN_REQUIRED"),
    },
    { id: "3", role: "USER", content: "Baggage" },
    {
      id: "4",
      role: "ASSISTANT",
      content: "Failed",
      failure: {
        request: { message: "Baggage", history: [] },
        retryable: true,
      },
    },
  ];
  assert.deepEqual(historyForRequest(blocked), []);
});

test("chat posts exact JSON, uses optional bearer and never sends a user ID", async () => {
  const calls = [];
  global.fetch = async (url, options) => {
    calls.push({ url, ...options });
    return new Response(JSON.stringify({ data: complete() }));
  };
  const request = { message: "Tìm vé SGN đến HAN", history: [] };
  await sendChat(request, undefined, new AbortController().signal);
  const result = await sendChat(
    request,
    "private-token",
    new AbortController().signal,
  );
  assert.equal(calls.length, 2);
  assert.ok(calls[0].url.endsWith("/api/v1/ai/chat"));
  assert.equal(calls[0].method, "POST");
  assert.equal(calls[0].headers.Authorization, undefined);
  assert.equal(calls[1].headers.Authorization, "Bearer private-token");
  assert.deepEqual(JSON.parse(calls[1].body), request);
  assert.equal(calls[1].body.includes("private-token"), false);
  assert.equal(
    result.flightSearches[0].options[0].departureAt,
    "2026-11-20T23:00:00+07:00",
  );
});

test("429 preserves retry delay and performs no automatic retries", async () => {
  let calls = 0;
  global.fetch = async () => {
    calls++;
    return new Response(
      JSON.stringify({
        error: { code: "AI_UPSTREAM_RATE_LIMITED", message: "Limit" },
      }),
      {
        status: 429,
        headers: { "Retry-After": "60" },
      },
    );
  };
  await assert.rejects(
    sendChat(
      { message: "Baggage", history: [] },
      undefined,
      new AbortController().signal,
    ),
    (error) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 429);
      assert.equal(error.retryAfterSeconds, 60);
      return true;
    },
  );
  assert.equal(calls, 1);
});

test("malformed success responses become a controlled error", async () => {
  global.fetch = async () =>
    new Response(JSON.stringify({ data: { answer: "Missing lists" } }));
  await assert.rejects(
    sendChat(
      { message: "Baggage", history: [] },
      undefined,
      new AbortController().signal,
    ),
    /kết quả hợp lệ/,
  );
});

test("API errors retain the AI message and distinguish gateway/route failures", async () => {
  for (const [response, code, message] of [
    [
      new Response(
        JSON.stringify({
          error: {
            code: "AI_UPSTREAM_UNAVAILABLE",
            message: "Gemini chưa xử lý được câu hỏi.",
          },
        }),
        { status: 502 },
      ),
      "AI_UPSTREAM_UNAVAILABLE",
      /Gemini/,
    ],
    [
      new Response(JSON.stringify({ status: 404, error: "Not Found" }), {
        status: 404,
      }),
      "HTTP_ERROR",
      /Không tìm thấy API/,
    ],
    [
      new Response("<html>Gateway unavailable</html>", { status: 502 }),
      "INVALID_API_RESPONSE",
      /đường hầm backend/,
    ],
  ]) {
    global.fetch = async () => response;
    await assert.rejects(
      sendChat(
        { message: "Baggage", history: [] },
        undefined,
        new AbortController().signal,
      ),
      (error) => {
        assert.ok(error instanceof ApiError);
        assert.equal(error.code, code);
        assert.match(error.message, message);
        return true;
      },
    );
  }
});

test("abort propagates to fetch and an explicit timeout reports REQUEST_TIMEOUT", async () => {
  let fetchSignal;
  global.fetch = async (_, options) =>
    new Promise((_, reject) => {
      fetchSignal = options.signal;
      const abort = () => reject(new Error("aborted"));
      if (fetchSignal.aborted) abort();
      else fetchSignal.addEventListener("abort", abort, { once: true });
    });
  const controller = new AbortController();
  const pending = sendChat(
    { message: "Baggage", history: [] },
    undefined,
    controller.signal,
  );
  controller.abort();
  await assert.rejects(pending);
  assert.equal(fetchSignal.aborted, true);
  await assert.rejects(apiRequest("/test", { timeoutMs: 5 }), (error) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.code, "REQUEST_TIMEOUT");
    return true;
  });
});

test("login redirect allows the chat route while retaining the destination whitelist", () => {
  const { loginDestination } = require(path.join(dir, "login-destination.js"));
  assert.equal(loginDestination("ai-chat").href, "/ai-chat");
  assert.equal(loginDestination("/ai-chat").href, "/ai-chat");
  assert.equal(loginDestination("https://example.com").href, "/");
});

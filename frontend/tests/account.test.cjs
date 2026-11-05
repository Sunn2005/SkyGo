const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const ts = require("typescript");

// Run the production mock adapter without needing React Native or a test runtime.
const compiled = fs.mkdtempSync(path.join(os.tmpdir(), "skygo-account-test-"));
for (const file of ["types", "utils/validation", "data/mock-account-service"]) {
  const source = fs.readFileSync(
    path.join(__dirname, "../src/modules/account", `${file}.ts`),
    "utf8",
  );
  const target = path.join(compiled, `${file}.js`);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(
    target,
    ts.transpileModule(source, {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
      },
    }).outputText,
  );
}
const { createMockAccountService, demoAccount } = require(
  path.join(compiled, "data/mock-account-service.js"),
);
const { normalizePhone, validateRegister } = require(
  path.join(compiled, "utils/validation.js"),
);
after(() => fs.rmSync(compiled, { recursive: true, force: true }));
const registration = {
  fullName: "  Trần Minh Anh  ",
  phoneNumber: "0987654321",
  email: "  minhanh@example.com  ",
  password: "TestSkyGo@2027",
  confirmPassword: "TestSkyGo@2027",
};
const service = (options = {}) =>
  createMockAccountService({ delayMs: 0, ...options });
const hasCode = (code) => (error) => error.code === code;

test("normalizes local and +84 formats to the same account key", () => {
  assert.equal(normalizePhone("0912 345 678"), "+84912345678");
  assert.equal(normalizePhone("+84 912 345 678"), "+84912345678");
  assert.equal(normalizePhone("123"), null);
});

test("validates name, email, phone, password policy and confirmation", () => {
  assert.deepEqual(validateRegister(registration), {});
  const errors = validateRegister({
    ...registration,
    fullName: " ",
    email: "bad",
    password: "weak",
    confirmPassword: "other",
  });
  for (const field of ["fullName", "email", "password", "confirmPassword"])
    assert.ok(errors[field]);
});

test("register → login → me → logout; no credentials returned in user payload", async () => {
  const api = service();
  const user = await api.register(registration);
  assert.equal(user.fullName, "Trần Minh Anh");
  assert.equal(user.email, "minhanh@example.com");
  assert.equal(user.password, undefined);
  assert.equal(user.accessToken, undefined);
  const session = await api.login(registration);
  assert.deepEqual(await api.me(session.accessToken), user);
  await api.logout(session.accessToken);
  await assert.rejects(api.me(session.accessToken), hasCode("UNAUTHENTICATED"));
  assert.ok((await api.login(registration)).accessToken);
});

test("duplicate phone detection uses the normalized number", async () => {
  const api = service();
  await assert.rejects(
    api.register({ ...registration, phoneNumber: "+84 912 345 678" }),
    hasCode("PHONE_ALREADY_REGISTERED"),
  );
});

test("unknown account and wrong password return the same error", async () => {
  const api = service();
  await assert.rejects(
    api.login({ ...demoAccount, password: "wrong" }),
    hasCode("INVALID_CREDENTIALS"),
  );
  await assert.rejects(
    api.login({ phoneNumber: "0987654321", password: "wrong" }),
    hasCode("INVALID_CREDENTIALS"),
  );
});

test("fifth failure locks even valid credentials for 15 minutes, then recovers", async () => {
  let now = Date.UTC(2026, 9, 31);
  const api = service({ now: () => now });
  for (let i = 0; i < 4; i++) {
    await assert.rejects(
      api.login({
        phoneNumber: i % 2 ? "+84912345678" : demoAccount.phoneNumber,
        password: "wrong",
      }),
      hasCode("INVALID_CREDENTIALS"),
    );
  }
  await assert.rejects(
    api.login({ ...demoAccount, password: "wrong" }),
    (error) =>
      error.code === "LOGIN_TEMPORARILY_LOCKED" &&
      error.retryAt === now + 900000,
  );
  await assert.rejects(
    api.login(demoAccount),
    hasCode("LOGIN_TEMPORARILY_LOCKED"),
  );
  now += 900001;
  assert.ok((await api.login(demoAccount)).accessToken);
});

test("successful login resets failures; failure window expires", async () => {
  let now = Date.UTC(2026, 9, 31);
  const api = service({ now: () => now });
  for (let i = 0; i < 4; i++)
    await assert.rejects(api.login({ ...demoAccount, password: "wrong" }));
  await api.login(demoAccount);
  await assert.rejects(
    api.login({ ...demoAccount, password: "wrong" }),
    hasCode("INVALID_CREDENTIALS"),
  );
  now += 900001;
  await assert.rejects(
    api.login({ ...demoAccount, password: "wrong" }),
    hasCode("INVALID_CREDENTIALS"),
  );
  assert.ok((await api.login(demoAccount)).accessToken);
});

test("expired and unknown sessions are rejected; password is never trimmed", async () => {
  let now = Date.UTC(2026, 9, 31);
  const api = service({ now: () => now });
  const session = await api.login(demoAccount);
  await assert.rejects(
    api.login({ ...demoAccount, password: ` ${demoAccount.password}` }),
    hasCode("INVALID_CREDENTIALS"),
  );
  await assert.rejects(api.me("unknown"), hasCode("UNAUTHENTICATED"));
  now = Date.parse(session.expiresAt);
  await assert.rejects(api.me(session.accessToken), hasCode("UNAUTHENTICATED"));
});

test("registration rejects missing name or email instead of creating incomplete accounts", async () => {
  const api = service();
  for (const field of ["fullName", "email"]) {
    const input = { ...registration, [field]: "   " };
    assert.ok(validateRegister(input)[field]);
    await assert.rejects(
      api.register(input),
      (error) => error.code === "VALIDATION_ERROR" && !!error.fields[field],
    );
  }
});

test("password accepts exactly 8 characters and requires every criterion", () => {
  for (const password of ["Abcd12!@", "LongerPassword1!"]) {
    assert.equal(
      validateRegister({ ...registration, password, confirmPassword: password })
        .password,
      undefined,
    );
  }
  for (const password of [
    "Abc12!@",
    "abcd12!@",
    "ABCD12!@",
    "Abcdef!@",
    "Abcd1234",
  ]) {
    assert.ok(
      validateRegister({ ...registration, password, confirmPassword: password })
        .password,
    );
  }
});

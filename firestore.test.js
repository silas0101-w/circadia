const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validLogPayload(uid, overrides = {}) {
  const past = new Date(Date.now() - 5000);
  return {
    userId: uid,
    localId: 1,
    dateIso: "2026-11-17",
    substanceId: "caffeine_coffee",
    substanceName: "Caffeine (Coffee)",
    category: "Stimulant",
    dosageAmount: 150.0,
    dosageUnit: "mg",
    takenAtMillis: Date.now() - 60000,
    halfLifeHours: 5.0,
    disruptionWeight: 1.0,
    notes: "Morning cup",
    updatedAtMillis: Date.now() - 1000,
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

function validSleepPayload(uid, overrides = {}) {
  const past = new Date(Date.now() - 5000);
  return {
    userId: uid,
    dateIso: "2026-11-17",
    source: "Health Connect",
    totalDurationMinutes: 450,
    remMinutes: 95,
    deepMinutes: 85,
    lightMinutes: 250,
    awakeMinutes: 20,
    restingHeartRateBpm: 58,
    bedtimeMillis: Date.now() - 36000000,
    wakeTimeMillis: Date.now() - 9000000,
    syncedAtMillis: Date.now() - 1000,
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

function validReportPayload(uid, overrides = {}) {
  const past = new Date(Date.now() - 5000);
  return {
    userId: uid,
    scopeKey: "day:2026-11-17",
    dateIso: "2026-11-17",
    providerUsed: "Gemini",
    modelUsed: "gemini-3.8-flash",
    severityRating: "Moderate",
    biologicalMechanism: "Mild residual adenosine receptor occupancy at bedtime.",
    mitigationTipsJson: "[\"Finish fluids 2h before bed\"]",
    createdAtMillis: Date.now() - 2000,
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user subcollection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/substance_logs`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/substance_logs`).doc("log_1").set(validLogPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/sleep_sessions`).doc("session_2026-11-17").set(validSleepPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/ai_reports`).doc("day_2026-11-17").set(validReportPayload(ALICE_UID))
  );
});

test("Authenticated owner can create, update, list, and delete substance logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection(`users/${ALICE_UID}/substance_logs`);

  await assertSucceeds(col.doc("log_1").set(validLogPayload(ALICE_UID)));
  await assertSucceeds(col.get());
  await assertSucceeds(
    col.doc("log_1").update({
      dosageAmount: 200.0,
      updatedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt mutation must fail
  await assertFails(
    col.doc("log_1").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Ghost field / @DocumentId 'id' rejection
  await assertFails(
    col.doc("log_2").set(validLogPayload(ALICE_UID, { id: "log_2" }))
  );

  // Future timestamp rejection
  await assertFails(
    col.doc("log_3").set(validLogPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  await assertSucceeds(col.doc("log_1").delete());
});

test("Authenticated owner can create and update sleep_sessions with and without optional RHR, plus ai_reports", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  const sleepNoRhr = validSleepPayload(ALICE_UID);
  delete sleepNoRhr.restingHeartRateBpm;
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/sleep_sessions`).doc("session_2026-11-17").set(sleepNoRhr)
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/sleep_sessions`).doc("session_2026-11-17").update({
      restingHeartRateBpm: 60,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/ai_reports`).doc("day_2026-11-17").set(validReportPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/ai_reports`).get()
  );
});

test("Cross-user access is strictly denied across all subcollections", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection(`users/${BOB_UID}/substance_logs`).doc("log_1").set(validLogPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/substance_logs`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/substance_logs`).doc("log_1").get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/substance_logs`).doc("log_1").update({
      notes: "Tampered",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/substance_logs`).doc("spoof").set(validLogPayload(BOB_UID))
  );
});

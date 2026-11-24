const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_racer_01";
const BOB_UID = "bob_racer_02";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function makeProfile(uid, ts) {
  return {
    userId: uid,
    displayName: "ViperDriver",
    credits: 15000,
    xp: 0,
    level: 1,
    racesCompleted: 0,
    racesWon: 0,
    unlockedAchievements: [],
    selectedCarId: "vipera_gtx",
    totalDistanceKm: 0.0,
    highestDriftCombo: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeGarageCar(uid, carId, ts) {
  return {
    userId: uid,
    carId,
    paintHex: "#E50914",
    rimStyle: "CHROME",
    underglowHex: "#00E5FF",
    spoilerStyle: "STOCK",
    bodyKitStyle: "STREET",
    windowTint: "SMOKE",
    headlightStyle: "XENON_WHITE",
    engineLevel: 1,
    turboLevel: 1,
    nitroLevel: 1,
    handlingLevel: 1,
    tiresLevel: 1,
    suspensionLevel: 1,
    brakesLevel: 1,
    createdAt: ts,
    updatedAt: ts,
  };
}

function makeCareerProgress(uid, eventId, ts) {
  return {
    userId: uid,
    eventId,
    bestTimeMs: 48200,
    bestDriftScore: 0,
    medalTier: "SILVER",
    completedCount: 1,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated users cannot read or write any player, garage, or career docs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(makeProfile(ALICE_UID, now))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("garageCars").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("careerProgress").get()
  );
});

test("Authenticated owner: valid profile create, XP/Level/Achievements updates, and negative guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(makeProfile(ALICE_UID, created))
  );

  // Valid partial updates matching Kotlin repository (including 5th car titan_v8, XP, level, achievements)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      selectedCarId: "titan_v8",
      credits: 20000,
      xp: 1250,
      level: 3,
      racesCompleted: 4,
      racesWon: 2,
      unlockedAchievements: ["first_victory", "garage_collector"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      credits: 25000,
      totalDistanceKm: 12.4,
      highestDriftCombo: 8500,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      credits: 99999,
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject out-of-range level
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      level: 999,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject future timestamp tampering
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      credits: 30000,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject listing /users root collection
  await assertFails(aliceDb.collection("users").get());
});

test("Authenticated owner: all 5 cars, 6 visual customizations, 6 upgrades, and invalid enum rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const carRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("garageCars")
    .doc("titan_v8");

  await assertSucceeds(carRef.set(makeGarageCar(ALICE_UID, "titan_v8", created)));

  // List subcollection (mirrors repository observeGarageCars)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("garageCars").get()
  );

  // Full 6-part visual customization update
  await assertSucceeds(
    carRef.update({
      paintHex: "#1565C0",
      rimStyle: "BRONZE",
      underglowHex: "#FF1744",
      spoilerStyle: "HYPER_AERO",
      bodyKitStyle: "WIDEBODY",
      windowTint: "MIDNIGHT",
      headlightStyle: "ICE_BLUE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Full 6-track performance upgrade update
  await assertSucceeds(
    carRef.update({
      engineLevel: 2,
      turboLevel: 3,
      nitroLevel: 2,
      handlingLevel: 2,
      tiresLevel: 2,
      suspensionLevel: 3,
      brakesLevel: 2,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid spoiler enum
  await assertFails(
    carRef.update({
      spoilerStyle: "ROCKET_WING",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject out-of-bounds upgrade level
  await assertFails(
    carRef.update({
      turboLevel: 99,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject invalid car ID
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("garageCars")
      .doc("hacked_car")
      .set(makeGarageCar(ALICE_UID, "hacked_car", created))
  );
});

test("Authenticated owner: careerProgress create, list, and record update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const eventRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("careerProgress")
    .doc("downtown_street_clash");

  await assertSucceeds(
    eventRef.set(makeCareerProgress(ALICE_UID, "downtown_street_clash", created))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("careerProgress").get()
  );

  await assertSucceeds(
    eventRef.update({
      bestTimeMs: 42100,
      bestDriftScore: 3400,
      medalTier: "GOLD",
      completedCount: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid medal tier
  await assertFails(
    eventRef.update({
      bestTimeMs: 40000,
      bestDriftScore: 5000,
      medalTier: "PLATINUM",
      completedCount: 3,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Alice cannot read or modify Bob's profile, garage, or career", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(BOB_UID).set(makeProfile(BOB_UID, created));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("garageCars")
      .doc("vipera_gtx")
      .set(makeGarageCar(BOB_UID, "vipera_gtx", created));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("careerProgress")
      .doc("downtown_sprint")
      .set(makeCareerProgress(BOB_UID, "downtown_sprint", created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).update({
      credits: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("garageCars").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("careerProgress").get()
  );
});

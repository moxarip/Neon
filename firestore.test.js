const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfile(uid, ts) {
  return {
    userId: uid,
    callsign: "NeonAce",
    level: 1,
    xp: 0,
    credits: 200,
    totalMatches: 0,
    wins: 0,
    losses: 0,
    goals: 0,
    assists: 0,
    saves: 0,
    bestTimeChallengeGoals: 0,
    tournamentsWon: 0,
    unlockedItems: ["outfit_cyan_pulse"],
    selectedOutfit: "outfit_cyan_pulse",
    selectedSkate: "skate_aero",
    selectedTrail: "trail_cyan",
    selectedBall: "ball_core",
    selectedGoalFx: "gfx_burst",
    selectedArena: "arena_cyber_dome",
    selectedCelebration: "celeb_spin",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validLeaderboard(uid, ts) {
  return {
    userId: uid,
    callsign: "NeonAce",
    rating: 450,
    level: 3,
    wins: 4,
    goals: 12,
    bestTimeChallenge: 7,
    selectedOutfit: "outfit_cyan_pulse",
    isPublic: true,
    createdAt: ts,
    updatedAt: ts,
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

test("Unauthenticated user cannot read profiles or leaderboard", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("leaderboard").where("isPublic", "==", true).get()
  );
});

test("Authenticated user can create and read own profile, but cannot read Bob's", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID, now))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Shadow update with ghost field on profile is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);
  const payload = { ...validProfile(ALICE_UID, now), isAdmin: true };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(payload));
});

test("Authenticated user can create match record in own subcollection, update rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);
  const matchRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("matches")
    .doc("match_001");

  await assertSucceeds(
    matchRef.set({
      mode: "quick_match",
      difficulty: "normal",
      playerScore: 4,
      opponentScore: 2,
      won: true,
      goalsScored: 3,
      assists: 1,
      saves: 2,
      xpEarned: 180,
      creditsEarned: 120,
      overtime: false,
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("matches").get()
  );
  await assertFails(matchRef.update({ playerScore: 99 }));
});

test("Leaderboard entry: owner can write, signed-in user can query isPublic==true, cross-user write rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb
      .collection("leaderboard")
      .doc(ALICE_UID)
      .set(validLeaderboard(ALICE_UID, now))
  );

  await assertSucceeds(
    bobDb.collection("leaderboard").where("isPublic", "==", true).get()
  );

  await assertFails(bobDb.collection("leaderboard").get());

  await assertFails(
    bobDb
      .collection("leaderboard")
      .doc(ALICE_UID)
      .set(validLeaderboard(ALICE_UID, now))
  );
});

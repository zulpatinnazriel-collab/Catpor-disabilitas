const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_worker_123";
const BOB_UID = "bob_worker_456";
const ADMIN_UID = "admin_zulpatin_789";
const ADMIN_EMAIL = "zulpatinnazriel@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

// --- ACTIVITIES TESTS ---

test("Unauthenticated user: cannot read activities", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("activities").get());
  await assertFails(unauthDb.collection("activities").doc("act_1").get());
});

test("Worker: can create and read own activity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@perusahaan.com", email_verified: true }).firestore();
  await assertSucceeds(
    aliceDb.collection("activities").doc("act_alice_1").set({
      userId: ALICE_UID,
      workerName: "Alice Pratama",
      workerId: "FLD-001",
      date: "2026-11-09",
      title: "Inspeksi Tiang Jaringan FO",
      category: "Inspeksi",
      location: "Jl. Sudirman No. 45",
      startTime: "09:00",
      endTime: "11:30",
      status: "COMPLETED",
      notes: "Kondisi tiang aman dan kabel rapi",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("activities").doc("act_alice_1").get());
});

test("Worker: cannot read another worker's activity", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("activities").doc("act_bob_secret").set({
      userId: BOB_UID,
      workerName: "Bob Santoso",
      date: "2026-11-09",
      title: "Maintenance Server Lapangan",
      category: "Maintenance",
      location: "Data Center Cikarang",
      status: "IN_PROGRESS",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@perusahaan.com", email_verified: true }).firestore();
  await assertFails(aliceDb.collection("activities").doc("act_bob_secret").get());
});

test("Admin (zulpatinnazriel@gmail.com): CAN read any worker's activity", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("activities").doc("act_bob_for_admin").set({
      userId: BOB_UID,
      workerName: "Bob Santoso",
      date: "2026-11-09",
      title: "Pemasangan Kabel FO Baru",
      category: "Instalasi",
      location: "Kuningan",
      status: "IN_PROGRESS",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  await assertSucceeds(adminDb.collection("activities").doc("act_bob_for_admin").get());
});

test("Admin (zulpatinnazriel@gmail.com): CAN list all activities across all workers", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("activities").doc("act_1").set({
      userId: ALICE_UID,
      workerName: "Alice Pratama",
      date: "2026-11-09",
      title: "Survey A",
      category: "Survey",
      location: "Jakarta",
      status: "COMPLETED",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    await context.firestore().collection("activities").doc("act_2").set({
      userId: BOB_UID,
      workerName: "Bob Santoso",
      date: "2026-11-09",
      title: "Survey B",
      category: "Survey",
      location: "Bekasi",
      status: "COMPLETED",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  await assertSucceeds(adminDb.collection("activities").get());
});

test("Spoofing admin with unverified email fails", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("activities").doc("act_secure").set({
      userId: BOB_UID,
      workerName: "Bob",
      date: "2026-11-09",
      title: "Rahasia",
      category: "Lainnya",
      location: "Jakarta",
      status: "PENDING",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  // Attempting with admin email but email_verified = false
  const fakeAdminDb = testEnv.authenticatedContext("fake_user", { email: ADMIN_EMAIL, email_verified: false }).firestore();
  await assertFails(fakeAdminDb.collection("activities").doc("act_secure").get());
});

// --- EXCEL REPORTS TESTS ---

test("Admin CAN read all uploaded Excel reports", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("excel_reports").doc("rep_bob_1").set({
      userId: BOB_UID,
      workerName: "Bob Santoso",
      fileName: "Laporan_Bob.xlsx",
      fileType: "XLSX",
      reportDate: "2026-11-09",
      totalRows: 10,
      status: "UPLOADED",
      notes: "Laporan hari Senin",
      fileSize: 34000,
      rawContentSummary: "10 baris",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  // Alice cannot read Bob's report
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@perusahaan.com", email_verified: true }).firestore();
  await assertFails(aliceDb.collection("excel_reports").doc("rep_bob_1").get());

  // Admin CAN read Bob's report
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  await assertSucceeds(adminDb.collection("excel_reports").doc("rep_bob_1").get());
  await assertSucceeds(adminDb.collection("excel_reports").get());
});

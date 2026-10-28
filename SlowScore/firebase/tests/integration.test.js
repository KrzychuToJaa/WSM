"use strict";
const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { initializeTestEnvironment, assertSucceeds, assertFails } = require("@firebase/rules-unit-testing");
const { doc, getDoc, setDoc, collection, getDocs, query, where } = require("firebase/firestore");
let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: "demo-slowscore", firestore: {
    host: "127.0.0.1", port: 8080, rules: readFileSync("firestore.rules", "utf8") } });
  await env.clearFirestore();
});
after(async () => { await env?.cleanup(); });
async function account(name) {
  const response = await fetch("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key", {
    method: "POST", headers: { "Content-Type": "application/json" }, signal: AbortSignal.timeout(15000),
    body: JSON.stringify({ email: `${name}-${Date.now()}@example.com`, password: "test-password-123", returnSecureToken: true }) });
  assert.equal(response.status, 200); const data = await response.json();
  return { uid: data.localId, token: data.idToken };
}
async function call(user, action, args = {}) {
  const response = await fetch("http://127.0.0.1:5001/demo-slowscore/europe-central2/score", {
    method: "POST", headers: { "Content-Type": "application/json", ...(user ? { Authorization: `Bearer ${user.token}` } : {}) },
    signal: AbortSignal.timeout(15000), body: JSON.stringify({ data: { action, ...args } }) });
  return response.json();
}
async function ok(user, action, args) { const data = await call(user, action, args); assert(!data.error, JSON.stringify(data)); return data.result; }
async function denied(user, action, args) { const data = await call(user, action, args); assert(data.error, JSON.stringify(data)); }
test("callable lifecycle, permissions, concurrent scores and Firestore rules", { timeout: 120000 }, async () => {
  const owner = await account("owner"), editor = await account("editor"), viewer = await account("viewer");
  const ownerDb = env.authenticatedContext(owner.uid).firestore(), editorDb = env.authenticatedContext(editor.uid).firestore();
  const viewerDb = env.authenticatedContext(viewer.uid).firestore(), guest = env.unauthenticatedContext().firestore();
  const args = { name: "Campus Cup", sport: "FOOTBALL", startDate: Date.now() + 3600000, teamNames: ["AA", "BB", "CC", "DD"], ownerId: viewer.uid };
  await denied(null, "create", args); await denied(owner, "create", args);
  await ok(owner, "profile", { username: "organizer" }); await ok(editor, "profile", { username: "editor" });
  await denied(viewer, "profile", { username: "organizer" }); await ok(viewer, "profile", { username: "viewer" });
  await ok(owner, "profile", { username: "ORGANIZER" }); await denied(owner, "profile", { username: "renamed" });
  await assertSucceeds(getDoc(doc(ownerDb, `users/${owner.uid}`)));
  await assertFails(getDoc(doc(viewerDb, `users/${owner.uid}`)));
  await assertFails(getDocs(collection(ownerDb, "users")));
  await assertFails(getDoc(doc(ownerDb, "usernames/organizer")));
  const { id } = await ok(owner, "create", args);
  const ref = db => doc(db, `tournaments/${id}`);
  const read = async () => (await getDoc(ref(ownerDb))).data();
  assert.equal((await read()).ownerId, owner.uid);
  await assertFails(getDoc(ref(guest))); await assertFails(getDoc(ref(viewerDb)));
  await assertFails(setDoc(ref(ownerDb), { ownerId: viewer.uid }, { merge: true }));
  await denied(viewer, "publish", { id }); await denied(editor, "shuffle", { id });
  await ok(owner, "addEditor", { id, username: "editor" });
  await assertSucceeds(getDoc(ref(editorDb)));
  await assertSucceeds(getDocs(query(collection(editorDb, "tournaments"), where("editorIds", "array-contains", editor.uid))));
  await denied(editor, "addEditor", { id, username: "viewer" }); await denied(owner, "addEditor", { id, username: "missing" });
  await ok(owner, "shuffle", { id }); await ok(owner, "publish", { id });
  await assertSucceeds(getDoc(ref(guest)));
  await assertSucceeds(getDocs(query(collection(guest, "tournaments"), where("status", "in", ["UPCOMING", "ONGOING"]))));
  await assertFails(getDocs(collection(guest, "tournaments")));
  await ok(viewer, "follow", { id, enabled: true });
  await assertSucceeds(getDoc(doc(viewerDb, `users/${viewer.uid}/followed/${id}`)));
  await assertFails(getDoc(doc(ownerDb, `users/${viewer.uid}/followed/${id}`)));
  const first = (await read()).matches.find(m => m.id === "0_0");
  const score = { id, matchId: first.id, version: first.version,
    result: { homeScore: 2, awayScore: 0, winnerId: first.homeTeamId, sets: [] } };
  await denied(viewer, "result", score); await assertFails(setDoc(ref(editorDb), { matches: [] }, { merge: true }));
  const concurrent = await Promise.all([call(editor, "result", score), call(editor, "result", score)]);
  assert.equal(concurrent.filter(response => response.error).length, 1);
  await ok(owner, "removeEditor", { id, userId: editor.uid });
  await denied(editor, "result", { ...score, version: first.version + 1 });
  for (const matchId of ["0_1", "1_0"]) {
    const match = (await read()).matches.find(m => m.id === matchId);
    await ok(owner, "result", { id, matchId, version: match.version,
      result: { homeScore: 2, awayScore: 0, winnerId: match.homeTeamId, sets: [] } });
  }
  const completed = await read(); assert.equal(completed.status, "COMPLETED");
  await assertFails(getDoc(ref(guest))); await assertSucceeds(getDoc(ref(viewerDb)));
  await ok(owner, "archive", { id }); assert.equal((await read()).endDate, completed.endDate);
  await assertSucceeds(getDocs(query(collection(viewerDb, "tournaments"), where("status", "in", ["COMPLETED", "ARCHIVED"]))));
  await ok(viewer, "follow", { id, enabled: false });
  assert.equal((await getDoc(doc(viewerDb, `users/${viewer.uid}/followed/${id}`))).exists(), false);
});

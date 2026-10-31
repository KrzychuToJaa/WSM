"use strict";
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { randomUUID } = require("node:crypto");
const rules = require("./rules");
initializeApp();
const db = getFirestore();
// Wspólny projekt deweloperski: dostęp wymaga konta i uprawnień, bez rejestrowania urządzeń.
exports.score = onCall({ region: "europe-central2", enforceAppCheck: false, maxInstances: 10 }, async request => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Zaloguj się, aby kontynuować.");
  const uid = request.auth.uid, args = request.data;
  try {
    rules.check(args && typeof args === "object" && typeof args.action === "string", "Brak operacji.");
    const now = Date.now(), profileRef = db.doc(`users/${uid}`);
    if (args.action === "profile") {
      const name = rules.username(args.username), nameRef = db.doc(`usernames/${name}`);
      await db.runTransaction(async tx => {
        const [profile, reserved] = await Promise.all([tx.get(profileRef), tx.get(nameRef)]);
        rules.check(!profile.exists || profile.data().username === name, "Nie można zmienić loginu.", "failed-precondition");
        rules.check(!reserved.exists || reserved.data().userId === uid, "Ten login jest zajęty.", "already-exists");
        tx.set(profileRef, { userId: uid, username: name }); tx.set(nameRef, { userId: uid, username: name });
      });
      return { ok: true };
    }
    const profile = await profileRef.get();
    rules.check(profile.exists, "Dokończ profil użytkownika.", "failed-precondition");
    if (args.action === "create") {
      rules.check(["FOOTBALL", "BASKETBALL", "VOLLEYBALL"].includes(args.sport), "Nieprawidłowa dyscyplina.");
      rules.check(Array.isArray(args.teamNames) && args.teamNames.length <= 16, "Nieprawidłowe drużyny.");
      const teams = args.teamNames.map(name => ({ id: randomUUID(), name: rules.text(name, 2, 40) }));
      const name = rules.text(args.name, 3, 80); rules.draft(name, args.startDate, teams, now);
      const ref = db.collection("tournaments").doc();
      await ref.create({ id: ref.id, name, sport: args.sport, ownerId: uid, startDate: args.startDate,
        endDate: null, status: "DRAFT", teams, matches: rules.bracket(teams), editors: {}, editorIds: [],
        version: 0, createdAt: now, updatedAt: now });
      return { id: ref.id };
    }
    const id = rules.text(args.id, 1, 100); rules.check(!id.includes("/"), "Nieprawidłowy identyfikator.");
    const ref = db.doc(`tournaments/${id}`);
    await db.runTransaction(async tx => {
      const doc = await tx.get(ref); rules.check(doc.exists, "Nie znaleziono turnieju.", "not-found");
      const item = doc.data();
      if (args.action === "follow") {
        rules.check(item.status !== "DRAFT", "Turniej nie został opublikowany.", "failed-precondition");
        rules.check(typeof args.enabled === "boolean", "Nieprawidłowa wartość obserwowania.");
        const followRef = profileRef.collection("followed").doc(id);
        if (args.enabled) tx.set(followRef, { followedAt: now }); else tx.delete(followRef);
        return;
      }
      const options = { ...args };
      if (args.action === "addEditor") {
        rules.owner(item, uid);
        const name = rules.username(args.username), editor = await tx.get(db.doc(`usernames/${name}`));
        rules.check(editor.exists, "Nie znaleziono użytkownika.", "not-found"); options.editor = editor.data();
      }
      tx.set(ref, rules.change(item, uid, args.action, options, now));
    });
    return { ok: true };
  } catch (error) {
    if (error instanceof rules.RuleError) throw new HttpsError(error.code, error.message);
    console.error("Score operation failed", error.code || error.name);
    throw new HttpsError("internal", "Nie udało się zapisać zmian. Spróbuj ponownie.");
  }
});

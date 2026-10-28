"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const r = require("../rules");
const now = 1000000;
function cup(count = 4, sport = "FOOTBALL") {
  const teams = Array.from({ length: count }, (_, i) => ({ id: `team${i}`, name: `Team ${i}` }));
  return { id: "cup", name: "Campus Cup", sport, ownerId: "owner", startDate: now + 1000,
    endDate: null, status: "DRAFT", teams, matches: r.bracket(teams), editors: {}, editorIds: [], version: 0 };
}
function result(item, matchId, uid = "owner") {
  const match = item.matches.find(m => m.id === matchId);
  return r.change(item, uid, "result", { matchId, version: match.version,
    result: { homeScore: 2, awayScore: 0, winnerId: match.homeTeamId, sets: [] } }, now);
}
test("profile and draft validation", () => {
  assert.equal(r.username(" OWNER_1 "), "owner_1");
  for (const name of [null, "aa", "a b", "ąbc"]) assert.throws(() => r.username(name), r.RuleError);
  const item = cup(); r.draft(item.name, item.startDate, item.teams, now);
  for (const teams of [[], item.teams.slice(1), [...item.teams.slice(0, 3), item.teams[0]]])
    assert.throws(() => r.draft(item.name, item.startDate, teams, now));
  assert.throws(() => r.draft(item.name, now, item.teams, now));
});
test("brackets cover 4, 8 and 16 teams, including shuffle", () => {
  for (const size of [4, 8, 16]) for (const shuffle of [false, true]) {
    const item = cup(size), matches = r.bracket(item.teams, shuffle);
    assert.equal(matches.length, size - 1);
    assert.equal(new Set(matches.filter(m => m.round === 0).flatMap(m => [m.homeTeamId, m.awayTeamId])).size, size);
    assert(matches.filter(m => m.round > 0).every(m => m.homeTeamId === null && m.awayTeamId === null));
  }
});
test("only owner manages tournament", () => {
  for (const action of ["shuffle", "publish", "archive", "addEditor", "removeEditor"])
    for (const uid of ["editor", "viewer", null]) assert.throws(() => r.change(cup(), uid, action, {}, now), { code: "permission-denied" });
});
test("publishing freezes bracket, archive preserves completion date", () => {
  const published = r.change(cup(), "owner", "publish", {}, now);
  assert.equal(published.status, "UPCOMING");
  assert.throws(() => r.change(published, "owner", "shuffle", {}, now));
  assert.throws(() => r.change(published, "owner", "publish", {}, now));
  assert.throws(() => r.change(published, "owner", "archive", {}, now));
  const archived = r.change({ ...published, status: "COMPLETED", endDate: 99 }, "owner", "archive", {}, now);
  assert.equal(archived.endDate, 99); assert.equal(archived.status, "ARCHIVED");
});
test("owner grants and revokes editor rights", () => {
  let item = r.change(cup(), "owner", "addEditor", { editor: { userId: "editor", username: "editor" } }, now);
  item = r.change(item, "owner", "publish", {}, now);
  item = result(item, "0_0", "editor"); assert.equal(item.status, "ONGOING");
  item = r.change(item, "owner", "removeEditor", { userId: "editor" }, now);
  assert.deepEqual(item.editorIds, []); assert.throws(() => result(item, "0_1", "editor"), { code: "permission-denied" });
});
test("full eight-team tournament advances winners and rejects stale scores", () => {
  let item = r.change(cup(8), "owner", "publish", {}, now);
  const before = item; item = result(item, "0_0");
  assert.equal(before.matches[0].result, null);
  assert.throws(() => r.change(item, "owner", "result", { matchId: "0_0", version: 0 }, now), { code: "aborted" });
  for (const id of ["0_1", "0_2", "0_3", "1_0", "1_1", "2_0"]) item = result(item, id);
  assert.equal(item.status, "COMPLETED"); assert.equal(item.endDate, now);
});
test("missing participants and downstream results prevent corrections", () => {
  let item = r.change(cup(8), "owner", "publish", {}, now);
  assert.throws(() => result(item, "1_0"));
  for (const id of ["0_0", "0_1", "1_0"]) item = result(item, id);
  assert.throws(() => result(item, "0_0"), { code: "failed-precondition" });
});
test("point scores enforce sport, winner and safe integer constraints", () => {
  const match = cup().matches[0], valid = { homeScore: 2, awayScore: 0, winnerId: match.homeTeamId, sets: [] };
  r.score("FOOTBALL", match, valid);
  r.score("FOOTBALL", match, { ...valid, homeScore: 0 });
  for (const bad of [{ ...valid, homeScore: -1 }, { ...valid, homeScore: 1.2 }, { ...valid, awayScore: 1000 },
    { ...valid, winnerId: match.awayTeamId }, { ...valid, ownerId: "attacker" }]) assert.throws(() => r.score("FOOTBALL", match, bad));
  assert.throws(() => r.score("BASKETBALL", match, { ...valid, homeScore: 0 }));
});
test("volleyball validates three and five sets and stopping at three wins", () => {
  const match = cup().matches[0], win = { home: 25, away: 20 }, lose = { home: 20, away: 25 };
  const score = sets => ({ homeScore: 3, awayScore: sets.length - 3, winnerId: match.homeTeamId, sets });
  r.score("VOLLEYBALL", match, score([win, win, win]));
  r.score("VOLLEYBALL", match, score([win, lose, win, lose, { home: 17, away: 15 }]));
  for (const sets of [[win, win], [win, win, win, lose], [win, win, { home: 26, away: 20 }],
    [win, lose, win, lose, { home: 15, away: 14 }]]) assert.throws(() => r.score("VOLLEYBALL", match, score(sets)));
});

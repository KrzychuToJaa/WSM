"use strict";
const { randomInt } = require("node:crypto");
class RuleError extends Error {
  constructor(message, code = "invalid-argument") { super(message); this.code = code; }
}
function check(condition, message, code) { if (!condition) throw new RuleError(message, code); }
function text(value, min, max) {
  check(typeof value === "string" && value.trim().length >= min && value.trim().length <= max, "Nieprawidłowy tekst.");
  return value.trim();
}
function username(value) {
  const name = text(value, 3, 24).toLowerCase();
  check(/^[a-z0-9_]{3,24}$/.test(name), "Login: 3–24 znaki, litery, cyfry i podkreślenie.");
  return name;
}
function integer(value, min = 0, max = 999) {
  check(Number.isSafeInteger(value) && value >= min && value <= max, "Nieprawidłowa liczba.");
}
function draft(name, startDate, teams, now) {
  text(name, 3, 80); integer(startDate, now + 1, Number.MAX_SAFE_INTEGER);
  check(Array.isArray(teams) && [4, 8, 16].includes(teams.length), "Wybierz 4, 8 lub 16 drużyn.");
  for (const team of teams) { text(team.id, 1, 100); text(team.name, 2, 40); }
  check(new Set(teams.map(t => t.id)).size === teams.length &&
    new Set(teams.map(t => t.name.trim().toLowerCase())).size === teams.length, "Drużyny muszą być różne.");
}
function bracket(teams, shuffle = false) {
  check(Array.isArray(teams) && [4, 8, 16].includes(teams.length), "Nieprawidłowa liczba drużyn.");
  const order = [...teams];
  if (shuffle) for (let i = order.length - 1; i > 0; i--) { const j = randomInt(i + 1); [order[i], order[j]] = [order[j], order[i]]; }
  const matches = [];
  for (let round = 0; round < Math.log2(order.length); round++) {
    for (let position = 0; position < (order.length >> (round + 1)); position++) {
      matches.push({ id: `${round}_${position}`, round, position,
        homeTeamId: round === 0 ? order[position * 2].id : null,
        awayTeamId: round === 0 ? order[position * 2 + 1].id : null, result: null, version: 0 });
    }
  }
  return matches;
}
function owner(item, uid) { check(item.ownerId === uid, "Tylko właściciel może wykonać tę operację.", "permission-denied"); }
function score(sport, match, result) {
  check(result && typeof result === "object" && !Array.isArray(result) &&
    Object.keys(result).every(key => ["homeScore", "awayScore", "winnerId", "sets"].includes(key)), "Nieprawidłowy wynik.");
  check(match.homeTeamId && match.awayTeamId && match.homeTeamId !== match.awayTeamId, "Brakuje uczestników meczu.", "failed-precondition");
  check([match.homeTeamId, match.awayTeamId].includes(result.winnerId), "Wybierz zwycięzcę meczu.");
  integer(result.homeScore); integer(result.awayScore);
  check(Array.isArray(result.sets), "Nieprawidłowe sety.");
  if (sport === "VOLLEYBALL") {
    check(result.sets.length >= 3 && result.sets.length <= 5, "Mecz musi mieć od 3 do 5 setów.");
    let home = 0, away = 0;
    result.sets.forEach((set, index) => {
      check(home < 3 && away < 3, "Mecz już zakończony.");
      check(set && typeof set === "object" && !Array.isArray(set) && Object.keys(set).every(key => ["home", "away"].includes(key)), "Nieprawidłowy set.");
      integer(set.home); integer(set.away);
      const high = Math.max(set.home, set.away), difference = Math.abs(set.home - set.away), target = index === 4 ? 15 : 25;
      check(high >= target && difference >= 2 && (high === target || difference === 2), "Nieprawidłowe punkty seta.");
      if (set.home > set.away) home++; else away++;
    });
    check(Math.max(home, away) === 3 && result.homeScore === home && result.awayScore === away, "Wynik nie zgadza się z setami.");
  } else {
    check(["FOOTBALL", "BASKETBALL"].includes(sport) && result.sets.length === 0, "Nieprawidłowa dyscyplina lub sety.");
    check(sport !== "BASKETBALL" || result.homeScore !== result.awayScore, "Koszykówka nie może zakończyć się remisem.");
  }
  if (result.homeScore !== result.awayScore) check(result.winnerId ===
    (result.homeScore > result.awayScore ? match.homeTeamId : match.awayTeamId), "Nieprawidłowy zwycięzca.");
}
function change(item, uid, action, args, now) {
  const next = structuredClone(item);
  if (action === "result") {
    check(uid === item.ownerId || Object.hasOwn(item.editors, uid), "Brak uprawnień do wpisywania wyników.", "permission-denied");
    check(["UPCOMING", "ONGOING"].includes(item.status), "Turniej nie jest aktywny.", "failed-precondition");
    const match = next.matches.find(m => m.id === args.matchId);
    check(match, "Nie znaleziono meczu.", "not-found"); integer(args.version, 0, Number.MAX_SAFE_INTEGER);
    check(match.version === args.version, "Wynik zmienił się. Odśwież dane.", "aborted");
    score(item.sport, match, args.result);
    const following = next.matches.find(m => m.round === match.round + 1 && m.position === Math.floor(match.position / 2));
    check(!following?.result, "Nie można zmienić wyniku po rozegraniu kolejnego meczu.", "failed-precondition");
    match.result = structuredClone(args.result); match.version++;
    if (following) { following[match.position % 2 === 0 ? "homeTeamId" : "awayTeamId"] = args.result.winnerId; following.version++; }
    next.status = following ? "ONGOING" : "COMPLETED"; next.endDate = following ? null : now;
  } else {
    owner(item, uid);
    switch (action) {
      case "shuffle":
        check(item.status === "DRAFT", "Losowanie dostępne tylko w szkicu.", "failed-precondition"); next.matches = bracket(item.teams, true); break;
      case "publish":
        check(item.status === "DRAFT", "Turniej został już opublikowany.", "failed-precondition");
        draft(item.name, item.startDate, item.teams, now); next.status = "UPCOMING"; break;
      case "archive":
        check(item.status === "COMPLETED", "Można archiwizować tylko zakończony turniej.", "failed-precondition"); next.status = "ARCHIVED"; break;
      case "addEditor":
        check(args.editor && args.editor.userId !== item.ownerId, "Właściciel ma już uprawnienia.");
        check(Object.keys(item.editors).length < 20 || Object.hasOwn(item.editors, args.editor.userId), "Maksymalnie 20 edytorów.");
        next.editors[args.editor.userId] = args.editor.username; break;
      case "removeEditor": text(args.userId, 1, 128); delete next.editors[args.userId]; break;
      default: throw new RuleError("Nieznana operacja.");
    }
  }
  next.editorIds = Object.keys(next.editors); next.version++; next.updatedAt = now;
  return next;
}
module.exports = { RuleError, check, text, username, integer, draft, bracket, owner, score, change };

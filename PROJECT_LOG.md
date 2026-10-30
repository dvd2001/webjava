# Projekt napló

Fullstack projekt: React + TypeScript + Vite (frontend), Java + Spring + Maven (backend), PostgreSQL (adatbázis).

A beszélgetések összefoglalóját időrendben ide naplózzuk.

---

## 2026-10-30

- Projekt indítása: a technológiai stack rögzítve (React + TypeScript + Vite, Java + Spring + Maven, PostgreSQL).
- Megállapodás: a beszélgetéseket ebbe a fájlba naplózzuk.

### Frontend MVP (kerékpár-kölcsönzés, egy állomás)

- Szerep: React + TS + Vite fejlesztő; csak frontend, tilos: bejelentkezés, külső szolgáltatás, backend/adatbázis.
- Elkészült a `frontend/` mappa (Vite + React 19 + TS), `npm run build` sikeres.
- Funkciók: felhasználóváltó (állomásvezető / bérlők, valódi login nélkül), biciklilista státusszal
  (elérhető / használatban / karbantartás alatt), státuszváltás (csak vezető; használatban lévő csak visszavétellel),
  új bicikli űrlap (csak vezető), bérlés (csak elérhető bicikli), visszavétel, díj = kezdődíj + megkezdett percek × percdíj.
- Üzleti logika: `src/store.ts` (tiszta függvények), `src/pricing.ts`; példaadatok: `src/data.ts`. Az állapot memóriában él.
- Indítás: `cd frontend; npm install; npm run dev`.

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

### Backend (Java 17 + Spring Boot 3.5 + Maven, in-memory)

- Mappa: `backend/`, csomag: `hu.kerekpar`. Tilos volt: adatbázis, bejelentkezés → in-memory tárolás, nincs hitelesítés.
- Clean architecture rétegek (ArchUnit teszt őrzi a függőségi irányt):
  - `domain` – `Bike`, `Rental`, `User`, `Tariff` (díj: kezdődíj + megkezdett percek × percdíj), domain kivételek; nincs keretrendszer-függés.
  - `application` – use case interfészek (`port.in`), kimeneti portok (`port.out`: repók, `ClockPort`, `UnitOfWork`), use case implementációk (`usecase`, Spring nélkül).
  - `adapter.in.web` – REST controllerek, DTO-k, egységes hibakezelés (`ApiError`), kérés-naplózó szűrő.
  - `adapter.out.memory` – in-memory repók, példaadatok (`DemoDataSeeder`), zár alapú `UnitOfWork`.
  - `config.BeanConfig` – egyetlen összekötési pont.
- API (felhasználóváltás: `X-User-Id` fejléc, nem hitelesítés; enumok kisbetűsek, pl. `in_use`):
  - `GET /api/users`, `GET /api/tariffs`, `GET /api/bikes`
  - `POST /api/bikes` (vezető), `PATCH /api/bikes/{id}/status` (vezető)
  - `POST /api/rentals` (bérlő), `POST /api/rentals/{id}/return`, `GET /api/rentals?status=all|active|finished`
  - Hibakódok: 400 érvénytelen, 403 jogosultság, 404 nincs találat, 409 üzleti szabály (pl. már bérelt bicikli).
- Naplózás: SLF4J/Logback, konzol + `backend/logs/kerekpar-backend.log` (UTF-8); use case események, hibák, minden API kérés.
- Frontend: Vite dev proxy `/api` → `http://localhost:8080` (a frontend store még nem hívja az API-t, következő lépés).
- Futtatás: `cd backend; .\mvnw.cmd verify` (23 teszt zöld), `java -jar target\kerekpar-berles-backend-0.1.0.jar` → http://localhost:8080.

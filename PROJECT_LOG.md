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

### PostgreSQL séma és Flyway migráció

- Új fájlok: `backend/src/main/resources/db/migration/V1__create_schema.sql` (app_user, tariff, bike, rental),
  `V2__seed_reference_data.sql` (3 tarifa, 4 példafelhasználó). Alkalmazott migrációt ne írjunk át, új változás = új `V3__...` fájl.
- Constraint-ek: PK/FK (CASCADE nélkül), CHECK a státuszra/típusra/szerepkörre/névre/díjakra, `uq_bike_name_lower`
  (kis/nagybetűtől független névegyediség), `uq_rental_one_active_per_bike` (részleges unique index: egy biciklinek legfeljebb egy aktív bérlése),
  `ck_rental_finished_consistent` (ended_at és fee együtt vagy sehol).
- Futtatás: csak a `flyway-maven-plugin` (pom.xml), a runtime még in-memory, nincs JPA/DataSource.
  `.\mvnw.cmd flyway:migrate -Ddb.url=jdbc:postgresql://localhost:5432/kerekpar -Ddb.user=kerekpar -Ddb.password=...`
- Ellenőrzés: üres PostgreSQL 16.15-ön (helyi, Docker nélkül) lefutott, a history 2 sikeres sort tartalmaz, újrafuttatás idempotens;
  14 negatív próba (érvénytelen értékek, dupla aktív bérlés, törlés hivatkozott soron) mind elutasítva.
- Nyitott: a bike.status és az aktív bérlés konzisztenciáját (IN_USE ⇔ van aktív bérlés) a use case tartja, DB constraint nem; az azonosítók
  a DB-ben bigint identity (az in-memory adapter "b1"/"r1" szövegesei helyett), a JPA adapternél leképezés kell.

### PostgreSQL/JPA adapter (`hu.kerekpar.adapter.out.persistence`)

- A négy repository port (Bike, Rental, User, Tariff) + `UnitOfWork` PostgreSQL/JPA megvalósítása; domain és application érintetlen.
- Profil: alapértelmezett = in-memory (DB autokonfigurációk kizárva), `--spring.profiles.active=postgres` = PostgreSQL
  (`KEREKPAR_DB_URL`, `KEREKPAR_DB_USER`, `KEREKPAR_DB_PASSWORD`; Flyway induláskor migrál, Hibernate csak `validate`, nincs `ddl-auto` séma-módosítás).
- Szerkezet: külön `*JpaEntity` (asszociáció nélkül, skalár külső kulcsok → nincs EAGER/cascade), explicit statikus `*Mapper`-ek,
  csomag-privát Spring Data repók, `PostgresPersistenceConfig` köti össze. ArchUnit szabály: JPA/Hibernate/Spring Data csak ebben a csomagban.
- Eltérések: domain azonosító "b3" ↔ DB bigint 3 (`EntityIds`, az API azonosítói nem változnak); `nextId()` az identity-sorozatból;
  `V3__prepare_jpa_adapter.sql`: identity BY DEFAULT + `version` oszlop (optimista zár bike/rental); a demo seeder a postgres profilban ki van kapcsolva.
- Tranzakció: írási use case-ek a `PostgresUnitOfWork`-ön (`TransactionTemplate`) át egy tranzakcióban; olvasás hívásonként csak olvasható tranzakció.
  Constraint-sértés / elavult verzió → `ConflictException` (409): `BIKE_NAME_TAKEN`, `BIKE_NOT_AVAILABLE`, `CONCURRENT_MODIFICATION`.
- Tesztek: beágyazott PostgreSQL-lel (zonky, Docker nélkül; a binárisa 14.x) 40 teszt zöld; valós PG 16-on is kipróbálva.
- Integrációs tesztek (`FlywayMigrationTest`, `RentalRoundTripTest`): a Flyway egy teljesen üres, új adatbázison fut le (3 migráció);
  a bérlés + bicikli + bérlő mentés → flush → `EntityManager.clear()` → újraolvasás után egyezik az eredetivel, a betöltést a Hibernate statisztika
  igazolja (nem első szintű cache); véletlen tesztsorrend; mutációs próbával ellenőrizve (hibás mapper → elbukik).
- Hibás esetek (`PersistenceFailureTest`): (1) második aktív bérlés ugyanarra a biciklire → a DB `uq_rental_one_active_per_bike` constraintje
  utasítja el flush-nál (SQLSTATE 23505), a végállapotban 1 sor; (2) a `RentBikeService` a bicikli státuszát flush-olja, a bérlés beszúrása
  constraint miatt hibázik → teljes rollback (bicikli AVAILABLE/version 0, nincs új bérlés). Tranzakció nélküli mutánson a rollback-teszt elbukik.

### OpenAPI szerződés, szerződés-első backend, frontend bekötés

- Szerződés: `api/openapi.yaml` (OpenAPI 3.0.3, 8 művelet: users, tariffs, bikes GET/POST, bikes/{id}/status PATCH, rentals GET/POST, rentals/{id}/return POST;
  `ApiError` {code,message}; `X-User-Id` fejléc a felhasználóváltáshoz, hitelesítés nincs).
- Backend: az `openapi-generator-maven-plugin` (spring, interfaceOnly) generálja az `*Api` interfészeket és a `*Dto` modelleket (`target/generated-sources`);
  a controllerek ezeket implementálják, `ApiMapper` képez a domain ↔ DTO között. `OpenApiContractTest`: a futó alkalmazás pontosan a spec végpontjait szolgálja ki.
  43 backend teszt zöld.
- HTTP ellenőrzés PostgreSQL 16 ellen (üres adatbázis, Flyway V1-V3, `--spring.profiles.active=postgres`): teljes folyamat curl-lel (felvétel, bérlés,
  ütközések 409, jogosultságok 403, visszavétel díjjal, hibakódok), a sorok a táblákban egyeztek a válaszokkal.
- Frontend: `npm run gen:api` az `api/openapi.yaml`-ból generál TypeScript típusokat (`src/api/schema.d.ts`); `src/api/client.ts` a típusos kliens,
  a memóriabeli logika (store/data) megszűnt. A felület a Vite proxyn át (`/api` → :8080) a backendet használja, 5 mp-enként frissít, kezeli a betöltés/hiba/üres állapotot.
- Böngészős ellenőrzés (Playwright, valódi PostgreSQL-lel): bérlés, ütköző név (409 üzenet), státuszváltás, felhasználóváltás és szerepkörök, visszavétel
  (E-bike-01: 500 + 1 megkezdett perc × 35 = 535 Ft), majd az adatbázis sorai egyeztek; backend leállításakor hibasáv, visszatéréskor magától helyreállt.
- Futtatás: backend `java -jar backend\target\kerekpar-berles-backend-0.1.0.jar --spring.profiles.active=postgres` (KEREKPAR_DB_URL/USER/PASSWORD),
  alapértelmezetten (profil nélkül) in-memory; frontend `cd frontend; npm run dev`.
- Nyitott (a review-ból): a `nextId()` ütközhet kézzel beszúrt azonosítóval (a `save()` ilyenkor felülír), N+1 a bérléslistában,
  a domain UTF-16 hossza vs DB kódpont-hossz (emoji név 500), a névegyediség a DB lokalizációjától függ.

### Hitelesítés és jogosultság (Spring Security, JWT)

- Szerződés: `POST /api/auth/login` (felhasználónév + jelszó → Bearer JWT, HS256, 30 perc); minden más végpont `Authorization: Bearer` fejlécet kér.
  Az `X-User-Id` fejléc megszűnt, az actor kizárólag a token `sub` claimjéből jön (`CurrentActor`).
- Útvonal egy kérésnél: `SecurityFilterChain` (`config/SecurityConfig`: állapotmentes, CSRF off, CORS, kérés-matcherek, `anyRequest().denyAll()`)
  → `BearerTokenAuthenticationFilter` + `JwtDecoder` (aláírás, lejárat, kiállító) → `JwtAuthenticationConverter` (`role` claim → `ROLE_MANAGER|ROLE_RENTER`)
  → `AuthenticationEntryPoint` 401 / `AccessDeniedHandler` 403 (`ApiSecurityErrorHandlers`, szerződés szerinti JSON) → controller (`BikeController`, `RentalController`, `ReferenceDataController`)
  → használati eset (`CurrentActor.id()` mint `actorId` a Command/Query-ben) → `ActorGuard` (a felhasználó és a szerepkör betöltése a tárolóból) → repository.
- Szerepkör-ellenőrzés két helyen: (1) URL-szinten a `SecurityFilterChain` (GET /api/users: manager; POST /api/bikes, PATCH /api/bikes/*/status: manager; POST /api/rentals: renter),
  (2) használati eset szinten az `ActorGuard` a tárolt szerepkör alapján (a token `role` claimje önmagában nem döntő).
- Tulajdonjog: `memberId` az URL-ben nincs; a megfelelője a `rentalId`: `ReturnBikeService` csak a bérlő saját bérlését engedi (vagy managert), különben 403 `NOT_RENTAL_OWNER`.
  A `GET /api/rentals` találatait a `ListRentalsService` szűri a hívóra.
- Bejelentkezés: `LoginService` (egységes hibaüzenet, mindig fut hash-ellenőrzés, hossz-korlát), `BcryptPasswordHasher`, `JwtTokenIssuer`, `LoginAttemptLimiter` (5 sikertelen kísérlet → 15 perc zárolás, 429).
- Jelszavak: V4 migráció (username + bcrypt hash oszlop, hash alapból NULL). A fejlesztői demo jelszavak (anna/peter/eszter/gabor, jelszó a `kerekpar.demo-users.password`) csak
  az ismételhető `db/dev/R__dev_demo_credentials.sql`-ben vannak, amit az `application-postgres.properties` tölt be; élesben a `spring.flyway.locations`-ből el kell hagyni a `classpath:db/dev` részt.
- JWT kulcs: `KEREKPAR_JWT_SECRET` (min. 32 bájt); ha hiányzik, indításkor véletlen kulcs készül (figyelmeztetéssel), nincs commitolt kulcs.
- Frontend (demó): nincs bejelentkező űrlap, az eredeti felhasználóváltó maradt. A váltó a demo felhasználóval (`src/demoUsers.ts`: anna/peter/eszter/gabor, jelszó: `VITE_DEMO_PASSWORD`,
  alapértelmezetten a backend fejlesztői demo jelszava) csendben bejelentkezik, így a backend minden kérést valódi tokennel hitelesít. A token csak memóriában él,
  lejárat előtt 60 mp-cel és 401 esetén csendben megújul. Valódi alkalmazásban bejelentkező űrlap kellene, és a jelszó nem kerülhetne a kliensbe.
- Tesztek: 209 backend teszt (domain, application, in-memory és biztonsági adapterek, web, ArchUnit, PostgreSQL integráció); a szerepkör-mátrix, tokenhamisítás és actor-hamisítás tesztek a `SecurityTest`-ben.

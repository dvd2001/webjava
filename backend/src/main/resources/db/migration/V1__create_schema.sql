-- Kerékpár állomás kölcsönzés: alapséma.
-- Az enum jellegű oszlopok varchar + CHECK formában vannak (egyszerűbb később bővíteni, mint a PG enum típust).
-- A külső kulcsokon nincs ON DELETE CASCADE: hivatkozott sor törlése hibát ad.

CREATE TABLE app_user (
    id   bigint GENERATED ALWAYS AS IDENTITY,
    name varchar(100) NOT NULL,
    role varchar(20)  NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT ck_app_user_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_app_user_role CHECK (role IN ('MANAGER', 'RENTER'))
);

CREATE TABLE tariff (
    bike_type  varchar(20) NOT NULL,
    base_fee   bigint      NOT NULL,
    per_minute bigint      NOT NULL,
    CONSTRAINT pk_tariff PRIMARY KEY (bike_type),
    CONSTRAINT ck_tariff_bike_type CHECK (bike_type IN ('CITY', 'MOUNTAIN', 'ELECTRIC')),
    CONSTRAINT ck_tariff_base_fee_non_negative CHECK (base_fee >= 0),
    CONSTRAINT ck_tariff_per_minute_non_negative CHECK (per_minute >= 0)
);

CREATE TABLE bike (
    id     bigint GENERATED ALWAYS AS IDENTITY,
    name   varchar(50) NOT NULL,
    type   varchar(20) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT pk_bike PRIMARY KEY (id),
    CONSTRAINT fk_bike_tariff FOREIGN KEY (type) REFERENCES tariff (bike_type),
    CONSTRAINT ck_bike_name_length CHECK (char_length(name) BETWEEN 2 AND 50),
    CONSTRAINT ck_bike_name_trimmed CHECK (name = btrim(name)),
    CONSTRAINT ck_bike_status CHECK (status IN ('AVAILABLE', 'IN_USE', 'MAINTENANCE'))
);

-- A bicikli neve kis- és nagybetűtől függetlenül egyedi (mint az AddBikeService-ben).
CREATE UNIQUE INDEX uq_bike_name_lower ON bike (lower(name));

CREATE TABLE rental (
    id         bigint GENERATED ALWAYS AS IDENTITY,
    bike_id    bigint      NOT NULL,
    user_id    bigint      NOT NULL,
    started_at timestamptz NOT NULL,
    ended_at   timestamptz,
    fee        bigint,
    CONSTRAINT pk_rental PRIMARY KEY (id),
    CONSTRAINT fk_rental_bike FOREIGN KEY (bike_id) REFERENCES bike (id),
    CONSTRAINT fk_rental_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_rental_finished_consistent CHECK (
        (ended_at IS NULL AND fee IS NULL) OR (ended_at IS NOT NULL AND fee IS NOT NULL)
    ),
    CONSTRAINT ck_rental_fee_non_negative CHECK (fee IS NULL OR fee >= 0),
    CONSTRAINT ck_rental_period CHECK (ended_at IS NULL OR ended_at >= started_at)
);

-- Egy biciklinek egyszerre legfeljebb egy aktív bérlése lehet (bérelt bicikli nem adható ki újra).
CREATE UNIQUE INDEX uq_rental_one_active_per_bike ON rental (bike_id) WHERE ended_at IS NULL;

-- A külső kulcsok indexei a bérléstörténeti lekérdezésekhez.
CREATE INDEX ix_rental_bike_id ON rental (bike_id);
CREATE INDEX ix_rental_user_id ON rental (user_id);

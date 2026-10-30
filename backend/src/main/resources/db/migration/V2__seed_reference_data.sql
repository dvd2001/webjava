-- Referenciaadatok: tarifák és példafelhasználók (megegyeznek az in-memory adapterek adataival).
-- A felhasználók azonosítói az identity sorrendből adódnak (1..4).

INSERT INTO tariff (bike_type, base_fee, per_minute) VALUES
    ('CITY', 300, 15),
    ('MOUNTAIN', 400, 20),
    ('ELECTRIC', 500, 35);

INSERT INTO app_user (name, role) VALUES
    ('Kovács Anna', 'MANAGER'),
    ('Nagy Péter', 'RENTER'),
    ('Szabó Eszter', 'RENTER'),
    ('Tóth Gábor', 'RENTER');

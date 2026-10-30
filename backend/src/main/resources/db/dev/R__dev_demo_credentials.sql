-- CSAK FEJLESZTÉSRE: ismert demo jelszó (lásd kerekpar.demo-users.password) a négy példafelhasználónak.
-- Ez a könyvtár nem része az éles migrációknak: a spring.flyway.locations értékéből el kell hagyni a classpath:db/dev részt.
-- Ismételhető migráció: csak azokat tölti ki, akiknek még nincs jelszava, így nem írja felül a később beállított jelszavakat.
UPDATE app_user
SET password_hash = '$2b$10$b5j6AVY0Sv0F/zWmOHlw9uousceXdp4JYMyrLRKPmLOgjl1j.wjya'
WHERE username IN ('anna', 'peter', 'eszter', 'gabor')
  AND password_hash IS NULL;

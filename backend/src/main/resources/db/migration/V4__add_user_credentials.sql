-- Bejelentkezés: felhasználónév és jelszó-hash a felhasználókhoz.
-- A jelszó-hash NULL, amíg nincs beállítva: ilyen felhasználó nem tud bejelentkezni.

ALTER TABLE app_user ADD COLUMN username varchar(50);
ALTER TABLE app_user ADD COLUMN password_hash varchar(100);

-- A V2 által betöltött négy felhasználó felhasználóneve; minden további sor a saját azonosítójából kap nevet.
UPDATE app_user SET username = CASE name
    WHEN 'Kovács Anna' THEN 'anna'
    WHEN 'Nagy Péter' THEN 'peter'
    WHEN 'Szabó Eszter' THEN 'eszter'
    WHEN 'Tóth Gábor' THEN 'gabor'
END;
UPDATE app_user SET username = 'user' || id WHERE username IS NULL;

ALTER TABLE app_user ALTER COLUMN username SET NOT NULL;
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_username_format CHECK (username ~ '^[a-z0-9._-]{3,50}$');
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_password_hash_bcrypt
    CHECK (password_hash IS NULL OR password_hash ~ '^\$2[aby]\$[0-9]{2}\$.{53}$');
CREATE UNIQUE INDEX uq_app_user_username ON app_user (username);

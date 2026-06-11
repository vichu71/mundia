-- Preferencias de UI por usuario (tema y vista compacta).
-- NULL = sin preferencia guardada: el frontend usa sus valores por defecto.
ALTER TABLE users
  ADD COLUMN pref_theme VARCHAR(10) NULL,
  ADD COLUMN pref_compact BOOLEAN NULL;

# Mundia database

Proyecto reservado para MariaDB.

Responsabilidades previstas:

- Scripts de esquema y migraciones.
- Datos seed del Mundial, equipos, grupos y partidos.
- Scripts de entorno local para levantar MariaDB.
- Documentacion del modelo relacional.
- Tablas de cache/sincronizacion para API-Football: fixtures externos, standings, rondas, estado de sincronizacion y auditoria de overrides manuales.

## Schema

Ver [SCHEMA.md](./SCHEMA.md).

Decision principal:

- Hibernate no debe crear el esquema final en produccion.
- Usar migraciones con Flyway o Liquibase.
- Usar `spring.jpa.hibernate.ddl-auto=validate` cuando existan migraciones.

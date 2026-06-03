# Docker setup

The project is designed as three Dockerized projects:

- `frontend`: Vue + Vite.
- `backend`: Spring Boot.
- `database`: MariaDB.

## First run

Create a local env file:

```bash
cp .env.example .env
```

Start everything:

```bash
docker compose up --build
```

Open:

```text
http://localhost:5173
```

## Current backend behavior

The backend service is already part of `docker-compose.yml`, but the Spring Boot project has not been scaffolded yet.

Until `backend/pom.xml` or `backend/mvnw` exists, the backend container stays alive and prints a message. Once Spring Boot is scaffolded, the same compose service will run:

```bash
mvn spring-boot:run
```

or:

```bash
./mvnw spring-boot:run
```

## Database

MariaDB runs with:

```text
database:3306 inside Docker
localhost:3307 from the host by default
```

Default local credentials are defined in `.env.example`.

Schema changes should be handled by backend migrations, not by raw init scripts, except for disposable local experiments.

## API-Football

Set the key in `.env`:

```bash
API_FOOTBALL_MODE=direct
API_FOOTBALL_KEY=your_key_here
```

The key is consumed by Spring Boot only. It must not be exposed in the frontend.

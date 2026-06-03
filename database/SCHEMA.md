# MariaDB schema draft

## Decision

Use MariaDB as the source of truth and manage schema changes with migrations.

Recommended approach:

- Development prototype: Hibernate can use `ddl-auto=update` only while the model is unstable.
- Real development baseline: create migrations with Flyway or Liquibase.
- Shared environments and production: Hibernate must use `ddl-auto=validate`.

Why:

- The app handles money, prizes, payments and audit logs.
- We need repeatable schema changes.
- We need reviewed indexes, constraints and unique keys.
- We should avoid accidental column/table changes caused by entity edits.

## Core model

### users

Application users authenticated by Google.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| google_sub | VARCHAR(128) UNIQUE | Google stable subject |
| email | VARCHAR(255) UNIQUE NOT NULL | login email |
| display_name | VARCHAR(160) NOT NULL | visible name |
| avatar_url | VARCHAR(512) NULL | Google/avatar URL |
| created_at | DATETIME NOT NULL | audit |
| updated_at | DATETIME NOT NULL | audit |

### pools

Each porra/apuesta.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| owner_user_id | BIGINT FK users.id | admin creator |
| name | VARCHAR(160) NOT NULL | porra name |
| description | VARCHAR(500) NULL | optional |
| invite_code | VARCHAR(40) UNIQUE NOT NULL | join code |
| entry_fee_cents | INT NOT NULL | default 1000 |
| currency | CHAR(3) NOT NULL | EUR |
| status | VARCHAR(30) NOT NULL | DRAFT, OPEN, LOCKED, FINISHED |
| initial_prediction_locked_at | DATETIME NULL | freeze date |
| initial_bonus_enabled | BOOLEAN NOT NULL | bonus for initial bet |
| created_at | DATETIME NOT NULL | audit |
| updated_at | DATETIME NOT NULL | audit |

### pool_members

Membership per porra.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| pool_id | BIGINT FK pools.id | porra |
| user_id | BIGINT FK users.id | member |
| role | VARCHAR(30) NOT NULL | ADMIN, PARTICIPANT |
| status | VARCHAR(30) NOT NULL | INVITED, ACCEPTED, REJECTED, REMOVED |
| joined_at | DATETIME NULL | accepted date |
| created_at | DATETIME NOT NULL | audit |

Unique:

- `(pool_id, user_id)`

### pool_invitations

Invitation tracking.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| pool_id | BIGINT FK pools.id | porra |
| invited_email | VARCHAR(255) NULL | email invite |
| invited_user_id | BIGINT FK users.id NULL | when known |
| token | VARCHAR(80) UNIQUE NOT NULL | secure invite token |
| status | VARCHAR(30) NOT NULL | PENDING, ACCEPTED, REJECTED, EXPIRED |
| expires_at | DATETIME NULL | optional |
| accepted_at | DATETIME NULL | audit |
| created_at | DATETIME NOT NULL | audit |

### payments

Manual Bizum/cash payment confirmation.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| pool_member_id | BIGINT FK pool_members.id | member payment |
| amount_cents | INT NOT NULL | paid amount |
| method | VARCHAR(30) NOT NULL | BIZUM, CASH, TRANSFER, OTHER |
| status | VARCHAR(30) NOT NULL | PENDING, CONFIRMED, CANCELLED |
| confirmed_by_user_id | BIGINT FK users.id NULL | admin |
| confirmed_at | DATETIME NULL | confirmation date |
| notes | VARCHAR(500) NULL | admin note |
| created_at | DATETIME NOT NULL | audit |

## Tournament data

### teams

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| external_team_id | BIGINT NULL | API-Football team id |
| name | VARCHAR(120) NOT NULL | display name |
| country_code | VARCHAR(16) NULL | flag code |
| created_at | DATETIME NOT NULL | audit |

Unique:

- `external_team_id` when not null

### rounds

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| name | VARCHAR(120) NOT NULL | e.g. Group Stage - 1 |
| stage | VARCHAR(40) NOT NULL | GROUP, ROUND_16, QUARTER, SEMI, FINAL |
| sort_order | INT NOT NULL | UI order |
| external_name | VARCHAR(160) NULL | provider round |

### matches

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| external_fixture_id | BIGINT UNIQUE NULL | API-Football fixture id |
| round_id | BIGINT FK rounds.id | round/stage |
| home_team_id | BIGINT FK teams.id | home team |
| away_team_id | BIGINT FK teams.id | away team |
| kickoff_at | DATETIME NULL | kickoff |
| status | VARCHAR(30) NOT NULL | SCHEDULED, LIVE, FINISHED, CANCELLED |
| status_short | VARCHAR(10) NULL | API status |
| elapsed | INT NULL | live minute |
| home_goals | INT NULL | real goals |
| away_goals | INT NULL | real goals |
| result_source | VARCHAR(30) NOT NULL | API, MANUAL, NONE |
| manual_override | BOOLEAN NOT NULL | admin override |
| last_synced_at | DATETIME NULL | API sync |
| raw_payload_hash | VARCHAR(128) NULL | dedupe/audit |
| created_at | DATETIME NOT NULL | audit |
| updated_at | DATETIME NOT NULL | audit |

Indexes:

- `(kickoff_at)`
- `(status)`
- `(round_id)`
- `(external_fixture_id)`

### standings_snapshots

Cached standings from API-Football.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| round_name | VARCHAR(120) NULL | optional |
| provider | VARCHAR(40) NOT NULL | API_FOOTBALL |
| payload_json | JSON NOT NULL | raw standings payload |
| synced_at | DATETIME NOT NULL | sync time |

## Predictions

### prediction_sets

One prediction set per user and pool. Initial and live are separate rows.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| pool_member_id | BIGINT FK pool_members.id | owner |
| type | VARCHAR(30) NOT NULL | INITIAL, LIVE |
| status | VARCHAR(30) NOT NULL | DRAFT, SUBMITTED, LOCKED |
| submitted_at | DATETIME NULL | submit date |
| locked_at | DATETIME NULL | lock date |
| created_at | DATETIME NOT NULL | audit |
| updated_at | DATETIME NOT NULL | audit |

Unique:

- `(pool_member_id, type)`

### match_predictions

Predicted score per match.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| prediction_set_id | BIGINT FK prediction_sets.id | prediction owner |
| match_id | BIGINT FK matches.id | match |
| home_goals | INT NOT NULL | predicted home goals |
| away_goals | INT NOT NULL | predicted away goals |
| predicted_winner_team_id | BIGINT FK teams.id NULL | null means draw |
| is_editable | BOOLEAN NOT NULL | for live prediction |
| created_at | DATETIME NOT NULL | audit |
| updated_at | DATETIME NOT NULL | audit |

Unique:

- `(prediction_set_id, match_id)`

### bracket_predictions

Predicted advancing teams for knockout stages.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| prediction_set_id | BIGINT FK prediction_sets.id | prediction owner |
| round_id | BIGINT FK rounds.id | round |
| slot_key | VARCHAR(80) NOT NULL | bracket position |
| predicted_team_id | BIGINT FK teams.id | predicted team |
| created_at | DATETIME NOT NULL | audit |

Unique:

- `(prediction_set_id, round_id, slot_key)`

## Scoring and prizes

### score_breakdowns

Detailed scoring per participant and match/category.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| pool_member_id | BIGINT FK pool_members.id | participant |
| prediction_set_id | BIGINT FK prediction_sets.id | INITIAL or LIVE |
| match_id | BIGINT FK matches.id NULL | match if applicable |
| category | VARCHAR(40) NOT NULL | WINNER, EXACT, TEAM_GOALS, GROUP, ROUND, CHAMPION, INITIAL_BONUS |
| points | INT NOT NULL | can be 0 |
| details_json | JSON NULL | explanation |
| calculated_at | DATETIME NOT NULL | scoring run |

Indexes:

- `(pool_member_id, category)`
- `(prediction_set_id)`

### prize_rules

Configured prizes per pool.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| pool_id | BIGINT FK pools.id | porra |
| category | VARCHAR(50) NOT NULL | PERFECT, GENERAL, INITIAL, EXACTS, WINNERS, CHAMPION |
| percentage_when_perfect_alive | DECIMAL(5,2) NOT NULL | percentage |
| percentage_when_perfect_extinct | DECIMAL(5,2) NOT NULL | redistribution percentage |
| enabled | BOOLEAN NOT NULL | active |

### prize_projections

Calculated current/potential prize state.

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| pool_member_id | BIGINT FK pool_members.id | participant |
| category | VARCHAR(50) NOT NULL | prize category |
| current_amount_cents | INT NOT NULL | estimated current amount |
| max_possible_amount_cents | INT NOT NULL | max possible amount |
| status | VARCHAR(30) NOT NULL | ALIVE, LOST, WON, IMPOSSIBLE |
| calculated_at | DATETIME NOT NULL | projection run |

## Sync and audit

### sports_sync_runs

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| provider | VARCHAR(40) NOT NULL | API_FOOTBALL |
| sync_type | VARCHAR(40) NOT NULL | LIVE_FIXTURES, DAILY_FIXTURES, ROUNDS, STANDINGS |
| status | VARCHAR(30) NOT NULL | SUCCESS, FAILED |
| request_url | VARCHAR(600) NOT NULL | without secret |
| response_hash | VARCHAR(128) NULL | dedupe |
| started_at | DATETIME NOT NULL | start |
| finished_at | DATETIME NULL | finish |
| error_message | VARCHAR(1000) NULL | if failed |

### audit_logs

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | internal id |
| actor_user_id | BIGINT FK users.id NULL | user/system |
| pool_id | BIGINT FK pools.id NULL | scope |
| entity_type | VARCHAR(80) NOT NULL | table/domain entity |
| entity_id | BIGINT NULL | entity id |
| action | VARCHAR(80) NOT NULL | CREATED, UPDATED, LOCKED, OVERRIDDEN |
| before_json | JSON NULL | previous state |
| after_json | JSON NULL | new state |
| created_at | DATETIME NOT NULL | audit |

## Java entity guidance

Create Java entities to match this schema, not the other way around.

Use:

- `@ManyToOne(fetch = FetchType.LAZY)` for most relationships.
- Explicit `@Table(indexes = ...)` and `@UniqueConstraint(...)`.
- Enums persisted as strings with `@Enumerated(EnumType.STRING)`.
- `Instant` or `OffsetDateTime` for timestamps.
- `Integer` for nullable goals and elapsed values.
- `@Version` on mutable aggregate roots if concurrent admin updates become risky.

Avoid:

- Relying on Hibernate default table/column names.
- Bidirectional collections everywhere.
- Cascade deletes on money, audit, score or prediction data.
- `ddl-auto=create` or `create-drop` outside disposable local experiments.

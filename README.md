# Internship Application Tracker

[![CI](https://github.com/gagann06/internship-tracker/actions/workflows/ci.yml/badge.svg)](https://github.com/gagann06/internship-tracker/actions/workflows/ci.yml)

A REST API for tracking internship applications through recruitment stages. Each user
keeps their own companies and applications, every status change is recorded as history,
upcoming deadlines are emailed daily, and a stats endpoint shows how far applications get
and how long each stage takes.

Java 21, Spring Boot 4.1, PostgreSQL 17, Flyway, Spring Security with JWT, Gradle,
Testcontainers, Docker.

## Features

- **Companies and applications**: full CRUD, with an application belonging to a company.
- **Status history**: moving an application to a new stage appends a history entry rather
  than overwriting a field, so the full path of every application is kept.
- **Accounts**: registration and login issue a signed JWT. Every request is scoped to the
  caller, and one user can never see or change another's data.
- **Deadline reminders**: a scheduled job emails each user a daily digest of deadlines
  due in the next three days.
- **Stats**: a stage funnel, the average time between statuses, and applications per company.
- **Web interface**: a single page served by the app at `/`, built with plain HTML, CSS and
  JavaScript, for using the tracker day to day.

## Running it

### Everything in Docker

Requires only Docker.

```bash
cp .env.example .env
```

Edit `.env`: set any `DB_PASSWORD`, and a signing key for `JWT_SECRET` from:

```bash
openssl rand -base64 32
```

Then start Postgres, Mailpit and the app:

```bash
docker compose --profile app up --build
```

Open `http://localhost:8080` to use the tracker. Every email the app sends is caught by
Mailpit at `http://localhost:8025`, so nothing reaches a real inbox.

### For development

Requires Java 21 and Docker. With `.env` set up as above:

```bash
./gradlew bootRun
```

Spring Boot's Docker Compose support starts Postgres and Mailpit automatically, but not the
containerised app, which sits behind the `app` profile so it doesn't clash with `bootRun`
over port 8080.

To see a reminder email without waiting for the 08:00 run, override the schedule:

```bash
APP_REMINDERS_CRON="*/20 * * * * *" ./gradlew bootRun
```

### In production

Set `SPRING_PROFILES_ACTIVE=prod` and provide:

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Token signing key |
| `MAIL_HOST`, `MAIL_PORT` (default 587), `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP server, over STARTTLS |
| `MAIL_FROM` | Sender address for emails |

The app refuses to start if any of these are missing. The prod profile turns off the API
documentation and trusts the hosting platform's `X-Forwarded-*` headers. `/actuator/health`
is public and returns only `UP` or `DOWN`, for the platform's health checks.

### Tests

```bash
./gradlew build
```

Integration tests run against a real Postgres started by Testcontainers, so Docker must be
running. GitHub Actions runs the full suite on every push.

### Trying the API

```bash
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"at-least-8-chars"}'
```

```bash
curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"at-least-8-chars"}'
```

Send the returned `accessToken` on every other request:

```bash
curl localhost:8080/api/applications -H "Authorization: Bearer <token>"
```

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account |
| `POST` | `/api/auth/login` | Get an access token |
| `PUT` | `/api/account/password` | Change password (needs the current one) |
| `DELETE` | `/api/account` | Delete the account and all its data (needs the password) |
| `GET` `POST` | `/api/companies` | List or create companies |
| `GET` `PUT` `DELETE` | `/api/companies/{id}` | Read, replace or delete a company |
| `GET` `POST` | `/api/applications` | List or create applications |
| `GET` `PUT` `DELETE` | `/api/applications/{id}` | Read, replace or delete an application |
| `POST` | `/api/applications/{id}/status` | Move an application to a new status |
| `GET` | `/api/applications/{id}/status-changes` | An application's history, oldest first |
| `GET` | `/api/stats` | Funnel, time between statuses, applications per company |

Everything except `/api/auth/**` needs a token. Errors use the standard `ProblemDetail`
JSON format throughout.

Interactive documentation is generated from the code at `/swagger-ui.html`. Register and log
in there, paste the token into **Authorize**, and every endpoint can be tried from the browser.

## Design decisions

### Status history rather than a status column

The common design stores an application's status in one column and overwrites it on every
change. That answers "where is this application now?" and nothing else: once an
application moves from the online assessment to rejected, the fact that it ever reached the
assessment is gone. The two questions a job hunt actually raises, "where do my
applications drop out?" and "how long does each stage take?", are unanswerable.

Here, every change appends a `StatusChange` row recording the previous status, the new
status, when, and an optional note. Rows are never edited. The funnel and the
time-between-stages statistics are both computed from this history, and neither would be
possible with a single column.

`applications` **also** keeps a `status` column, a deliberate copy of the latest history
entry. Deriving the current status from history on every read would make simple questions
("everything currently at assessment centre") need a latest-row-per-application query. The
risk with two copies of the same fact is that they drift apart, so there is exactly one way
to change status: `Application.changeStatus`, which appends the history row and updates the
column together, inside one transaction. There is no setter for `status`, and the history
list is exposed read-only, so neither can be changed on its own. The history remains the
source of truth; the column is a cached view of it.

Moving to the status an application already has is rejected with a `409`, so the history
only ever records things that happened. New applications start at `TO_APPLY` with a first
history entry, and a migration backfilled that first entry for applications created before
history existed.

### How authentication is structured

- **Passwords** are hashed with BCrypt, which is deliberately slow and salts each password,
  so two users with the same password store different hashes. Passwords are limited to 64
  characters because BCrypt only reads the first 72 bytes.
- **Login** checks the password and issues a JWT signed with HMAC-SHA256. Its subject is the
  user's id, it lasts one hour, and it carries nothing sensitive, since a JWT's contents can
  be read by anyone. A wrong password and an unknown email return the identical `401`, so
  login does not reveal which emails have accounts.
- **Every other request** carries `Authorization: Bearer <token>`. Validation uses Spring
  Security's OAuth2 resource server rather than a hand-written filter, so parsing and
  signature checks come from a maintained implementation. The decoder is pinned to HS256,
  which blocks the known attack of a token claiming a different algorithm or none.
- **Sessions are stateless.** The server stores nothing between requests; the token is the
  proof of identity. CSRF protection is disabled because it defends cookie-based sessions,
  and this API takes its credentials from a header that a malicious site cannot make a
  browser attach.
- **The signing key** is never committed. It is read from the environment (a git-ignored
  `.env` locally), and CI generates a throwaway key for each run.

One symmetric key both signs and verifies tokens because a single service does both. A
public and private key pair would only matter if other services needed to verify tokens
without being able to create them.

### Ownership and isolation

Companies and applications each record their owner, and every lookup is "by id **and**
owner", so another user's data is filtered out by the query itself rather than by a check
someone has to remember. Asking for another user's application returns `404`, not `403`, so
the API does not confirm that the id exists. An application's owner is copied from its
company and cannot be moved to another user's company.

Companies are per user rather than shared. A shared directory would raise the question of
who may rename or delete a company everyone uses. Each user may have a company of any name,
but not two whose names differ only by case, enforced by a unique index on
`(user_id, upper(name))`.

Ownership was added after data existed. The migration adds the column as nullable,
backfills existing rows to the oldest account, then makes it `NOT NULL`, all in one
transaction, so it either succeeds completely or fails without leaving anything half done.

### Schema and data access

- **Flyway owns the schema.** Hibernate is set to `validate`, so it checks the entities
  against the tables at startup and never alters them. Every schema change is a reviewable,
  versioned migration.
- **Uniqueness lives in the database.** Friendly checks in the services give clear `409`
  messages, but unique indexes are the real guarantee, since two concurrent requests can
  both pass a check before either inserts.
- **The N+1 problem** was measured (four queries to list three applications) and fixed with
  a `join fetch`, giving one query regardless of list size.
- **Open Session In View is off.** Every database read happens inside a service
  transaction, so lazy loading cannot quietly run extra queries while a response is being
  written.

### Deadlines and reminders

`deadline` means "when the next thing is due": the closing date while applying, then an
online assessment deadline, then an offer acceptance date. Reminders therefore cover any
application with a deadline in the next three days unless it is rejected, withdrawn or
expired, and repeat daily until acted on. Overdue deadlines are not reminded.

The job runs at 08:00 London time. It reads the current date from an injected `Clock`, so
tests freeze time and can check behaviour around midnight and daylight saving changes. Each
user receives one email listing all of their deadlines, and a failure sending to one user is
logged without stopping the others.

### Stats

- **The funnel** counts how many applications reached each stage. A stage includes its
  completed status, so a HireVue logged only as completed still counts as reaching HireVue.
  An application rejected after an online assessment still counts as having reached the
  assessment. Percentages are relative to applications made rather than the previous stage,
  because stages are optional and differ between firms.
- **Time between statuses** uses a native PostgreSQL query with the `LAG()` window function
  to find the time since each application's previous change. JPQL cannot express window
  functions, so this is the one place the code is tied to PostgreSQL. Native queries are not
  checked at startup the way JPQL is, which is why this one has its own tests.

### Web interface

The interface is a static page in `src/main/resources/static`, served by the same
application, so there is one deployment and no cross-origin configuration. It uses no
framework and talks to the API exactly as any other client would. It was generated with AI
assistance; the API behind it is the substance of the project.

The access token is kept in `sessionStorage` rather than `localStorage`, so it is discarded
when the tab closes, limiting how long a leaked token stays useful. All user-entered text is
inserted as text, never as HTML, so it cannot be run as script.

### Docker

The image is built in two stages: a full JDK compiles the application, and the final image
contains only a Java runtime and the jar. That keeps it around 40% smaller and free of
compilers, build tools and source code. It runs as a non-root user.

### Testing

138 tests across three levels:

- **Domain tests** with no framework, for rules that live in the entities, such as recording
  history and refusing to move an application to another user's company.
- **Service tests** with Mockito, for logic in isolation.
- **Integration tests** through HTTP against real PostgreSQL. These include tests that send
  real tokens (missing, forged, expired, genuine), and tests where one user tries to read,
  change or delete another's data on every endpoint.

Integration tests start user ids at 1000 while other ids stay low, so a call that swaps an
id and an owner id fails visibly instead of passing when the numbers happen to coincide.

## Known limitations and planned work

- **CSV import** of existing applications is planned.
- **Tokens cannot be revoked** before they expire. Short expiry limits the risk; refresh
  tokens would be the next step.
- **The reminder job assumes a single instance.** Running several would send duplicate
  emails; a database lock such as ShedLock would fix that.
- **Login timing** differs slightly between an unknown email and a wrong password, which
  could in principle reveal which emails are registered.
- **Authentication errors** from the security layer do not yet use the `ProblemDetail`
  format the rest of the API uses.

## Project structure

```
src/main/java/io/github/gagann06/internshiptracker/
├── application/   applications, status history, ApplicationStatus
├── auth/          users, registration, login, JWT and security configuration
├── company/       companies
├── error/         maps exceptions to ProblemDetail responses
├── reminder/      the scheduled deadline reminder job and email sending
└── stats/         funnel, transition times, per-company counts

src/main/resources/db/migration/   Flyway migrations V1 to V5
```

Code is grouped by feature rather than by layer, so everything for one feature sits together.

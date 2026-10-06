# Bookify: Appointment Booking Platform

A full-stack booking platform where customers find providers (doctors, coaches, consultants), pick a free time slot and book. Providers manage services and working hours and confirm requests; admins see analytics and manage users.

**Stack:** Java 17 · Spring Boot 3 · Spring Security (JWT + rotating refresh tokens) · Spring Data JPA / Hibernate · PostgreSQL · Flyway · Angular 17 (standalone components, signals, reactive forms) · Docker Compose · GitHub Actions

## Run it

**Everything in Docker (one command)**
```bash
docker compose up --build
```
| What | URL |
|---|---|
| App | http://localhost:4200 |
| API docs (Swagger) | http://localhost:8080/swagger-ui.html |
| Caught emails (MailHog) | http://localhost:8025 |

**Local development** (hot reload)
```bash
docker compose -f docker-compose.dev.yml up -d     # PostgreSQL + MailHog
cd backend  && mvn spring-boot:run                 # http://localhost:8080
cd frontend && npm install && npm start            # http://localhost:4200 (proxies /api to :8080)
```

**Demo accounts** (password `Password@123`, created on first start)
| Role | Email |
|---|---|
| Customer | customer@bookify.dev |
| Provider | meera@bookify.dev (also arjun@, sofia@, kabir@) |
| Admin | admin@bookify.dev |

## Features

- **Customers:** search and filter providers (text, category, minimum rating, sort, pagination), see real free slots per service and day, book, reschedule, cancel, leave reviews after a completed visit.
- **Providers:** manage services (name, duration, price) and weekly working hours, confirm / reject / complete / cancel bookings.
- **Admins:** KPI cards, bookings-per-day chart, top providers, enable or disable user accounts.
- **Emails:** sent asynchronously after the booking transaction commits (booking requested, confirmed, rejected, cancelled, rescheduled) plus a scheduled 24-hour reminder.

## Engineering decisions worth talking about

**1. No double bookings, three layers deep** (`BookingService`)
1. `SELECT ... FOR UPDATE` on the provider row, so two bookings for the same provider are checked one at a time.
2. An overlap query inside that lock. It catches partially overlapping times, not just identical start times, so a 60-minute and a 30-minute service can't collide.
3. A partial unique index `(provider_id, start_time) WHERE status IN ('PENDING','CONFIRMED')` as the last line of defence if application logic is ever bypassed.

`BookingConcurrencyTest` fires 8 simultaneous requests at one slot and asserts exactly one wins and the rest get a clean `409`.

**2. Optimistic locking for status changes.** `Booking` has `@Version`. If a customer cancels while the provider confirms, one of them gets a `409` instead of silently overwriting the other.

**3. Auth.** Short-lived JWT access tokens (15 min) and opaque, single-use refresh tokens stored in the database and rotated on every refresh. The Angular interceptor retries a failed request once after refreshing, and parallel 401s share one in-flight refresh. Disabled accounts are locked out on the next request, not at token expiry.

**4. Emails never slow down or lie.** `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`: no email for a rolled-back booking, and a slow SMTP server never delays the API.

**5. Reviews keep a correct running average** by locking the provider row while updating `ratingAvg` / `ratingCount`.

**6. API hygiene.** DTO records with Bean Validation, one global error shape (`{timestamp, status, message, errors}`), sort keys whitelisted (never raw user input), `open-in-view` off, N+1 avoided with entity graphs, schema owned by Flyway and checked by Hibernate (`ddl-auto: validate`).

## API overview (~26 endpoints)

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register · login · refresh · logout` |
| Public | `GET /api/providers` (search, filter, sort, page) · `/categories` · `/{id}` · `/{id}/slots?serviceId&date` · `/{id}/reviews` |
| Bookings | `POST /api/bookings` · `GET /api/bookings/me` · `PATCH /api/bookings/{id}/cancel · confirm · reject · complete · reschedule` |
| Reviews | `POST /api/reviews` |
| Provider | `GET/POST/PUT/DELETE /api/provider/services` · `GET/PUT /api/provider/availability` |
| Admin | `GET /api/admin/analytics/summary` · `GET /api/admin/users` · `PATCH /api/admin/users/{id}/enabled` |

## Project layout

```
bookify/
├── backend/   Spring Boot: controller → service → repository, plus security, event, scheduler
│   └── src/main/resources/db/migration/V1__init_schema.sql
├── frontend/  Angular: core (api, auth, interceptor, guards) · auth · customer · provider · admin · shared
├── docker-compose.yml        full stack
├── docker-compose.dev.yml    database + mail only
└── .github/workflows/ci.yml  backend tests + frontend build on every push
```

## Tests

```bash
cd backend && mvn test
```
Unit tests (Mockito) for booking rules, slot generation and JWT handling, plus the concurrency integration test against an in-memory H2 database.

## Ideas for next steps

Redis cache for the provider list · Testcontainers (real PostgreSQL) in CI · rate limiting on `/api/auth/*` · email verification · ICS calendar invites · Playwright end-to-end tests.

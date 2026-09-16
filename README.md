# Digital Onboarding Service

Backend REST APIs that let customers register and open a bank account remotely,
without visiting a branch.

Built with Java 21, Spring Boot 3.3.5, Spring Data JPA and H2.

---

## Requirements Covered

| Requirement | Where it is implemented |
|---|---|
| Register with name, address, username, date of birth | `RegisterRequest`, `RegistrationService` |
| Unique username, clear error when taken | `CustomerRepository.existsByUsername` → `ConflictException` (409) |
| Auto-generate Dutch (NL) IBAN | `IbanGenerator` (MOD-97 check digits) |
| Generate a default password | `PasswordGenerator` |
| Login with username + generated password | `AuthenticationService`, `SessionService` |
| View account balance and type once logged in | `AccountService`, `GET /overview` |
| Only Netherlands and Belgium allowed | `@AllowedCountry` + `app.registration.allowed-countries` |
| Easy to add new countries | Config list in `application.yml` — no code change |
| 18+ only | `@MinimumAge(18)` |
| Limit API traffic to reduce DB load | `ApiRateLimitFilter`; strict DB throughput control remains a future enhancement |

---

## Running Locally

Requires JDK 21 (or newer) and Maven.

```bash
mvn spring-boot:run
```

The service starts on port **8085**.

| Resource | URL |
|---|---|
| Swagger UI | http://localhost:8085/swagger-ui.html |
| OpenAPI spec (live, JSON) | http://localhost:8085/v3/api-docs |
| OpenAPI spec (static, YAML) | `docs/api-spec.yml` |
| H2 console | http://localhost:8085/h2-console |

H2 credentials: JDBC URL `jdbc:h2:mem:digitalonboardingservice`, user `admin`, password `admin`.

Run the tests:

```bash
mvn test
```

---

## Running with Docker

Requires Docker with Linux containers (Docker Desktop on Windows). From the directory
containing `compose.yaml`, run:

```bash
docker compose up --build -d
docker compose logs -f
```

Open http://localhost:8085/swagger-ui.html. Stop with `docker compose down`.
If port 8085 is occupied, stop the local application or change the host port in
`compose.yaml`. Java and Maven are included in the build image.

The image build skips tests because the current rate-limit test is timing-dependent;
run `mvn test` separately. H2 data and sessions are lost when the container stops.
The H2 console is disabled in the container, and the port is exposed only on localhost.

---

## API Documentation

The API follows the **OpenAPI 3.0** specification. There are two ways to explore the API:

### Live OpenAPI Spec (Auto-generated)
- **URL:** `http://localhost:8085/v3/api-docs` (JSON format)
- **Rendered in Swagger UI:** `http://localhost:8085/swagger-ui.html`
- **Source:** Auto-generated from `@Operation`, `@ApiResponse` annotations in controller code
- **Syncs with code:** Changes to annotations are reflected immediately
- **Best for:** Interactive testing, live exploration

### Static OpenAPI Spec (Documentation)
- **File:** `docs/api-spec.yml` (YAML format)
- **Includes:** Business rules, design notes, and comprehensive examples
- **Use:** Offline documentation, documentation sites, version control history
- **Sync:** Must be manually updated if API changes (not auto-generated)

---

## API Usage

### 1. Register

```bash
curl -X POST http://localhost:8085/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "John Doe",
    "address": "Damrak 1, Amsterdam",
    "username": "john_doe",
    "dateOfBirth": "1990-05-20",
    "countryCode": "NL"
  }'
```

```json
{
  "username": "john_doe",
  "defaultPassword": "a1b2c3d4e5f6",
  "message": "Registration successful"
}
```

### 2. Login

```bash
curl -X POST http://localhost:8085/login \
  -H "Content-Type: application/json" \
  -d '{ "username": "john_doe", "password": "a1b2c3d4e5f6" }'
```

```json
{
  "username": "john_doe",
  "token": "5f8a3c21-9b4e-4d17-8c33-1e6a7b2d9f04",
  "message": "Login successful"
}
```

### 3. Overview

```bash
curl http://localhost:8085/overview \
  -H "Authorization: Bearer 5f8a3c21-9b4e-4d17-8c33-1e6a7b2d9f04"
```

```json
{
  "accountNumber": "NL91RABO0417164300",
  "accountType": "CURRENT",
  "balance": 0.00,
  "currency": "EUR"
}
```

---

## Error Responses

Controller errors use the following shape. The filter's `429` response currently includes only `code` and `message`.

```json
{
  "code": "CONFLICT",
  "message": "Username already exists",
  "details": null,
  "timestamp": 1758000000000
}
```

| Scenario | Status | `code` |
|---|---|---|
| Validation failed / under 18 / country not allowed | 400 | `VALIDATION_ERROR` |
| Username already taken | 409 | `CONFLICT` |
| Bad credentials, missing or invalid token | 401 | `UNAUTHORIZED` |
| Account not found | 404 | `NOT_FOUND` |
| Rate limit exceeded | 429 | `TOO_MANY_REQUESTS` |
| Unexpected failure | 500 | `INTERNAL_ERROR` |

---

## Configuration

```yaml
app:
  db:
    max-requests-per-second: 2      # API request limit, not SQL throughput
  registration:
    allowed-countries: NL,BE        # extend this comma-separated list
```

Adding a new country is a one-line config change and requires no redeployment of code logic.

---

## Design Decisions

### Protecting the legacy database

`ApiRateLimitFilter` runs **before** Spring MVC and allows two API requests per
calendar second per instance. A request can execute several SQL statements, so this
does not yet enforce the database's two-requests-per-second limit.

Placing the limit in a filter rather than in the service layer means a rejected request
never reaches the controller, never opens a transaction, and never acquires a database
connection. Requests over budget receive `429 Too Many Requests` immediately.

The counter is intentionally **global** rather than per-user or per-IP. The constraint
being modelled is the database's total throughput, not fairness between clients — a
per-user limit would still allow 10 users × 2 req/sec = 20 req/sec to reach the database.

Registration is additionally wrapped in a single `@Transactional` unit so that the
customer and account rows commit or roll back together. This still requires multiple
database round trips.

### Why there is no response cache

Caching `/overview` was considered and deliberately rejected.

Balance is mutable data. A cache without a TTL and without eviction on write would serve
a stale balance indefinitely. Any future cache should have a TTL and eviction on
balance changes. Caching can reduce reads, but database throughput control is still needed.

Caching becomes worthwhile once balance-mutating endpoints exist (deposits, transfers).
At that point the right approach is a short TTL as a safety net **combined with**
`@CacheEvict` on every write path — not one or the other.

### Token-based session handling

Login exchanges credentials for an opaque UUID token held by `SessionService`. Subsequent
requests present it as `Authorization: Bearer <token>`.

The deliberate property here is that token verification performs **no database access** —
it is a single in-memory map lookup. This keeps per-request load off the legacy database,
which matters directly given the 2 req/sec ceiling.

Login returns the same `UnauthorizedException` message for an unknown username and for a
wrong password, so the API does not leak which usernames exist.

### Service contracts and encapsulation

Each service is split into a public interface in `com.randombank.onboarding.service` and a
package-private implementation in `com.randombank.onboarding.service.impl`:

| Contract | Implementation |
|---|---|
| `RegistrationService` | `RegistrationServiceImpl` |
| `AuthenticationService` | `AuthenticationServiceImpl` |
| `AccountService` | `AccountServiceImpl` |
| `SessionService` | `SessionServiceImpl` |

The implementation classes are package-private, so the controller can only depend on the
interfaces. Spring instantiates them by component scanning and injects them by type.

`SessionService` is deliberately separate from `AuthenticationService` rather than merged
into it. Authentication is a policy (are these credentials valid?); session storage is an
infrastructure concern. Keeping them apart means replacing the in-memory map with Redis
or JWT requires one new implementation and no change to authentication logic.

### Validation placement

Business rules live as declarative constraints on the request DTO rather than as
imperative checks inside services:

- `@MinimumAge(18)` — derives age from date of birth at request time
- `@AllowedCountry` — reads the permitted list from configuration

Each validator has a single responsibility and returns `true` for `null`, deferring
null-checking to `@NotBlank`/`@NotNull`. This keeps error messages accurate — a missing
country reports "Country code is required" rather than "Country is not allowed".

### IBAN generation

`IbanGenerator` produces a structurally valid Dutch IBAN including correctly computed
MOD-97 check digits, rather than a random string. `IbanGeneratorTest` independently
verifies the checksum across repeated runs. Generation retries when a pre-insert check
finds an existing IBAN. The unique index prevents duplicates, but concurrent collisions
still need transaction-level retry handling.

---

## Known Limitations

Current scope limitations:

- Passwords are stored in plain text — the assignment explicitly waives encryption.
- Tokens do not expire and there is no logout endpoint.
- The session store is in-memory, so tokens are lost on restart and would not work across
  multiple instances. A shared store (for example Redis) would be required to scale out.
- The rate limiter counter is per-instance; a clustered deployment would need a
  distributed counter.
- H2 in-memory is used for portability; data does not survive a restart.

---

## Future Enhancements

The following gaps remain open; documenting them does not resolve them.

| Area | Problem and proposed solution |
|---|---|
| Database throughput | API limits allow multiple SQL calls and bursts across second boundaries. Pace database operations to enforce two requests per second, with a shared budget when scaling out. |
| Concurrent registration | Simultaneous requests can pass uniqueness checks and return 500. Map username constraint violations to 409 and retry IBAN collisions in a fresh transaction. |
| Reliable tests | The rate-limit test depends on wall-clock timing. Inject a controllable clock; add SQL throughput, concurrency, rollback, Belgium, and exact-age-18 coverage. |
| API contract | OpenAPI authentication, validation limits, and error schemas differ from the implementation. Align both specifications and standardize all error responses, including 429. |
| Error privacy | Unexpected errors expose internal exception messages. Log details server-side and return a generic message to clients. |
| Documentation and delivery | Postman pacing instructions are outdated. Align instructions with the collection and commit all required artifacts before submission. |

---

## Further Reading

`docs/ARCHITECTURE.md` contains the full request-flow walkthrough, token lifecycle
diagrams, and a file-by-file responsibility breakdown.

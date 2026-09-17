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
| Protect the legacy database (maximum 2 operations/second) | `DatabaseAccessInterceptor` + `DatabaseOperationRateLimiter` |

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

The image build runs the complete test suite. H2 data and sessions are lost when the container stops.
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

Controller errors use the following shape.

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
| Unexpected failure | 500 | `INTERNAL_ERROR` |

---

## Configuration

```yaml
app:
  db:
    max-operations-per-second: 2    # global repository-operation throughput per instance
  registration:
    allowed-countries: NL,BE        # extend this comma-separated list
```

Adding a new country is a one-line config change and requires no redeployment of code logic.

---

## Design Decisions

### Protecting the legacy database

`DatabaseAccessInterceptor` applies Spring AOP around every public Spring Data repository
method in this service. Before a repository operation starts, the interceptor calls the
global `DatabaseOperationRateLimiter`. At the default rate of two operations per second,
operation starts are spaced by 500 ms.

The limiter is intentionally global rather than per-user, per-IP, or per-endpoint. The
constraint being modelled is total legacy-database throughput. A per-user limit would
still allow many users to overload the same database.

Excess operations **wait in a fair in-process queue** instead of being rejected. This is
important for registration, which performs several repository operations in one
`@Transactional` unit. Returning `429` during its third operation would roll back every
registration; queuing lets the customer and account rows commit or roll back together
while still pacing database access. The trade-off is increased response latency under
load: a normal registration takes roughly 1.5 seconds at the default rate.

The rate is configurable through `app.db.max-operations-per-second`. Unit tests use a
controllable monotonic clock, and `DatabaseThrottleIntegrationTest` verifies that the AOP
advice is applied to real Spring Data repository proxies.

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
- The database-operation queue is per-instance; a clustered deployment would need a
  shared/distributed limiter so the combined rate remains two operations per second.
- The in-process queue has no request timeout or maximum depth. Production deployment
  should add bounded waiting and overload handling based on an agreed service-level target.
- H2 in-memory is used for portability; data does not survive a restart.

---

## Future Enhancements

The following gaps remain open; documenting them does not resolve them.

| Area | Problem and proposed solution |
|---|---|
| Distributed database throughput | Replace the per-instance limiter with a shared budget when scaling to multiple application instances. |
| Concurrent registration | Simultaneous requests can pass uniqueness checks and return 500. Map username constraint violations to 409 and retry IBAN collisions in a fresh transaction. |
| Additional resilience tests | Add sustained-load, queue-timeout, rollback, Belgium, and exact-age-18 coverage. |
| API contract | Keep authentication, validation limits, and error schemas aligned between the static and generated OpenAPI specifications. |
| Error privacy | Unexpected errors expose internal exception messages. Log details server-side and return a generic message to clients. |
| Documentation and delivery | Align the Postman pacing scenarios and architecture document with the blocking database-operation throttle. |

---

## Further Reading

`docs/ARCHITECTURE.md` contains the full request-flow walkthrough, token lifecycle
diagrams, and a file-by-file responsibility breakdown.

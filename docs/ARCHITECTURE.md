# Architecture & Request Flow

This document explains how `digital-onboarding-service` is wired together: the layers,
the token/session mechanism, and the database-protection (rate limiting) strategy.

---

## 1. Layered Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                            HTTP Client                               │
│                       (Postman / Browser / App)                      │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│  CONTROLLER LAYER                                                    │
│  OnboardingController          ← @RestController                     │
│  • @Valid triggers DTO validation                                    │
│  • parses "Authorization: Bearer <token>"                            │
│  GlobalExceptionHandler        ← @RestControllerAdvice               │
│  • maps exceptions → ErrorResponse + HTTP status                     │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│  SERVICE LAYER (business logic)                                      │
│  Interfaces in  service/        Implementations in  service/impl/    │
│  RegistrationService  · AuthenticationService  · AccountService      │
│  SessionService       · IbanGenerator          · PasswordGenerator   │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│  REPOSITORY LAYER                                                    │
│  CustomerRepository  ·  AccountRepository       (Spring Data JPA)    │
│  DatabaseAccessInterceptor → DatabaseOperationRateLimiter (2 ops/s)  │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│  DATABASE (H2 in-memory — stands in for the "legacy" DB)             │
│  customers  ·  accounts                                              │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. File-by-File Responsibilities

### Entry point
| File | Role |
|---|---|
| `DigitalOnboardingServiceApplication.java` | Boots Spring. |

### Database protection
| File | Role |
|---|---|
| `config/DatabaseAccessInterceptor.java` | Spring AOP advice that intercepts public repository operations before they reach JPA. |
| `config/DatabaseOperationRateLimiter.java` | Fair blocking throttle that spaces operation starts according to `app.db.max-operations-per-second` (default 2). |

### Controller layer
| File | Role |
|---|---|
| `controller/OnboardingController.java` | The 3 exposed endpoints. Also extracts + validates the `Bearer` token for `/overview`. |
| `controller/GlobalExceptionHandler.java` | Central error translation. Converts typed exceptions and validation failures into a consistent `ErrorResponse` JSON body. |

### Service layer
| File | Role |
|---|---|
| `service/RegistrationService.java` | Contract for registration. |
| `service/impl/RegistrationServiceImpl.java` | Orchestrates registration in one `@Transactional` unit: uniqueness check → password gen → IBAN gen → save `Customer` + `Account`. |
| `service/AuthenticationService.java` | Contract for login and token resolution. |
| `service/impl/AuthenticationServiceImpl.java` | Verifies credentials, delegates token creation/resolution to `SessionService`. |
| `service/SessionService.java` | Contract for session storage, kept separate so the strategy can change. |
| `service/impl/SessionServiceImpl.java` | Holds `token → username` in a `ConcurrentHashMap`. Issues UUID tokens, resolves them, rejects unknown ones. |
| `service/AccountService.java` | Contract for account reads. |
| `service/impl/AccountServiceImpl.java` | Reads the account and maps to `OverviewResponse`. |
| `service/IbanGenerator.java` | Builds a valid Dutch IBAN (`NL` + MOD-97 check digits + bank code + 10 digits). |
| `service/PasswordGenerator.java` | Produces the default password handed back at registration. |

### Domain & persistence
| File | Role |
|---|---|
| `domain/entity/Customer.java` | JPA entity. Owns identity fields + password; `@OneToOne` to `Account`. |
| `domain/entity/Account.java` | JPA entity. IBAN (unique), type, balance, currency; owning side of the relation. |
| `domain/enums/AccountType.java` | `CURRENT`. |
| `repository/CustomerRepository.java` | `existsByUsername`, `findByUsername`. |
| `repository/AccountRepository.java` | `existsByIban`, `findByCustomerUsername`. |

### DTOs & validation
| File | Role |
|---|---|
| `dto/request/RegisterRequest.java` | Input contract + all registration rules (`@NotBlank`, `@Size`, `@Pattern`, `@MinimumAge`, `@AllowedCountry`). |
| `dto/request/LoginRequest.java` | Username + password input. |
| `dto/response/RegisterResponse.java` | Returns username + generated default password. |
| `dto/response/LoginResponse.java` | Returns username + **token**. |
| `dto/response/OverviewResponse.java` | accountNumber, accountType, balance, currency. |
| `dto/response/ErrorResponse.java` | Uniform error shape: code, message, details, timestamp. |
| `validation/MinimumAge.java` + `MinimumAgeValidator.java` | Enforces 18+ from date of birth. |
| `validation/AllowedCountry.java` + `AllowedCountryValidator.java` | Enforces country ∈ config list (`NL`, `BE`). Extensible via `application.yml`. |

### Exceptions
| File | Maps to |
|---|---|
| `exception/ConflictException.java` | `409` — e.g. duplicate username |
| `exception/UnauthorizedException.java` | `401` — bad credentials / bad token |
| `exception/NotFoundException.java` | `404` — customer/account missing |
| `exception/BadRequestException.java` | `400` — business-rule failure |

---

## 3. Token Lifecycle

The token answers one question: **"which customer is making this request?"**

HTTP is stateless — `/overview` has no idea who you are. Sending the password on every
call would be bad practice, so login exchanges credentials **once** for a short opaque
handle (the token), and later requests present that handle instead.

### 3.1 Token creation (during `/login`)

```
CLIENT                 CONTROLLER              AUTH SERVICE            SESSION SERVICE        DB
  │                        │                        │                        │                │
  │ POST /login            │                        │                        │                │
  │ {username, password}   │                        │                        │                │
  ├───────────────────────►│                        │                        │                │
  │                        │ login(user, pass)      │                        │                │
  │                        ├───────────────────────►│                        │                │
  │                        │                        │ findByUsername(user)   │                │
  │                        │                        ├────────────────────────┼───────────────►│
  │                        │                        │◄───────────────────────┼────────────────┤
  │                        │                        │  Optional<Customer>    │                │
  │                        │                        │                        │                │
  │                        │                        │ ── empty?  ─► throw UnauthorizedException (401)
  │                        │                        │ ── pass mismatch? ─► throw UnauthorizedException (401)
  │                        │                        │                        │                │
  │                        │                        │ createSession(user)    │                │
  │                        │                        ├───────────────────────►│                │
  │                        │                        │                        │ token = UUID   │
  │                        │                        │                        │ map.put(token, │
  │                        │                        │                        │         user)  │
  │                        │                        │◄───────────────────────┤                │
  │                        │◄───────────────────────┤  token                 │                │
  │◄───────────────────────┤                        │                        │                │
  │ 200 {username, token}  │                        │                        │                │
```

**Note:** the same `UnauthorizedException` message is used for "user not found" and
"wrong password" on purpose — it avoids leaking which usernames exist.

### 3.2 Token usage (during `/overview`)

```
CLIENT              CONTROLLER            AUTH SERVICE        SESSION SERVICE      ACCOUNT SERVICE       DB
  │                     │                      │                    │                    │               │
  │ GET /overview       │                      │                    │                    │               │
  │ Authorization:      │                      │                    │                    │               │
  │   Bearer <token>    │                      │                    │                    │               │
  ├────────────────────►│                      │                    │                    │               │
  │                     │                      │                    │                    │               │
  │            extractBearerToken()            │                    │                    │               │
  │            • header null/no "Bearer " ─► 401                    │                    │               │
  │            • empty token             ─► 401                    │                    │               │
  │                     │                      │                    │                    │               │
  │                     │ getUsernameByToken() │                    │                    │               │
  │                     ├─────────────────────►│                    │                    │               │
  │                     │                      │ map.get(token)     │                    │               │
  │                     │                      ├───────────────────►│                    │               │
  │                     │                      │                    │ null ─► 401        │               │
  │                     │◄─────────────────────┤◄───────────────────┤                    │               │
  │                     │       username       │                    │                    │               │
  │                     │                      │                    │                    │               │
  │                     │ getAccountOverview(username)              │                    │               │
  │                     ├───────────────────────────────────────────┼───────────────────►│               │
  │                     │                                           │ findByCustomerUsername()           │
  │                     │                                           │                    ├──────────────►│
  │                     │                                           │                    │◄──────────────┤
  │                     │                                           │   absent ─► 404    │               │
  │                     │◄──────────────────────────────────────────┼────────────────────┤               │
  │◄────────────────────┤     OverviewResponse                      │                    │               │
  │ 200 {accountNumber, │                                                                                │
  │      accountType,   │                                                                                │
  │      balance,       │                                                                                │
  │      currency}      │                                                                                │
```

### 3.3 Token state machine

```
        ┌──────────────┐
        │  NO TOKEN    │
        └──────┬───────┘
               │ POST /login with valid credentials
               ▼
        ┌──────────────────────────────────┐
        │  TOKEN ISSUED                    │
        │  SessionService map:             │
        │    "a1b2-c3d4..." → "john_doe"   │
        └──────┬───────────────────────────┘
               │
       ┌───────┴────────┐
       │                │
 valid token      unknown/garbage token
       │                │
       ▼                ▼
  username          401 Unauthorized
  resolved         "Invalid or expired token"
       │
       ▼
  /overview served
```

**Current characteristics (assignment scope):**
- Opaque UUID, not a JWT — carries no data, it is purely a lookup key.
- Stored in memory → cleared on application restart.
- No expiry / no logout endpoint.
- Not encrypted (assignment explicitly waives password encryption).

---

## 4. Who uses `AuthenticationService`?

`AuthenticationService` is used in **two distinct moments**, and it is the only class the
controller talks to for identity:

```
          ┌──────────────────────────────────────────────┐
          │           OnboardingController               │
          └───────┬──────────────────────────┬───────────┘
                  │                          │
      /login      │                          │   /overview
                  ▼                          ▼
         login(username, password)   getUsernameByToken(token)
                  │                          │
                  ▼                          ▼
          ┌───────────────────────────────────────────┐
          │          AuthenticationService            │
          │                                           │
          │  login()                                  │
          │   1. CustomerRepository.findByUsername    │  ──► DB
          │   2. compare password                     │
          │   3. SessionService.createSession         │  ──► token store
          │                                           │
          │  getUsernameByToken()                     │
          │   └─ SessionService.getUsernameByToken    │  ──► token store (no DB)
          └───────────────────────────────────────────┘
```

The split matters: `login()` **issues** identity (and touches the DB once),
`getUsernameByToken()` **verifies** identity (and touches no DB at all).
That second property is deliberate — it keeps per-request load off the legacy database.

Both methods are declared on the `AuthenticationService` interface; the controller never
sees `AuthenticationServiceImpl` or the underlying `SessionService` implementation.

---

## 5. Database-operation throttling — protecting the legacy DB

The assignment limits database throughput, not HTTP request throughput. One registration
request performs several repository operations, so limiting API calls would not reliably
protect the database. Spring AOP therefore intercepts repository methods directly.

```
Controller → Service → Spring Data repository proxy
                              │
                              ▼
                    DatabaseAccessInterceptor
                              │
                              ▼
                    DatabaseOperationRateLimiter
                    wait for next 500 ms slot
                              │
                              ▼
                         DB operation
```

`DatabaseOperationRateLimiter` uses a fair lock and a monotonic clock. With
`app.db.max-operations-per-second: 2`, operation starts are spaced by 500 ms. Concurrent
callers queue in arrival order instead of starting together.

Waiting rather than returning `429` is deliberate. Registration is transactional and
requires multiple repository calls; rejecting a later call would roll back the customer
and account every time. Queuing preserves transaction completion while enforcing the
legacy throughput constraint. The expected trade-off is increased response latency under
load.

The limiter is global within one application instance. Multiple service instances would
require a shared limiter so their combined database rate remains two operations per
second. A production version should also bound queue depth and waiting time.

---

## 6. End-to-end happy path

```
 STEP 1 ── POST /register
   @Valid: name, address, username pattern, age ≥ 18, country ∈ {NL, BE}
   RegistrationService (@Transactional)
     ├─ existsByUsername? ──► ConflictException 409 if taken
     ├─ PasswordGenerator.generateDefaultPassword()
     ├─ save Customer
     ├─ IbanGenerator.generateIban()  (retry until unique, max 10)
     └─ save Account (CURRENT, 0.00, EUR)
   ⇩
   201 { username, defaultPassword, "Registration successful" }


 STEP 2 ── POST /login
   AuthenticationService.login()
     ├─ findByUsername ──► 401 if absent
     ├─ password equals? ──► 401 if mismatch
     └─ SessionService.createSession() → UUID token
   ⇩
   200 { username, token, "Login successful" }


 STEP 3 ── GET /overview   (Authorization: Bearer <token>)
   extractBearerToken() ──► 401 if header malformed
   AuthenticationService.getUsernameByToken() ──► 401 if token unknown
   AccountService.getAccountOverview()
     └─ findByCustomerUsername ──► 404 if absent
   ⇩
   200 { accountNumber, accountType, balance, currency }
```

---

## 7. Error response matrix

| Scenario | Thrown by | Status | `code` |
|---|---|---|---|
| Field validation failed | `@Valid` → `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| Under 18 | `MinimumAgeValidator` | 400 | `VALIDATION_ERROR` |
| Country not allowed | `AllowedCountryValidator` | 400 | `VALIDATION_ERROR` |
| Username taken | `RegistrationService` | 409 | `CONFLICT` |
| Bad credentials | `AuthenticationService` | 401 | `UNAUTHORIZED` |
| Missing/invalid token | `OnboardingController` / `SessionService` | 401 | `UNAUTHORIZED` |
| Account not found | `AccountService` | 404 | `NOT_FOUND` |
| IBAN generation exhausted | `RegistrationService` | 400 | `BAD_REQUEST` |
| Anything else | `GlobalExceptionHandler` | 500 | `INTERNAL_ERROR` |

Errors are rendered by `GlobalExceptionHandler` as:

```json
{
  "code": "CONFLICT",
  "message": "Username already exists",
  "details": null,
  "timestamp": 1758000000000
}
```

---

## 8. Known gaps (deliberate, for later steps)

- Tokens never expire and there is no `/logout`.
- Session store is in-memory — not viable across multiple instances.
- Passwords stored in plain text (explicitly allowed by the assignment).
- Rate limiter is per-instance; a clustered deployment would need a shared counter.

## 9. Why there is no response cache

Caching `/overview` was considered and deliberately rejected. Balance is mutable data, so
a cache without TTL and without eviction on write would serve a stale balance
indefinitely. The rate limiter already guarantees a hard ceiling on database load, which
is what the requirement actually asks for.

Caching becomes worthwhile once balance-mutating endpoints exist. The correct approach at
that point is a short TTL as a safety net **combined with** `@CacheEvict` on every write
path — not one or the other.


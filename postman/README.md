# Postman Collection Guide

## Import

1. Open Postman → **Import**
2. Select `postman/Digital-Onboarding-Service.postman_collection.json`
3. Start the service: `mvn spring-boot:run`

Run the whole thing with **Collection Runner**, or run folders individually.

---

## Structure

| Folder | Requests | Purpose |
|---|---|---|
| 1. Happy Path | 3 | register → login → overview |
| 2. Negative - Registration | 12 | validation and conflict rules |
| 3. Negative - Login | 4 | auth failures and user enumeration |
| 4. Negative - Authorization | 5 | Bearer token handling |

**24 requests covering the happy path and principal failure scenarios.**

---

## Database throttling behavior

The service does not reject API requests based on their arrival rate. Instead,
`DatabaseAccessInterceptor` queues repository operations and
`DatabaseOperationRateLimiter` starts at most two operations per second per application
instance. The collection therefore needs no pacing script.

Requests that access the database can take longer under load. At the default setting,
registration performs several repository operations and normally takes roughly 1.5
seconds. This waiting is expected and protects the legacy database without failing a
transaction halfway through. Automated throughput behavior is covered by the Java unit
and Spring integration tests rather than by status-code assertions in Postman.

---

## Negative scenarios covered

### Registration

| Test | Status | Asserted on |
|---|---|---|
| Duplicate username | 409 | `CONFLICT`, "Username already exists" |
| Under 18 | 400 | "at least 18 years old" |
| Country not allowed (DE) | 400 | "Country is not allowed" |
| Country wrong case (nl) | 400 | "uppercase ISO 3166-1" |
| Country too long (NLD) | 400 | "exactly 2 characters" |
| Username too short | 400 | "Username must be between 3 and 50" |
| Username invalid chars | 400 | "alphanumeric" |
| All fields missing | 400 | each field reported individually |
| Full name too short | 400 | "Full name must be between 2 and 100" |
| Address too short | 400 | "Address must be between 5 and 200" |
| Malformed date | 400 | `VALIDATION_ERROR`, no internal type leak |
| Future date of birth | 400 | "at least 18 years old" |

Two of these encode subtle design properties:

- **All Fields Missing** asserts the response says *"Country code is required"* and
  **not** *"Country is not allowed"*. The custom validators return `true` for `null` so
  `@NotBlank` owns the message. A regression here produces a misleading error.
- **Malformed Date** asserts the body does not contain `java.time`. Before this was
  fixed the raw Jackson message leaked internal type names to the caller.

### Login

| Test | Status | Asserted on |
|---|---|---|
| Wrong password | 401 | "Invalid username or password" |
| Unknown username | 401 | **identical** message to wrong password |
| Missing password | 400 | "Password is required" |
| Blank username | 400 | "Username is required" |

The unknown-username test stores the wrong-password message in a variable and asserts
the two are byte-identical. Divergent messages would let an attacker enumerate valid
usernames.

### Authorization

| Test | Status | Asserted on |
|---|---|---|
| No Authorization header | 401 | "Authorization header is missing", not `INTERNAL_ERROR` |
| Non-Bearer scheme (Basic) | 401 | "must use Bearer token" |
| Raw token, no `Bearer ` prefix | 401 | "must use Bearer token" |
| Empty Bearer token | 401 | no usable token |
| Unknown token | 401 | "Invalid or expired token" |

**Raw Token Without Bearer Prefix** reproduces the Swagger UI mistake — pasting a valid
token without the `Bearer ` prefix. It looks like a missing header but is not.

---

## Variables

| Variable | Default | Purpose |
|---|---|---|
| `baseUrl` | `http://localhost:8085` | API base URL |
| `authToken` | *(auto)* | set by Login |
| `username` | *(auto)* | set by Register |
| `defaultPassword` | *(auto)* | set by Register |

---

## Self-contained tests

Two requests seed their own preconditions so they pass standalone:

- **Duplicate Username** registers `duplicate_probe_user` in its pre-request script
  (ignoring whether it returns 201 or 409), then registers again to force the 409.
- **Under 18** and **Future Date Of Birth** compute dates relative to today, so they
  never expire.

Requests in folder 3 use `{{username}}` from the Happy Path — run folder 1 first.

---

## Verifying without Postman

`postman/verify-assertions.sh` mirrors every assertion using curl:

```bash
mvn spring-boot:run &
bash postman/verify-assertions.sh
```

```
RESULT  pass=55  fail=0
```

Useful in CI or when Postman/Newman is unavailable.

---

## Troubleshooting

**Folder 3 tests fail with 401 on a valid user** — `{{username}}` is empty. Run the
Happy Path folder first.

**Duplicate Username returns 201** — rerun the request once so the seeded user exists.


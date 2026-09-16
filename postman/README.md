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
| 5. Rate Limiter | 3 | legacy DB protection |

**27 requests, ~70 assertions.**

---

## The rate limit will bite you (and how the collection handles it)

The service allows **2 requests/second globally** across `/register`, `/login` and
`/overview`. Naively running 27 requests back to back means most of them return `429`
and every assertion fails for the wrong reason.

The collection solves this with a **collection-level pre-request script** that paces
requests:

```javascript
const gapMs = Number(pm.collectionVariables.get('requestGapMs')) || 700;
const last  = Number(pm.collectionVariables.get('lastRequestAt')) || 0;
const wait  = Math.max(0, gapMs - (Date.now() - last));
if (wait > 0) { setTimeout(function () {}, wait); }
pm.collectionVariables.set('lastRequestAt', Date.now() + wait);
```

700 ms between requests keeps you at ~1.4 req/sec, safely under the limit.

Set `pacingEnabled = false` to turn it off.

---

## Testing the rate limiter

Pacing makes normal tests reliable, but it is the opposite of what a limiter test needs.
The rate limiter folder bypasses it by firing bursts with `pm.sendRequest()` inside the
**pre-request script** — those calls do not go through collection-level scripts.

### Burst Test — expect 429

Pre-request fires 8 rapid requests and records each status:

```javascript
const burstSize = Number(pm.collectionVariables.get('rateLimitBurstSize')) || 8;
const statuses = [];

function fire(i, done) {
    if (i >= burstSize) { return done(); }
    pm.sendRequest({
        url: baseUrl + '/overview',
        method: 'GET',
        header: { 'Authorization': 'Bearer rate-limit-probe' }
    }, function (err, res) {
        statuses.push(err ? 0 : res.code);
        fire(i + 1, done);
    });
}

fire(0, function () {
    pm.collectionVariables.set('burstStatuses', JSON.stringify(statuses));
});
```

Requests are sequential but each takes only a few ms on localhost, so all 8 land inside
the same one-second window.

**An invalid token is used deliberately.** `ApiRateLimitFilter` runs *before* the
controller, so requests within budget return `401` and throttled ones return `429`.
That difference is exactly what the test asserts on — no valid session required.

Observed output:

```
statuses: 401 401 429 429 429 429 429 429
allowed=2  throttled=6
```

Exactly 2 allowed, matching `app.db.max-requests-per-second: 2`.

Assertions:
- at least one `429`
- some requests allowed through
- allowed count ≤ budget × 2 (margin for straddling a window boundary)
- `429` body is `{"code":"TOO_MANY_REQUESTS", ...}`

### Recovery After One Second — expect not 429

Waits 1500 ms, then asserts the request is **not** throttled and reaches the controller
(`401` for the fake token). Proves the limiter is a rolling per-second window, not a
permanent block.

### Limiter Is Global — Register Burst

Bursts `/register` instead, proving the budget is shared across endpoints rather than
per-endpoint:

```
statuses: 201 201 201 429 429 429 429 429
```

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
| `pacingEnabled` | `true` | toggle request pacing |
| `requestGapMs` | `700` | delay between requests |
| `rateLimitBurstSize` | `8` | requests per burst |
| `maxRequestsPerSecond` | `2` | must match `application.yml` |

If you change `app.db.max-requests-per-second` in `application.yml`, update
`maxRequestsPerSecond` to match.

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
RESULT  pass=62  fail=0
```

Useful in CI or when Postman/Newman is unavailable.

---

## Troubleshooting

**Everything returns 429** — pacing is off or `requestGapMs` is too low. Set
`pacingEnabled = true` and `requestGapMs = 700`. If using Collection Runner, also set a
delay of ~700 ms.

**Burst test reports no 429** — the service may be slow enough that requests span
multiple seconds. Raise `rateLimitBurstSize` to 15.

**Folder 3 tests fail with 401 on a valid user** — `{{username}}` is empty. Run the
Happy Path folder first.

**Duplicate Username returns 201** — the pre-request seed was throttled. Re-run it; the
user now exists.


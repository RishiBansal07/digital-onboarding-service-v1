# Postman Collection Guide

## Overview
This Postman collection provides a complete workflow for testing the Digital Onboarding Service API.

## Import Instructions

1. **Open Postman**
2. **Click "Import"** (top-left)
3. **Select the file:** `Digital-Onboarding-Service.postman_collection.json`
4. **Click "Import"**

## Environment Variables

The collection uses these variables (auto-populated during workflow):

| Variable | Purpose | Set By |
|----------|---------|--------|
| `baseUrl` | API base URL | Default: `http://localhost:8085` |
| `authToken` | Bearer token from login | Set by Login request (test script) |
| `username` | Registered username | Set by Register request (test script) |
| `defaultPassword` | Password from registration | Set by Register request (test script) |

## Workflow

### Step 1: Register a Customer

**Request:** `POST /register`

```json
{
  "fullName": "John Doe",
  "address": "Damrak 1, Amsterdam",
  "username": "john_doe_{{$timestamp}}",
  "dateOfBirth": "1990-05-20",
  "countryCode": "NL"
}
```

**Response (201 Created):**
```json
{
  "username": "john_doe_1234567890",
  "defaultPassword": "a1b2c3d4e5f6",
  "message": "Registration successful"
}
```

**Automation:** Test script automatically extracts `username` and `defaultPassword` and saves them to environment variables.

---

### Step 2: Login

**Request:** `POST /login`

```json
{
  "username": "{{username}}",
  "password": "{{defaultPassword}}"
}
```

**Response (200 OK):**
```json
{
  "username": "john_doe_1234567890",
  "token": "7f55c9fa-2af5-41eb-ad0c-ef8a336ee50f",
  "message": "Login successful"
}
```

**Automation:** Test script automatically extracts the `token` and saves it to `{{authToken}}` environment variable.

---

### Step 3: Get Account Overview

**Request:** `GET /overview`

**Authentication:** Bearer token (automatically added from `{{authToken}}`)

**Response (200 OK):**
```json
{
  "accountNumber": "NL91RABO0417164300",
  "accountType": "CURRENT",
  "balance": 0.00,
  "currency": "EUR"
}
```

---

## Quick Start

1. **Ensure server is running:**
   ```bash
   mvn spring-boot:run
   ```

2. **Import collection into Postman**

3. **Run requests in order:**
   - Register Customer → Login → Get Account Overview

4. **That's it!** The token is automatically passed to subsequent requests.

---

## Error Scenarios

### Duplicate Username (409 Conflict)
Register with the same username twice:
```json
{
  "code": "CONFLICT",
  "message": "Username already exists",
  "details": null,
  "timestamp": 1234567890
}
```

### Invalid Age (400 Bad Request)
Register with a customer under 18:
```json
{
  "dateOfBirth": "2015-01-01"
}
```
Response:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": "dateOfBirth: Customer must be at least 18 years old",
  "timestamp": 1234567890
}
```

### Country Not Allowed (400 Bad Request)
Register with an unsupported country:
```json
{
  "countryCode": "DE"
}
```
Response:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": "countryCode: Country is not allowed. Allowed countries: NL, BE",
  "timestamp": 1234567890
}
```

### Invalid Credentials (401 Unauthorized)
Login with wrong password:
```json
{
  "code": "UNAUTHORIZED",
  "message": "Invalid username or password",
  "details": null,
  "timestamp": 1234567890
}
```

### Missing Token (401 Unauthorized)
Call `/overview` without Authorization header:
```json
{
  "code": "UNAUTHORIZED",
  "message": "Authorization header is missing",
  "details": null,
  "timestamp": 1234567890
}
```

### Rate Limited (429 Too Many Requests)
Exceed 2 requests per second:
```json
{
  "code": "TOO_MANY_REQUESTS",
  "message": "Rate limit exceeded. Please retry shortly.",
  "details": null,
  "timestamp": 1234567890
}
```

---

## Testing Tips

1. **Use `{{$timestamp}}`** in username to ensure uniqueness:
   ```
   "username": "user_{{$timestamp}}"
   ```

2. **Change `baseUrl`** if running on a different host/port:
   - Edit collection variables → `baseUrl` → set to your API URL

3. **Check Console** (Postman → View → Show Postman Console) to see:
   - Extracted token and username
   - Request/response details

4. **Run requests individually** or create a **Collection Runner** to test the full flow

---

## Allowed Countries

Only the following country codes are accepted:
- `NL` (Netherlands)
- `BE` (Belgium)

To add more countries, edit `application.yml`:
```yaml
app:
  registration:
    allowed-countries: NL,BE,DE
```

---

## Additional Notes

- Server port: `8085`
- Default max requests per second: `2`
- Default password is auto-generated and provided in registration response
- Tokens do not expire (in-memory session store)
- Account balance starts at `0.00 EUR`


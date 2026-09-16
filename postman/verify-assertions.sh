#!/bin/bash
BASE=http://localhost:8085
pass=0
fail=0

chk() {
  if [ "$2" = "$3" ]; then
    echo "  PASS  $1"
    pass=$((pass + 1))
  else
    echo "  FAIL  $1 (expected $2, got $3)"
    fail=$((fail + 1))
  fi
}

ok() {
  if [ "$2" -eq 1 ]; then
    echo "  PASS  $1"
    pass=$((pass + 1))
  else
    echo "  FAIL  $1"
    fail=$((fail + 1))
  fi
}

code() { curl -s -o /tmp/b.json -w '%{http_code}' "$@"; }
body() { cat /tmp/b.json; }
has() { grep -q "$1" /tmp/b.json && echo 1 || echo 0; }

echo "=== 5. RATE LIMITER: burst ==="
sleep 2
statuses=""
for i in 1 2 3 4 5 6 7 8; do
  c=$(code -X GET "$BASE/overview" -H 'Authorization: Bearer rate-limit-probe')
  statuses="$statuses $c"
done
echo "  statuses:$statuses"
throttled=$(echo $statuses | tr ' ' '\n' | grep -c '^429$')
allowed=$(echo $statuses | tr ' ' '\n' | grep -v '^429$' | grep -c '^[0-9]')
echo "  allowed=$allowed throttled=$throttled"
ok "at least one 429" $([ "$throttled" -gt 0 ] && echo 1 || echo 0)
ok "some allowed through" $([ "$allowed" -gt 0 ] && echo 1 || echo 0)
ok "allowed <= budget*2 (4)" $([ "$allowed" -le 4 ] && echo 1 || echo 0)
echo "  429 body: $(body)"
ok "429 body code=TOO_MANY_REQUESTS" $(has TOO_MANY_REQUESTS)
ok "429 body has 'Rate limit exceeded'" $(has 'Rate limit exceeded')

echo "=== 5. RATE LIMITER: recovery ==="
sleep 2
c=$(code -X GET "$BASE/overview" -H 'Authorization: Bearer rate-limit-probe')
chk "recovered, reached controller (401 not 429)" "401" "$c"

echo "=== 5. RATE LIMITER: global across /register ==="
sleep 2
rstat=""
for i in 1 2 3 4 5 6 7 8; do
  c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
    -d "{\"fullName\":\"Burst User\",\"address\":\"Damrak 1, Amsterdam\",\"username\":\"burst_$$_$i\",\"dateOfBirth\":\"1990-05-20\",\"countryCode\":\"NL\"}")
  rstat="$rstat $c"
done
echo "  statuses:$rstat"
rthr=$(echo $rstat | tr ' ' '\n' | grep -c '^429$')
ok "register is rate limited too" $([ "$rthr" -gt 0 ] && echo 1 || echo 0)

echo "=== 1. HAPPY PATH ==="
sleep 2
U="john_doe_$$"
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"John Doe\",\"address\":\"Damrak 1, Amsterdam\",\"username\":\"$U\",\"dateOfBirth\":\"1990-05-20\",\"countryCode\":\"NL\"}")
chk "register -> 201" "201" "$c"
PASSW=$(sed -n 's/.*"defaultPassword":"\([^"]*\)".*/\1/p' /tmp/b.json)

sleep 1
c=$(code -X POST "$BASE/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"$PASSW\"}")
chk "login -> 200" "200" "$c"
TOKEN=$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' /tmp/b.json)

sleep 1
c=$(code -X GET "$BASE/overview" -H "Authorization: Bearer $TOKEN")
chk "overview -> 200" "200" "$c"
echo "  $(body)"
ok "IBAN matches NL pattern" $(grep -qE '"accountNumber":"NL[0-9]{2}[A-Z]{4}[0-9]{10}"' /tmp/b.json && echo 1 || echo 0)

echo "=== 2. NEGATIVE - REGISTRATION ==="
sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"John Doe\",\"address\":\"Damrak 1, Amsterdam\",\"username\":\"$U\",\"dateOfBirth\":\"1990-05-20\",\"countryCode\":\"NL\"}")
chk "duplicate username -> 409" "409" "$c"
ok "message 'Username already exists'" $(has 'Username already exists')

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d '{"fullName":"Too Young","address":"Damrak 1, Amsterdam","username":"too_young_user","dateOfBirth":"2016-01-01","countryCode":"NL"}')
chk "under 18 -> 400" "400" "$c"
ok "details 'at least 18 years old'" $(has 'at least 18 years old')

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d '{"fullName":"Wrong Country","address":"Alexanderplatz 1, Berlin","username":"wrong_country_user","dateOfBirth":"1990-05-20","countryCode":"DE"}')
chk "country DE -> 400" "400" "$c"
ok "details 'Country is not allowed'" $(has 'Country is not allowed')

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d '{"fullName":"Lower Case","address":"Damrak 1, Amsterdam","username":"lowercase_country","dateOfBirth":"1990-05-20","countryCode":"nl"}')
chk "country lowercase -> 400" "400" "$c"
ok "details 'uppercase ISO 3166-1'" $(has 'uppercase ISO 3166-1')

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d '{"fullName":"Long Country","address":"Damrak 1, Amsterdam","username":"long_country","dateOfBirth":"1990-05-20","countryCode":"NLD"}')
chk "country NLD -> 400" "400" "$c"
ok "details 'exactly 2 characters'" $(has 'exactly 2 characters')

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d '{"fullName":"Short Username","address":"Damrak 1, Amsterdam","username":"ab","dateOfBirth":"1990-05-20","countryCode":"NL"}')
chk "username too short -> 400" "400" "$c"
ok "details 'Username must be between 3 and 50'" $(has 'Username must be between 3 and 50')

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d '{"fullName":"Bad Chars","address":"Damrak 1, Amsterdam","username":"john doe!@#","dateOfBirth":"1990-05-20","countryCode":"NL"}')
chk "username bad chars -> 400" "400" "$c"
ok "details 'alphanumeric'" $(has 'alphanumeric')

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' -d '{}')
chk "empty body -> 400" "400" "$c"
echo "  $(body)"
ok "details 'Full name is required'" $(has 'Full name is required')
ok "details 'Address is required'" $(has 'Address is required')
ok "details 'Username is required'" $(has 'Username is required')
ok "details 'Date of birth is required'" $(has 'Date of birth is required')
ok "details 'Country code is required'" $(has 'Country code is required')
ok "does NOT say 'Country is not allowed'" $(grep -q 'Country is not allowed' /tmp/b.json && echo 0 || echo 1)

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d '{"fullName":"X","address":"Damrak 1, Amsterdam","username":"short_name_user","dateOfBirth":"1990-05-20","countryCode":"NL"}')
chk "full name too short -> 400" "400" "$c"
ok "details 'Full name must be between 2 and 100'" $(has 'Full name must be between 2 and 100')

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d '{"fullName":"Short Address","address":"A1","username":"short_addr_user","dateOfBirth":"1990-05-20","countryCode":"NL"}')
chk "address too short -> 400" "400" "$c"
ok "details 'Address must be between 5 and 200'" $(has 'Address must be between 5 and 200')

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d '{"fullName":"Bad Date","address":"Damrak 1, Amsterdam","username":"bad_date_user","dateOfBirth":"20-05-1990","countryCode":"NL"}')
chk "malformed date -> 400" "400" "$c"
echo "  $(body)"
ok "code=VALIDATION_ERROR" $(has 'VALIDATION_ERROR')
ok "does not leak java.time internals" $(grep -q 'java.time' /tmp/b.json && echo 0 || echo 1)

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' -d '{ not valid json ')
chk "malformed JSON -> 400" "400" "$c"
ok "code=VALIDATION_ERROR" $(has 'VALIDATION_ERROR')

sleep 1
c=$(code -X POST "$BASE/register" -H 'Content-Type: application/json' \
  -d '{"fullName":"Future Person","address":"Damrak 1, Amsterdam","username":"future_dob_user","dateOfBirth":"2027-01-01","countryCode":"NL"}')
chk "future dob -> 400" "400" "$c"
ok "details 'at least 18 years old'" $(has 'at least 18 years old')

echo "=== 3. NEGATIVE - LOGIN ==="
sleep 1
c=$(code -X POST "$BASE/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"definitely-not-the-password\"}")
chk "wrong password -> 401" "401" "$c"
ok "message 'Invalid username or password'" $(has 'Invalid username or password')

sleep 1
c=$(code -X POST "$BASE/login" -H 'Content-Type: application/json' \
  -d '{"username":"no_such_user_at_all","password":"any-password"}')
chk "unknown user -> 401" "401" "$c"
ok "same generic message (no enumeration)" $(has 'Invalid username or password')

sleep 1
c=$(code -X POST "$BASE/login" -H 'Content-Type: application/json' -d "{\"username\":\"$U\"}")
chk "missing password -> 400" "400" "$c"
ok "details 'Password is required'" $(has 'Password is required')

sleep 1
c=$(code -X POST "$BASE/login" -H 'Content-Type: application/json' \
  -d '{"username":"   ","password":"some-password"}')
chk "blank username -> 400" "400" "$c"
ok "details 'Username is required'" $(has 'Username is required')

echo "=== 4. NEGATIVE - AUTHORIZATION ==="
sleep 1
c=$(code -X GET "$BASE/overview")
chk "no header -> 401" "401" "$c"
ok "message 'Authorization header is missing'" $(has 'Authorization header is missing')
ok "not INTERNAL_ERROR" $(grep -q 'INTERNAL_ERROR' /tmp/b.json && echo 0 || echo 1)

sleep 1
c=$(code -X GET "$BASE/overview" -H 'Authorization: Basic dXNlcjpwYXNz')
chk "basic scheme -> 401" "401" "$c"
ok "message 'must use Bearer token'" $(has 'must use Bearer token')

sleep 1
c=$(code -X GET "$BASE/overview" -H "Authorization: $TOKEN")
chk "raw token no prefix -> 401" "401" "$c"
ok "message 'must use Bearer token'" $(has 'must use Bearer token')

sleep 1
c=$(code -X GET "$BASE/overview" -H 'Authorization: Bearer    ')
chk "empty bearer -> 401" "401" "$c"
# curl trims trailing header whitespace, so this can hit either rejection branch.
ok "rejected with a no-usable-token message" $(grep -qE 'Bearer token is missing|must use Bearer token' /tmp/b.json && echo 1 || echo 0)

sleep 1
c=$(code -X GET "$BASE/overview" -H 'Authorization: Bearer 00000000-0000-0000-0000-000000000000')
chk "unknown token -> 401" "401" "$c"
ok "message 'Invalid or expired token'" $(has 'Invalid or expired token')

echo
echo "================================"
echo "RESULT  pass=$pass  fail=$fail"
echo "================================"
[ "$fail" -eq 0 ]


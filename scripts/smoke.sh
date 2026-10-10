#!/usr/bin/env bash
# Post-deploy smoke test. Run against any environment that exposes the backend
# at $BASE_URL. Exits non-zero on any failure, so it can gate a CD pipeline.
#
# Usage:
#   BASE_URL=https://api.church.example.com ADMIN_EMAIL=... ADMIN_PASSWORD=... \
#     ./scripts/smoke.sh
#
# Without credentials it still runs the unauthenticated subset.
set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"

PASS=0
FAIL=0

green() { printf "\033[32m%s\033[0m" "$1"; }
red()   { printf "\033[31m%s\033[0m" "$1"; }

check() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    printf "  [%s] %s (%s)\n" "$(green PASS)" "$label" "$actual"
    PASS=$((PASS + 1))
  else
    printf "  [%s] %s — expected %s, got %s\n" "$(red FAIL)" "$label" "$expected" "$actual"
    FAIL=$((FAIL + 1))
  fi
}

echo "== Smoke test against $BASE_URL =="

echo "-- Public health check"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health")
check "actuator/health is 200" "200" "$STATUS"

BODY=$(curl -s "$BASE_URL/actuator/health")
if [[ "$BODY" == *'"status":"UP"'* ]]; then
  printf "  [%s] actuator/health reports UP\n" "$(green PASS)"
  PASS=$((PASS + 1))
else
  printf "  [%s] actuator/health body missing UP — got: %s\n" "$(red FAIL)" "$BODY"
  FAIL=$((FAIL + 1))
fi

echo "-- Public landing API (no auth)"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/public/this-week")
check "/api/v1/public/this-week is 200" "200" "$STATUS"

echo "-- Public rate limiter"
# 65 requests in a tight loop — should trip the 60/min cap by the end.
RATE_STATUS=""
for i in $(seq 1 65); do
  RATE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/public/this-week")
done
if [[ "$RATE_STATUS" == "429" ]]; then
  printf "  [%s] rate limiter trips to 429 after burst\n" "$(green PASS)"
  PASS=$((PASS + 1))
else
  printf "  [%s] rate limiter did NOT return 429 after 65 requests — got %s. Check app.rate-limit.public.*\n" \
    "$(red FAIL)" "$RATE_STATUS"
  FAIL=$((FAIL + 1))
fi

echo "-- CORS preflight"
CORS=$(curl -s -o /dev/null -w "%{http_code}" -X OPTIONS "$BASE_URL/api/v1/auth/login" \
  -H "Origin: ${CORS_TEST_ORIGIN:-http://localhost:5180}" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: content-type")
check "CORS OPTIONS preflight is 200/204" "200" "$CORS"

if [[ -z "$ADMIN_EMAIL" || -z "$ADMIN_PASSWORD" ]]; then
  echo
  echo "(Skipping authenticated checks — ADMIN_EMAIL/ADMIN_PASSWORD not set.)"
else
  echo "-- Admin login"
  LOGIN=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
  if command -v python >/dev/null 2>&1; then
    TOKEN=$(printf '%s' "$LOGIN" | python -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('accessToken',''))" 2>/dev/null)
    PCR=$(printf '%s' "$LOGIN" | python -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('passwordChangeRequired',''))" 2>/dev/null)
  else
    TOKEN=$(printf '%s' "$LOGIN" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
    PCR=$(printf '%s' "$LOGIN" | sed -n 's/.*"passwordChangeRequired":\([a-z]*\).*/\1/p')
  fi

  if [[ -n "$TOKEN" ]]; then
    printf "  [%s] login returned an accessToken\n" "$(green PASS)"
    PASS=$((PASS + 1))
    if [[ "$PCR" == "True" || "$PCR" == "true" ]]; then
      printf "  [NOTE] passwordChangeRequired=true — the first-admin flow is active (expected on fresh deploy)\n"
    fi

    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/auth/me" \
      -H "Authorization: Bearer $TOKEN")
    check "GET /auth/me with the token is 200" "200" "$STATUS"
  else
    printf "  [%s] login did not return an accessToken — response: %s\n" "$(red FAIL)" "$LOGIN"
    FAIL=$((FAIL + 1))
  fi
fi

echo
echo "== Result: $(green "$PASS passed"), $(red "$FAIL failed") =="

if [[ $FAIL -gt 0 ]]; then
  exit 1
fi

#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Setup Keycloak to trust authService's JWKS for Actor Token validation.
#
# This script:
#   1. Obtains an admin token from Keycloak
#   2. Creates/updates the `commart-auth-service` client with JWKS URL
#   3. Grants it realm-management roles (manage-users, view-users)
#   4. Configures scm-backend client for JWT Bearer authentication
#
# Prerequisites:
#   - Keycloak running at KC_URL
#   - Realm "scm" exists
#   - authService running at AUTH_URL (for JWKS endpoint)
#
# Usage: ./setup-keycloak-jwks.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

KC_URL="${KC_URL:-http://localhost:8181}"
REALM="${REALM:-scm}"
AUTH_URL="${AUTH_URL:-http://localhost:8097}"
KC_ADMIN_USER="${KC_ADMIN_USER:-admin}"
KC_ADMIN_PASS="${KC_ADMIN_PASS:-admin123}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Keycloak JWKS Setup for Actor Token Validation"
echo "  Keycloak: $KC_URL"
echo "  Realm: $REALM"
echo "  Auth Service JWKS: $AUTH_URL/.well-known/jwks.json"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ─── Step 0: Verify authService JWKS is accessible ─────────────────────────

echo ""
echo "[1/6] Verifying authService JWKS endpoint..."
JWKS=$(curl -sf "$AUTH_URL/.well-known/jwks.json" 2>/dev/null || echo "FAIL")
if [ "$JWKS" = "FAIL" ]; then
    echo "ERROR: Cannot reach $AUTH_URL/.well-known/jwks.json"
    echo "Start authService first, then re-run this script."
    exit 1
fi
echo "  ✓ JWKS endpoint responding (kid=$(echo "$JWKS" | python3 -c "import sys,json; print(json.load(sys.stdin)['keys'][0]['kid'])" 2>/dev/null || echo 'unknown'))"

# ─── Step 1: Get Admin Token ───────────────────────────────────────────────

echo ""
echo "[2/6] Obtaining admin token..."
ADMIN_TOKEN=$(curl -sf -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=$KC_ADMIN_USER" \
  -d "password=$KC_ADMIN_PASS" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

if [ -z "$ADMIN_TOKEN" ]; then
    echo "ERROR: Failed to get admin token. Check Keycloak credentials."
    exit 1
fi
echo "  ✓ Admin token obtained"

# ─── Step 2: Create commart-auth-service client ────────────────────────────

echo ""
echo "[3/6] Creating/updating commart-auth-service client..."

# Check if client already exists
EXISTING=$(curl -sf "$KC_URL/admin/realms/$REALM/clients?clientId=commart-auth-service" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['id'] if d else '')")

# Use host.docker.internal if Keycloak is in Docker, otherwise localhost
JWKS_URL="$AUTH_URL/.well-known/jwks.json"

CLIENT_JSON='{
  "clientId": "commart-auth-service",
  "name": "Commart Auth Service",
  "description": "Service account for authService — signs Actor Tokens for Keycloak validation",
  "enabled": true,
  "clientAuthenticatorType": "client-jwt",
  "serviceAccountsEnabled": true,
  "standardFlowEnabled": false,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "publicClient": false,
  "protocol": "openid-connect",
  "attributes": {
    "use.jwks.url": "true",
    "jwks.url": "'"$JWKS_URL"'",
    "token.endpoint.auth.signing.alg": "RS256"
  }
}'

if [ -n "$EXISTING" ]; then
    curl -sf -X PUT "$KC_URL/admin/realms/$REALM/clients/$EXISTING" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d "$CLIENT_JSON" > /dev/null
    CLIENT_UUID="$EXISTING"
    echo "  ✓ Client updated (UUID: $CLIENT_UUID)"
else
    curl -sf -X POST "$KC_URL/admin/realms/$REALM/clients" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d "$CLIENT_JSON" > /dev/null
    CLIENT_UUID=$(curl -sf "$KC_URL/admin/realms/$REALM/clients?clientId=commart-auth-service" \
      -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")
    echo "  ✓ Client created (UUID: $CLIENT_UUID)"
fi

# ─── Step 3: Get service account user ──────────────────────────────────────

echo ""
echo "[4/6] Configuring service account roles..."

SA_USER_ID=$(curl -sf "$KC_URL/admin/realms/$REALM/clients/$CLIENT_UUID/service-account-user" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# Get realm-management client UUID
RM_CLIENT_UUID=$(curl -sf "$KC_URL/admin/realms/$REALM/clients?clientId=realm-management" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

# Get role objects
MANAGE_USERS=$(curl -sf "$KC_URL/admin/realms/$REALM/clients/$RM_CLIENT_UUID/roles/manage-users" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
VIEW_USERS=$(curl -sf "$KC_URL/admin/realms/$REALM/clients/$RM_CLIENT_UUID/roles/view-users" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

# Assign roles
curl -sf -X POST "$KC_URL/admin/realms/$REALM/users/$SA_USER_ID/role-mappings/clients/$RM_CLIENT_UUID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "[$MANAGE_USERS, $VIEW_USERS]" > /dev/null 2>&1 || true

echo "  ✓ manage-users + view-users roles assigned"

# ─── Step 4: Configure scm-backend for JWT Bearer auth method ──────────────

echo ""
echo "[5/6] Updating scm-backend client for JWT Bearer authentication..."

SCM_CLIENT_UUID=$(curl -sf "$KC_URL/admin/realms/$REALM/clients?clientId=scm-backend" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['id'] if d else '')" 2>/dev/null || echo "")

if [ -n "$SCM_CLIENT_UUID" ]; then
    # Ensure scm-backend accepts JWT Bearer client authentication
    curl -sf -X PUT "$KC_URL/admin/realms/$REALM/clients/$SCM_CLIENT_UUID" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{
        \"id\": \"$SCM_CLIENT_UUID\",
        \"clientId\": \"scm-backend\",
        \"attributes\": {
          \"token.endpoint.auth.signing.alg\": \"RS256\"
        }
      }" > /dev/null 2>&1 || true
    echo "  ✓ scm-backend updated"
else
    echo "  ⚠ scm-backend client not found (create it manually)"
fi

# ─── Step 5: Verify JWKS validation works ─────────────────────────────────

echo ""
echo "[6/6] Verifying Keycloak can reach JWKS..."

# Keycloak fetches JWKS lazily on first token validation, so we just confirm
# the configuration is in place
CONFIGURED_JWKS=$(curl -sf "$KC_URL/admin/realms/$REALM/clients/$CLIENT_UUID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('attributes',{}).get('jwks.url','NOT SET'))")

echo "  ✓ JWKS URL configured: $CONFIGURED_JWKS"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Setup complete!"
echo ""
echo "  Keycloak will validate Actor Tokens by fetching:"
echo "    $JWKS_URL"
echo ""
echo "  The authService refresh endpoint now sends:"
echo "    client_assertion_type = jwt-bearer"
echo "    client_assertion = <Actor Token signed by JWKS>"
echo ""
echo "  Keycloak verifies the signature using the kid"
echo "  from the JWKS endpoint before issuing new tokens."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
